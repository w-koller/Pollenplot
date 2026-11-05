package io.github.wkoller.pollenplot.data

import io.github.wkoller.pollenplot.data.db.PlantEventEntity
import io.github.wkoller.pollenplot.data.db.PlantPotEntity
import io.github.wkoller.pollenplot.data.db.PollenplotDatabase
import io.github.wkoller.pollenplot.domain.garden.PlantEvent
import io.github.wkoller.pollenplot.domain.garden.PlantEventKind
import io.github.wkoller.pollenplot.domain.garden.PlantPot
import io.github.wkoller.pollenplot.domain.garden.PotMaterial
import io.github.wkoller.pollenplot.domain.garden.PotMix
import io.github.wkoller.pollenplot.domain.semantic.PotGraph
import io.github.wkoller.pollenplot.domain.semantic.Triple
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

/**
 * The pots plants grow in and the care done to plants on a day, such as repotting or flushing a pot. Both go with
 * their plant when it is removed.
 */
class PotRepository(db: PollenplotDatabase, private val clock: Clock) {
    private val dao = db.potDao()

    /** Each potted plant's pot, by plant. */
    val pots: Flow<Map<String, PlantPot>> = dao.observePots().map { list -> list.associate { it.plantId to it.toDomain() } }

    /** Every plant event, the latest first. */
    val events: Flow<List<PlantEvent>> = dao.observeEvents().map { list -> list.mapNotNull { it.toDomain() } }

    suspend fun pot(plantId: String): PlantPot? = dao.potOf(plantId)?.toDomain()

    /** Saves a plant's pot, keeping the id of the one it replaces. */
    suspend fun savePot(pot: PlantPot) {
        val id = dao.potOf(pot.plantId)?.id ?: pot.id
        dao.upsertPot(
            PlantPotEntity(id, pot.plantId, pot.diameterMm, pot.volumeMl, pot.material?.name, pot.mix?.name, pot.mixNote.trim(), clock.millis()),
        )
    }

    suspend fun deletePot(plantId: String) = dao.deletePot(plantId)

    suspend fun addEvent(plantId: String, kind: PlantEventKind, date: LocalDate, notes: String = ""): PlantEvent {
        val event = PlantEvent(UUID.randomUUID().toString(), plantId, kind, date, notes.trim())
        dao.upsertEvent(PlantEventEntity(event.id, plantId, kind.name, date.toEpochDay(), event.notes, clock.millis()))
        return event
    }

    suspend fun deleteEvent(id: String) = dao.deleteEvent(id)

    fun newId(): String = UUID.randomUUID().toString()

    /** Every pot and plant event as linked data. */
    suspend fun triples(): List<Triple> =
        dao.allPots().flatMap { PotGraph.pot(it.toDomain()) } + dao.allEvents().mapNotNull { it.toDomain() }.flatMap { PotGraph.event(it) }

    private fun PlantPotEntity.toDomain() = PlantPot(
        id, plantId, diameterMm, volumeMl,
        PotMaterial.entries.firstOrNull { it.name == material },
        PotMix.entries.firstOrNull { it.name == mix },
        mixNote,
    )

    private fun PlantEventEntity.toDomain(): PlantEvent? =
        PlantEventKind.entries.firstOrNull { it.name == kind }?.let { PlantEvent(id, plantId, it, LocalDate.ofEpochDay(dateEpochDay), notes) }
}
