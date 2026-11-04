package io.github.wkoller.pollenplot.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * A plant on the wishlist. It keeps its own copy of the find's cut-out path, so it still shows a picture if the find
 * goes; the price is in cents and the place is rounded to about 1 km.
 */
@Entity(
    tableName = "wishes",
    foreignKeys = [
        ForeignKey(entity = DetectionEntity::class, parentColumns = ["id"], childColumns = ["detectionId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = ZoneEntity::class, parentColumns = ["id"], childColumns = ["targetZoneId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("detectionId"), Index("targetZoneId")],
)
data class WishEntity(
    @PrimaryKey val id: String,
    val speciesName: String?,
    val customName: String?,
    val detectionId: String?,
    val thumbnail: String?,
    val placeName: String,
    val latE7: Int?,
    val lonE7: Int?,
    val priceCents: Long?,
    val notes: String,
    val targetZoneId: String?,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Dao
interface WishDao {
    @Query("SELECT * FROM wishes ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<WishEntity>>

    @Query("SELECT * FROM wishes")
    suspend fun all(): List<WishEntity>

    @Query("SELECT * FROM wishes WHERE id = :id")
    suspend fun get(id: String): WishEntity?

    @Upsert
    suspend fun upsert(wish: WishEntity)

    @Query("DELETE FROM wishes WHERE id = :id")
    suspend fun delete(id: String)
}
