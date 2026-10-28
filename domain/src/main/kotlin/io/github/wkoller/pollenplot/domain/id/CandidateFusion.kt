package io.github.wkoller.pollenplot.domain.id

import io.github.wkoller.pollenplot.domain.plants.KnowledgeBase

/**
 * A suggestion for what a region shows. [speciesName] is the knowledge base's name for it when the model's label is a
 * species or synonym the knowledge base knows; otherwise the suggestion is shown by its [common] name from a model, or
 * the model's own label.
 */
data class Candidate(
    val label: String,
    val speciesName: String?,
    val scoreBp: Int,
    val sources: Set<IdSource>,
    val common: String? = null,
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
    val DEFAULT_WEIGHTS: Map<IdSource, Int> = mapOf(
        IdSource.BIOCAP to 6,
        IdSource.AIY_PLANTS to 3,
        IdSource.AIY_BIRDS to 3,
        IdSource.AIY_INSECTS to 3,
        IdSource.GEMMA to 1,
    )

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

    /**
     * Whether a detection's suggestions are unsure enough to be worth a second opinion from Gemma 4: there is more
     * than one, and the best is not of high confidence.
     */
    fun needsSecondOpinion(candidates: List<Candidate>): Boolean =
        candidates.size > 1 && Confidence.of(candidates.maxOf { it.scoreBp }) != Confidence.HIGH

    /**
     * The suggestions after a second opinion from Gemma 4. Its choice, by index, counts as one more model's vote at
     * its weight, the way [fuse] weighs each model: the chosen suggestion gets the vote and the others none of it. Null
     * means it chose none of them, which lowers each a little, as a model that saw none of them would.
     */
    fun withSecondOpinion(
        candidates: List<Candidate>,
        chosen: Int?,
        garden: Set<String>,
        nearby: (String) -> Int = { 0 },
        weights: Map<IdSource, Int> = DEFAULT_WEIGHTS,
    ): List<Candidate> {
        val total = candidates.flatMap { it.sources }.toSet().sumOf { weights[it] ?: 1 }.coerceAtLeast(1).toLong()
        val gemma = (weights[IdSource.GEMMA] ?: 1).toLong()
        return candidates.mapIndexed { i, candidate ->
            val vote = if (i == chosen) BoxBp.FULL.toLong() else 0L
            val unboosted = (withoutBoost(candidate, garden, nearby) * total + vote * gemma) / (total + gemma)
            val score = unboosted * boostBp(candidate.speciesName, candidate.label, garden, nearby) / BoxBp.FULL
            candidate.copy(
                scoreBp = score.toInt().coerceAtMost(BoxBp.FULL),
                sources = if (i == chosen) candidate.sources + IdSource.GEMMA else candidate.sources,
            )
        }.sortedByDescending { it.scoreBp }
    }

    fun fuse(
        results: Map<IdSource, List<LabelScore>>,
        kb: KnowledgeBase?,
        garden: Set<String>,
        weights: Map<IdSource, Int> = DEFAULT_WEIGHTS,
        limit: Int = 5,
        nearby: (String) -> Int = { 0 },
    ): List<Candidate> = fuse(results, { label -> kb?.species(label)?.name }, garden, weights, limit, nearby)

    /**
     * As [fuse] above, with [nameOf] giving the knowledge base's name for a model's label, so animals can be matched
     * to the animal knowledge base the same way plants are matched to the plant one.
     */
    fun fuse(
        results: Map<IdSource, List<LabelScore>>,
        nameOf: (String) -> String?,
        garden: Set<String>,
        weights: Map<IdSource, Int> = DEFAULT_WEIGHTS,
        limit: Int = 5,
        nearby: (String) -> Int = { 0 },
    ): List<Candidate> {
        val present = results.filterValues { it.isNotEmpty() }
        val totalWeight = present.keys.sumOf { weights[it] ?: 1 }.coerceAtLeast(1)
        data class Entry(var label: String, val speciesName: String?, var weighted: Long, val sources: MutableSet<IdSource>, var common: String?)
        val entries = linkedMapOf<String, Entry>()
        for ((source, labels) in present) {
            val weight = weights[source] ?: 1
            // A model can name one species twice through synonyms; only its best score for that species counts.
            val best = mutableMapOf<String, LabelScore>()
            for (label in labels) {
                val species = nameOf(label.label)
                val key = KnowledgeBase.normalise(species ?: label.label)
                if ((best[key]?.scoreBp ?: -1) < label.scoreBp) best[key] = label
            }
            for ((key, value) in best) {
                val species = nameOf(value.label)
                val entry = entries.getOrPut(key) { Entry(value.label, species, 0, mutableSetOf(), value.common) }
                entry.weighted += value.scoreBp.toLong() * weight
                entry.sources += source
                if (entry.common == null) entry.common = value.common
            }
        }
        return entries.values
            .map { e ->
                val score = e.weighted / totalWeight * boostBp(e.speciesName, e.label, garden, nearby) / BoxBp.FULL
                Candidate(e.label, e.speciesName, score.toInt().coerceAtMost(BoxBp.FULL), e.sources.toSet(), e.common)
            }
            .sortedByDescending { it.scoreBp }
            .take(limit)
    }
}
