package io.github.wkoller.pollenplot.data

import android.graphics.Bitmap
import io.github.wkoller.pollenplot.data.kb.KnowledgeRepository
import io.github.wkoller.pollenplot.data.photos.PhotoStore
import io.github.wkoller.pollenplot.data.photos.StoredPhoto
import io.github.wkoller.pollenplot.domain.garden.PlantStatus
import io.github.wkoller.pollenplot.domain.id.Candidate
import io.github.wkoller.pollenplot.domain.id.CandidateFusion
import io.github.wkoller.pollenplot.domain.id.FlowerCall
import io.github.wkoller.pollenplot.domain.id.FoundMerge
import io.github.wkoller.pollenplot.domain.id.PhotoOrigin
import io.github.wkoller.pollenplot.domain.id.PhotoScope
import io.github.wkoller.pollenplot.domain.plants.CareResolver
import io.github.wkoller.pollenplot.domain.plants.KnowledgeBase
import io.github.wkoller.pollenplot.domain.season.Hemisphere
import io.github.wkoller.pollenplot.domain.season.MonthSet
import io.github.wkoller.pollenplot.domain.wildlife.LifeGroup
import io.github.wkoller.pollenplot.ml.FoundRegion
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
    /** The garden's hemisphere, which the month a photo was taken is read in. */
    private val hemisphere: suspend () -> Hemisphere = { Hemisphere.SOUTHERN },
) {
    /**
     * Looks for what [group] the camera was pointed at in [bitmap], or, for a photo nobody chose a group for, for what
     * the identifier says the photo shows. [scope] says whether a plant photo shows one plant or several.
     */
    suspend fun analyse(
        bitmap: Bitmap,
        origin: PhotoOrigin,
        takenAt: Instant = clock.instant(),
        group: LifeGroup? = null,
        scope: PhotoScope = PhotoScope.SEVERAL,
    ): AnalysedPhoto {
        val models = identifier()
        // The camera says what it was pointed at; a photo from the gallery is looked at for what it shows.
        val found = models.identify(bitmap, group ?: models.groupOf(bitmap), scope)
        if (found.regions.isEmpty()) return AnalysedPhoto(null, found.found, emptyList())
        val kb = knowledge.await()
        val fauna = knowledge.awaitFauna()
        // Species already growing in the garden are boosted, because most photos show the same plants again, and
        // species recorded near the garden a little.
        val gardenSpecies = garden.plantList.first().filter { it.status == PlantStatus.GROWING }.mapNotNull { it.speciesName }.toSet()
        val fused = found.regions.map { region ->
            val candidates = if (region.group == LifeGroup.PLANT) {
                CandidateFusion.fuse(region.labels, kb, gardenSpecies, nearby = nearby)
            } else {
                CandidateFusion.fuse(region.labels, { label -> fauna.animal(label)?.name }, emptySet(), nearby = nearby)
            }
            Fused(region, candidates, region.flowering, region.modelFlowering)
        }
        // Tiles overlap, so one plant is often found several times; once its suggestions are known it is shown once.
        val merged = FoundMerge.merge(
            fused,
            box = { it.region.box },
            candidates = { it.candidates },
            fold = { kept, other -> kept.copy(colour = kept.colour || other.colour, model = foldModel(kept.model, other.model)) },
        )
        val month = takenAt.atZone(clock.zone).month
        val hemisphere = hemisphere()
        val stored = photos.savePhoto(bitmap)
        val drafts = merged.map { (region, candidates, colour, model) ->
            val flowering = region.group == LifeGroup.PLANT && FlowerCall.decide(model, floweringMonths(kb, candidates), month, colour, hemisphere).flowering
            DetectionDraft(region.box, photos.saveThumbnail(region.thumbnail), flowering, candidates, region.group)
        }
        return AnalysedPhoto(stored, found.found, garden.recordPhoto(stored, origin, takenAt, drafts))
    }

    /**
     * A region with its fused suggestions, before the same plant found twice is made one, with its signs of flowers:
     * the [colour] hint and BioCAP's answer, the [model].
     */
    private data class Fused(val region: FoundRegion, val candidates: List<Candidate>, val colour: Boolean, val model: Boolean?)

    /** BioCAP's answers for two looks at one plant: flowers seen in either, else none seen in either, else no answer. */
    private fun foldModel(a: Boolean?, b: Boolean?): Boolean? = if (a == true || b == true) true else a ?: b

    companion object {
        /** The flowering months of the plant a find most likely is, when the knowledge base records them. */
        fun floweringMonths(kb: KnowledgeBase, candidates: List<Candidate>): MonthSet? {
            val species = candidates.firstOrNull()?.speciesName?.let(kb::species) ?: return null
            return CareResolver.resolve(kb, species).care.flowering
        }
    }
}
