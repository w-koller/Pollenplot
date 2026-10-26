package io.github.wkoller.pollenplot.domain.id

import io.github.wkoller.pollenplot.domain.plants.KnowledgeBase

/**
 * A suggestion for what a region shows. [speciesName] is the knowledge base's name for it when the model's label is a
 * species or synonym the knowledge base knows; otherwise the suggestion is shown by the model's own label.
 */
data class Candidate(
    val label: String,
    val speciesName: String?,
    val scoreBp: Int,
    val sources: Set<IdSource>,
) {
    /** The genus, from the first word of the scientific name. */
    val genus: String get() = (speciesName ?: label).substringBefore(' ')
}

/**
 * Combines the suggestions of each model into one ranked list. Each model's score is weighted, a species already
 * growing in the garden gets a boost because most photos show the same plants again, a species recorded near the
 * garden gets a smaller one, and labels that name the same species (such as a synonym) are merged.
 */
object CandidateFusion {
    /** How much each model counts, in relative units. The accurate model counts most once it is installed. */
    val DEFAULT_WEIGHTS: Map<IdSource, Int> = mapOf(IdSource.BIOCAP to 6, IdSource.AIY_PLANTS to 3, IdSource.GEMMA to 1)

    /** A species already in the garden has its score multiplied by this, in basis points of 1. */
    const val GARDEN_BOOST_BP = 15_000

    /** A species recorded near the garden at least [NEARBY_MIN_RECORDS] times has its score multiplied by this. */
    const val NEARBY_BOOST_BP = 12_000

    /** Fewer records than this near the garden could be a mistake or a plant passing through, so they get no boost. */
    const val NEARBY_MIN_RECORDS = 3

    /**
     * How much a suggestion is boosted for what is known before the photo is looked at, in basis points of 1.
     * [nearby] gives how many times a plant, by its scientific name, has been recorded near the garden.
     */
    fun boostBp(speciesName: String?, label: String, garden: Set<String>, nearby: (String) -> Int): Int {
        var boost = BoxBp.FULL.toLong()
        if (speciesName != null && speciesName in garden) boost = boost * GARDEN_BOOST_BP / BoxBp.FULL
        if (nearby(speciesName ?: label) >= NEARBY_MIN_RECORDS) boost = boost * NEARBY_BOOST_BP / BoxBp.FULL
        return boost.toInt()
    }

    /**
     * A stored suggestion's score as its models gave it, without its boosts, so it can be fused again with another
     * model's suggestions. A boost for records nearby is undone with the records known now, which a monthly update
     * may have changed since; at most that moves a score by the nearby boost.
     */
    fun withoutBoost(candidate: Candidate, garden: Set<String>, nearby: (String) -> Int = { 0 }): Int =
        (candidate.scoreBp.toLong() * BoxBp.FULL / boostBp(candidate.speciesName, candidate.label, garden, nearby)).toInt()

    fun fuse(
        results: Map<IdSource, List<LabelScore>>,
        kb: KnowledgeBase?,
        garden: Set<String>,
        weights: Map<IdSource, Int> = DEFAULT_WEIGHTS,
        limit: Int = 5,
        nearby: (String) -> Int = { 0 },
    ): List<Candidate> {
        val present = results.filterValues { it.isNotEmpty() }
        val totalWeight = present.keys.sumOf { weights[it] ?: 1 }.coerceAtLeast(1)
        data class Entry(var label: String, val speciesName: String?, var weighted: Long, val sources: MutableSet<IdSource>)
        val entries = linkedMapOf<String, Entry>()
        for ((source, labels) in present) {
            val weight = weights[source] ?: 1
            // A model can name one species twice through synonyms; only its best score for that species counts.
            val best = mutableMapOf<String, Pair<String, Int>>()
            for (label in labels) {
                val species = kb?.species(label.label)?.name
                val key = KnowledgeBase.normalise(species ?: label.label)
                if ((best[key]?.second ?: -1) < label.scoreBp) best[key] = label.label to label.scoreBp
            }
            for ((key, value) in best) {
                val species = kb?.species(value.first)?.name
                val entry = entries.getOrPut(key) { Entry(value.first, species, 0, mutableSetOf()) }
                entry.weighted += value.second.toLong() * weight
                entry.sources += source
            }
        }
        return entries.values
            .map { e ->
                val score = e.weighted / totalWeight * boostBp(e.speciesName, e.label, garden, nearby) / BoxBp.FULL
                Candidate(e.label, e.speciesName, score.toInt().coerceAtMost(BoxBp.FULL), e.sources.toSet())
            }
            .sortedByDescending { it.scoreBp }
            .take(limit)
    }
}
