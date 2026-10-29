package io.github.wkoller.pollenplot.data

import androidx.room.withTransaction
import io.github.wkoller.pollenplot.data.db.HotelEntity
import io.github.wkoller.pollenplot.data.db.HotelObservationEntity
import io.github.wkoller.pollenplot.data.db.HotelSectionEntity
import io.github.wkoller.pollenplot.data.db.HotelUpkeepEntity
import io.github.wkoller.pollenplot.data.db.PollenplotDatabase
import io.github.wkoller.pollenplot.data.db.SearchEntity
import io.github.wkoller.pollenplot.domain.bees.stingless.Facing
import io.github.wkoller.pollenplot.domain.hotels.BeeHotel
import io.github.wkoller.pollenplot.domain.hotels.HotelMaterial
import io.github.wkoller.pollenplot.domain.hotels.HotelObservation
import io.github.wkoller.pollenplot.domain.hotels.HotelPest
import io.github.wkoller.pollenplot.domain.hotels.HotelSection
import io.github.wkoller.pollenplot.domain.hotels.HotelUpkeep
import io.github.wkoller.pollenplot.domain.hotels.Seal
import io.github.wkoller.pollenplot.domain.hotels.UpkeepKind
import io.github.wkoller.pollenplot.domain.semantic.HotelGraph
import io.github.wkoller.pollenplot.domain.semantic.SearchText
import io.github.wkoller.pollenplot.domain.semantic.Triple
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

/**
 * Bee and insect hotels, kept apart from the hives: each hotel with its sections, the looks at it and its upkeep.
 * Hotels are in the garden's search index; a hotel's records go with it when it is deleted.
 */
class HotelRepository(private val db: PollenplotDatabase, private val clock: Clock) {
    private val dao = db.hotelDao()
    private val search = db.searchDao()
    private val zones = db.zoneDao()

    private fun now() = clock.millis()

    val hotels: Flow<List<BeeHotel>> = combine(dao.observeHotels(), dao.observeSections()) { hotels, sections ->
        val byHotel = sections.groupBy { it.hotelId }
        hotels.map { it.toDomain(byHotel[it.id].orEmpty()) }
    }

    val observations: Flow<List<HotelObservation>> = dao.observeObservations().map { list -> list.map { it.toDomain() } }

    val upkeep: Flow<List<HotelUpkeep>> = dao.observeUpkeep().map { list ->
        list.map { HotelUpkeep(it.id, it.hotelId, LocalDate.ofEpochDay(it.dateEpochDay), UpkeepKind.valueOf(it.kind), it.notes) }
    }

    /** A new record's key, for a form that saves a new record. */
    fun newRecordId(): String = UUID.randomUUID().toString()

    /** Adds [hotel], or replaces the hotel with its id and its sections, and puts it in the search index. */
    suspend fun saveHotel(hotel: BeeHotel) = db.withTransaction {
        val trimmed = hotel.copy(name = hotel.name.trim(), notes = hotel.notes.trim())
        val createdAt = dao.hotel(hotel.id)?.createdAt ?: now()
        dao.upsertHotel(
            HotelEntity(
                trimmed.id, trimmed.name, trimmed.zoneId, trimmed.facing?.name, trimmed.heightMm, trimmed.installedOn?.toEpochDay(), trimmed.notes,
                createdAt, now(),
            ),
        )
        dao.deleteSections(hotel.id)
        dao.insertSections(
            trimmed.sections.mapIndexed { index, section ->
                HotelSectionEntity(UUID.randomUUID().toString(), hotel.id, index, section.material.name, section.holeMm, section.depthMm, section.holes)
            },
        )
        index(trimmed)
    }

    suspend fun deleteHotel(id: String) = db.withTransaction {
        dao.deleteHotel(id)
        search.delete(id)
    }

    suspend fun saveObservation(observation: HotelObservation) = dao.upsertObservation(
        HotelObservationEntity(
            observation.id, observation.hotelId, observation.date.toEpochDay(),
            observation.sealed.filterValues { it > 0 }.entries.sortedBy { it.key.ordinal }.joinToString(",") { "${it.key.name}=${it.value}" },
            observation.busy, observation.emerged, observation.pests.sortedBy { it.ordinal }.joinToString(",") { it.name }, observation.notes.trim(), now(),
        ),
    )

    suspend fun deleteObservation(id: String) = dao.deleteObservation(id)

    suspend fun saveUpkeep(upkeep: HotelUpkeep) =
        dao.upsertUpkeep(HotelUpkeepEntity(upkeep.id, upkeep.hotelId, upkeep.date.toEpochDay(), upkeep.kind.name, upkeep.notes.trim(), now()))

    suspend fun deleteUpkeep(id: String) = dao.deleteUpkeep(id)

    /** Every hotel and its records as linked data. */
    suspend fun triples(): List<Triple> = buildList {
        hotels.first().forEach { addAll(HotelGraph.hotel(it)) }
        observations.first().forEach { addAll(HotelGraph.observation(it)) }
        upkeep.first().forEach { addAll(HotelGraph.upkeep(it)) }
    }

    /** Puts every hotel in the search index again, after the garden's index has been rebuilt. */
    suspend fun reindex() = dao.hotels().forEach { index(it.toDomain(dao.sections(it.id))) }

    private suspend fun index(hotel: BeeHotel) {
        val doc = SearchText.hotel(hotel, hotel.zoneId?.let { zones.get(it)?.name })
        search.delete(hotel.id)
        search.replace(SearchEntity(recordId = doc.id, kind = doc.kind.name, title = doc.title, body = doc.body))
    }

    private fun HotelEntity.toDomain(sections: List<HotelSectionEntity>) = BeeHotel(
        id, name, zoneId, facing?.let(Facing::valueOf), heightMm, installedOnEpochDay?.let(LocalDate::ofEpochDay),
        sections.sortedBy { it.position }.map { HotelSection(HotelMaterial.valueOf(it.material), it.holeMm, it.depthMm, it.holes) }, notes,
    )

    private fun HotelObservationEntity.toDomain() = HotelObservation(
        id, hotelId, LocalDate.ofEpochDay(dateEpochDay),
        sealed.split(',').filter { '=' in it }.mapNotNull { pair ->
            val seal = Seal.entries.firstOrNull { it.name == pair.substringBefore('=') } ?: return@mapNotNull null
            pair.substringAfter('=').toIntOrNull()?.let { seal to it }
        }.toMap(),
        busy, emerged, pests.split(',').mapNotNull { name -> HotelPest.entries.firstOrNull { it.name == name } }.toSet(), notes,
    )
}
