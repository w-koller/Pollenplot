package io.github.wkoller.pollenplot.domain.id

/**
 * Keeps the camera's live suggestions steady. Each frame is classified on its own, and sensor noise, focus and
 * exposure move its scores a little every time, so showing each frame's list as it comes makes the names swap and
 * blink while the camera is held still. This remembers the frames before:
 *
 * - each name's score is a running average, in which a new frame counts for [NEW_FRAME_BP];
 * - a name appears once its average reaches [SHOW_BP] and leaves only below [HIDE_BP];
 * - the first name gives way only to one that has led it by [LEAD_BP] for [LEAD_FRAMES] frames running;
 * - the word for how sure a name is changes only once the average is [WORD_MARGIN_BP] past the line between two words;
 * - the list clears after [BACKGROUND_FRAMES] frames running that show no plant or animal, not after one.
 *
 * These numbers are Pollenplot's choice. The scores it gives are averages, for showing; a photo is judged afresh.
 */
class LiveLabels(private val count: Int = 3) {
    private class Seen(var candidate: Candidate, var averageBp: Int, var shown: Boolean = false, var word: Confidence? = null)

    private val seen = linkedMapOf<String, Seen>()
    private var first: String? = null
    private var challenger: String? = null
    private var challengerFrames = 0
    private var backgroundFrames = 0

    /** Forgets every frame, for when the camera is asked about another kind of thing or starts again. */
    fun reset() {
        seen.clear()
        first = null
        challenger = null
        challengerFrames = 0
        backgroundFrames = 0
    }

    /**
     * Takes one frame's suggestions, best first, and whether the frame showed nothing to name ([background]), and
     * gives the suggestions to show.
     */
    fun update(frame: List<Candidate>, background: Boolean = false): List<Candidate> {
        if (background) {
            // One frame of nothing is a hand moving or the focus hunting; two are the camera pointed elsewhere.
            if (++backgroundFrames >= BACKGROUND_FRAMES) reset()
            return shown()
        }
        backgroundFrames = 0
        val now = frame.associateBy(FoundMerge::key)
        for ((key, candidate) in now) seen.getOrPut(key) { Seen(candidate, 0) }.candidate = candidate
        val gone = mutableListOf<String>()
        for ((key, entry) in seen) {
            val score = now[key]?.scoreBp ?: 0
            entry.averageBp += (score - entry.averageBp) * NEW_FRAME_BP / BoxBp.FULL
            entry.shown = if (entry.shown) entry.averageBp >= HIDE_BP else entry.averageBp >= SHOW_BP
            if (!entry.shown) entry.word = null
            if (entry.averageBp < FORGET_BP && key !in now) gone += key
        }
        gone.forEach(seen::remove)
        lead()
        return shown()
    }

    /** Decides which name is first: the one that was, unless it has left or another has clearly led it for a while. */
    private fun lead() {
        val best = seen.entries.filter { it.value.shown }.maxByOrNull { it.value.averageBp }
        val current = first?.let { seen[it] }?.takeIf { it.shown }
        when {
            best == null -> {
                first = null
                challenger = null
            }
            current == null -> {
                first = best.key
                challenger = null
            }
            best.key != first && best.value.averageBp.toLong() * BoxBp.FULL > current.averageBp.toLong() * (BoxBp.FULL + LEAD_BP) -> {
                challengerFrames = if (challenger == best.key) challengerFrames + 1 else 1
                challenger = best.key
                if (challengerFrames >= LEAD_FRAMES) {
                    first = best.key
                    challenger = null
                }
            }
            else -> challenger = null
        }
    }

    private fun shown(): List<Candidate> {
        val leader = first
        return seen.entries.filter { it.value.shown }
            .sortedWith(compareBy({ it.key != leader }, { -it.value.averageBp }))
            .take(count)
            .map { (_, entry) ->
                val word = steadyWord(entry.averageBp, entry.word)
                entry.word = word
                entry.candidate.copy(scoreBp = scoreFor(entry.averageBp, word))
            }
    }

    private companion object {
        /** How much a new frame counts in a name's running average, in basis points of 1. */
        const val NEW_FRAME_BP = 4_000
        const val SHOW_BP = 2_000
        const val HIDE_BP = 1_000

        /** A name no longer in the frames is forgotten below this. */
        const val FORGET_BP = 100

        /** How far ahead of the first name another must be, in basis points of the first name's score. */
        const val LEAD_BP = 1_500
        const val LEAD_FRAMES = 2
        const val WORD_MARGIN_BP = 500
        const val BACKGROUND_FRAMES = 2

        /** The lowest score of each word, as [Confidence.of] reads them. */
        const val HIGH_FROM_BP = 6_000
        const val MEDIUM_FROM_BP = 3_000

        /** The word for [scoreBp], which stays [before] while the score is within the margin of the line it crossed. */
        fun steadyWord(scoreBp: Int, before: Confidence?): Confidence {
            val plain = Confidence.of(scoreBp)
            if (before == null || before == plain) return plain
            val nearby = listOf(Confidence.of(scoreBp - WORD_MARGIN_BP), Confidence.of(scoreBp + WORD_MARGIN_BP - 1))
            return if (before in nearby) before else plain
        }

        /** A score that reads as [word]: [scoreBp] itself, or the nearest score of that word when it was held. */
        fun scoreFor(scoreBp: Int, word: Confidence): Int = when {
            Confidence.of(scoreBp) == word -> scoreBp
            word == Confidence.HIGH -> HIGH_FROM_BP
            word == Confidence.MEDIUM -> if (scoreBp >= HIGH_FROM_BP) HIGH_FROM_BP - 1 else MEDIUM_FROM_BP
            else -> MEDIUM_FROM_BP - 1
        }
    }
}
