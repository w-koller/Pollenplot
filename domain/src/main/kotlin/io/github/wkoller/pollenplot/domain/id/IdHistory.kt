package io.github.wkoller.pollenplot.domain.id

import io.github.wkoller.pollenplot.domain.plants.KnowledgeBase

/**
 * One find the user has decided: the name the models put first ([top]), and the name the user gave it ([chosen]), or
 * null when the user said it was not a plant or not wildlife. [chosenSpecies] is the knowledge base's name for the
 * choice, when it has one.
 */
data class DecidedFind(val top: String, val chosen: String?, val chosenSpecies: String? = null)

/**
 * What the user's own decisions say about the models' suggestions, so a mistake corrected once is not made again.
 *
 * The models make the same mistake for the same plant: a grevillea they call a hakea today is called a hakea
 * tomorrow. So when the name the models put first has been corrected to another name more often than it was accepted,
 * that other name is put first, marked [IdSource.USER]. A name that was put first and turned down as not a plant at
 * least [REJECTED_MIN] times, and never accepted or corrected, loses half its score. These numbers are Pollenplot's
 * choice.
 */
class IdHistory(decided: List<DecidedFind>) {
    private class Tally(var accepted: Int = 0, var rejected: Int = 0, val corrected: MutableMap<String, Pair<DecidedFind, Int>> = linkedMapOf())

    private val tallies: Map<String, Tally> = buildMap<String, Tally> {
        for (find in decided) {
            val tally = getOrPut(KnowledgeBase.normalise(find.top)) { Tally() }
            val chosen = find.chosen?.trim()?.ifBlank { null }
            when {
                chosen == null -> tally.rejected++
                key(find) == KnowledgeBase.normalise(find.top) -> tally.accepted++
                else -> tally.corrected.merge(key(find), find to 1) { old, _ -> old.first to old.second + 1 }
            }
        }
    }

    /** Whether there is anything to apply. */
    val isEmpty: Boolean get() = tallies.isEmpty()

    /**
     * [candidates], best first, as the user's decisions change them. [nameOf] gives the knowledge base's name for a
     * name the user chose, so a correction reads as the plant or animal the knowledge base knows.
     */
    fun apply(candidates: List<Candidate>, nameOf: (String) -> String? = { null }): List<Candidate> {
        if (candidates.isEmpty() || tallies.isEmpty()) return candidates
        val halved = candidates.map { candidate ->
            val tally = tallies[FoundMerge.key(candidate)]
            if (tally != null && tally.rejected >= REJECTED_MIN && tally.accepted == 0 && tally.corrected.isEmpty()) {
                candidate.copy(scoreBp = candidate.scoreBp / 2)
            } else {
                candidate
            }
        }.sortedByDescending { it.scoreBp }
        val top = halved.first()
        val tally = tallies[FoundMerge.key(top)] ?: return halved
        val (correctedKey, correction) = tally.corrected.entries.maxByOrNull { it.value.second } ?: return halved
        if (correction.second <= tally.accepted) return halved
        val find = correction.first
        val known = halved.firstOrNull { FoundMerge.key(it) == correctedKey }
        val first = known?.copy(scoreBp = top.scoreBp, sources = known.sources + IdSource.USER)
            ?: Candidate(find.chosen!!.trim(), find.chosenSpecies ?: nameOf(find.chosen), top.scoreBp, setOf(IdSource.USER))
        return (listOf(first) + halved.filterNot { FoundMerge.key(it) == correctedKey }).take(maxOf(candidates.size, MIN_KEPT))
    }

    private fun key(find: DecidedFind) = KnowledgeBase.normalise(find.chosenSpecies ?: find.chosen.orEmpty())

    companion object {
        /** A name turned down this many times, and never accepted, loses half its score. */
        const val REJECTED_MIN = 2

        /** A correction is added to a short list rather than replacing its last suggestion. */
        private const val MIN_KEPT = 3

        val NONE = IdHistory(emptyList())
    }
}
