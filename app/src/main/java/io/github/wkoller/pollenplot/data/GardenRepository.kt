package io.github.wkoller.pollenplot.data

import androidx.room.withTransaction
import io.github.wkoller.pollenplot.data.db.LinkEntity
import io.github.wkoller.pollenplot.data.db.PollenplotDatabase
import io.github.wkoller.pollenplot.data.db.SearchEntity
import io.github.wkoller.pollenplot.data.db.SearchHit
import io.github.wkoller.pollenplot.data.db.toDomain
import io.github.wkoller.pollenplot.data.db.toEntity
import io.github.wkoller.pollenplot.domain.garden.GardenPlant
import io.github.wkoller.pollenplot.domain.garden.PlotLayout
import io.github.wkoller.pollenplot.domain.garden.Zone
import io.github.wkoller.pollenplot.domain.garden.ZoneKind
import io.github.wkoller.pollenplot.domain.garden.ZoneRect
import io.github.wkoller.pollenplot.domain.plants.AuState
import io.github.wkoller.pollenplot.domain.plants.CareResolver
import io.github.wkoller.pollenplot.domain.plants.EcoRole
import io.github.wkoller.pollenplot.domain.plants.EcoRoles
import io.github.wkoller.pollenplot.domain.plants.KnowledgeBase
import io.github.wkoller.pollenplot.domain.semantic.GardenGraph
import io.github.wkoller.pollenplot.domain.semantic.JsonLd
import io.github.wkoller.pollenplot.domain.semantic.Link
import io.github.wkoller.pollenplot.domain.semantic.SearchText
import io.github.wkoller.pollenplot.domain.semantic.Triple
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

/**
 * Zones, plants and the links between records. Every write also updates the search index, so "Search your garden"
 * always matches what is stored, and every record can be exported as linked data.
 */
class GardenRepository(
    private val db: PollenplotDatabase,
    private val clock: Clock,
    private val knowledge: suspend () -> KnowledgeBase,
    private val gardenState: suspend () -> AuState?,
) {
    private val zones = db.zoneDao()
    private val plants = db.plantDao()
    private val links = db.linkDao()
    private val search = db.searchDao()
    private val layoutLock = Mutex()

    val zoneList: Flow<List<Zone>> = zones.observeAll().map { list -> list.map { it.toDomain() } }
    val plantList: Flow<List<GardenPlant>> = plants.observeAll().map { list -> list.map { it.toDomain() } }

    private fun now() = clock.millis()

    suspend fun zone(id: String): Zone? = zones.get(id)?.toDomain()

    suspend fun plant(id: String): GardenPlant? = plants.get(id)?.toDomain()

    /** Adds a zone in the first free spot on the canvas and returns it. */
    suspend fun addZone(name: String, kind: ZoneKind): Zone {
        val existing = zones.all().map { it.toDomain().rect }
        val zone = Zone(UUID.randomUUID().toString(), name.trim(), kind, PlotLayout.placeNew(existing))
        val time = now()
        db.withTransaction {
            zones.upsert(zone.toEntity(time, time))
            index(zone)
        }
        return zone
    }

    /** Saves changes to a zone. A new name is also written into the search text of the plants in it. */
    suspend fun updateZone(zone: Zone) {
        val old = zones.get(zone.id) ?: return
        db.withTransaction {
            zones.upsert(zone.toEntity(old.createdAt, now()))
            index(zone)
            if (old.name != zone.name) plants.inZone(zone.id).forEach { index(it.toDomain()) }
        }
    }

    suspend fun moveZone(id: String, rect: ZoneRect) = adjustZone(id) { rect }

    /**
     * Changes a zone's place or size starting from where it is now, so quick taps on the move and resize buttons build
     * on each other instead of each starting from an older position. The result snaps to the grid.
     */
    suspend fun adjustZone(id: String, change: (ZoneRect) -> ZoneRect) = layoutLock.withLock {
        val zone = zone(id) ?: return@withLock
        updateZone(zone.copy(rect = PlotLayout.snapped(change(zone.rect))))
    }

    /** Removes a zone. Its plants stay in the garden without a zone. */
    suspend fun deleteZone(id: String) = db.withTransaction {
        val affected = plants.inZone(id).map { it.toDomain() }
        zones.delete(id)
        links.deleteFor(id)
        search.delete(id)
        affected.forEach { index(it.copy(zoneId = null)) }
    }

    suspend fun addPlant(
        speciesName: String?,
        customName: String?,
        zoneId: String?,
        quantity: Int = 1,
        plantedOn: LocalDate? = null,
    ): GardenPlant {
        val plant = GardenPlant(UUID.randomUUID().toString(), speciesName, customName?.trim()?.ifBlank { null }, zoneId, quantity, plantedOn)
        val time = now()
        db.withTransaction {
            plants.upsert(plant.toEntity(time, time))
            index(plant)
        }
        return plant
    }

    suspend fun updatePlant(plant: GardenPlant) {
        val old = plants.get(plant.id) ?: return
        db.withTransaction {
            plants.upsert(plant.toEntity(old.createdAt, now()))
            index(plant)
        }
    }

    suspend fun deletePlant(id: String) = db.withTransaction {
        plants.delete(id)
        links.deleteFor(id)
        search.delete(id)
    }

    suspend fun link(link: Link) = links.upsert(
        LinkEntity(link.subjectId, link.predicate, link.objectId, link.provenance.name, link.confidenceBp, now()),
    )

    /**
     * Finds records whose words start with each word of [query]. The words are reduced to letters and digits, so
     * nothing the user types can break the search syntax.
     */
    suspend fun search(query: String, limit: Int = 50): List<SearchHit> {
        val words = query.lowercase().split(Regex("""[^\p{L}\p{N}]+""")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        return search.search(words.joinToString(" ") { "$it*" }, limit)
    }

    /** Rebuilds the whole search index, for example after the knowledge base or the garden's state changes. */
    suspend fun reindexAll() = db.withTransaction {
        search.clear()
        zones.all().forEach { index(it.toDomain()) }
        plants.all().forEach { index(it.toDomain()) }
    }

    /** The eco-roles of a plant in this garden. */
    suspend fun rolesOf(plant: GardenPlant): List<EcoRole> {
        val kb = knowledge()
        val species = plant.speciesName?.let(kb::species) ?: return emptyList()
        return EcoRoles.of(species, CareResolver.resolve(kb, species).care, gardenState())
    }

    /** Every record as JSON-LD, using Darwin Core, schema.org and Pollenplot's own terms. */
    suspend fun exportJsonLd(): String {
        val kb = knowledge()
        val triples = mutableListOf<Triple>()
        zones.all().forEach { triples += GardenGraph.zone(it.toDomain()) }
        plants.all().forEach { entity ->
            val plant = entity.toDomain()
            triples += GardenGraph.plant(plant, plant.speciesName?.let(kb::species), rolesOf(plant))
        }
        links.all().forEach { triples += GardenGraph.link(it.toDomain()) }
        return JsonLd.document(triples).toString()
    }

    private suspend fun index(zone: Zone) {
        val doc = SearchText.zone(zone)
        search.replace(SearchEntity(recordId = doc.id, kind = doc.kind.name, title = doc.title, body = doc.body))
    }

    private suspend fun index(plant: GardenPlant) {
        val kb = knowledge()
        val species = plant.speciesName?.let(kb::species)
        val zoneName = plant.zoneId?.let { zones.get(it)?.name }
        val doc = SearchText.plant(plant, species, zoneName, rolesOf(plant))
        search.replace(SearchEntity(recordId = doc.id, kind = doc.kind.name, title = doc.title, body = doc.body))
    }
}
