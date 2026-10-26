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

    @Query("SELECT * FROM photos WHERE id IN (:ids)")
    suspend fun getAll(ids: List<String>): List<PhotoEntity>
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

    @Query("SELECT * FROM detections WHERE photoId IN (:photoIds) ORDER BY leftBp, topBp")
    fun observeForPhotos(photoIds: List<String>): Flow<List<DetectionEntity>>

    /** Photos with plants waiting for review, the most recently taken first. */
    @Query(
        "SELECT d.photoId FROM detections d JOIN photos p ON p.id = d.photoId WHERE d.status = 'NEEDS_REVIEW' " +
            "GROUP BY d.photoId ORDER BY MAX(p.takenAt) DESC",
    )
    fun observePendingPhotoIds(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM detections WHERE status = 'NEEDS_REVIEW'")
    fun observePendingCount(): Flow<Int>

    /** Plants waiting for review that [source] has not suggested names for. */
    @Query(
        "SELECT * FROM detections d WHERE d.status = 'NEEDS_REVIEW' AND NOT EXISTS " +
            "(SELECT 1 FROM candidates c WHERE c.detectionId = d.id AND c.sources LIKE '%' || :source || '%') ORDER BY d.createdAt DESC",
    )
    suspend fun pendingWithout(source: String): List<DetectionEntity>

    @Query("DELETE FROM candidates WHERE detectionId = :id")
    suspend fun deleteCandidates(id: String)

    /** Marks a detection as changed, so screens that show it read its suggestions again. */
    @Query("UPDATE detections SET createdAt = createdAt WHERE id = :id")
    suspend fun touch(id: String)

    @Query("UPDATE detections SET status = :status, plantId = :plantId, sightingId = :sightingId WHERE id = :id")
    suspend fun setStatus(id: String, status: String, plantId: String?, sightingId: String?)
}

@Dao
interface SightingDao {
    @Upsert
    suspend fun upsert(sighting: SightingEntity)

    @Query("SELECT * FROM sightings WHERE id = :id")
    suspend fun get(id: String): SightingEntity?

    @Query("SELECT * FROM sightings")
    suspend fun all(): List<SightingEntity>

    /** Every sighting, the most recent first. */
    @Query("SELECT * FROM sightings ORDER BY seenAt DESC")
    fun observeAll(): Flow<List<SightingEntity>>

    @Query("DELETE FROM sightings WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface ScannedMediaDao {
    @Upsert
    suspend fun upsert(media: ScannedMediaEntity)

    @Query("SELECT mediaId FROM scanned_media WHERE mediaId IN (:ids)")
    suspend fun known(ids: List<Long>): List<Long>

    /** Gallery photos the quick model saw no plant in and the accurate pack has not looked at, the newest first. */
    @Query("SELECT * FROM scanned_media WHERE outcome = 'NO_PLANT' AND accurate = 0 ORDER BY takenAt DESC LIMIT :limit")
    suspend fun toRecheck(limit: Int): List<ScannedMediaEntity>
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

    @Query("SELECT recordId, kind, title, body FROM search")
    suspend fun all(): List<SearchHit>

    @Query("SELECT recordId, kind, title, body FROM search WHERE recordId IN (:ids)")
    suspend fun byIds(ids: List<String>): List<SearchHit>

    /** Emits whenever the index changes, so the meaning of changed records can be worked out again. */
    @Query("SELECT COUNT(*) FROM search")
    fun observeChanges(): Flow<Int>
}

@Dao
interface EmbeddingDao {
    @Query("SELECT * FROM embeddings WHERE model = :model")
    suspend fun all(model: String): List<EmbeddingEntity>

    @Upsert
    suspend fun upsert(embedding: EmbeddingEntity)

    /** Removes vectors for records that are gone, and those from another model. */
    @Query("DELETE FROM embeddings WHERE model != :model OR recordId NOT IN (SELECT recordId FROM search)")
    suspend fun prune(model: String)

    @Query("DELETE FROM embeddings")
    suspend fun clear()
}
