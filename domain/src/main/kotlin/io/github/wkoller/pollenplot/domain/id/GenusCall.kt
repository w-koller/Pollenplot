package io.github.wkoller.pollenplot.domain.id

/** The genus a find very likely belongs to, when the models are less sure of the species: its name and its share. */
data class GenusLead(val genus: String, val shareBp: Int)

/**
 * Says which genus a find is when the species is unsure. The models often cannot tell one grevillea from another but
 * can tell it is a grevillea: their belief is spread over several species of one genus, so none of them reads as
 * likely, though together they are.
 *
 * A genus is given when the best suggestion is not of high confidence and the suggestions of one genus together hold
 * at least [MIN_SHARE_BP]. On the Australian photos of the fusion evaluation, that held for about one photo in six, and in
 * those the genus was right about four times in five and the first species about one time in three
 * (`FusionEvalTest`). A find the
 * user has corrected before is left to their correction.
 */
object GenusCall {
    /** The share of the models' belief that one genus must hold. */
    const val MIN_SHARE_BP = 6_000

    fun of(candidates: List<Candidate>): GenusLead? {
        val top = candidates.firstOrNull() ?: return null
        if (Confidence.of(top.scoreBp) == Confidence.HIGH || candidates.any { IdSource.USER in it.sources }) return null
        val (_, same) = candidates.groupBy { it.genus.lowercase() }.maxBy { (_, list) -> list.sumOf { it.scoreBp } }
        val share = same.sumOf { it.scoreBp }
        // One species alone below high confidence cannot hold the share, so this is always several species.
        if (share < MIN_SHARE_BP || same.size < 2) return null
        return GenusLead(same.first().genus, share.coerceAtMost(BoxBp.FULL))
    }
}
