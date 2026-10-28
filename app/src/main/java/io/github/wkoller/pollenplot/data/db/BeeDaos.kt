package io.github.wkoller.pollenplot.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** The honey bee records: apiaries, hives and what happens to each hive. */
@Dao
interface HoneyDao {
    @Query("SELECT * FROM apiaries ORDER BY name")
    fun observeApiaries(): Flow<List<ApiaryEntity>>

    @Upsert
    suspend fun upsertApiary(apiary: ApiaryEntity)

    @Query("SELECT * FROM apiaries WHERE id = :id")
    suspend fun apiary(id: String): ApiaryEntity?

    @Query("DELETE FROM apiaries WHERE id = :id")
    suspend fun deleteApiary(id: String)

    @Query("SELECT * FROM honey_hives ORDER BY name")
    fun observeHives(): Flow<List<HoneyHiveEntity>>

    @Query("SELECT * FROM honey_hives")
    suspend fun hives(): List<HoneyHiveEntity>

    @Query("SELECT * FROM honey_hives WHERE id = :id")
    suspend fun hive(id: String): HoneyHiveEntity?

    @Upsert
    suspend fun upsertHive(hive: HoneyHiveEntity)

    @Query("DELETE FROM honey_hives WHERE id = :id")
    suspend fun deleteHive(id: String)

    @Query("SELECT * FROM honey_inspections ORDER BY dateEpochDay DESC, createdAt DESC")
    fun observeInspections(): Flow<List<InspectionEntity>>

    @Upsert
    suspend fun upsertInspection(inspection: InspectionEntity)

    @Query("DELETE FROM honey_inspections WHERE id = :id")
    suspend fun deleteInspection(id: String)

    @Query("SELECT * FROM varroa_checks ORDER BY dateEpochDay DESC, createdAt DESC")
    fun observeChecks(): Flow<List<VarroaCheckEntity>>

    @Upsert
    suspend fun upsertCheck(check: VarroaCheckEntity)

    @Query("DELETE FROM varroa_checks WHERE id = :id")
    suspend fun deleteCheck(id: String)

    @Query("SELECT * FROM treatments ORDER BY startedEpochDay DESC")
    fun observeTreatments(): Flow<List<TreatmentEntity>>

    @Upsert
    suspend fun upsertTreatment(treatment: TreatmentEntity)

    @Query("DELETE FROM treatments WHERE id = :id")
    suspend fun deleteTreatment(id: String)

    @Query("SELECT * FROM feedings ORDER BY dateEpochDay DESC")
    fun observeFeedings(): Flow<List<FeedingEntity>>

    @Upsert
    suspend fun upsertFeeding(feeding: FeedingEntity)

    @Query("DELETE FROM feedings WHERE id = :id")
    suspend fun deleteFeeding(id: String)

    @Query("SELECT * FROM harvests ORDER BY dateEpochDay DESC")
    fun observeHarvests(): Flow<List<HarvestEntity>>

    @Upsert
    suspend fun upsertHarvest(harvest: HarvestEntity)

    @Query("DELETE FROM harvests WHERE id = :id")
    suspend fun deleteHarvest(id: String)

    @Query("SELECT * FROM colony_events ORDER BY dateEpochDay DESC")
    fun observeEvents(): Flow<List<ColonyEventEntity>>

    @Upsert
    suspend fun upsertEvent(event: ColonyEventEntity)

    @Query("DELETE FROM colony_events WHERE id = :id")
    suspend fun deleteEvent(id: String)
}
