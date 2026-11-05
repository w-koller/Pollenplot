package io.github.wkoller.pollenplot.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** What was picked from a plant on a day. The honey harvests are in `harvests`. */
@Entity(
    tableName = "plant_harvests",
    foreignKeys = [ForeignKey(entity = PlantEntity::class, parentColumns = ["id"], childColumns = ["plantId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("plantId")],
)
data class PlantHarvestEntity(
    @PrimaryKey val id: String,
    val plantId: String,
    val dateEpochDay: Long,
    val weightG: Int?,
    val count: Int?,
    /** From 1 to 5. */
    val rating: Int?,
    val notes: String,
    val createdAt: Long,
)

@Dao
interface HarvestDao {
    @Query("SELECT * FROM plant_harvests ORDER BY dateEpochDay DESC, createdAt DESC")
    fun observeAll(): Flow<List<PlantHarvestEntity>>

    @Query("SELECT * FROM plant_harvests")
    suspend fun all(): List<PlantHarvestEntity>

    @Query("SELECT * FROM plant_harvests WHERE id = :id")
    suspend fun get(id: String): PlantHarvestEntity?

    @Upsert
    suspend fun upsert(harvest: PlantHarvestEntity)

    @Query("DELETE FROM plant_harvests WHERE id = :id")
    suspend fun delete(id: String)
}
