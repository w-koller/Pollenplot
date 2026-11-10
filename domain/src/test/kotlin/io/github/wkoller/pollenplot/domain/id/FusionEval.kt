package io.github.wkoller.pollenplot.domain.id

import io.github.wkoller.pollenplot.domain.plants.KnowledgeBase
import io.github.wkoller.pollenplot.domain.plants.KnowledgeBaseFilesTest
import io.github.wkoller.pollenplot.domain.wildlife.FaunaBase
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * One photo of the fusion evaluation: what it shows, and what each of the phone's models said of it, as
 * tools/models/eval_fusion.py recorded them. [biocap] holds BioCAP's closest species of the photo's group with their
 * cosine similarities for each look at the photo; [aiyCentre] and [aiyWhole] are the quick model's two looks, for the
 * groups that have one.
 */
class EvalPhoto(
    val species: String,
    val also: List<String>,
    val group: String,
    val biocap: Map<String, List<Pair<String, Float>>>,
    val aiyCentre: TileResult?,
    val aiyWhole: TileResult?,
)

/** How often the right species came first and among the first five, of [photos]. */
data class EvalScore(val photos: Int, val top1: Int, val top5: Int) {
    /** In basis points, so a baseline is written and compared as whole numbers. */
    val top1Bp: Int get() = if (photos == 0) 0 else top1 * BoxBp.FULL / photos
    val top5Bp: Int get() = if (photos == 0) 0 else top5 * BoxBp.FULL / photos

    operator fun plus(other: EvalScore) = EvalScore(photos + other.photos, top1 + other.top1, top5 + other.top5)

    override fun toString() = "%.1f%% first, %.1f%% in five, of %d".format(top1Bp / 100.0, top5Bp / 100.0, photos)
}

/**
 * The fusion evaluation: the recorded answers of the models the app ships for Australian photos of known species,
 * taken through the steps the phone takes for a photo of one plant or one animal, so a change to those steps or to
 * [CandidateFusion] is measured. The photos get no boost for the garden or for records nearby, as a photo taken away
 * from home gets none.
 */
class FusionEval(val photos: List<EvalPhoto>, val logitScale: Float, private val kb: KnowledgeBase, private val fauna: FaunaBase) {
    /** The knowledge base's name for a model's label, as the app matches plants to one and animals to the other. */
    fun nameOf(group: String): (String) -> String? =
        if (group == PLANT) { label -> kb.species(label)?.name } else { label -> fauna.animal(label)?.name }

    /** The quick model's answer for a photo of one plant or animal, as `FastPlantIdentifier` gives it; null for none. */
    fun quick(photo: EvalPhoto): List<LabelScore>? {
        val centre = photo.aiyCentre ?: return null
        if (photo.group != PLANT) return centre.labels.ifEmpty { null }
        return RegionPlanner.one(centre, photo.aiyWhole ?: return null)?.labels
    }

    /** The look the app gives a photo: three looks as one for a plant, and the middle of the frame for an animal. */
    fun lookFor(photo: EvalPhoto): String = if (photo.group == PLANT) ALL_LOOKS else CENTRE

    /** BioCAP's answer for one [look] at a photo, as `AccurateModels` gives it: shares of its closest species. */
    fun accurate(photo: EvalPhoto, look: String = lookFor(photo)): List<LabelScore> =
        AccurateRegions.shares(photo.biocap.getValue(look), logitScale).take(KEPT)

    /**
     * The suggestions the app gives for a photo with the accurate pack installed, best first, or none when it finds
     * nothing to name. [fuse] is the step being measured.
     */
    fun asTheApp(
        photo: EvalPhoto,
        accurate: List<LabelScore> = accurate(photo),
        fuse: (Map<IdSource, List<LabelScore>>, (String) -> String?) -> List<Candidate> = { results, nameOf -> CandidateFusion.fuse(results, nameOf, emptySet()) },
    ): List<Candidate> {
        val quick = quick(photo)
        // BioCAP names a species for any photo, so alone it counts for a plant only when it is as sure as for a new region.
        if (photo.group == PLANT && quick == null && (accurate.firstOrNull()?.scoreBp ?: 0) < AccurateRegions.MIN_SHARE_BP) return emptyList()
        val results = buildMap {
            if (quick != null) put(SOURCES.getValue(photo.group), quick)
            if (accurate.isNotEmpty()) put(IdSource.BIOCAP, accurate)
        }
        return fuse(results, nameOf(photo.group))
    }

    /** The suggestions without the accurate pack: the quick model alone, which names nothing for most animals. */
    fun withoutThePack(photo: EvalPhoto): List<Candidate> {
        val quick = quick(photo) ?: return emptyList()
        return CandidateFusion.fuse(mapOf(SOURCES.getValue(photo.group) to quick), nameOf(photo.group), emptySet())
    }

    /** Whether [candidate] names what [photo] shows, by the species' name, its other names or the knowledge base's. */
    fun names(candidate: Candidate, photo: EvalPhoto): Boolean {
        val nameOf = nameOf(photo.group)
        val right = (listOf(photo.species) + photo.also).flatMap { listOfNotNull(it, nameOf(it)) }.map(KnowledgeBase::normalise).toSet()
        return FoundMerge.key(candidate) in right || KnowledgeBase.normalise(candidate.label) in right
    }

    /** Each group's score, and `all`, for the suggestions [suggest] gives each photo. */
    fun measure(suggest: (EvalPhoto) -> List<Candidate>): Map<String, EvalScore> {
        val scores = linkedMapOf<String, EvalScore>()
        for (photo in photos) {
            val rank = suggest(photo).indexOfFirst { names(it, photo) }
            val score = EvalScore(1, if (rank == 0) 1 else 0, if (rank in 0..4) 1 else 0)
            scores.merge(photo.group, score, EvalScore::plus)
            scores.merge(ALL, score, EvalScore::plus)
        }
        return scores
    }

    companion object {
        const val PLANT = "plant"
        const val ALL = "all"
        const val CENTRE = "centre"
        const val MIDDLE = "middle"
        const val TIGHT = "tight"

        /** The three looks as one, which is how the app looks at a photo of one plant. */
        const val ALL_LOOKS = "all"

        /** The suggestions BioCAP's look keeps, as `AccurateModels.KEPT`. */
        const val KEPT = 5

        /** The quick model of each group that has one, as `AiyModel.of`. */
        val SOURCES = mapOf(PLANT to IdSource.AIY_PLANTS, "bird" to IdSource.AIY_BIRDS, "insect" to IdSource.AIY_INSECTS)

        /** The recorded answers in the test resources, with the knowledge bases the app ships. */
        fun load(): FusionEval {
            val stream = FusionEval::class.java.getResourceAsStream("/id/eval_outputs.json.gz") ?: error("eval_outputs.json.gz is in the test resources")
            val root = Json.parseToJsonElement(GZIPInputStream(stream).bufferedReader().use { it.readText() }).jsonObject
            val names = root.getValue("names").jsonArray.map { it.jsonPrimitive.content }
            fun name(index: Int) = names[index]
            fun labels(list: JsonArray) = list.map { pair -> LabelScore(name(pair.jsonArray[0].jsonPrimitive.int), pair.jsonArray[1].jsonPrimitive.int) }
            fun look(aiy: JsonObject, key: String) =
                TileResult(BoxBp.WHOLE, labels(aiy.getValue(key).jsonArray), aiy.getValue(key + "Background").jsonPrimitive.int)
            val photos = root.getValue("photos").jsonArray.map { element ->
                val photo = element.jsonObject
                val aiy = photo["aiy"]?.jsonObject
                EvalPhoto(
                    species = name(photo.getValue("species").jsonPrimitive.int),
                    also = photo.getValue("also").jsonArray.map { name(it.jsonPrimitive.int) },
                    group = photo.getValue("group").jsonPrimitive.content,
                    biocap = photo.getValue("biocap").jsonObject.mapValues { (_, list) ->
                        list.jsonArray.map { pair -> name(pair.jsonArray[0].jsonPrimitive.int) to pair.jsonArray[1].jsonPrimitive.float }
                    },
                    aiyCentre = aiy?.let { look(it, "centre") },
                    aiyWhole = aiy?.let { look(it, "whole") },
                )
            }
            val dir = File(System.getProperty("kb.dir") ?: error("kb.dir is set by domain/build.gradle.kts"), "fauna")
            val fauna = FaunaBase.parse(dir.listFiles { f -> f.name.endsWith(".json") }!!.sortedBy { it.name }.associate { it.name to it.readText() }).fauna
            return FusionEval(photos, root.getValue("logitScale").jsonPrimitive.float, KnowledgeBaseFilesTest.load().knowledgeBase, fauna)
        }
    }
}
