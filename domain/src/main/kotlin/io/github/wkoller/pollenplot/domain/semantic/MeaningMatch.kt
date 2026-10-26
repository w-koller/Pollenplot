package io.github.wkoller.pollenplot.domain.semantic

/**
 * Which records to offer as related by meaning. The small on-phone text model scores unrelated text around 0.5 and
 * strong matches above 0.62, so only matches that clear [MIN_SCORE] are offered, and only a few, after the records
 * the words themselves found.
 */
object MeaningMatch {
    /** Measured on garden records: nonsense queries score at most about 0.54, synonyms such as "poisonous" for "toxic" 0.65. */
    const val MIN_SCORE = 0.62f
    const val MAX_RESULTS = 3

    /** The IDs of the best [scores] above [MIN_SCORE], leaving out records already in [found]. */
    fun pick(scores: Map<String, Float>, found: Set<String>, max: Int = MAX_RESULTS): List<String> = scores.entries
        .filter { it.key !in found && it.value >= MIN_SCORE }
        .sortedByDescending { it.value }
        .take(max)
        .map { it.key }

    /** The dot product, which is the cosine similarity for vectors of unit length. */
    fun similarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Vectors of different lengths cannot be compared" }
        var sum = 0f
        for (i in a.indices) sum += a[i] * b[i]
        return sum
    }
}
