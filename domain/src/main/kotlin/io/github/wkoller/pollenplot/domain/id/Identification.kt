package io.github.wkoller.pollenplot.domain.id

import io.github.wkoller.pollenplot.domain.plants.KnowledgeBase
import io.github.wkoller.pollenplot.domain.season.MonthSet
import java.time.Month
import kotlin.math.max
import kotlin.math.min

/** The models that suggest what a photo shows. */
enum class IdSource { AIY_PLANTS, AIY_BIRDS, AIY_INSECTS, BIOCAP, GEMMA }

/** Where a photo came from. */
enum class PhotoOrigin { CAMERA, IMPORT, LIBRARY }

/**
 * How much of a photo is looked at for plants. A photo taken of one plant gives one card for the plant in the middle; a
 * photo of a garden bed, or one from the gallery, is looked at for each plant in it.
 */
enum class PhotoScope { ONE, SEVERAL }

/** Where a detection is in review: waiting, confirmed as a plant in the garden, or not a plant. */
enum class DetectionStatus { NEEDS_REVIEW, CONFIRMED, REJECTED }

/**
 * One label from one model, as the model named it, with its score in basis points, and a [common] name when the model
 * has one, as the accurate pack's species index does.
 */
data class LabelScore(val label: String, val scoreBp: Int, val common: String? = null)

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

    /** How much of this box lies inside [other]: 1 when it is wholly inside, 0 when they do not touch. */
    fun coveredBy(other: BoxBp): Double {
        val w = min(right, other.right) - max(left, other.left)
        val h = min(bottom, other.bottom) - max(top, other.top)
        if (w <= 0 || h <= 0 || area == 0L) return 0.0
        return w.toLong() * h / area.toDouble()
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

    /** The most regions a photo gives. More than this were nearly always one plant seen again. */
    const val MAX_REGIONS = 4

    /**
     * The regions worth showing: the most confident tiles first, without two regions for the same plant. Labels are
     * compared by [key], so two spellings of one species count as the same.
     */
    fun select(results: List<TileResult>, maxRegions: Int = MAX_REGIONS, key: (String) -> String = KnowledgeBase::normalise): List<TileResult> {
        val confident = results
            .filter { r -> r.top.let { it != null && it.scoreBp >= MIN_TILE_SCORE_BP && it.scoreBp > r.backgroundBp } }
            .sortedByDescending { it.top!!.scoreBp }
        val chosen = mutableListOf<TileResult>()
        for (candidate in confident) {
            if (chosen.size == maxRegions) break
            val clashes = chosen.any { kept ->
                val overlap = kept.box.iou(candidate.box)
                overlap > ANY_SPECIES_IOU || (key(kept.top!!.label) == key(candidate.top!!.label) && overlap > SAME_SPECIES_IOU)
            }
            if (!clashes) chosen += candidate
        }
        if (chosen.isEmpty()) {
            val whole = results.firstOrNull { it.box == BoxBp.WHOLE }
            if (whole?.top != null && whole.top!!.scoreBp >= MIN_WHOLE_SCORE_BP && whole.top!!.scoreBp > whole.backgroundBp) chosen += whole
        }
        return chosen
    }

    /**
     * The one plant in a photo taken of a single plant: the middle of the frame and the whole photo as one region on
     * [centre]'s box, each label at its better score of the two. It is null when neither is sure enough of a plant to
     * suggest one, by the same measure as the whole photo with no confident tile.
     */
    fun one(centre: TileResult, whole: TileResult, key: (String) -> String = KnowledgeBase::normalise): TileResult? {
        val labels = (centre.labels + whole.labels)
            .groupBy { key(it.label) }
            .map { (_, same) -> same.maxBy { it.scoreBp } }
            .sortedByDescending { it.scoreBp }
        val background = min(centre.backgroundBp, whole.backgroundBp)
        val top = labels.firstOrNull() ?: return null
        if (top.scoreBp < MIN_WHOLE_SCORE_BP || top.scoreBp <= background) return null
        return TileResult(centre.box, labels, background)
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
 * Sky, mulch, soil and terracotta are vivid too, so blue in the top of a cut-out and dull browns and oranges do not
 * count, and only the middle of the cut-out, where the plant is, is looked at. It is the last of the signs
 * [FlowerCall] weighs, after BioCAP and the plant's flowering months.
 */
object FlowerHint {
    /** Counts from sampled pixels: how many were vivid and outside the greens. */
    data class ColourStats(val sampled: Int, val vividNonGreen: Int)

    /** Vivid non-green pixels make up at least this share of the region, in basis points. */
    const val MIN_SHARE_BP = 800

    /** Only this middle share of the cut-out, across and down, is sampled, in basis points. */
    const val MIDDLE_BP = 7_000

    fun likely(stats: ColourStats): Boolean =
        stats.sampled > 0 && stats.vividNonGreen.toLong() * BoxBp.FULL / stats.sampled >= MIN_SHARE_BP

    /** Whether a point [x] across and [y] down the cut-out, in basis points, is in the middle that is sampled. */
    fun sampled(x: Int, y: Int): Boolean {
        val margin = (BoxBp.FULL - MIDDLE_BP) / 2
        return x in margin..BoxBp.FULL - margin && y in margin..BoxBp.FULL - margin
    }

    /**
     * Whether one pixel looks like a flower. [hue] is 0 to 360 degrees; [saturation] and [value] 0 to 1; [y] is how
     * far down the cut-out it is, in basis points, because sky is at the top.
     */
    fun isFlowerColour(hue: Float, saturation: Float, value: Float, y: Int = BoxBp.FULL / 2): Boolean {
        if (saturation < 0.45f || value < 0.35f) return false
        if (hue in 65f..175f) return false
        // Mulch, soil, bark and terracotta pots: browns and oranges that are not bright.
        if (hue in 10f..50f && value < 0.6f) return false
        // Sky: blue in the top third.
        if (hue in 185f..250f && y < BoxBp.FULL / 3) return false
        return true
    }
}

/** Why the review starts with the plant marked as in flower or not. */
enum class FlowerReason {
    /** BioCAP compared the cut-out with flowering and leafy plants. */
    MODEL,

    /** The plant does not usually flower in the month the photo was taken. */
    OUT_OF_SEASON,

    /** The cut-out has flower colours in its middle. */
    COLOUR,

    /** Nothing in the photo suggests flowers. */
    NO_SIGN,
}

/**
 * Whether a plant in a photo is in flower, and why, from the strongest sign there is: BioCAP's answer when it is sure,
 * then the plant's flowering months, then the colours of the cut-out. With no sign it is not in flower, so the review
 * never says a plant flowered that nobody saw flowering.
 */
data class FlowerCall(val flowering: Boolean, val reason: FlowerReason) {
    companion object {
        /**
         * [model] is BioCAP's answer, or null when it gave none. [months] are the plant's flowering months in the
         * Southern Hemisphere, or null when they are not recorded. [month] is when the photo was taken. [colour] is
         * the colour hint.
         */
        fun decide(model: Boolean?, months: MonthSet?, month: Month, colour: Boolean): FlowerCall = when {
            model == true -> FlowerCall(true, FlowerReason.MODEL)
            months != null && !months.isEmpty && month !in months -> FlowerCall(false, FlowerReason.OUT_OF_SEASON)
            model == false -> FlowerCall(false, FlowerReason.MODEL)
            colour -> FlowerCall(true, FlowerReason.COLOUR)
            else -> FlowerCall(false, FlowerReason.NO_SIGN)
        }

        /**
         * BioCAP's answer from its belief that a cut-out shows flowers, in basis points: yes at or above [yesBp], no
         * at or below [noBp], and none between, where it was too often wrong when the pack was evaluated.
         */
        fun model(flowerBp: Int, yesBp: Int, noBp: Int): Boolean? = when {
            flowerBp >= yesBp -> true
            flowerBp <= noBp -> false
            else -> null
        }
    }
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

