package io.github.wkoller.pollenplot.domain.id

import io.github.wkoller.pollenplot.domain.plants.KnowledgeBase

/**
 * The finds in one photo after their suggestions are fused, with the same plant seen twice made one. The tiles a photo
 * is looked at in overlap and cover the same plant from several sides, so without this a single bush can give a card
 * for each tile it fills.
 *
 * The strongest find comes first. A later find is folded into a kept one when its best suggestion names the same
 * species, wherever it is in the photo, or when it lies mostly inside the part of the photo the kept one covers (the box
 * around every find folded into it) and either names one of its suggestions or is itself unsure. At most [MAX_FOUND]
 * are kept.
 */
object FoundMerge {
    /** The most finds one photo gives. */
    const val MAX_FOUND = 4

    /** A find with at least this share of its box inside a stronger one may be part of that plant. */
    const val INSIDE_SHARE = 0.6

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
            val top = candidates(find).firstOrNull()
            val into = kept.indices.firstOrNull { i -> top != null && samePlant(box(find), top, candidates(kept[i]), covers[i]) }
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

    private fun samePlant(box: BoxBp, top: Candidate, theirs: List<Candidate>, cover: BoxBp): Boolean {
        val topKey = key(top)
        if (theirs.firstOrNull()?.let(::key) == topKey) return true
        if (box.coveredBy(cover) < INSIDE_SHARE) return false
        return theirs.any { key(it) == topKey } || Confidence.of(top.scoreBp) == Confidence.LOW
    }

    private fun BoxBp.around(other: BoxBp) =
        BoxBp(minOf(left, other.left), minOf(top, other.top), maxOf(right, other.right), maxOf(bottom, other.bottom))
}
