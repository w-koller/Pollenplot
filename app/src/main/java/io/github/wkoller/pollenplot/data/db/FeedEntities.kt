package io.github.wkoller.pollenplot.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** A fertiliser or conditioner the user added, with the analysis from its label in basis points. */
@Entity(tableName = "soil_products")
data class SoilProductEntity(
    @PrimaryKey val id: String,
    val name: String,
    val maker: String?,
    /** A `ProductKind`, such as LIQUID. */
    val kind: String,
    val nBp: Int?,
    val pBp: Int?,
    val kBp: Int?,
    val createdAt: Long,
)

/** A feed given on a day: a built-in product's key or the id of the user's own, and the rate the user typed. */
@Entity(tableName = "soil_feeds")
data class SoilFeedEntity(
    @PrimaryKey val id: String,
    val productKey: String,
    val dateEpochDay: Long,
    val rateText: String,
    val notes: String,
    val createdAt: Long,
)

/** The zones a feed went on. */
@Entity(
    tableName = "soil_feed_zones",
    primaryKeys = ["feedId", "zoneId"],
    foreignKeys = [
        ForeignKey(entity = SoilFeedEntity::class, parentColumns = ["id"], childColumns = ["feedId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ZoneEntity::class, parentColumns = ["id"], childColumns = ["zoneId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("zoneId")],
)
data class SoilFeedZoneEntity(val feedId: String, val zoneId: String)

@Dao
interface FeedDao {
    @Query("SELECT * FROM soil_products ORDER BY name")
    fun observeProducts(): Flow<List<SoilProductEntity>>

    @Query("SELECT * FROM soil_products")
    suspend fun allProducts(): List<SoilProductEntity>

    @Upsert
    suspend fun upsertProduct(product: SoilProductEntity)

    @Query("SELECT * FROM soil_feeds ORDER BY dateEpochDay DESC, createdAt DESC")
    fun observeFeeds(): Flow<List<SoilFeedEntity>>

    @Query("SELECT * FROM soil_feeds")
    suspend fun allFeeds(): List<SoilFeedEntity>

    @Query("SELECT * FROM soil_feed_zones")
    fun observeFeedZones(): Flow<List<SoilFeedZoneEntity>>

    @Query("SELECT * FROM soil_feed_zones")
    suspend fun allFeedZones(): List<SoilFeedZoneEntity>

    @Upsert
    suspend fun upsertFeed(feed: SoilFeedEntity)

    @Upsert
    suspend fun upsertFeedZones(zones: List<SoilFeedZoneEntity>)

    @Query("DELETE FROM soil_feeds WHERE id = :id")
    suspend fun deleteFeed(id: String)
}
