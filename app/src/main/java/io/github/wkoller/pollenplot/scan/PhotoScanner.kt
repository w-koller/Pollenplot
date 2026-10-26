package io.github.wkoller.pollenplot.scan

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import io.github.wkoller.pollenplot.data.GardenRepository
import io.github.wkoller.pollenplot.data.PhotoAnalysis
import io.github.wkoller.pollenplot.data.ScanOutcome
import io.github.wkoller.pollenplot.data.photos.PhotoStore
import io.github.wkoller.pollenplot.domain.id.PhotoOrigin
import java.time.Clock
import java.time.Instant

/** What a scan of some photos found. */
data class ScanTally(val photos: Int = 0, val photosWithPlants: Int = 0, val plants: Int = 0, val unreadable: Int = 0) {
    fun withPhoto(plantsFound: Int?): ScanTally = when (plantsFound) {
        null -> copy(photos = photos + 1, unreadable = unreadable + 1)
        0 -> copy(photos = photos + 1)
        else -> copy(photos = photos + 1, photosWithPlants = photosWithPlants + 1, plants = plants + plantsFound)
    }
}

/**
 * Looks for plants in photos from the gallery and photos the user adds. Photos that show plants are kept for review;
 * the others are not kept. Each gallery photo is remembered, so no scan looks at it twice.
 */
class PhotoScanner(
    private val gallery: Gallery,
    /** The quick model alone, for background scans of the gallery; the accurate pass follows while charging. */
    private val quick: PhotoAnalysis,
    /** BioCAP too when its pack is on the phone, for photos the user added and the accurate pass. */
    private val analysis: PhotoAnalysis,
    private val garden: GardenRepository,
    private val clock: Clock,
) {
    /** Scans one gallery photo and returns how many plants it showed, or null if it could not be read. */
    suspend fun scanLibraryPhoto(photo: GalleryPhoto): Int? {
        val bitmap = gallery.load(photo.uri, PhotoStore.MAX_SIDE)
        if (bitmap == null) {
            garden.recordScan(photo.id, photo.takenAt, ScanOutcome.FAILED, null)
            return null
        }
        val result = quick.analyse(bitmap, PhotoOrigin.LIBRARY, photo.takenAt)
        val outcome = if (result.detections.isEmpty()) ScanOutcome.NO_PLANT else ScanOutcome.PLANTS
        garden.recordScan(photo.id, photo.takenAt, outcome, result.photo?.id)
        return result.detections.size
    }

    /**
     * Looks again at a gallery photo the quick model saw no plant in, now that the accurate pack is on the phone, and
     * returns how many plants it showed, or null if it could not be read.
     */
    suspend fun recheckLibraryPhoto(mediaId: Long, takenAt: Instant): Int? {
        val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, mediaId)
        val bitmap = gallery.load(uri, PhotoStore.MAX_SIDE)
        if (bitmap == null) {
            garden.recordScan(mediaId, takenAt, ScanOutcome.FAILED, null, accurate = true)
            return null
        }
        val result = analysis.analyse(bitmap, PhotoOrigin.LIBRARY, takenAt)
        val outcome = if (result.detections.isEmpty()) ScanOutcome.NO_PLANT else ScanOutcome.PLANTS
        garden.recordScan(mediaId, takenAt, outcome, result.photo?.id, accurate = true)
        return result.detections.size
    }

    /** Scans a photo the user added and returns how many plants it showed, or null if it could not be read. */
    suspend fun scanAddedPhoto(uri: Uri): Int? {
        val bitmap = gallery.load(uri, PhotoStore.MAX_SIDE) ?: return null
        val takenAt = gallery.takenAt(uri) ?: clock.instant()
        val result = analysis.analyse(bitmap, PhotoOrigin.IMPORT, takenAt)
        // A photo from the gallery is remembered, so a later scan of the gallery does not add its plants twice.
        mediaIdOf(uri)?.let { id ->
            garden.recordScan(id, takenAt, if (result.detections.isEmpty()) ScanOutcome.NO_PLANT else ScanOutcome.PLANTS, result.photo?.id)
        }
        return result.detections.size
    }

    /**
     * The media store ID behind a link to a photo on the phone: a media store link, or the photo picker's link to a
     * local photo. Photos from cloud albums have IDs of their own, so they are not matched.
     */
    private fun mediaIdOf(uri: Uri): Long? {
        if (uri.authority != MediaStore.AUTHORITY) return null
        val path = uri.pathSegments
        val local = path.firstOrNull() == "external" || (path.firstOrNull() == "picker" && path.getOrNull(2) == LOCAL_PICKER)
        return if (local) uri.lastPathSegment?.toLongOrNull() else null
    }

    private companion object {
        /** The photo picker's provider for photos stored on the phone. */
        const val LOCAL_PICKER = "com.android.providers.media.photopicker"
    }
}
