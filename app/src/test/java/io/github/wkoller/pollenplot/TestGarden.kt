package io.github.wkoller.pollenplot

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import io.github.wkoller.pollenplot.domain.id.BoxBp
import io.github.wkoller.pollenplot.domain.id.IdSource
import io.github.wkoller.pollenplot.domain.id.LabelScore
import io.github.wkoller.pollenplot.domain.id.TileResult
import io.github.wkoller.pollenplot.ml.FoundRegion
import io.github.wkoller.pollenplot.ml.Identification
import io.github.wkoller.pollenplot.domain.wildlife.LifeGroup
import io.github.wkoller.pollenplot.ml.Identifier
import io.github.wkoller.pollenplot.ml.text.Embedder
import io.github.wkoller.pollenplot.scan.Album
import io.github.wkoller.pollenplot.scan.Gallery
import io.github.wkoller.pollenplot.scan.GalleryAccess
import io.github.wkoller.pollenplot.scan.GalleryPhoto
import java.time.Instant
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * The app with a gallery of painted photos and a stand-in for the models, for tests of scanning and review. Tests that
 * need it say `@Config(application = TestApp::class)`.
 */
class TestApp : PollenplotApp() {
    val gallery = FakeGallery()
    val identifier = ScriptedIdentifier()

    override fun createContainer(): AppContainer = AppContainer(this, identifier = identifier, gallery = gallery)
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
 * animal in the middle of the frame is.
 */
class ScriptedIdentifier(
    var animalFor: (Bitmap, LifeGroup) -> List<FoundRegion> = { _, _ -> emptyList() },
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
