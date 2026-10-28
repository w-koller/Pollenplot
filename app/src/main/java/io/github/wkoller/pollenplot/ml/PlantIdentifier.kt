package io.github.wkoller.pollenplot.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import androidx.core.graphics.createBitmap
import io.github.wkoller.pollenplot.domain.id.BoxBp
import io.github.wkoller.pollenplot.domain.id.FlowerHint
import io.github.wkoller.pollenplot.domain.id.IdSource
import io.github.wkoller.pollenplot.domain.id.LabelScore
import io.github.wkoller.pollenplot.domain.id.RegionPlanner
import io.github.wkoller.pollenplot.domain.id.TileResult
import io.github.wkoller.pollenplot.domain.wildlife.LifeGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/** One region of a photo that seems to show a plant or an animal, with each model's suggestions and a cut-out. */
class FoundRegion(
    val box: BoxBp,
    val labels: Map<IdSource, List<LabelScore>>,
    val thumbnail: Bitmap,
    val flowering: Boolean,
    val group: LifeGroup = LifeGroup.PLANT,
)

/** What the identifier found in a photo: whether it shows what was looked for, and each region. */
class Identification(val found: Boolean, val regions: List<FoundRegion>)

/** What the quick model saw in a photo: every tile's scores, and the regions it was sure of. */
class QuickScan(val tiles: List<TileResult>, val regions: List<FoundRegion>, val showsPlant: Boolean)

/**
 * Identifies the plants and animals in photos and camera frames. The app uses the on-device models; tests use a fake.
 * [LifeGroup.PLANT] looks for every plant in a photo; an animal group, which the user chooses on the camera, looks at
 * the animal in the middle of the frame.
 */
interface Identifier {
    suspend fun identify(photo: Bitmap, group: LifeGroup = LifeGroup.PLANT): Identification

    /** A quick look at the middle of a camera frame, for the live suggestions. */
    suspend fun glance(frame: Bitmap, group: LifeGroup = LifeGroup.PLANT): TileResult

    fun close()
}

/** The AIY classifiers in the app's assets, each for one group, with the source its suggestions are credited to. */
enum class AiyModel(val model: String, val labels: String, val source: IdSource) {
    PLANTS("ml/aiy_plants_v1.tflite", "ml/aiy_plants_v1_labels.txt", IdSource.AIY_PLANTS),
    BIRDS("ml/aiy_birds_v1.tflite", "ml/aiy_birds_v1_labels.txt", IdSource.AIY_BIRDS),
    INSECTS("ml/aiy_insects_v1.tflite", "ml/aiy_insects_v1_labels.txt", IdSource.AIY_INSECTS),
    ;

    companion object {
        /** The quick model for [group], if there is one. */
        fun of(group: LifeGroup): AiyModel? = when (group) {
            LifeGroup.PLANT -> PLANTS
            LifeGroup.BIRD -> BIRDS
            LifeGroup.INSECT -> INSECTS
            else -> null
        }
    }
}

/**
 * The fast identifier: AIY Plants V1, run on overlapping tiles of the photo so each plant in a garden bed gets its own
 * suggestions, and AIY Birds and Insects V1 for the animal in the middle of the frame. The quick models cannot tell a
 * plant from an animal, so the user's choice on the camera says which to ask. A model is not safe to call from two
 * threads at once, so calls take turns, and each is loaded the first time it is needed.
 */
class FastPlantIdentifier(private val context: Context) : Identifier {
    private val classifiers = mutableMapOf<AiyModel, AiyClassifier>()
    private val lock = Mutex()

    private fun classifier(model: AiyModel) = classifiers.getOrPut(model) { AiyClassifier(context, model) }

    override suspend fun identify(photo: Bitmap, group: LifeGroup): Identification {
        if (group == LifeGroup.PLANT) return scan(photo).let { Identification(it.showsPlant, it.regions) }
        val model = AiyModel.of(group) ?: return Identification(false, emptyList())
        val result = withContext(Dispatchers.Default) { lock.withLock { classifier(model).classify(Bitmaps.scaleDown(photo, WORKING_SIDE), CENTRE) } }
        if (result.labels.isEmpty()) return Identification(false, emptyList())
        return Identification(true, listOf(region(photo, CENTRE, mapOf(model.source to result.labels), group)))
    }

    /** The quick model's look at every tile of [photo], which the accurate pass builds on. */
    suspend fun scan(photo: Bitmap): QuickScan = withContext(Dispatchers.Default) {
        lock.withLock {
            // The model looks at 224 × 224 pixels, so a smaller copy loses nothing and is quicker to cut into tiles.
            val image = Bitmaps.scaleDown(photo, WORKING_SIDE)
            val results = RegionPlanner.tiles().map { classifier(AiyModel.PLANTS).classify(image, it) }
            val regions = RegionPlanner.select(results)
            QuickScan(
                tiles = results,
                regions = regions.map { region -> region(photo, region.box, mapOf(IdSource.AIY_PLANTS to region.labels)) },
                showsPlant = RegionPlanner.showsPlant(results, regions),
            )
        }
    }

    override suspend fun glance(frame: Bitmap, group: LifeGroup): TileResult = withContext(Dispatchers.Default) {
        val model = AiyModel.of(group) ?: return@withContext TileResult(CENTRE, emptyList(), backgroundBp = 0)
        lock.withLock { classifier(model).classify(frame, CENTRE) }
    }

    override fun close() = classifiers.values.forEach { it.close() }

    companion object {
        private const val WORKING_SIDE = 1_024
        private const val THUMB_SIDE = 512

        /** The middle of the frame, inside the camera's square guide, where the user points the camera. */
        val CENTRE = BoxBp(1_500, 1_500, 8_500, 8_500)

        /** A region of [photo] with its cut-out and, for a plant, its flowering hint. */
        fun region(photo: Bitmap, box: BoxBp, labels: Map<IdSource, List<LabelScore>>, group: LifeGroup = LifeGroup.PLANT): FoundRegion {
            val crop = Bitmaps.squareCrop(photo, box, THUMB_SIDE)
            val flowering = group == LifeGroup.PLANT && FlowerHint.likely(Bitmaps.colourStats(crop))
            return FoundRegion(box, labels, crop, flowering, group)
        }
    }
}

/**
 * One of the AIY classifiers, read from the app's assets and run on the phone with LiteRT. Each takes 224 × 224 RGB
 * pixels as bytes and gives each class a probability in 256ths; the last class is "none of these". LiteRT sends
 * nothing off the phone.
 */
class AiyClassifier(context: Context, model: AiyModel) {
    private val labels: List<String> = context.assets.open(model.labels).bufferedReader().use { it.readLines() }
    private val classes = labels.size
    private val background = classes - 1
    private val interpreter = Interpreter(mapAsset(context, model.model), Interpreter.Options().setNumThreads(THREADS))
    private val tile = createBitmap(SIDE, SIDE)
    private val canvas = Canvas(tile)
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val target = Rect(0, 0, SIDE, SIDE)
    private val pixels = IntArray(SIDE * SIDE)
    private val input = ByteBuffer.allocateDirect(SIDE * SIDE * 3).order(ByteOrder.nativeOrder())
    private val output = ByteBuffer.allocateDirect(classes).order(ByteOrder.nativeOrder())

    /**
     * Classifies the part of [image] inside [box], stretched to the model's square as the model saw in training. The
     * background class is reported separately. [image] must be a software bitmap.
     */
    fun classify(image: Bitmap, box: BoxBp): TileResult {
        val source = Rect(
            box.left * image.width / BoxBp.FULL,
            box.top * image.height / BoxBp.FULL,
            (box.right * image.width / BoxBp.FULL).coerceAtLeast(box.left * image.width / BoxBp.FULL + 1),
            (box.bottom * image.height / BoxBp.FULL).coerceAtLeast(box.top * image.height / BoxBp.FULL + 1),
        )
        canvas.drawBitmap(image, source, target, paint)
        tile.getPixels(pixels, 0, SIDE, 0, 0, SIDE, SIDE)
        input.rewind()
        for (pixel in pixels) {
            input.put((pixel shr 16).toByte())
            input.put((pixel shr 8).toByte())
            input.put(pixel.toByte())
        }
        input.rewind()
        output.rewind()
        interpreter.run(input, output)
        val scores = IntArray(classes) { (output.get(it).toInt() and 0xFF) * BoxBp.FULL / QUANTA }
        val labelled = (0 until classes)
            .filter { it != background && scores[it] > 0 }
            .sortedByDescending { scores[it] }
            .take(MAX_RESULTS)
            .map { LabelScore(labels.getOrElse(it) { "Class $it" }, scores[it]) }
        return TileResult(box, labelled, scores[background])
    }

    fun close() = interpreter.close()

    companion object {
        private const val SIDE = 224

        /** The model's output is a byte, where 256 would be certainty. */
        private const val QUANTA = 256
        private const val MAX_RESULTS = 10
        private const val THREADS = 4

        /** Maps the model straight from the app's package; the build keeps it uncompressed for this. */
        private fun mapAsset(context: Context, path: String): MappedByteBuffer = context.assets.openFd(path).use { fd ->
            FileInputStream(fd.fileDescriptor).channel.use { it.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength) }
        }
    }
}
