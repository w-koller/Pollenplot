package io.github.wkoller.pollenplot.domain.weeds

/** What a weed looks like, as a council's identification tool asks about it. Null means any. */
data class WeedFeatures(
    val type: String? = null,
    val flowerColour: String? = null,
    val leafForm: String? = null,
    val leafArrangement: String? = null,
)

/** The values each feature takes on a council's list, most common first. */
data class WeedFeatureOptions(
    val types: List<String>,
    val flowerColours: List<String>,
    val leafForms: List<String>,
    val leafArrangements: List<String>,
)

/** Finds a weed on a council's list by what it looks like, as the council's own identification tool does. */
object WeedFinder {
    fun options(weeds: List<CouncilWeed>): WeedFeatureOptions {
        fun common(values: List<String>) = values.groupingBy { it }.eachCount().entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).map { it.key }
        return WeedFeatureOptions(
            types = common(weeds.mapNotNull { it.type }),
            flowerColours = common(weeds.flatMap { it.flowerColours }),
            leafForms = common(weeds.flatMap { it.leafForm }),
            leafArrangements = common(weeds.flatMap { it.leafArrangement }),
        )
    }

    /**
     * The weeds with every chosen feature. Weeds the identifier suggested for the photo come first, in the order it
     * suggested them, then the most serious, then by common name.
     */
    fun find(weeds: List<CouncilWeed>, features: WeedFeatures, suggested: List<String> = emptyList()): List<CouncilWeed> {
        val matching = weeds.filter { weed ->
            (features.type == null || weed.type == features.type) &&
                (features.flowerColour == null || features.flowerColour in weed.flowerColours) &&
                (features.leafForm == null || features.leafForm in weed.leafForm) &&
                (features.leafArrangement == null || features.leafArrangement in weed.leafArrangement)
        }
        fun suggestion(weed: CouncilWeed) = suggested.indexOfFirst { weed.names.matches(listOf(it)) }.let { if (it < 0) Int.MAX_VALUE else it }
        return matching.sortedWith(compareBy<CouncilWeed>({ suggestion(it) }, { seriousness(it) }, { it.common.lowercase() }))
    }

    /** Whether the identifier suggested this weed for the photo. */
    fun isSuggested(weed: CouncilWeed, suggested: List<String>): Boolean = suggested.any { weed.names.matches(listOf(it)) }

    private fun seriousness(weed: CouncilWeed): Int = when {
        weed.prohibited -> 0
        weed.restricted.isNotEmpty() -> 1
        weed.councilPest -> 2
        else -> 3
    }
}
