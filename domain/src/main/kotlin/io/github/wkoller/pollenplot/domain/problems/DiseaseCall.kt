package io.github.wkoller.pollenplot.domain.problems

/** How sure the plant problems model is of a suggestion, shown as a word beside it. */
enum class DiseaseConfidence { LIKELY, POSSIBLE }

/** A problem from the catalogue that a photo looks like, with the model's share of belief in basis points. */
data class DiseaseGuess(val problem: ProblemInfo, val shareBp: Int, val confidence: DiseaseConfidence)

/** What the plant problems model makes of a photo. */
sealed interface DiseaseCall {
    /** The model is sure enough that the plant is healthy: "No problem found". */
    data class NoProblemFound(val shareBp: Int) : DiseaseCall

    /** Problems the photo looks like, the likeliest first. The user still chooses one and saves it. */
    data class LooksLike(val guesses: List<DiseaseGuess>) : DiseaseCall

    /** Nothing reaches a level worth showing. */
    data object Unsure : DiseaseCall

    companion object {
        /** The class the pack uses for a plant with no problem. */
        const val HEALTHY = "healthy"

        /** At most this many suggestions are shown as chips. */
        const val SHOWN = 3

        /**
         * The call for one photo from the model's [shares] (class to basis points, summing to about 10,000).
         * [yesBp] and [maybeBp] come from the pack's manifest, chosen by its evaluation on the tuning observers: a
         * class at [yesBp] or above is "likely" (and "healthy" there is "No problem found"), and one at [maybeBp] or
         * above is "possible". Classes the [catalogue] does not hold are never offered: the pack learns some on
         * purpose so their photos are not called something else, and a pack may know a problem the app has since dropped.
         */
        fun of(shares: Map<String, Int>, yesBp: Int, maybeBp: Int, catalogue: ProblemCatalogue): DiseaseCall {
            require(maybeBp in 1..yesBp) { "maybeBp must be between 1 and yesBp" }
            val ranked = shares.entries.sortedByDescending { it.value }
            val top = ranked.firstOrNull() ?: return Unsure
            if (top.key == HEALTHY && top.value >= yesBp) return NoProblemFound(top.value)
            // The pack also learns problems the catalogue does not hold, so their photos are not called something
            // else. When one of those is sure, the photo shows something the app cannot name.
            if (top.key != HEALTHY && top.value >= yesBp && catalogue.problem(top.key) == null) return Unsure
            val guesses = ranked.asSequence()
                .filter { it.key != HEALTHY && it.value >= maybeBp }
                .mapNotNull { (key, bp) ->
                    catalogue.problem(key)?.let { DiseaseGuess(it, bp, if (bp >= yesBp) DiseaseConfidence.LIKELY else DiseaseConfidence.POSSIBLE) }
                }
                .take(SHOWN)
                .toList()
            return if (guesses.isEmpty()) Unsure else LooksLike(guesses)
        }
    }
}
