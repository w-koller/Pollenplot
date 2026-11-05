package io.github.wkoller.pollenplot.data

import io.github.wkoller.pollenplot.data.db.PlantHarvestEntity
import io.github.wkoller.pollenplot.data.db.PollenplotDatabase
import io.github.wkoller.pollenplot.domain.garden.Harvest
import io.github.wkoller.pollenplot.domain.semantic.HarvestGraph
import io.github.wkoller.pollenplot.domain.semantic.Triple
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

/** What was picked from each plant. Harvests go with their plant when it is removed. */
class HarvestRepository(db: PollenplotDatabase, private val clock: Clock) {
    private val dao = db.harvestDao()

    /** Every harvest, the latest first. */
    val harvests: Flow<List<Harvest>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun harvest(id: String): Harvest? = dao.get(id)?.toDomain()

    suspend fun add(plantId: String, date: LocalDate, weightG: Int?, count: Int?, rating: Int?, notes: String): Harvest {
        val harvest = Harvest(UUID.randomUUID().toString(), plantId, date, weightG, count, rating, notes.trim())
        write(harvest, clock.millis())
        return harvest
    }

    suspend fun update(harvest: Harvest) {
        val stored = dao.get(harvest.id) ?: return
        write(harvest.copy(notes = harvest.notes.trim()), stored.createdAt)
    }

    suspend fun delete(id: String) = dao.delete(id)

    /** Every harvest as linked data. */
    suspend fun triples(): List<Triple> = dao.all().flatMap { HarvestGraph.harvest(it.toDomain()) }

    private suspend fun write(harvest: Harvest, createdAt: Long) {
        dao.upsert(PlantHarvestEntity(harvest.id, harvest.plantId, harvest.date.toEpochDay(), harvest.weightG, harvest.count, harvest.rating, harvest.notes, createdAt))
    }

    private fun PlantHarvestEntity.toDomain() = Harvest(id, plantId, LocalDate.ofEpochDay(dateEpochDay), weightG, count, rating, notes)
}
