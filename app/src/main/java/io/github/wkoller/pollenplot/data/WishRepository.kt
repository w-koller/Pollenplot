package io.github.wkoller.pollenplot.data

import androidx.room.withTransaction
import io.github.wkoller.pollenplot.data.db.PollenplotDatabase
import io.github.wkoller.pollenplot.data.db.SearchEntity
import io.github.wkoller.pollenplot.data.db.WishEntity
import io.github.wkoller.pollenplot.domain.garden.Wish
import io.github.wkoller.pollenplot.domain.garden.WishStatus
import io.github.wkoller.pollenplot.domain.id.DetectionStatus
import io.github.wkoller.pollenplot.domain.plants.KnowledgeBase
import io.github.wkoller.pollenplot.domain.semantic.SearchText
import io.github.wkoller.pollenplot.domain.semantic.Triple
import io.github.wkoller.pollenplot.domain.semantic.WishGraph
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * The wishlist: plants the user would like to grow, most of them photographed away from home. A wish saved from a find
 * keeps the find as SAVED, so its photo stays and the find never waits in the Review queue. Wishes are in the garden's
 * search index.
 */
class WishRepository(
    private val db: PollenplotDatabase,
    private val clock: Clock,
    private val knowledge: suspend () -> KnowledgeBase,
    /** Deletes a stored photo or cut-out file. */
    private val deleteFile: suspend (String) -> Unit = {},
) {
    private val dao = db.wishDao()
    private val search = db.searchDao()
    private val zones = db.zoneDao()
    private val detections = db.detectionDao()
    private val photos = db.photoDao()

    private fun now() = clock.millis()

    val wishes: Flow<List<Wish>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun wish(id: String): Wish? = dao.get(id)?.toDomain()

    /** Saves a find in a photo to the wishlist, as [speciesName] or the user's [customName], and returns the wish. */
    suspend fun saveFind(detectionId: String, speciesName: String?, customName: String?, targetZoneId: String?): Wish = db.withTransaction {
        val detection = detections.get(detectionId) ?: error("No detection $detectionId")
        val wish = Wish(
            id = UUID.randomUUID().toString(),
            speciesName = speciesName,
            customName = customName?.trim()?.ifBlank { null },
            detectionId = detectionId,
            targetZoneId = targetZoneId,
            thumbnail = detection.thumbnail,
            createdAt = clock.instant(),
        )
        detections.setStatus(detectionId, DetectionStatus.SAVED.name, null, null)
        write(wish)
        wish
    }

    /** Adds a plant by name, such as a native alternative to a weed. */
    suspend fun add(speciesName: String, targetZoneId: String? = null): Wish {
        val wish = Wish(UUID.randomUUID().toString(), speciesName, null, null, targetZoneId = targetZoneId, createdAt = clock.instant())
        db.withTransaction { write(wish) }
        return wish
    }

    /** Saves changes to a wish, such as where it was seen, its price, its zone or its status. */
    suspend fun update(wish: Wish) = db.withTransaction {
        if (dao.get(wish.id) == null) return@withTransaction
        write(wish.copy(customName = wish.customName?.trim()?.ifBlank { null }, placeName = wish.placeName.trim(), notes = wish.notes.trim()))
    }

    suspend fun setStatus(id: String, status: WishStatus) = db.withTransaction {
        val wish = dao.get(id)?.toDomain() ?: return@withTransaction
        write(wish.copy(status = status))
    }

    /** Removes a wish, and the photo taken away from home that it was saved from, unless more was saved from it. */
    suspend fun delete(id: String) {
        val photoId = db.withTransaction {
            val wish = dao.get(id) ?: return@withTransaction null
            dao.delete(id)
            search.delete(id)
            wish.detectionId?.let { detectionId ->
                detections.setStatus(detectionId, DetectionStatus.REJECTED.name, null, null)
                detections.get(detectionId)?.photoId
            }
        }
        photoId?.let { discardFieldPhoto(it) }
    }

    /**
     * Deletes a photo taken away from home, with its finds and their files, unless something in it was saved to the
     * wishlist. A wish keeps its cut-out, so a cut-out a wish still shows is left in place.
     */
    suspend fun discardFieldPhoto(photoId: String) {
        val files = db.withTransaction {
            val photo = photos.get(photoId) ?: return@withTransaction emptyList()
            if (photo.origin != "FIELD" || detections.savedInPhoto(photoId) > 0) return@withTransaction emptyList()
            val kept = dao.all().mapNotNull { it.thumbnail }.toSet()
            val paths = listOf(photo.path) + detections.thumbnailsInPhoto(photoId).filterNot { it in kept }
            photos.delete(photoId)
            paths
        }
        files.forEach { deleteFile(it) }
    }

    /** Every wish as linked data. */
    suspend fun triples(): List<Triple> {
        val kb = knowledge()
        return dao.all().flatMap { entity -> entity.toDomain().let { WishGraph.wish(it, it.speciesName?.let(kb::species)) } }
    }

    /** Puts every wish in the search index again, after the garden's index has been rebuilt. */
    suspend fun reindex() = dao.all().forEach { index(it.toDomain()) }

    private suspend fun write(wish: Wish) {
        val createdAt = dao.get(wish.id)?.createdAt ?: wish.createdAt.toEpochMilli()
        dao.upsert(
            WishEntity(
                wish.id, wish.speciesName, wish.customName, wish.detectionId, wish.thumbnail, wish.placeName, wish.latE7, wish.lonE7,
                wish.priceCents, wish.notes, wish.targetZoneId, wish.status.name, createdAt, now(),
            ),
        )
        index(wish)
    }

    private suspend fun index(wish: Wish) {
        val doc = SearchText.wish(wish, wish.speciesName?.let(knowledge()::species), wish.targetZoneId?.let { zones.get(it)?.name })
        search.delete(wish.id)
        search.replace(SearchEntity(recordId = doc.id, kind = doc.kind.name, title = doc.title, body = doc.body))
    }

    private fun WishEntity.toDomain() = Wish(
        id, speciesName, customName, detectionId, placeName, latE7, lonE7, priceCents, notes, targetZoneId,
        WishStatus.entries.firstOrNull { it.name == status } ?: WishStatus.WANTED, thumbnail, Instant.ofEpochMilli(createdAt),
    )
}
