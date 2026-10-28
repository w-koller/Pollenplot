package io.github.wkoller.pollenplot.domain.id

import kotlin.math.max
import kotlin.math.min

/** The models that suggest what a photo shows. */
enum class IdSource { AIY_PLANTS, AIY_BIRDS, AIY_INSECTS, BIOCAP, GEMMA }

/** Where a photo came from. */
enum class PhotoOrigin { CAMERA, IMPORT, LIBRARY }

/** Where a detection is in review: waiting, confirmed as a plant in the garden, or not a plant. */
enum class DetectionStatus { NEEDS_REVIEW, CONFIRMED, REJECTED }

/** One label from one model, as the model named it, with its score in basis points. */
data class LabelScore(val label: String, val scoreBp: Int)

/** A box within an image, in basis points of the image's width and height (0 to 10,000). */
data class BoxBp(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    init {
        require(left in 0..right && top in 0..bottom && right <= FULL && bottom <= FULL) { "A box lies within the image" }
    }

    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val area: Long get() = width.toLong() * height

    /** Intersection over union: 1 for the same box, 0 for boxes that do not touch. */
    fun iou(other: BoxBp): Double {
        val w = min(right, other.right) - max(left, other.left)
        val h = min(bottom, other.bottom) - max(top, other.top)
        if (w <= 0 || h <= 0) return 0.0
        val overlap = w.toLong() * h
        return overlap.toDouble() / (area + other.area - overlap)
    }

    companion object {
        const val FULL = 10_000
        val WHOLE = BoxBp(0, 0, FULL, FULL)
    }
}

/** What the fast classifier said about one part of a photo. [backgroundBp] is its score for "no plant here". */
data class TileResult(val box: BoxBp, val labels: List<LabelScore>, val backgroundBp: Int) {
    val top: LabelScore? get() = labels.firstOrNull()
}

/**
 * Finds the plants in a photo by classifying overlapping tiles: the whole photo, a 2 × 2 grid of large tiles and a
 * 3 × 3 grid of smaller ones. A photo of a garden bed often shows several plants, and each tile that is sure of a
 * species becomes a region of its own.
 */
object RegionPlanner {
    /** A tile counts as showing a plant when its best species scores at least this and beats the background. */
    const val MIN_TILE_SCORE_BP = 2_500

    /** With no confident tile, the whole photo still gets suggestions if its best species scores at least this. */
    const val MIN_WHOLE_SCORE_BP = 800

    /** Two regions of the same species that overlap more than this are one plant. */
    private const val SAME_SPECIES_IOU = 0.1

    /** Two regions of different species that overlap more than this are one plant seen twice. */
    private const val ANY_SPECIES_IOU = 0.5

    fun tiles(): List<BoxBp> = buildList {
        add(BoxBp.WHOLE)
        addGrid(2, 6_000)
        addGrid(3, 4_000)
    }

    /** [count] × [count] tiles of [size] basis points, spread evenly so neighbours overlap. */
    private fun MutableList<BoxBp>.addGrid(count: Int, size: Int) {
        val step = (BoxBp.FULL - size) / (count - 1)
        for (row in 0 until count) for (col in 0 until count) {
            add(BoxBp(col * step, row * step, col * step + size, row * step + size))
        }
    }

    /** The regions worth showing: the most confident tiles first, without two regions for the same plant. */
    fun select(results: List<TileResult>, maxRegions: Int = 6): List<TileResult> {
        val confident = results
            .filter { r -> r.top.let { it != null && it.scoreBp >= MIN_TILE_SCORE_BP && it.scoreBp > r.backgroundBp } }
            .sortedByDescending { it.top!!.scoreBp }
        val chosen = mutableListOf<TileResult>()
        for (candidate in confident) {
            if (chosen.size == maxRegions) break
            val clashes = chosen.any { kept ->
                val overlap = kept.box.iou(candidate.box)
                overlap > ANY_SPECIES_IOU || (kept.top!!.label == candidate.top!!.label && overlap > SAME_SPECIES_IOU)
            }
            if (!clashes) chosen += candidate
        }
        if (chosen.isEmpty()) {
            val whole = results.firstOrNull { it.box == BoxBp.WHOLE }
            if (whole?.top != null && whole.top!!.scoreBp >= MIN_WHOLE_SCORE_BP && whole.top!!.scoreBp > whole.backgroundBp) chosen += whole
        }
        return chosen
    }

    /** Whether the photo seems to show a plant at all, judged on the whole photo and the regions found. */
    fun showsPlant(results: List<TileResult>, regions: List<TileResult>): Boolean {
        if (regions.isNotEmpty()) return true
        val whole = results.firstOrNull { it.box == BoxBp.WHOLE } ?: return false
        return whole.backgroundBp < 5_000
    }
}

/** A square crop, in pixels. */
data class PixelRect(val left: Int, val top: Int, val size: Int)

object ThumbnailMath {
    /**
     * The square crop around [box] in an image of [width] × [height] pixels, with [paddingBp] of the box's longer side
     * added around it, kept inside the image.
     */
    fun squareAround(box: BoxBp, width: Int, height: Int, paddingBp: Int = 1_000): PixelRect {
        val left = box.left.toLong() * width / BoxBp.FULL
        val right = box.right.toLong() * width / BoxBp.FULL
        val top = box.top.toLong() * height / BoxBp.FULL
        val bottom = box.bottom.toLong() * height / BoxBp.FULL
        val longer = max(right - left, bottom - top)
        val size = min((longer + 2 * longer * paddingBp / BoxBp.FULL), min(width, height).toLong()).toInt().coerceAtLeast(1)
        val centreX = (left + right) / 2
        val centreY = (top + bottom) / 2
        val x = (centreX - size / 2).coerceIn(0, (width - size).toLong()).toInt()
        val y = (centreY - size / 2).coerceIn(0, (height - size).toLong()).toInt()
        return PixelRect(x, y, size)
    }
}

/**
 * A hint that a photo shows flowers, from its colours: flowers are the vivid, non-green part of most garden photos.
 * It is only a starting point, which the user can correct; the accurate model replaces it later.
 */
object FlowerHint {
    /** Counts from sampled pixels: how many were vivid and outside the greens. */
    data class ColourStats(val sampled: Int, val vividNonGreen: Int)

    /** Vivid non-green pixels make up at least this share of the region, in basis points. */
    const val MIN_SHARE_BP = 400

    fun likely(stats: ColourStats): Boolean =
        stats.sampled > 0 && stats.vividNonGreen.toLong() * BoxBp.FULL / stats.sampled >= MIN_SHARE_BP

    /** Whether one pixel counts as vivid and not green. [hue] is 0 to 360 degrees; [saturation] and [value] 0 to 1. */
    fun isVividNonGreen(hue: Float, saturation: Float, value: Float): Boolean =
        saturation >= 0.45f && value >= 0.35f && (hue < 65f || hue > 175f)
}

/** How sure the app is of a suggestion, in words for the screen. */
enum class Confidence {
    HIGH,
    MEDIUM,
    LOW,
    ;

    companion object {
        fun of(scoreBp: Int): Confidence = when {
            scoreBp >= 6_000 -> HIGH
            scoreBp >= 3_000 -> MEDIUM
            else -> LOW
        }
    }
}

