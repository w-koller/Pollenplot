package io.github.wkoller.pollenplot.data

import io.github.wkoller.pollenplot.data.kb.KnowledgeRepository
import io.github.wkoller.pollenplot.data.prefs.SettingsStore
import io.github.wkoller.pollenplot.domain.garden.GardenPlant
import io.github.wkoller.pollenplot.domain.garden.PlantFacts
import io.github.wkoller.pollenplot.domain.garden.PlantStatus
import io.github.wkoller.pollenplot.domain.garden.Zone
import io.github.wkoller.pollenplot.domain.garden.ZoneBadge
import io.github.wkoller.pollenplot.domain.garden.ZoneBadges
import io.github.wkoller.pollenplot.domain.plants.AuState
import io.github.wkoller.pollenplot.domain.plants.CareProfile
import io.github.wkoller.pollenplot.domain.plants.CareResolver
import io.github.wkoller.pollenplot.domain.plants.EcoRole
import io.github.wkoller.pollenplot.domain.plants.EcoRoles
import io.github.wkoller.pollenplot.domain.plants.KnowledgeBase
import io.github.wkoller.pollenplot.domain.plants.ResolvedCare
import io.github.wkoller.pollenplot.domain.plants.Species
import io.github.wkoller.pollenplot.domain.weeds.WeedStatus
import io.github.wkoller.pollenplot.domain.wildlife.Animal
import io.github.wkoller.pollenplot.domain.wildlife.Sighting
import io.github.wkoller.pollenplot.weeds.WeedData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Where the garden is, as far as badges are concerned: its state, which decides what is a local native, and the weed
 * lists for its area, which decide what is a weed there.
 */
data class Locality(val state: AuState?, val weeds: WeedData) {
    /** A species' weed status in the garden's area, found by its name or any of its synonyms. */
    fun weed(species: Species): WeedStatus = weeds.status(listOf(species.name) + species.synonyms)

    /** The weed status of a plant the knowledge base does not have, by its scientific name. */
    fun weed(name: String): WeedStatus = weeds.status(listOf(name))

    /** A species' badges in this garden. */
    fun roles(species: Species, care: CareProfile): List<EcoRole> = EcoRoles.of(species, care, state, weedRisk = weed(species).isWeed)

    companion object {
        val NONE = Locality(null, WeedData.EMPTY)
    }
}

/** A plant as the screens show it: the record, its species and resolved care, its eco-roles and its zone. */
data class PlantView(
    val plant: GardenPlant,
    val species: Species?,
    val care: ResolvedCare?,
    val roles: List<EcoRole>,
    val zoneName: String?,
    /** Its weed status in the garden's area. */
    val weed: WeedStatus = WeedStatus.NONE,
) {
    /** The user's own name for the plant, or else its common name, or else its scientific name. */
    val title: String get() = plant.customName ?: species?.displayName ?: plant.speciesName.orEmpty()

    /** The scientific name, shown under the title when the title is something else. */
    val scientificName: String? get() = species?.name?.takeIf { it != title }

    val isGrowing: Boolean get() = plant.status == PlantStatus.GROWING
}

/** A sighting with its animal in the knowledge base, when it is there, and the names of its zone and plant. */
data class SightingView(val sighting: Sighting, val animal: Animal?, val zoneName: String?, val plantTitle: String?) {
    /** The animal's common name, or the name it was recorded under. */
    val title: String get() = animal?.displayName ?: sighting.label

    val scientificName: String? get() = animal?.name?.takeIf { it != title }

    /** The kind it is of, as [io.github.wkoller.pollenplot.domain.wildlife.Biodiversity.keyOf] names it. */
    val key: String get() = animal?.name ?: KnowledgeBase.normalise(sighting.speciesName ?: sighting.label)
}

/** A zone with the plants growing in it, the zone's badges and the animals seen in it, the latest first. */
data class ZoneView(val zone: Zone, val plants: List<PlantView>, val badges: List<ZoneBadge>, val sightings: List<SightingView> = emptyList())

/** The garden for the screens: records joined with the knowledge base, the garden's state and its weed lists. */
class GardenViews(repository: GardenRepository, knowledge: KnowledgeRepository, settings: SettingsStore, weeds: Flow<WeedData>) {
    val locality: Flow<Locality> = combine(settings.settings.map { it.gardenState }.distinctUntilChanged(), weeds) { state, data -> Locality(state, data) }

    val plants: Flow<List<PlantView>> = combine(
        repository.plantList,
        repository.zoneList,
        knowledge.knowledge,
        locality,
    ) { plants, zones, kb, locality ->
        val zoneNames = zones.associate { it.id to it.name }
        plants.map { view(it, kb, locality, zoneNames) }
    }

    /** Every sighting, the most recent first. */
    val sightings: Flow<List<SightingView>> = combine(repository.sightingList, repository.zoneList, plants, knowledge.fauna) { sightings, zones, plants, fauna ->
        val zoneNames = zones.associate { it.id to it.name }
        val plantTitles = plants.associate { it.plant.id to it.title }
        sightings.map { sighting ->
            SightingView(
                sighting,
                fauna?.animal(sighting.speciesName ?: sighting.label),
                sighting.zoneId?.let(zoneNames::get),
                sighting.plantId?.let(plantTitles::get),
            )
        }
    }

    val zones: Flow<List<ZoneView>> = combine(repository.zoneList, plants, sightings) { zones, plants, sightings ->
        val byZone = plants.filter { it.isGrowing }.groupBy { it.plant.zoneId }
        val seenIn = sightings.groupBy { it.sighting.zoneId }
        zones.map { zone ->
            val inZone = byZone[zone.id].orEmpty()
            val seen = seenIn[zone.id].orEmpty()
            val groups = seen.map { it.animal?.group ?: it.sighting.group }.toSet()
            ZoneView(zone, inZone, ZoneBadges.of(inZone.map { PlantFacts(it.care?.care?.water, it.roles) }, groups), seen)
        }
    }

    fun plant(id: String): Flow<PlantView?> = plants.map { list -> list.firstOrNull { it.plant.id == id } }

    fun zone(id: String): Flow<ZoneView?> = zones.map { list -> list.firstOrNull { it.zone.id == id } }

    companion object {
        fun view(plant: GardenPlant, kb: KnowledgeBase?, locality: Locality, zoneNames: Map<String, String>): PlantView {
            val species = plant.speciesName?.let { kb?.species(it) }
            val care = if (kb != null && species != null) CareResolver.resolve(kb, species) else null
            val weed = species?.let(locality::weed) ?: plant.speciesName?.let(locality::weed) ?: WeedStatus.NONE
            val roles = when {
                care != null -> EcoRoles.of(species, care.care, locality.state, weedRisk = weed.isWeed)
                weed.isWeed -> listOf(EcoRole.WEED_RISK)
                else -> emptyList()
            }
            return PlantView(plant, species, care, roles, plant.zoneId?.let(zoneNames::get), weed)
        }
    }
}
