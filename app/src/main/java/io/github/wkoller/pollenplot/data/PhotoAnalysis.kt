package io.github.wkoller.pollenplot.data

import android.graphics.Bitmap
import io.github.wkoller.pollenplot.data.kb.KnowledgeRepository
import io.github.wkoller.pollenplot.data.photos.PhotoStore
import io.github.wkoller.pollenplot.data.photos.StoredPhoto
import io.github.wkoller.pollenplot.domain.garden.PlantStatus
import io.github.wkoller.pollenplot.domain.id.CandidateFusion
import io.github.wkoller.pollenplot.domain.id.PhotoOrigin
import io.github.wkoller.pollenplot.ml.PlantIdentifier
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Instant

/** A photo after analysis: whether it showed a plant, and each plant found in it, waiting for review. */
data class AnalysedPhoto(val photo: StoredPhoto?, val showsPlant: Boolean, val detections: List<DetectionRecord>)

/**
 * Identifies the plants in a photo, keeps the photo and a cut-out of each plant, and records them for review. The
 * camera, imported photos and the background scan all come through here. A photo with no plant in it is not kept.
 */
class PhotoAnalysis(
    private val identifier: () -> PlantIdentifier,
    private val photos: PhotoStore,
    private val garden: GardenRepository,
    private val knowledge: KnowledgeRepository,
    private val clock: Clock,
    /** How many times a plant has been recorded near the garden, for the boost such plants get. */
    private val nearby: (String) -> Int = { 0 },
) {
    suspend fun analyse(bitmap: Bitmap, origin: PhotoOrigin, takenAt: Instant = clock.instant()): AnalysedPhoto {
        val found = identifier().identify(bitmap)
        if (found.regions.isEmpty()) return AnalysedPhoto(null, found.showsPlant, emptyList())
        val kb = knowledge.await()
        // Species already growing in the garden are boosted, because most photos show the same plants again, and
        // species recorded near the garden a little.
        val gardenSpecies = garden.plantList.first().filter { it.status == PlantStatus.GROWING }.mapNotNull { it.speciesName }.toSet()
        val stored = photos.savePhoto(bitmap)
        val drafts = found.regions.map { region ->
            DetectionDraft(region.box, photos.saveThumbnail(region.thumbnail), region.flowering, CandidateFusion.fuse(region.labels, kb, gardenSpecies, nearby = nearby))
        }
        return AnalysedPhoto(stored, found.showsPlant, garden.recordPhoto(stored, origin, takenAt, drafts))
    }
}
