package io.github.wkoller.pollenplot.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import io.github.wkoller.pollenplot.data.kb.KnowledgeRepository
import io.github.wkoller.pollenplot.data.photos.PhotoStore
import io.github.wkoller.pollenplot.data.photos.StoredPhoto
import io.github.wkoller.pollenplot.domain.garden.PlantStatus
import io.github.wkoller.pollenplot.domain.id.BoxBp
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant

/** A plant a model can name, by its scientific name, with a common name when the model has one. */
data class NamedPlant(val name: String, val common: String? = null)

/** A photo after analysis: whether it showed what was looked for, and each plant or animal found, waiting for review. */
data class AnalysedPhoto(
    val photo: StoredPhoto?,
    val found: Boolean,
    val detections: List<DetectionRecord>,
    /** The same picture was already kept, and these are the finds it gave then; nothing new was recorded. */
    val duplicate: Boolean = false,
)

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
    /** The plants the models can name, for the search that corrects a suggestion. */
    private val names: suspend () -> List<NamedPlant> = { emptyList() },
) {
    /** The plants the models can name beyond the knowledge base, for the search that corrects a suggestion. */
    suspend fun plantNames(): List<NamedPlant> = names()

    /**
     * Looks again at a find the user says is of another [group], such as an animal where a plant was looked for, and
     * gives it that group's suggestions. A group no model on the phone can name leaves it with none, for the user to
     * name.
     */
    suspend fun regroup(detection: DetectionRecord, group: LifeGroup) {
        if (detection.group == group) return
        val stored = garden.photo(detection.photoId) ?: return
        val part = withContext(Dispatchers.IO) {
            BitmapFactory.decodeFile(photos.file(stored.path).path)?.let { photo ->
                val left = (photo.width.toLong() * detection.box.left / BoxBp.FULL).toInt().coerceIn(0, photo.width - 1)
                val top = (photo.height.toLong() * detection.box.top / BoxBp.FULL).toInt().coerceIn(0, photo.height - 1)
                val width = (photo.width.toLong() * detection.box.width / BoxBp.FULL).toInt().coerceIn(1, photo.width - left)
                val height = (photo.height.toLong() * detection.box.height / BoxBp.FULL).toInt().coerceIn(1, photo.height - top)
                Bitmap.createBitmap(photo, left, top, width, height)
            }
        } ?: return
        val region = identifier().identify(part, group, PhotoScope.ONE).regions.firstOrNull()
        val candidates = if (region == null) {
            emptyList()
        } else {
            val plant = group == LifeGroup.PLANT
            val away = garden.photoOrigin(detection.photoId) == PhotoOrigin.FIELD
            val nameOf: (String) -> String? = if (plant) {
                val kb = knowledge.await()
                ({ label -> kb.species(label)?.name })
            } else {
                val fauna = knowledge.awaitFauna()
                ({ label -> fauna.animal(label)?.name })
            }
            val growing = when {
                away -> emptySet()
                plant -> garden.plantList.first().filter { it.status == PlantStatus.GROWING }.mapNotNull { it.speciesName }.toSet()
                else -> garden.sightedSpecies()
            }
            garden.idHistory().apply(CandidateFusion.fuse(region.labels, nameOf, growing, nearby = if (away) { _ -> 0 } else nearby), nameOf)
        }
        garden.regroupDetection(detection.id, group, candidates)
    }

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
        // A picture from the phone's photos that was already kept is not kept again: its finds are the ones it gave
        // then, unless it is now looked at for another kind of thing. A photo from the camera is always a new one.
        val hash = if (origin == PhotoOrigin.IMPORT || origin == PhotoOrigin.LIBRARY) withContext(Dispatchers.Default) { contentHash(bitmap) } else null
        if (hash != null) {
            val kept = garden.photoWithHash(hash)
            val finds = kept?.let { garden.findsInPhoto(it.id) }.orEmpty()
            if (kept != null && finds.isNotEmpty() && (group == null || finds.any { it.group == group })) {
                return AnalysedPhoto(kept, true, finds, duplicate = true)
            }
        }
        val models = identifier()
        // The camera says what it was pointed at; a photo from the gallery is looked at for what it shows.
        val found = models.identify(bitmap, group ?: models.groupOf(bitmap), scope)
        if (found.regions.isEmpty()) return AnalysedPhoto(null, found.found, emptyList())
        val kb = knowledge.await()
        val fauna = knowledge.awaitFauna()
        // Species already growing in the garden are boosted, because most photos show the same plants again, and
        // species recorded near the garden a little. A photo taken away from home gets neither boost.
        val away = origin == PhotoOrigin.FIELD
        val gardenSpecies = if (away) emptySet() else garden.plantList.first().filter { it.status == PlantStatus.GROWING }.mapNotNull { it.speciesName }.toSet()
        // An animal already recorded in the garden gets the same boost as a plant growing in it.
        val sighted = if (away || found.regions.all { it.group == LifeGroup.PLANT }) emptySet() else garden.sightedSpecies()
        val recorded: (String) -> Int = if (away) { _ -> 0 } else nearby
        // A name the user has corrected before is corrected here too.
        val history = garden.idHistory()
        val fused = found.regions.map { region ->
            val nameOf: (String) -> String? =
                if (region.group == LifeGroup.PLANT) { label -> kb.species(label)?.name } else { label -> fauna.animal(label)?.name }
            val growing = if (region.group == LifeGroup.PLANT) gardenSpecies else sighted
            val candidates = history.apply(CandidateFusion.fuse(region.labels, nameOf, growing, nearby = recorded), nameOf)
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
        return AnalysedPhoto(stored, found.found, garden.recordPhoto(stored, origin, takenAt, drafts, contentHash = hash))
    }

    /** SHA-256 of a picture's size and pixels, which is the same whenever the same file is read the same way. */
    private fun contentHash(bitmap: Bitmap): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(ByteBuffer.allocate(8).putInt(bitmap.width).putInt(bitmap.height).array())
        val row = IntArray(bitmap.width)
        val bytes = ByteBuffer.allocate(bitmap.width * 4)
        for (y in 0 until bitmap.height) {
            bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
            bytes.clear()
            bytes.asIntBuffer().put(row)
            digest.update(bytes.array())
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
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
