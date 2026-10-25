package io.github.wkoller.pollenplot.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ZoneDao {
    @Query("SELECT * FROM zones ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<ZoneEntity>>

    @Query("SELECT * FROM zones ORDER BY name COLLATE NOCASE")
    suspend fun all(): List<ZoneEntity>

    @Query("SELECT * FROM zones WHERE id = :id")
    suspend fun get(id: String): ZoneEntity?

    @Upsert
    suspend fun upsert(zone: ZoneEntity)

    @Query("DELETE FROM zones WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface PlantDao {
    @Query("SELECT * FROM plants ORDER BY createdAt")
    fun observeAll(): Flow<List<PlantEntity>>

    @Query("SELECT * FROM plants ORDER BY createdAt")
    suspend fun all(): List<PlantEntity>

    @Query("SELECT * FROM plants WHERE zoneId = :zoneId")
    suspend fun inZone(zoneId: String): List<PlantEntity>

    @Query("SELECT * FROM plants WHERE id = :id")
    suspend fun get(id: String): PlantEntity?

    @Upsert
    suspend fun upsert(plant: PlantEntity)

    @Query("DELETE FROM plants WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface PhotoDao {
    @Insert
    suspend fun insert(photo: PhotoEntity)

    @Query("SELECT * FROM photos WHERE id = :id")
    suspend fun get(id: String): PhotoEntity?
}

@Dao
interface DetectionDao {
    @Insert
    suspend fun insert(detection: DetectionEntity)

    @Insert
    suspend fun insertCandidates(candidates: List<CandidateEntity>)

    @Query("SELECT * FROM detections WHERE id = :id")
    suspend fun get(id: String): DetectionEntity?

    @Query("SELECT * FROM candidates WHERE detectionId = :id ORDER BY rank")
    suspend fun candidates(id: String): List<CandidateEntity>

    @Query("SELECT * FROM detections WHERE status = :status ORDER BY createdAt")
    fun observeWithStatus(status: String): Flow<List<DetectionEntity>>

    /** A photo's detections, from the left of the photo to the right, so the review follows the photo. */
    @Query("SELECT * FROM detections WHERE photoId = :photoId ORDER BY leftBp, topBp")
    fun observeForPhoto(photoId: String): Flow<List<DetectionEntity>>

    @Query("UPDATE detections SET status = :status, plantId = :plantId WHERE id = :id")
    suspend fun setStatus(id: String, status: String, plantId: String?)
}

@Dao
interface FloweringDao {
    @Insert
    suspend fun insert(observation: FloweringEntity)

    @Query("SELECT * FROM flowering WHERE plantId = :plantId ORDER BY observedOnEpochDay")
    suspend fun forPlant(plantId: String): List<FloweringEntity>

    @Query("SELECT * FROM flowering ORDER BY observedOnEpochDay")
    fun observeAll(): Flow<List<FloweringEntity>>
}

@Dao
interface LinkDao {
    @Query("SELECT * FROM links")
    suspend fun all(): List<LinkEntity>

    @Upsert
    suspend fun upsert(link: LinkEntity)

    @Query("DELETE FROM links WHERE subjectId = :id OR objectId = :id")
    suspend fun deleteFor(id: String)
}

@Dao
interface SearchDao {
    @Query("DELETE FROM search WHERE recordId = :recordId")
    suspend fun delete(recordId: String)

    @Insert
    suspend fun insert(entry: SearchEntity)

    @Transaction
    suspend fun replace(entry: SearchEntity) {
        delete(entry.recordId)
        insert(entry)
    }

    @Query("DELETE FROM search")
    suspend fun clear()

    /** [match] is an FTS4 query such as `lemon* tree*`. */
    @Query("SELECT recordId, kind, title, body FROM search WHERE search MATCH :match LIMIT :limit")
    suspend fun search(match: String, limit: Int): List<SearchHit>
}
