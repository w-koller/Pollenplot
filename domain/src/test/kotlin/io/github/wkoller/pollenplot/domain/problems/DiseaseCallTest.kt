package io.github.wkoller.pollenplot.domain.problems

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiseaseCallTest {
    private fun info(key: String, kind: ProblemKind = ProblemKind.DISEASE) =
        ProblemInfo(key, key.replaceFirstChar(Char::uppercase), kind, "d", "a", "Source", "https://example.gov.au/")

    private val catalogue = ProblemCatalogue(listOf(info("powdery-mildew"), info("downy-mildew"), info("botrytis"), info("myrtle-rust"), info("iron-deficiency", ProblemKind.DEFICIENCY)))

    @Test
    fun `a sure healthy answer is no problem found`() {
        val call = DiseaseCall.of(mapOf("healthy" to 8_200, "powdery-mildew" to 1_000, "botrytis" to 800), yesBp = 7_000, maybeBp = 1_500, catalogue)
        assertEquals(DiseaseCall.NoProblemFound(8_200), call)
    }

    @Test
    fun `an unsure healthy answer still offers what the photo may show`() {
        val call = DiseaseCall.of(mapOf("healthy" to 5_000, "powdery-mildew" to 3_000, "botrytis" to 2_000), yesBp = 7_000, maybeBp = 1_500, catalogue)
        call as DiseaseCall.LooksLike
        assertEquals(listOf("powdery-mildew", "botrytis"), call.guesses.map { it.problem.key })
        assertTrue(call.guesses.all { it.confidence == DiseaseConfidence.POSSIBLE })
    }

    @Test
    fun `suggestions are ranked, worded by confidence and at most three`() {
        val shares = mapOf("myrtle-rust" to 7_500, "powdery-mildew" to 600, "downy-mildew" to 600, "botrytis" to 600, "iron-deficiency" to 700)
        val call = DiseaseCall.of(shares, yesBp = 7_000, maybeBp = 500, catalogue) as DiseaseCall.LooksLike
        assertEquals("myrtle-rust", call.guesses.first().problem.key)
        assertEquals(DiseaseConfidence.LIKELY, call.guesses.first().confidence)
        assertEquals(DiseaseCall.SHOWN, call.guesses.size)
        assertEquals("iron-deficiency", call.guesses[1].problem.key)
        assertEquals(DiseaseConfidence.POSSIBLE, call.guesses[1].confidence)
    }

    @Test
    fun `classes below the floor or missing from the catalogue are left out`() {
        assertEquals(DiseaseCall.Unsure, DiseaseCall.of(mapOf("healthy" to 4_000, "powdery-mildew" to 1_000, "black-spot" to 5_000), yesBp = 7_000, maybeBp = 1_500, catalogue))
        assertEquals(DiseaseCall.Unsure, DiseaseCall.of(emptyMap(), yesBp = 7_000, maybeBp = 1_500, catalogue))
    }

    @Test
    fun `a sure answer the catalogue cannot name is unsure, not a weaker guess`() {
        val call = DiseaseCall.of(mapOf("early-blight" to 8_000, "botrytis" to 1_600, "healthy" to 400), yesBp = 7_000, maybeBp = 1_500, catalogue)
        assertEquals(DiseaseCall.Unsure, call)
        val unsure = DiseaseCall.of(mapOf("early-blight" to 5_000, "botrytis" to 3_000, "healthy" to 2_000), yesBp = 7_000, maybeBp = 1_500, catalogue) as DiseaseCall.LooksLike
        assertEquals(listOf("botrytis"), unsure.guesses.map { it.problem.key })
    }

    @Test
    fun `the floor cannot be above the sure level`() {
        assertTrue(runCatching { DiseaseCall.of(mapOf("healthy" to 10_000), yesBp = 5_000, maybeBp = 6_000, catalogue) }.isFailure)
    }
}
