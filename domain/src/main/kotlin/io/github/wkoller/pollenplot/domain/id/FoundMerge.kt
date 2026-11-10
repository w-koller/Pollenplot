package io.github.wkoller.pollenplot.domain.id

import io.github.wkoller.pollenplot.domain.plants.KnowledgeBase

/**
 * The finds in one photo after their suggestions are fused, with the same plant seen twice made one. The tiles a photo
 * is looked at in overlap and cover the same plant from several sides, so without this a single bush can give a card
 * for each tile it fills.
 *
 * The strongest find comes first. A later find is folded into a kept one when its best suggestion names the same
 * species, wherever it is in the photo. It is also folded when the two lie mostly over each other (either mostly inside
 * the part of the photo the kept one covers, which is the box around every find folded into it, or mostly around it,
 * as the whole photo is around a tile) and the models as good as agree: one of its leading suggestions is among the
 * kept one's, their best suggestions are of one genus, or it is itself unsure. Two finds the models are sure are
 * different plants stay two, however they lie. At most [MAX_FOUND] are kept.
 */
object FoundMerge {
    /** The most finds one photo gives. */
    const val MAX_FOUND = 4

    /** A find with at least this share of its box inside a stronger one may be part of that plant. */
    const val INSIDE_SHARE = 0.6

    /** How many of a find's suggestions, from the best, count when two finds are compared. */
    const val LEADING = 3

    /** The key a suggestion is compared by: the knowledge base's name, or else the model's label. */
    fun key(candidate: Candidate): String = KnowledgeBase.normalise(candidate.speciesName ?: candidate.label)

    /**
     * [found] with each plant once, the strongest first. [box] and [candidates] read a find; [fold] gives the kept find
     * after another was folded into it, so the caller can keep what both saw, such as flowers.
     */
    fun <T> merge(
        found: List<T>,
        box: (T) -> BoxBp,
        candidates: (T) -> List<Candidate>,
        fold: (kept: T, other: T) -> T = { kept, _ -> kept },
        max: Int = MAX_FOUND,
    ): List<T> {
        val ordered = found.sortedByDescending { candidates(it).firstOrNull()?.scoreBp ?: -1 }
        val kept = mutableListOf<T>()
        // The part of the photo each kept find covers, which grows as finds are folded into it.
        val covers = mutableListOf<BoxBp>()
        for (find in ordered) {
            val mine = candidates(find)
            val into = kept.indices.firstOrNull { i -> mine.isNotEmpty() && samePlant(box(find), mine, candidates(kept[i]), covers[i]) }
            when {
                into != null -> {
                    kept[into] = fold(kept[into], find)
                    covers[into] = covers[into].around(box(find))
                }
                kept.size < max -> {
                    kept += find
                    covers += box(find)
                }
            }
        }
        return kept
    }

    private fun samePlant(box: BoxBp, mine: List<Candidate>, theirs: List<Candidate>, cover: BoxBp): Boolean {
        val top = mine.first()
        val theirTop = theirs.firstOrNull()
        if (theirTop?.let(::key) == key(top)) return true
        // A weak look at the whole photo lies around a strong tile, not inside it, and is the same plant as often.
        if (maxOf(box.coveredBy(cover), cover.coveredBy(box)) < INSIDE_SHARE) return false
        val leading = theirs.take(LEADING).map(::key).toSet()
        return mine.take(LEADING).any { key(it) in leading } ||
            (theirTop != null && top.genus.equals(theirTop.genus, ignoreCase = true)) ||
            Confidence.of(top.scoreBp) == Confidence.LOW
    }

    private fun BoxBp.around(other: BoxBp) =
        BoxBp(minOf(left, other.left), minOf(top, other.top), maxOf(right, other.right), maxOf(bottom, other.bottom))
}
