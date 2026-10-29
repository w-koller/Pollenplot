package io.github.wkoller.pollenplot.data

import androidx.room.withTransaction
import io.github.wkoller.pollenplot.data.db.NativeHiveEntity
import io.github.wkoller.pollenplot.data.db.NativeLogEntity
import io.github.wkoller.pollenplot.data.db.PollenplotDatabase
import io.github.wkoller.pollenplot.data.db.SearchEntity
import io.github.wkoller.pollenplot.domain.bees.stingless.Facing
import io.github.wkoller.pollenplot.domain.bees.stingless.NativeBox
import io.github.wkoller.pollenplot.domain.bees.stingless.NativeHive
import io.github.wkoller.pollenplot.domain.bees.stingless.NativeLog
import io.github.wkoller.pollenplot.domain.bees.stingless.NativeLogKind
import io.github.wkoller.pollenplot.domain.bees.stingless.NativeOrigin
import io.github.wkoller.pollenplot.domain.bees.stingless.NativePest
import io.github.wkoller.pollenplot.domain.bees.stingless.NativeStatus
import io.github.wkoller.pollenplot.domain.bees.stingless.NativeSwarm
import io.github.wkoller.pollenplot.domain.bees.stingless.StinglessSpecies
import io.github.wkoller.pollenplot.domain.semantic.NativeBeeGraph
import io.github.wkoller.pollenplot.domain.semantic.SearchText
import io.github.wkoller.pollenplot.domain.semantic.Triple
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

/**
 * The native bee records: stingless bee hives and their logs, kept apart from the honey bees. Hives are in the
 * garden's search index; a hive's logs go with it when it is deleted.
 */
class NativeBeeRepository(private val db: PollenplotDatabase, private val clock: Clock) {
    private val natives = db.nativeBeeDao()
    private val search = db.searchDao()
    private val zones = db.zoneDao()

    private fun now() = clock.millis()

    val hives: Flow<List<NativeHive>> = natives.observeHives().map { list -> list.map { it.toDomain() } }

    val logs: Flow<List<NativeLog>> = natives.observeLogs().map { list -> list.map { it.toDomain() } }

    suspend fun addHive(name: String, species: StinglessSpecies, box: NativeBox, origin: NativeOrigin, zoneId: String?, facing: Facing?, startedOn: LocalDate?): NativeHive =
        db.withTransaction {
            val hive = NativeHive(UUID.randomUUID().toString(), name.trim(), species, box, origin, zoneId, facing, NativeStatus.ACTIVE, startedOn)
            natives.upsertHive(hive.toEntity(now(), now()))
            index(hive)
            hive
        }

    suspend fun updateHive(hive: NativeHive) = db.withTransaction {
        val existing = natives.hive(hive.id) ?: return@withTransaction
        val trimmed = hive.copy(name = hive.name.trim(), notes = hive.notes.trim())
        natives.upsertHive(trimmed.toEntity(existing.createdAt, now()))
        index(trimmed)
    }

    suspend fun deleteHive(id: String) = db.withTransaction {
        natives.deleteHive(id)
        search.delete(id)
    }

    suspend fun saveLog(log: NativeLog) = natives.upsertLog(
        NativeLogEntity(
            log.id, log.hiveId, log.date.toEpochDay(), log.kind.name, log.beesPerMinute, log.weightGrams, log.honeyMl, log.pest?.name, log.swarm?.name,
            log.notes.trim(), now(),
        ),
    )

    suspend fun deleteLog(id: String) = natives.deleteLog(id)

    /** A new record's key, for a form that saves a new record. */
    fun newRecordId(): String = UUID.randomUUID().toString()

    /** Every native hive and log as linked data. */
    suspend fun triples(): List<Triple> = buildList {
        hives.first().forEach { addAll(NativeBeeGraph.hive(it)) }
        logs.first().forEach { addAll(NativeBeeGraph.log(it)) }
    }

    /** Puts every native hive in the search index again, after the garden's index has been rebuilt. */
    suspend fun reindex() = natives.hives().forEach { index(it.toDomain()) }

    private suspend fun index(hive: NativeHive) {
        val doc = SearchText.nativeHive(hive, hive.zoneId?.let { zones.get(it)?.name })
        search.delete(hive.id)
        search.replace(SearchEntity(recordId = doc.id, kind = doc.kind.name, title = doc.title, body = doc.body))
    }

    private fun NativeHiveEntity.toDomain() = NativeHive(
        id, name, StinglessSpecies.valueOf(species), NativeBox.valueOf(box), NativeOrigin.valueOf(origin), zoneId, facing?.let(Facing::valueOf),
        NativeStatus.valueOf(status), startedOnEpochDay?.let(LocalDate::ofEpochDay), notes,
    )

    private fun NativeHive.toEntity(createdAt: Long, updatedAt: Long) = NativeHiveEntity(
        id, name, species.name, box.name, origin.name, zoneId, facing?.name, status.name, startedOn?.toEpochDay(), notes, createdAt, updatedAt,
    )

    private fun NativeLogEntity.toDomain() = NativeLog(
        id, hiveId, LocalDate.ofEpochDay(dateEpochDay), NativeLogKind.valueOf(kind), beesPerMinute, weightGrams, honeyMl,
        pest?.let(NativePest::valueOf), swarm?.let(NativeSwarm::valueOf), notes,
    )
}
