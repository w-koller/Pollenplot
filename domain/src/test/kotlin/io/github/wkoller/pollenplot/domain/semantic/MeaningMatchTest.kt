package io.github.wkoller.pollenplot.domain.semantic

import org.junit.Assert.assertEquals
import org.junit.Test

class MeaningMatchTest {
    @Test
    fun `only strong matches are offered, best first, and never twice`() {
        val scores = mapOf("lantana" to 0.68f, "lemon" to 0.49f, "rosemary" to 0.63f, "grevillea" to 0.65f, "tomato" to 0.64f)
        assertEquals(listOf("lantana", "grevillea", "tomato"), MeaningMatch.pick(scores, found = emptySet()))
        assertEquals("Records the words found are not offered again", listOf("grevillea", "tomato", "rosemary"), MeaningMatch.pick(scores, found = setOf("lantana")))
    }

    @Test
    fun `a query that means nothing in the garden offers nothing`() {
        // Scores measured for "xyzzy plugh" against the demo yard.
        assertEquals(emptyList<String>(), MeaningMatch.pick(mapOf("lomandra" to 0.536f, "lemon" to 0.511f), found = emptySet()))
    }

    @Test
    fun `similarity of unit vectors is their cosine`() {
        assertEquals(1f, MeaningMatch.similarity(floatArrayOf(0.6f, 0.8f), floatArrayOf(0.6f, 0.8f)), 1e-6f)
        assertEquals(0f, MeaningMatch.similarity(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f)), 1e-6f)
    }
}
