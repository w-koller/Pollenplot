package io.github.wkoller.pollenplot.domain.id

import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * The accurate pass: BioCAP looks again at the parts of a photo the quick model was unsure about, which is most
 * Australian natives, as AIY Plants never learned them. Its similarities become shares that add up to one, the way
 * CLIP models turn them into probabilities, so the fusion can weigh them against the quick model's scores.
 */
object AccurateRegions {
    /** Tiles the quick model calls "no plant here" at least this strongly are not looked at again. */
    const val MAX_BACKGROUND_BP = 3_000

    /** A tile becomes a region of its own when BioCAP gives its best species at least this share. */
    const val MIN_SHARE_BP = 3_500

    /** Two new regions of the same species that overlap more than this are one plant. */
    private const val SAME_SPECIES_IOU = 0.1

    /** A new region that overlaps an existing one more than this is that plant again. */
    private const val ANY_IOU = 0.5

    /** [similarities] (cosine, best first) as shares of BioCAP's belief, in basis points, best first. */
    fun shares(similarities: List<Pair<String, Float>>, logitScale: Float): List<LabelScore> {
        if (similarities.isEmpty()) return emptyList()
        val top = similarities.maxOf { it.second }
        // Subtracting the best keeps the exponentials in range; the shares are the same.
        val weights = similarities.map { (_, s) -> exp((logitScale * (s - top)).toDouble()) }
        val total = weights.sum()
        return similarities.mapIndexed { i, (label, _) -> LabelScore(label, (weights[i] / total * BoxBp.FULL).roundToInt()) }
            .sortedByDescending { it.scoreBp }
    }

    /** The tiles worth BioCAP's look: those the quick model did not call "no plant" and no region covers already. */
    fun tilesToCheck(tiles: List<TileResult>, regions: List<BoxBp>): List<BoxBp> = tiles
        .filter { it.backgroundBp < MAX_BACKGROUND_BP }
        .map { it.box }
        .filter { box -> regions.none { it.iou(box) > ANY_IOU } }

    /**
     * The new regions from BioCAP's look at [checked] tiles: those confident enough, the most confident first, without
     * two for the same plant and without repeating one of the [existing] regions, given with BioCAP's best species.
     */
    fun select(checked: Map<BoxBp, List<LabelScore>>, existing: List<Pair<BoxBp, String>>, max: Int): List<Pair<BoxBp, List<LabelScore>>> {
        val confident = checked.entries
            .filter { (_, labels) -> (labels.firstOrNull()?.scoreBp ?: 0) >= MIN_SHARE_BP }
            .sortedByDescending { it.value.first().scoreBp }
        val kept = existing.toMutableList()
        val chosen = mutableListOf<Pair<BoxBp, List<LabelScore>>>()
        for ((box, labels) in confident) {
            if (chosen.size == max) break
            val species = labels.first().label
            val clashes = kept.any { (other, otherSpecies) ->
                val overlap = other.iou(box)
                overlap > ANY_IOU || (otherSpecies == species && overlap > SAME_SPECIES_IOU)
            }
            if (!clashes) {
                chosen += box to labels
                kept += box to species
            }
        }
        return chosen
    }
}
