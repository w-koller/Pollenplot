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

    /** The photo already kept with this picture, if there is one. */
    @Query("SELECT * FROM photos WHERE contentHash = :hash LIMIT 1")
    suspend fun withHash(hash: String): PhotoEntity?

    /** Removes a photo with the finds in it and their suggestions. */
    @Query("DELETE FROM photos WHERE id = :id")
    suspend fun delete(id: String)
}

/** A decided find as [DetectionDao.decided] reads it. */
data class DecidedRow(val label: String, val speciesName: String?, val status: String, val chosenLabel: String?, val chosenSpecies: String?)

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

    /** The finds added to a plant, the most recently taken first. */
    @Query("SELECT d.* FROM detections d JOIN photos p ON p.id = d.photoId WHERE d.plantId = :plantId AND d.status = 'CONFIRMED' ORDER BY p.takenAt DESC")
    fun observeForPlant(plantId: String): Flow<List<DetectionEntity>>

    @Query("SELECT * FROM detections WHERE photoId IN (:photoIds) ORDER BY leftBp, topBp")
    fun observeForPhotos(photoIds: List<String>): Flow<List<DetectionEntity>>

    /** Photos with plants waiting for review, the most recently taken first. */
    @Query(
        "SELECT d.photoId FROM detections d JOIN photos p ON p.id = d.photoId WHERE d.status = 'NEEDS_REVIEW' " +
            "AND p.origin != 'FIELD' GROUP BY d.photoId ORDER BY MAX(p.takenAt) DESC",
    )
    fun observePendingPhotoIds(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM detections d JOIN photos p ON p.id = d.photoId WHERE d.status = 'NEEDS_REVIEW' AND p.origin != 'FIELD'")
    fun observePendingCount(): Flow<Int>

    /** Plants waiting for review that [source] has not suggested names for. Finds away from home are left out. */
    @Query(
        "SELECT d.* FROM detections d JOIN photos p ON p.id = d.photoId WHERE d.status = 'NEEDS_REVIEW' AND p.origin != 'FIELD' AND NOT EXISTS " +
            "(SELECT 1 FROM candidates c WHERE c.detectionId = d.id AND c.sources LIKE '%' || :source || '%') ORDER BY d.createdAt DESC",
    )
    suspend fun pendingWithout(source: String): List<DetectionEntity>

    @Query(
        "SELECT d.* FROM detections d JOIN photos p ON p.id = d.photoId WHERE d.status = 'NEEDS_REVIEW' AND p.origin != 'FIELD' " +
            "AND d.secondOpinion = 0 ORDER BY d.createdAt DESC LIMIT :limit",
    )
    suspend fun waitingForSecondOpinion(limit: Int): List<DetectionEntity>

    @Query("UPDATE detections SET secondOpinion = 1 WHERE id = :id")
    suspend fun markSecondOpinion(id: String)

    @Query("DELETE FROM candidates WHERE detectionId = :id")
    suspend fun deleteCandidates(id: String)

    /** Marks a detection as changed, so screens that show it read its suggestions again. */
    @Query("UPDATE detections SET createdAt = createdAt WHERE id = :id")
    suspend fun touch(id: String)

    @Query("UPDATE detections SET status = :status, plantId = :plantId, sightingId = :sightingId WHERE id = :id")
    suspend fun setStatus(id: String, status: String, plantId: String?, sightingId: String?)

    @Query("SELECT * FROM detections WHERE photoId = :photoId ORDER BY leftBp, topBp")
    suspend fun inPhoto(photoId: String): List<DetectionEntity>

    @Query("DELETE FROM detections WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE detections SET flowering = :flowering WHERE id = :id")
    suspend fun setFlowering(id: String, flowering: Boolean)

    @Query("SELECT * FROM detections WHERE plantId = :plantId AND status = 'CONFIRMED'")
    suspend fun forPlant(plantId: String): List<DetectionEntity>

    /** Records what the user said a find is, and where that stood among the suggestions. */
    @Query("UPDATE detections SET chosenLabel = :label, chosenSpecies = :species, chosenRank = :rank WHERE id = :id")
    suspend fun setChoice(id: String, label: String?, species: String?, rank: Int?)

    /** Each find the user has decided, with the name the models put first, for [io.github.wkoller.pollenplot.domain.id.IdHistory]. */
    @Query(
        "SELECT c.label AS label, c.speciesName AS speciesName, d.status AS status, d.chosenLabel AS chosenLabel, d.chosenSpecies AS chosenSpecies " +
            "FROM detections d JOIN candidates c ON c.detectionId = d.id AND c.rank = 0 WHERE d.status IN ('CONFIRMED', 'REJECTED')",
    )
    suspend fun decided(): List<DecidedRow>

    @Query("UPDATE detections SET lifeGroup = :group, secondOpinion = 0 WHERE id = :id")
    suspend fun setGroup(id: String, group: String)

    @Query("SELECT COUNT(*) FROM detections WHERE photoId = :photoId AND status = 'SAVED'")
    suspend fun savedInPhoto(photoId: String): Int

    @Query("SELECT thumbnail FROM detections WHERE photoId = :photoId")
    suspend fun thumbnailsInPhoto(photoId: String): List<String>
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

    @Query("SELECT * FROM flowering ORDER BY observedOnEpochDay")
    suspend fun all(): List<FloweringEntity>

    @Query("SELECT * FROM flowering WHERE plantId = :plantId ORDER BY observedOnEpochDay DESC")
    fun observePlant(plantId: String): Flow<List<FloweringEntity>>

    @Query("SELECT * FROM flowering WHERE plantId = :plantId AND observedOnEpochDay = :epochDay LIMIT 1")
    suspend fun onDay(plantId: String, epochDay: Long): FloweringEntity?

    @Query("DELETE FROM flowering WHERE id = :id")
    suspend fun delete(id: String)

    /** Gives the days a plant was seen in flower in one photo to another plant, when the photo's find moves to it. */
    @Query("UPDATE flowering SET plantId = :to WHERE plantId = :from AND photoId = :photoId")
    suspend fun movePhoto(photoId: String, from: String, to: String)
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

@Dao
interface CareDraftDao {
    @Query("SELECT * FROM care_drafts WHERE plantId = :plantId")
    fun observe(plantId: String): Flow<CareDraftEntity?>

    @Upsert
    suspend fun upsert(draft: CareDraftEntity)

    @Query("UPDATE care_drafts SET kept = 1 WHERE plantId = :plantId")
    suspend fun keep(plantId: String)

    @Query("DELETE FROM care_drafts WHERE plantId = :plantId")
    suspend fun delete(plantId: String)
}
