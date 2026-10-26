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

/** One region of a photo that seems to show a plant, with each model's suggestions and a cut-out. */
class FoundRegion(val box: BoxBp, val labels: Map<IdSource, List<LabelScore>>, val thumbnail: Bitmap, val flowering: Boolean)

/** What the identifier found in a photo. */
class Identification(val showsPlant: Boolean, val regions: List<FoundRegion>)

/** What the quick model saw in a photo: every tile's scores, and the regions it was sure of. */
class QuickScan(val tiles: List<TileResult>, val regions: List<FoundRegion>, val showsPlant: Boolean)

/** Identifies the plants in photos and camera frames. The app uses the on-device models; tests use a fake. */
interface PlantIdentifier {
    suspend fun identify(photo: Bitmap): Identification

    /** A quick look at the middle of a camera frame, for the live suggestions. */
    suspend fun glance(frame: Bitmap): TileResult

    fun close()
}

/**
 * The fast identifier: AIY Plants V1, run on overlapping tiles of the photo so each plant in a garden bed gets its own
 * suggestions. The model is not safe to call from two threads at once, so calls take turns.
 */
class FastPlantIdentifier(context: Context) : PlantIdentifier {
    private val classifier by lazy { AiyPlantClassifier(context) }
    private val lock = Mutex()

    override suspend fun identify(photo: Bitmap): Identification = scan(photo).let { Identification(it.showsPlant, it.regions) }

    /** The quick model's look at every tile of [photo], which the accurate pass builds on. */
    suspend fun scan(photo: Bitmap): QuickScan = withContext(Dispatchers.Default) {
        lock.withLock {
            // The model looks at 224 × 224 pixels, so a smaller copy loses nothing and is quicker to cut into tiles.
            val image = Bitmaps.scaleDown(photo, WORKING_SIDE)
            val results = RegionPlanner.tiles().map { classifier.classify(image, it) }
            val regions = RegionPlanner.select(results)
            QuickScan(
                tiles = results,
                regions = regions.map { region -> region(photo, region.box, mapOf(IdSource.AIY_PLANTS to region.labels)) },
                showsPlant = RegionPlanner.showsPlant(results, regions),
            )
        }
    }

    override suspend fun glance(frame: Bitmap): TileResult = withContext(Dispatchers.Default) {
        lock.withLock { classifier.classify(frame, CENTRE) }
    }

    override fun close() = classifier.close()

    companion object {
        private const val WORKING_SIDE = 1_024
        private const val THUMB_SIDE = 512

        /** A region of [photo] with its cut-out and flowering hint. */
        fun region(photo: Bitmap, box: BoxBp, labels: Map<IdSource, List<LabelScore>>): FoundRegion {
            val crop = Bitmaps.squareCrop(photo, box, THUMB_SIDE)
            return FoundRegion(box, labels, crop, FlowerHint.likely(Bitmaps.colourStats(crop)))
        }

        /** The middle of the frame, where the user points the camera. */
        private val CENTRE = BoxBp(1_500, 1_500, 8_500, 8_500)
    }
}

/**
 * AIY Plants V1 (2,101 species and a background class), read from the app's assets and run on the phone with LiteRT.
 * The model takes 224 × 224 RGB pixels as bytes and gives each class a probability in 256ths. LiteRT sends nothing
 * off the phone.
 */
class AiyPlantClassifier(context: Context) {
    private val labels: List<String> = context.assets.open(LABELS).bufferedReader().use { it.readLines() }
    private val interpreter = Interpreter(mapAsset(context, MODEL), Interpreter.Options().setNumThreads(THREADS))
    private val tile = createBitmap(SIDE, SIDE)
    private val canvas = Canvas(tile)
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val target = Rect(0, 0, SIDE, SIDE)
    private val pixels = IntArray(SIDE * SIDE)
    private val input = ByteBuffer.allocateDirect(SIDE * SIDE * 3).order(ByteOrder.nativeOrder())
    private val output = ByteBuffer.allocateDirect(CLASSES).order(ByteOrder.nativeOrder())

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
        val scores = IntArray(CLASSES) { (output.get(it).toInt() and 0xFF) * BoxBp.FULL / QUANTA }
        val labelled = (0 until CLASSES)
            .filter { it != BACKGROUND_INDEX && scores[it] > 0 }
            .sortedByDescending { scores[it] }
            .take(MAX_RESULTS)
            .map { LabelScore(labels.getOrElse(it) { "Class $it" }, scores[it]) }
        return TileResult(box, labelled, scores[BACKGROUND_INDEX])
    }

    fun close() = interpreter.close()

    companion object {
        const val MODEL = "ml/aiy_plants_v1.tflite"
        const val LABELS = "ml/aiy_plants_v1_labels.txt"
        const val BACKGROUND_INDEX = 2_101
        private const val CLASSES = 2_102
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
