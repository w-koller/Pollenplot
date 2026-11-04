package io.github.wkoller.pollenplot.domain.weeds

import io.github.wkoller.pollenplot.domain.plants.AuState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeedAlternativesTest {
    private val list = WeedAlternatives.parse(
        """
        {
          "version": 1,
          "sources": [
            {"key": "nsw", "title": "Grow Me Instead: New South Wales", "url": "https://example.org/nsw.pdf", "states": ["NSW", "ACT"]},
            {"key": "qld", "title": "Grow Me Instead: Queensland", "url": "https://example.org/qld.pdf", "states": ["QLD"]}
          ],
          "weeds": [
            {"weed": "Lantana camara L.", "common": "Lantana", "alternatives": [
              {"species": "Westringia fruticosa", "source": "nsw"},
              {"species": "Grevillea robusta", "source": "qld"},
              {"species": "Westringia fruticosa", "source": "qld"}
            ]}
          ]
        }
        """.trimIndent(),
    )

    @Test
    fun `alternatives come from the sources written for the garden's state`() {
        assertEquals(listOf("Westringia fruticosa"), list.forWeed(listOf("Lantana camara"), AuState.NSW).map { it.speciesName })
        assertEquals(listOf("Grevillea robusta", "Westringia fruticosa"), list.forWeed(listOf("Lantana camara"), AuState.QLD).map { it.speciesName })
        assertEquals(emptyList<SuggestedAlternative>(), list.forWeed(listOf("Lantana camara"), AuState.WA))
    }

    @Test
    fun `without a state every source counts, each plant once, found by any of the weed's names`() {
        val all = list.forWeed(listOf("Lantana aculeata", "lantana camara var. camara"), null)
        assertEquals(listOf("Westringia fruticosa", "Grevillea robusta"), all.map { it.speciesName })
        assertEquals("nsw", all.first().source.key)
    }

    @Test
    fun `a sound file has no problems and a broken one says what is wrong`() {
        assertTrue(list.problems().isEmpty())
        val broken = WeedAlternatives(
            1,
            listOf(AlternativeSource("x", "X", "http://example.org", listOf("QLD", "QUEENSLAND"))),
            listOf(WeedWithAlternatives("Lantana camara", null, listOf(Alternative("Westringia fruticosa", "y"))), WeedWithAlternatives("Lantana camara", null, emptyList())),
        )
        val problems = broken.problems()
        assertTrue(problems.toString(), problems.any { "https" in it })
        assertTrue(problems.toString(), problems.any { "QUEENSLAND" in it })
        assertTrue(problems.toString(), problems.any { "unknown source y" in it })
        assertTrue(problems.toString(), problems.any { "listed twice" in it })
        assertTrue(problems.toString(), problems.any { "no alternatives" in it })
    }
}
