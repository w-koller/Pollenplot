package io.github.wkoller.pollenplot.data

import androidx.room.withTransaction
import io.github.wkoller.pollenplot.data.db.CompostBatchEntity
import io.github.wkoller.pollenplot.data.db.CompostBinEntity
import io.github.wkoller.pollenplot.data.db.CompostEventEntity
import io.github.wkoller.pollenplot.data.db.PollenplotDatabase
import io.github.wkoller.pollenplot.data.db.SearchEntity
import io.github.wkoller.pollenplot.domain.compost.CompostBatch
import io.github.wkoller.pollenplot.domain.compost.CompostBin
import io.github.wkoller.pollenplot.domain.compost.CompostEvent
import io.github.wkoller.pollenplot.domain.compost.CompostEventKind
import io.github.wkoller.pollenplot.domain.compost.CompostStages
import io.github.wkoller.pollenplot.domain.semantic.CompostGraph
import io.github.wkoller.pollenplot.domain.semantic.SearchText
import io.github.wkoller.pollenplot.domain.semantic.Triple
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Compost bins, each fill of them and what was done to each fill. A bin holds one open batch at a time: it is started
 * with the first scraps, filled when the user says no more will go in, and ends when it is emptied. Bins are in the
 * garden's search index.
 */
class CompostRepository(private val db: PollenplotDatabase, private val clock: Clock) {
    private val dao = db.compostDao()
    private val search = db.searchDao()
    private val zones = db.zoneDao()

    val bins: Flow<List<CompostBin>> = dao.observeBins().map { list -> list.map { it.toDomain() } }
    val batches: Flow<List<CompostBatch>> = dao.observeBatches().map { list -> list.map { it.toDomain() } }
    val events: Flow<List<CompostEvent>> = dao.observeEvents().map { list -> list.map { it.toDomain() } }

    suspend fun bin(id: String): CompostBin? = dao.bin(id)?.toDomain()

    suspend fun addBin(name: String, zoneId: String? = null, structureId: String? = null, turnEveryDays: Int? = null): CompostBin {
        val bin = CompostBin(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            zoneId = zoneId,
            structureId = structureId,
            turnEveryDays = turnEveryDays ?: CompostStages.TURN_EVERY_DAYS,
            createdAt = clock.instant(),
        )
        db.withTransaction { write(bin) }
        return bin
    }

    suspend fun updateBin(bin: CompostBin) = db.withTransaction {
        if (dao.bin(bin.id) == null) return@withTransaction
        write(bin.copy(name = bin.name.trim(), notes = bin.notes.trim()))
    }

    suspend fun deleteBin(id: String) = db.withTransaction {
        dao.deleteBin(id)
        search.delete(id)
    }

    /** Starts a fill of [binId] on [day], unless one is already open, and returns the open one. */
    suspend fun startBatch(binId: String, day: LocalDate = today()): CompostBatch = db.withTransaction {
        dao.allBatches().firstOrNull { it.binId == binId && it.emptiedOnEpochDay == null }?.toDomain()
            ?: CompostBatch(UUID.randomUUID().toString(), binId, startedOn = day).also { writeBatch(it) }
    }

    /** Records that the batch is full and nothing more will go in, from [day], or with null that it is still filling. */
    suspend fun filled(batchId: String, day: LocalDate?) = db.withTransaction {
        val batch = dao.batch(batchId)?.toDomain() ?: return@withTransaction
        writeBatch(batch.copy(filledOn = day))
    }

    /** Records that the batch was emptied on [day], or with null that it was not, as an undo does. */
    suspend fun emptied(batchId: String, day: LocalDate?) = db.withTransaction {
        val batch = dao.batch(batchId)?.toDomain() ?: return@withTransaction
        // A batch emptied without being marked full was full by then.
        writeBatch(batch.copy(emptiedOn = day, filledOn = batch.filledOn ?: day))
    }

    suspend fun deleteBatch(id: String) = dao.deleteBatch(id)

    suspend fun addEvent(batchId: String, kind: CompostEventKind, day: LocalDate = today(), tenthsC: Int? = null, notes: String = ""): CompostEvent {
        val event = CompostEvent(UUID.randomUUID().toString(), batchId, kind, day, tenthsC.takeIf { kind == CompostEventKind.TEMPERATURE }, notes.trim())
        dao.upsertEvent(CompostEventEntity(event.id, event.batchId, event.kind.name, event.date.toEpochDay(), event.tenthsC, event.notes, clock.millis()))
        return event
    }

    suspend fun restoreEvent(event: CompostEvent) =
        dao.upsertEvent(CompostEventEntity(event.id, event.batchId, event.kind.name, event.date.toEpochDay(), event.tenthsC, event.notes, clock.millis()))

    suspend fun deleteEvent(id: String) = dao.deleteEvent(id)

    /** Every bin, batch and event as linked data. */
    suspend fun triples(): List<Triple> =
        dao.allBins().flatMap { CompostGraph.bin(it.toDomain()) } +
            dao.allBatches().flatMap { CompostGraph.batch(it.toDomain()) } +
            dao.allEvents().flatMap { CompostGraph.event(it.toDomain()) }

    /** Puts every bin in the search index again, after the garden's index has been rebuilt. */
    suspend fun reindex() = dao.allBins().forEach { index(it.toDomain()) }

    /** How many bins and batches there are, for a backup's contents. */
    suspend fun counts(): Pair<Int, Int> = dao.allBins().size to dao.allBatches().size

    private fun today(): LocalDate = LocalDate.now(clock)

    private suspend fun write(bin: CompostBin) {
        val createdAt = dao.bin(bin.id)?.createdAt ?: bin.createdAt.toEpochMilli()
        dao.upsertBin(CompostBinEntity(bin.id, bin.name, bin.zoneId, bin.structureId, bin.turnEveryDays, bin.notes, createdAt, clock.millis()))
        index(bin)
    }

    private suspend fun writeBatch(batch: CompostBatch) {
        val createdAt = dao.batch(batch.id)?.createdAt ?: clock.millis()
        dao.upsertBatch(
            CompostBatchEntity(
                batch.id, batch.binId, batch.startedOn.toEpochDay(), batch.filledOn?.toEpochDay(), batch.emptiedOn?.toEpochDay(),
                batch.notes, createdAt, clock.millis(),
            ),
        )
    }

    private suspend fun index(bin: CompostBin) {
        val doc = SearchText.compostBin(bin, bin.zoneId?.let { zones.get(it)?.name })
        search.delete(bin.id)
        search.replace(SearchEntity(recordId = doc.id, kind = doc.kind.name, title = doc.title, body = doc.body))
    }

    private fun CompostBinEntity.toDomain() = CompostBin(id, name, zoneId, structureId, turnEveryDays, notes, Instant.ofEpochMilli(createdAt))

    private fun CompostBatchEntity.toDomain() = CompostBatch(
        id = id,
        binId = binId,
        startedOn = LocalDate.ofEpochDay(startedOnEpochDay),
        filledOn = filledOnEpochDay?.let(LocalDate::ofEpochDay),
        emptiedOn = emptiedOnEpochDay?.let(LocalDate::ofEpochDay),
        notes = notes,
    )

    private fun CompostEventEntity.toDomain() = CompostEvent(
        id = id,
        batchId = batchId,
        kind = CompostEventKind.entries.firstOrNull { it.name == kind } ?: CompostEventKind.TURNED,
        date = LocalDate.ofEpochDay(epochDay),
        tenthsC = tenthsC,
        notes = notes,
    )
}
