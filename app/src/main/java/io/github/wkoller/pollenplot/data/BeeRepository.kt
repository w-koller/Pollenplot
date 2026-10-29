package io.github.wkoller.pollenplot.data

import androidx.room.withTransaction
import io.github.wkoller.pollenplot.data.db.ApiaryEntity
import io.github.wkoller.pollenplot.data.db.ColonyEventEntity
import io.github.wkoller.pollenplot.data.db.FeedingEntity
import io.github.wkoller.pollenplot.data.db.HarvestEntity
import io.github.wkoller.pollenplot.data.db.HoneyHiveEntity
import io.github.wkoller.pollenplot.data.db.InspectionEntity
import io.github.wkoller.pollenplot.data.db.PollenplotDatabase
import io.github.wkoller.pollenplot.data.db.SearchEntity
import io.github.wkoller.pollenplot.data.db.TreatmentEntity
import io.github.wkoller.pollenplot.data.db.VarroaCheckEntity
import io.github.wkoller.pollenplot.domain.bees.honey.Apiary
import io.github.wkoller.pollenplot.domain.bees.honey.BroodPattern
import io.github.wkoller.pollenplot.domain.bees.honey.ColonyEvent
import io.github.wkoller.pollenplot.domain.bees.honey.ColonyEventKind
import io.github.wkoller.pollenplot.domain.bees.honey.FeedKind
import io.github.wkoller.pollenplot.domain.bees.honey.Feeding
import io.github.wkoller.pollenplot.domain.bees.honey.Harvest
import io.github.wkoller.pollenplot.domain.bees.honey.HiveSign
import io.github.wkoller.pollenplot.domain.bees.honey.HiveStatus
import io.github.wkoller.pollenplot.domain.bees.honey.HiveType
import io.github.wkoller.pollenplot.domain.bees.honey.HoneyHive
import io.github.wkoller.pollenplot.domain.bees.honey.Inspection
import io.github.wkoller.pollenplot.domain.bees.honey.QueenCells
import io.github.wkoller.pollenplot.domain.bees.honey.Temperament
import io.github.wkoller.pollenplot.domain.bees.honey.Treatment
import io.github.wkoller.pollenplot.domain.bees.honey.VarroaCheck
import io.github.wkoller.pollenplot.domain.bees.honey.VarroaMethod
import io.github.wkoller.pollenplot.domain.semantic.HoneyGraph
import io.github.wkoller.pollenplot.domain.semantic.SearchText
import io.github.wkoller.pollenplot.domain.semantic.Triple
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

/**
 * The honey bee records, kept apart from the native bees and bee hotels. Hives are in the garden's search index, like
 * zones and plants; the records of a hive go with it when it is deleted.
 */
class BeeRepository(private val db: PollenplotDatabase, private val clock: Clock) {
    private val honey = db.honeyDao()
    private val search = db.searchDao()
    private val zones = db.zoneDao()

    private fun now() = clock.millis()

    private fun newId() = UUID.randomUUID().toString()

    val apiaries: Flow<List<Apiary>> = honey.observeApiaries().map { list -> list.map { Apiary(it.id, it.name, it.registration, it.notes) } }

    val hives: Flow<List<HoneyHive>> = honey.observeHives().map { list -> list.map { it.toDomain() } }

    val inspections: Flow<List<Inspection>> = honey.observeInspections().map { list -> list.map { it.toDomain() } }

    val checks: Flow<List<VarroaCheck>> = honey.observeChecks().map { list -> list.map { it.toDomain() } }

    val treatments: Flow<List<Treatment>> = honey.observeTreatments().map { list ->
        list.map { Treatment(it.id, it.hiveId, it.product, it.activeIngredient, LocalDate.ofEpochDay(it.startedEpochDay), it.endedEpochDay?.let(LocalDate::ofEpochDay), it.supersRemoved, it.notes) }
    }

    val feedings: Flow<List<Feeding>> = honey.observeFeedings().map { list ->
        list.map { Feeding(it.id, it.hiveId, LocalDate.ofEpochDay(it.dateEpochDay), FeedKind.valueOf(it.kind), it.amount, it.notes) }
    }

    val harvests: Flow<List<Harvest>> = honey.observeHarvests().map { list ->
        list.map { Harvest(it.id, it.hiveId, LocalDate.ofEpochDay(it.dateEpochDay), it.frames, it.honeyGrams, it.notes) }
    }

    val events: Flow<List<ColonyEvent>> = honey.observeEvents().map { list ->
        list.map { ColonyEvent(it.id, it.hiveId, LocalDate.ofEpochDay(it.dateEpochDay), ColonyEventKind.valueOf(it.kind), it.notes) }
    }

    suspend fun addApiary(name: String, registration: String?): Apiary = db.withTransaction {
        val apiary = Apiary(newId(), name.trim(), registration?.trim()?.ifBlank { null })
        honey.upsertApiary(ApiaryEntity(apiary.id, apiary.name, apiary.registration, apiary.notes, now(), now()))
        apiary
    }

    suspend fun updateApiary(apiary: Apiary) = db.withTransaction {
        val existing = honey.apiary(apiary.id) ?: return@withTransaction
        honey.upsertApiary(existing.copy(name = apiary.name.trim(), registration = apiary.registration?.trim()?.ifBlank { null }, notes = apiary.notes, updatedAt = now()))
        honey.hives().filter { it.apiaryId == apiary.id }.forEach { index(it.toDomain()) }
    }

    suspend fun deleteApiary(id: String) = db.withTransaction {
        honey.deleteApiary(id)
        honey.hives().filter { it.apiaryId == null }.forEach { index(it.toDomain()) }
    }

    /** Starts a hive, with nothing recorded for it yet. */
    suspend fun addHive(name: String, type: HiveType, zoneId: String?, apiaryId: String?, queenYear: Int?, startedOn: LocalDate?): HoneyHive = db.withTransaction {
        val hive = HoneyHive(newId(), name.trim(), type, apiaryId, zoneId, broodBoxes = 1, supers = 0, queenYear = queenYear, status = HiveStatus.ACTIVE, startedOn = startedOn)
        honey.upsertHive(hive.toEntity(now(), now()))
        index(hive)
        hive
    }

    suspend fun updateHive(hive: HoneyHive) = db.withTransaction {
        val existing = honey.hive(hive.id) ?: return@withTransaction
        honey.upsertHive(hive.copy(name = hive.name.trim()).toEntity(existing.createdAt, now()))
        index(hive)
    }

    suspend fun deleteHive(id: String) = db.withTransaction {
        honey.deleteHive(id)
        search.delete(id)
    }

    suspend fun saveInspection(inspection: Inspection) = honey.upsertInspection(
        InspectionEntity(
            inspection.id, inspection.hiveId, inspection.date.toEpochDay(), inspection.queenSeen, inspection.eggsSeen, inspection.brood?.name,
            inspection.temperament?.name, inspection.framesOfBees, inspection.framesOfStores, inspection.queenCells.name,
            inspection.signs.sortedBy { it.ordinal }.joinToString(",") { it.name }, inspection.actions.trim(), inspection.notes.trim(), now(),
        ),
    )

    suspend fun deleteInspection(id: String) = honey.deleteInspection(id)

    suspend fun saveCheck(check: VarroaCheck) = honey.upsertCheck(
        VarroaCheckEntity(check.id, check.hiveId, check.date.toEpochDay(), check.method.name, check.sample, check.mites, check.reportedOn?.toEpochDay(), now()),
    )

    suspend fun deleteCheck(id: String) = honey.deleteCheck(id)

    suspend fun saveTreatment(treatment: Treatment) = honey.upsertTreatment(
        TreatmentEntity(
            treatment.id, treatment.hiveId, treatment.product.trim(), treatment.activeIngredient.trim(), treatment.started.toEpochDay(),
            treatment.ended?.toEpochDay(), treatment.supersRemoved, treatment.notes.trim(), now(),
        ),
    )

    suspend fun deleteTreatment(id: String) = honey.deleteTreatment(id)

    suspend fun saveFeeding(feeding: Feeding) =
        honey.upsertFeeding(FeedingEntity(feeding.id, feeding.hiveId, feeding.date.toEpochDay(), feeding.kind.name, feeding.amount, feeding.notes.trim(), now()))

    suspend fun deleteFeeding(id: String) = honey.deleteFeeding(id)

    suspend fun saveHarvest(harvest: Harvest) =
        honey.upsertHarvest(HarvestEntity(harvest.id, harvest.hiveId, harvest.date.toEpochDay(), harvest.frames, harvest.honeyGrams, harvest.notes.trim(), now()))

    suspend fun deleteHarvest(id: String) = honey.deleteHarvest(id)

    /** Records what happened to a colony; one that died or is gone takes its hive's status with it. */
    suspend fun saveEvent(event: ColonyEvent) = db.withTransaction {
        honey.upsertEvent(ColonyEventEntity(event.id, event.hiveId, event.date.toEpochDay(), event.kind.name, event.notes.trim(), now()))
        if (event.kind == ColonyEventKind.DIED) {
            honey.hive(event.hiveId)?.let { updateHive(it.toDomain().copy(status = HiveStatus.DEAD)) }
        }
    }

    suspend fun deleteEvent(id: String) = honey.deleteEvent(id)

    /** A new record's key, for a form that saves a new record. */
    fun newRecordId(): String = newId()

    /** Every honey bee record as linked data, as the Biosecurity Code of Practice asks beekeepers to keep them. */
    suspend fun triples(): List<Triple> = buildList {
        apiaries.first().forEach { addAll(HoneyGraph.apiary(it)) }
        hives.first().forEach { addAll(HoneyGraph.hive(it)) }
        inspections.first().forEach { addAll(HoneyGraph.inspection(it)) }
        checks.first().forEach { addAll(HoneyGraph.check(it)) }
        treatments.first().forEach { addAll(HoneyGraph.treatment(it)) }
        feedings.first().forEach { addAll(HoneyGraph.feeding(it)) }
        harvests.first().forEach { addAll(HoneyGraph.harvest(it)) }
        events.first().forEach { addAll(HoneyGraph.event(it)) }
    }

    /** Puts every hive in the search index again, after the garden's index has been rebuilt. */
    suspend fun reindex() = honey.hives().forEach { index(it.toDomain()) }

    private suspend fun index(hive: HoneyHive) {
        val zoneName = hive.zoneId?.let { zones.get(it)?.name }
        val apiaryName = hive.apiaryId?.let { honey.apiary(it)?.name }
        val doc = SearchText.hive(hive, zoneName, apiaryName)
        search.delete(hive.id)
        search.replace(SearchEntity(recordId = doc.id, kind = doc.kind.name, title = doc.title, body = doc.body))
    }

    private fun HoneyHiveEntity.toDomain() = HoneyHive(
        id, name, HiveType.valueOf(type), apiaryId, zoneId, broodBoxes, supers, queenYear, HiveStatus.valueOf(status),
        startedOnEpochDay?.let(LocalDate::ofEpochDay), notes,
    )

    private fun HoneyHive.toEntity(createdAt: Long, updatedAt: Long) = HoneyHiveEntity(
        id, name, type.name, apiaryId, zoneId, broodBoxes, supers, queenYear, status.name, startedOn?.toEpochDay(), notes, createdAt, updatedAt,
    )

    private fun InspectionEntity.toDomain() = Inspection(
        id, hiveId, LocalDate.ofEpochDay(dateEpochDay), queenSeen, eggsSeen, brood?.let(BroodPattern::valueOf), temperament?.let(Temperament::valueOf),
        framesOfBees, framesOfStores, QueenCells.valueOf(queenCells),
        signs.split(',').filter { it.isNotBlank() }.mapNotNull { name -> HiveSign.entries.firstOrNull { it.name == name } }.toSet(), actions, notes,
    )

    private fun VarroaCheckEntity.toDomain() =
        VarroaCheck(id, hiveId, LocalDate.ofEpochDay(dateEpochDay), VarroaMethod.valueOf(method), sample, mites, reportedOnEpochDay?.let(LocalDate::ofEpochDay))
}
