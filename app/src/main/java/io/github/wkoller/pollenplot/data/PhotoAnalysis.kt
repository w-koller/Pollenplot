package io.github.wkoller.pollenplot.data

import android.graphics.Bitmap
import io.github.wkoller.pollenplot.data.kb.KnowledgeRepository
import io.github.wkoller.pollenplot.data.photos.PhotoStore
import io.github.wkoller.pollenplot.data.photos.StoredPhoto
import io.github.wkoller.pollenplot.domain.garden.PlantStatus
import io.github.wkoller.pollenplot.domain.id.CandidateFusion
import io.github.wkoller.pollenplot.domain.id.PhotoOrigin
import io.github.wkoller.pollenplot.domain.wildlife.LifeGroup
import io.github.wkoller.pollenplot.ml.Identifier
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Instant

/** A photo after analysis: whether it showed what was looked for, and each plant or animal found, waiting for review. */
data class AnalysedPhoto(val photo: StoredPhoto?, val found: Boolean, val detections: List<DetectionRecord>)

/**
 * Identifies the plants or the animal in a photo, keeps the photo and a cut-out of each, and records them for review.
 * The camera, imported photos and the background scan all come through here. A photo with nothing found in it is not
 * kept.
 */
class PhotoAnalysis(
    private val identifier: () -> Identifier,
    private val photos: PhotoStore,
    private val garden: GardenRepository,
    private val knowledge: KnowledgeRepository,
    private val clock: Clock,
    /** How many times a plant or animal has been recorded near the garden, for the boost they get. */
    private val nearby: (String) -> Int = { 0 },
) {
    /**
     * Looks for what [group] the camera was pointed at in [bitmap], or, for a photo nobody chose a group for, for what
     * the identifier says the photo shows.
     */
    suspend fun analyse(bitmap: Bitmap, origin: PhotoOrigin, takenAt: Instant = clock.instant(), group: LifeGroup? = null): AnalysedPhoto {
        val models = identifier()
        // The camera says what it was pointed at; a photo from the gallery is looked at for what it shows.
        val found = models.identify(bitmap, group ?: models.groupOf(bitmap))
        if (found.regions.isEmpty()) return AnalysedPhoto(null, found.found, emptyList())
        val kb = knowledge.await()
        val fauna = knowledge.awaitFauna()
        // Species already growing in the garden are boosted, because most photos show the same plants again, and
        // species recorded near the garden a little.
        val gardenSpecies = garden.plantList.first().filter { it.status == PlantStatus.GROWING }.mapNotNull { it.speciesName }.toSet()
        val stored = photos.savePhoto(bitmap)
        val drafts = found.regions.map { region ->
            val candidates = if (region.group == LifeGroup.PLANT) {
                CandidateFusion.fuse(region.labels, kb, gardenSpecies, nearby = nearby)
            } else {
                CandidateFusion.fuse(region.labels, { label -> fauna.animal(label)?.name }, emptySet(), nearby = nearby)
            }
            DetectionDraft(region.box, photos.saveThumbnail(region.thumbnail), region.flowering, candidates, region.group)
        }
        return AnalysedPhoto(stored, found.found, garden.recordPhoto(stored, origin, takenAt, drafts))
    }
}
