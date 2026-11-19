package io.github.wkoller.pollenplot.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** A compost bin or heap, in a zone or beside a compost structure on the Plot when the user says where. */
@Entity(
    tableName = "compost_bins",
    foreignKeys = [
        ForeignKey(entity = ZoneEntity::class, parentColumns = ["id"], childColumns = ["zoneId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = StructureEntity::class, parentColumns = ["id"], childColumns = ["structureId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("zoneId"), Index("structureId")],
)
data class CompostBinEntity(
    @PrimaryKey val id: String,
    val name: String,
    val zoneId: String?,
    val structureId: String?,
    val turnEveryDays: Int,
    val notes: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/** One fill of a bin, from its first scraps to the day it is emptied. */
@Entity(
    tableName = "compost_batches",
    foreignKeys = [ForeignKey(entity = CompostBinEntity::class, parentColumns = ["id"], childColumns = ["binId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("binId")],
)
data class CompostBatchEntity(
    @PrimaryKey val id: String,
    val binId: String,
    val startedOnEpochDay: Long,
    val filledOnEpochDay: Long?,
    val emptiedOnEpochDay: Long?,
    val notes: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Browns or greens added, a turn, a watering or a temperature reading, on a batch. */
@Entity(
    tableName = "compost_events",
    foreignKeys = [ForeignKey(entity = CompostBatchEntity::class, parentColumns = ["id"], childColumns = ["batchId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("batchId")],
)
data class CompostEventEntity(
    @PrimaryKey val id: String,
    val batchId: String,
    /** A `CompostEventKind`, such as TURNED. */
    val kind: String,
    val epochDay: Long,
    val tenthsC: Int?,
    val notes: String,
    val createdAt: Long,
)

@Dao
interface CompostDao {
    @Query("SELECT * FROM compost_bins ORDER BY createdAt")
    fun observeBins(): Flow<List<CompostBinEntity>>

    @Query("SELECT * FROM compost_bins ORDER BY createdAt")
    suspend fun allBins(): List<CompostBinEntity>

    @Query("SELECT * FROM compost_bins WHERE id = :id")
    suspend fun bin(id: String): CompostBinEntity?

    @Upsert
    suspend fun upsertBin(bin: CompostBinEntity)

    @Query("DELETE FROM compost_bins WHERE id = :id")
    suspend fun deleteBin(id: String)

    @Query("SELECT * FROM compost_batches ORDER BY startedOnEpochDay")
    fun observeBatches(): Flow<List<CompostBatchEntity>>

    @Query("SELECT * FROM compost_batches ORDER BY startedOnEpochDay")
    suspend fun allBatches(): List<CompostBatchEntity>

    @Query("SELECT * FROM compost_batches WHERE id = :id")
    suspend fun batch(id: String): CompostBatchEntity?

    @Upsert
    suspend fun upsertBatch(batch: CompostBatchEntity)

    @Query("DELETE FROM compost_batches WHERE id = :id")
    suspend fun deleteBatch(id: String)

    @Query("SELECT * FROM compost_events ORDER BY epochDay, createdAt")
    fun observeEvents(): Flow<List<CompostEventEntity>>

    @Query("SELECT * FROM compost_events ORDER BY epochDay, createdAt")
    suspend fun allEvents(): List<CompostEventEntity>

    @Query("SELECT * FROM compost_events WHERE id = :id")
    suspend fun event(id: String): CompostEventEntity?

    @Upsert
    suspend fun upsertEvent(event: CompostEventEntity)

    @Query("DELETE FROM compost_events WHERE id = :id")
    suspend fun deleteEvent(id: String)
}
