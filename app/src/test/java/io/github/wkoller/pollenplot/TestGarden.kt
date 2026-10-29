package io.github.wkoller.pollenplot

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import io.github.wkoller.pollenplot.ai.AskSubject
import io.github.wkoller.pollenplot.ai.Assistant
import io.github.wkoller.pollenplot.ai.AssistantReply
import io.github.wkoller.pollenplot.domain.assistant.CitedRecord
import io.github.wkoller.pollenplot.domain.assistant.DraftedCare
import io.github.wkoller.pollenplot.domain.id.BoxBp
import io.github.wkoller.pollenplot.domain.id.IdSource
import io.github.wkoller.pollenplot.domain.id.LabelScore
import io.github.wkoller.pollenplot.domain.id.TileResult
import io.github.wkoller.pollenplot.domain.wildlife.LifeGroup
import io.github.wkoller.pollenplot.ml.FoundRegion
import io.github.wkoller.pollenplot.ml.Identification
import io.github.wkoller.pollenplot.ml.Identifier
import io.github.wkoller.pollenplot.ml.text.Embedder
import io.github.wkoller.pollenplot.scan.Album
import io.github.wkoller.pollenplot.scan.Gallery
import io.github.wkoller.pollenplot.scan.GalleryAccess
import io.github.wkoller.pollenplot.scan.GalleryPhoto
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Instant

/**
 * The app with a gallery of painted photos and a stand-in for the models, for tests of scanning and review. Tests that
 * need it say `@Config(application = TestApp::class)`.
 */
class TestApp : PollenplotApp() {
    val gallery = FakeGallery()
    val identifier = ScriptedIdentifier()

    override fun createContainer(): AppContainer = AppContainer(this, identifier = identifier, gallery = gallery)
}

/** The app on a fixed morning, 28 October 2026 in Brisbane, so the Wildlife tab's year and months stay put. */
class WildlifeTestApp : PollenplotApp() {
    override fun createContainer(): AppContainer = AppContainer(this, fixedClock = DemoWildlife.clock)
}

/** The app with a stand-in for Gemma 4, which cannot run on the JVM, on the same fixed morning as the wildlife tests. */
class AskTestApp : PollenplotApp() {
    val assistant = FakeAssistant()

    override fun createContainer(): AppContainer = AppContainer(this, assistant = assistant, fixedClock = DemoWildlife.clock)
}

/**
 * Stands in for Gemma 4: answers every question with [answer], citing [cited], a word at a time, and gives the second
 * opinion [choose] picks.
 */
class FakeAssistant(
    var answer: String = "",
    var cited: List<CitedRecord> = emptyList(),
    var choose: (List<String>) -> Int? = { null },
) : Assistant {
    /** The suggestions each second opinion was asked about. */
    val opinions = mutableListOf<List<String>>()

    /** The care drafted for every plant asked about, and the plants asked about. */
    var draft: DraftedCare? = null
    val drafted = mutableListOf<String>()

    override suspend fun draftCare(name: String): DraftedCare? {
        drafted += name
        return draft
    }

    override suspend fun secondOpinion(thumbnail: String, suggestions: List<String>): Int? {
        opinions += suggestions
        return choose(suggestions)
    }

    /** The questions asked, with the official weed status the screen passed for each. */
    val asked = mutableListOf<Pair<String, String?>>()

    override val ready: Boolean = true

    override fun ask(subject: AskSubject, question: String, history: List<Pair<String, String>>, weedStatus: String?): Flow<AssistantReply> = flow {
        asked += question to weedStatus
        emit(AssistantReply.Loading)
        val words = answer.split(" ")
        for (i in 1 until words.size) emit(AssistantReply.Streaming(words.take(i).joinToString(" ")))
        emit(AssistantReply.Done(answer, cited))
    }

    override fun stop() = Unit
}

/** The app with a stand-in for the text model, for tests of search by meaning. */
class MeaningTestApp : PollenplotApp() {
    override fun createContainer(): AppContainer = AppContainer(this, embedder = ConceptEmbedder())
}

/**
 * Stands in for the text model: each vector has one place for each idea, found by its words, so "poisonous" and
 * "toxic" mean the same. Text with none of the ideas gets a place of its own and matches nothing.
 */
class ConceptEmbedder : Embedder {
    private val concepts = listOf(
        setOf("toxic", "poison", "poisonous"),
        setOf("edible", "fruit", "eat"),
        setOf("bird", "birds"),
        setOf("frog", "frogs"),
        setOf("bee", "bees", "pollinator"),
    )

    override fun embedPassage(text: String) = vector(text)

    override fun embedQuery(text: String) = vector(text)

    private fun vector(text: String): FloatArray {
        val words = text.lowercase().split(Regex("[^a-z]+")).toSet()
        val vector = FloatArray(concepts.size + 1) { i -> if (i < concepts.size && concepts[i].any { it in words }) 1f else 0f }
        if (vector.all { it == 0f }) vector[concepts.size] = 1f
        val length = kotlin.math.sqrt(vector.sumOf { (it * it).toDouble() }).toFloat()
        return FloatArray(vector.size) { vector[it] / length }
    }
}

/**
 * Stands in for the on-device models: it finds whatever [regionsFor] says each photo shows, and [animalFor] says the
 * animal in the middle of the frame is. [groupFor] is what the accurate pack would say a photo shows.
 */
class ScriptedIdentifier(
    var animalFor: (Bitmap, LifeGroup) -> List<FoundRegion> = { _, _ -> emptyList() },
    var groupFor: (Bitmap) -> LifeGroup = { LifeGroup.PLANT },
    var regionsFor: (Bitmap) -> List<FoundRegion> = { emptyList() },
) : Identifier {
    /** The groups the camera asked about, in order. */
    val asked = mutableListOf<LifeGroup>()

    override suspend fun identify(photo: Bitmap, group: LifeGroup): Identification {
        asked += group
        val regions = if (group == LifeGroup.PLANT) regionsFor(photo) else animalFor(photo, group)
        return Identification(found = regions.isNotEmpty(), regions = regions)
    }

    override suspend fun glance(frame: Bitmap, group: LifeGroup) = TileResult(BoxBp.WHOLE, emptyList(), backgroundBp = 0)

    override suspend fun groupOf(photo: Bitmap) = groupFor(photo)

    override fun close() = Unit
}

/** A gallery held in memory. Photos in [FakeGallery.CAMERA] count as the camera's own album. */
class FakeGallery : Gallery {
    var access = GalleryAccess.FULL
    private val photos = mutableListOf<Pair<GalleryPhoto, Bitmap>>()

    fun add(id: Long, takenAt: Instant, bitmap: Bitmap, albumId: String = CAMERA): GalleryPhoto {
        val photo = GalleryPhoto(id, Uri.parse("content://media/external/images/media/$id"), takenAt, albumId)
        photos += photo to bitmap
        return photo
    }

    override fun access() = access

    override fun photosSince(since: Instant, albums: Set<String>?): List<GalleryPhoto> = photos.map { it.first }
        .filter { !it.takenAt.isBefore(since) && (if (albums == null) it.albumId == CAMERA else it.albumId in albums) }
        .sortedByDescending { it.takenAt }

    override fun albums(): List<Album> = photos.groupBy { it.first.albumId }.map { (id, list) ->
        Album(id ?: "", if (id == CAMERA) "Camera" else id ?: "", list.size, camera = id == CAMERA)
    }

    override fun load(uri: Uri, maxSide: Int): Bitmap? = photos.firstOrNull { it.first.uri == uri }?.second

    override fun takenAt(uri: Uri): Instant? = photos.firstOrNull { it.first.uri == uri }?.first?.takenAt

    companion object {
        const val CAMERA = "camera"
    }
}

/** Painted garden photos: plants as scattered leaves with round flowers, on mulch. */
object TestPhotos {
    class Painted(val box: BoxBp, val leaf: Int, val flower: Int?)

    val bottlebrush = Painted(BoxBp(0, 3_000, 4_000, 9_000), Color.rgb(64, 110, 52), Color.rgb(200, 30, 45))
    val rosemary = Painted(BoxBp(3_500, 1_000, 6_500, 7_000), Color.rgb(88, 120, 96), Color.rgb(150, 160, 220))
    val vine = Painted(BoxBp(6_000, 3_000, 10_000, 9_000), Color.rgb(70, 130, 40), Color.rgb(245, 200, 30))

    /** What the models "found" in the three-plant photo: a bottlebrush, rosemary, and a yellow flower they are unsure of. */
    fun threePlants() = listOf(
        region(bottlebrush, flowering = true, LabelScore("Callistemon citrinus", 7_400), LabelScore("Hibiscus syriacus", 900)),
        region(rosemary, flowering = false, LabelScore("Rosmarinus officinalis", 4_300), LabelScore("Lavandula stoechas", 2_100)),
        region(vine, flowering = false, LabelScore("Hibiscus trionum", 2_600), LabelScore("Hibiscus rosa-sinensis", 1_400)),
    )

    fun region(plant: Painted, flowering: Boolean, vararg labels: LabelScore) =
        FoundRegion(plant.box, mapOf(IdSource.AIY_PLANTS to labels.toList()), paintedPlant(plant, 512), flowering)

    /** A garden bed with [plants] in their boxes, on mulch. */
    fun gardenPhoto(plants: List<Painted> = listOf(bottlebrush, rosemary, vine)): Bitmap {
        val width = 1_600
        val height = 1_200
        val photo = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(photo)
        canvas.drawColor(Color.rgb(104, 80, 58))
        for (plant in plants) {
            val target = Rect(
                plant.box.left * width / BoxBp.FULL,
                plant.box.top * height / BoxBp.FULL,
                plant.box.right * width / BoxBp.FULL,
                plant.box.bottom * height / BoxBp.FULL,
            )
            canvas.drawBitmap(paintedPlant(plant, 512, mulch = false), null, target, null)
        }
        return photo
    }

    /** Leaves of one colour, scattered, with round flowers of another. */
    fun paintedPlant(plant: Painted, size: Int, mulch: Boolean = true): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (mulch) canvas.drawColor(Color.rgb(104, 80, 58))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val random = Random(plant.leaf)
        val centre = size / 2f
        repeat(90) {
            val angle = random.nextFloat() * 360f
            val reach = random.nextFloat() * size * 0.42f
            val x = centre + reach * cos(Math.toRadians(angle.toDouble())).toFloat()
            val y = centre + reach * sin(Math.toRadians(angle.toDouble())).toFloat()
            paint.color = shade(plant.leaf, random.nextFloat())
            canvas.save()
            canvas.rotate(angle, x, y)
            canvas.drawOval(x - size * 0.09f, y - size * 0.025f, x + size * 0.09f, y + size * 0.025f, paint)
            canvas.restore()
        }
        plant.flower?.let { colour ->
            repeat(16) {
                paint.color = shade(colour, random.nextFloat())
                canvas.drawCircle(centre + (random.nextFloat() - 0.5f) * size * 0.7f, centre + (random.nextFloat() - 0.5f) * size * 0.7f, size * 0.045f, paint)
            }
        }
        return bitmap
    }

    private fun shade(colour: Int, amount: Float): Int {
        val f = 0.75f + amount * 0.4f
        return Color.rgb((Color.red(colour) * f).toInt().coerceAtMost(255), (Color.green(colour) * f).toInt().coerceAtMost(255), (Color.blue(colour) * f).toInt().coerceAtMost(255))
    }
}

/**
 * How long a screen test waits for something to appear. A wait ends as soon as it is met, so this only matters on a
 * slow machine, such as CI running the tests beside lint and the build.
 */
const val WAIT_MS = 30_000L
