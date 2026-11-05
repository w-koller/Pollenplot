package io.github.wkoller.pollenplot.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Water set out for wildlife in a zone, such as a bird bath or a frog pond. */
@Entity(
    tableName = "water_features",
    foreignKeys = [ForeignKey(entity = ZoneEntity::class, parentColumns = ["id"], childColumns = ["zoneId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("zoneId")],
)
data class WaterFeatureEntity(
    @PrimaryKey val id: String,
    val zoneId: String,
    /** A `WaterKind`, such as BIRD_BATH. */
    val kind: String,
    val notes: String,
    val cleanedOnEpochDay: Long?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Dao
interface HabitatDao {
    @Query("SELECT * FROM water_features ORDER BY createdAt")
    fun observeWater(): Flow<List<WaterFeatureEntity>>

    @Query("SELECT * FROM water_features")
    suspend fun allWater(): List<WaterFeatureEntity>

    @Query("SELECT * FROM water_features WHERE id = :id")
    suspend fun water(id: String): WaterFeatureEntity?

    @Query("SELECT id FROM water_features WHERE zoneId = :zoneId")
    suspend fun waterIdsIn(zoneId: String): List<String>

    @Upsert
    suspend fun upsertWater(feature: WaterFeatureEntity)

    @Query("DELETE FROM water_features WHERE id = :id")
    suspend fun deleteWater(id: String)
}
