package io.github.wkoller.pollenplot.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** The pot a plant grows in, one for each plant. Sizes are in millimetres and millilitres. */
@Entity(
    tableName = "plant_pots",
    foreignKeys = [ForeignKey(entity = PlantEntity::class, parentColumns = ["id"], childColumns = ["plantId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["plantId"], unique = true)],
)
data class PlantPotEntity(
    @PrimaryKey val id: String,
    val plantId: String,
    val diameterMm: Int?,
    val volumeMl: Long?,
    /** A `PotMaterial`, such as TERRACOTTA. */
    val material: String?,
    /** A `PotMix`, such as NATIVE. */
    val mix: String?,
    val mixNote: String,
    val updatedAt: Long,
)

/** Something done to a plant on a day, such as repotting it or flushing its pot. */
@Entity(
    tableName = "plant_events",
    foreignKeys = [ForeignKey(entity = PlantEntity::class, parentColumns = ["id"], childColumns = ["plantId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("plantId")],
)
data class PlantEventEntity(
    @PrimaryKey val id: String,
    val plantId: String,
    /** A `PlantEventKind`, such as REPOTTED. */
    val kind: String,
    val dateEpochDay: Long,
    val notes: String,
    val createdAt: Long,
)

@Dao
interface PotDao {
    @Query("SELECT * FROM plant_pots")
    fun observePots(): Flow<List<PlantPotEntity>>

    @Query("SELECT * FROM plant_pots")
    suspend fun allPots(): List<PlantPotEntity>

    @Query("SELECT * FROM plant_pots WHERE plantId = :plantId")
    suspend fun potOf(plantId: String): PlantPotEntity?

    @Upsert
    suspend fun upsertPot(pot: PlantPotEntity)

    @Query("DELETE FROM plant_pots WHERE plantId = :plantId")
    suspend fun deletePot(plantId: String)

    @Query("SELECT * FROM plant_events ORDER BY dateEpochDay DESC, createdAt DESC")
    fun observeEvents(): Flow<List<PlantEventEntity>>

    @Query("SELECT * FROM plant_events")
    suspend fun allEvents(): List<PlantEventEntity>

    @Upsert
    suspend fun upsertEvent(event: PlantEventEntity)

    @Query("DELETE FROM plant_events WHERE id = :id")
    suspend fun deleteEvent(id: String)
}
