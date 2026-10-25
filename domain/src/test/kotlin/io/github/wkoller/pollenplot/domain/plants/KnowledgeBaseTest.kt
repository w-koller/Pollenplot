package io.github.wkoller.pollenplot.domain.plants

import io.github.wkoller.pollenplot.domain.season.MonthSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Month

class KnowledgeBaseTest {
    private val families = """[
        { "name": "Proteaceae", "fertiliser": "low_phosphorus", "description": "Proteas and relatives." },
        { "name": "Lamiaceae", "description": "Mints." }
    ]"""
    private val genera = """[
        { "name": "Grevillea", "family": "Proteaceae", "sun": ["full_sun"], "water": "low",
          "wildlife": { "pollinator": 2, "birds": 3 }, "description": "Grevilleas." }
    ]"""

    private fun parse(species: String, companions: String = "[]") = KnowledgeBase.parse(
        KbFiles("""{ "version": 3 }""", mapOf("test.json" to species), genera, families, companions),
    )

    @Test
    fun `a valid file parses without problems and fills defaults`() {
        val result = parse(
            """[{ "name": "Grevillea robusta", "common": ["Silky oak"], "form": "tree", "native": ["QLD", "NSW"],
                  "flowers": ["oct", "nov"], "peak": ["oct"], "height": [15000, 30000], "ph": [5.5, 7.0],
                  "description": "A tall tree." }]""",
        )
        assertEquals(emptyList<String>(), result.problems)
        val s = result.knowledgeBase.species("grevillea  ROBUSTA")!!
        assertEquals(3, result.knowledgeBase.version)
        assertEquals("Grevillea", s.genus)
        assertEquals("Proteaceae", s.family)
        assertEquals(setOf(AuState.QLD, AuState.NSW), s.nativeStates)
        assertEquals(MonthSet.of(Month.OCTOBER, Month.NOVEMBER), s.care.flowering)
        assertEquals(55, s.care.phMinTenths)
        assertEquals(SizeRange(15000, 30000), s.care.height)
    }

    @Test
    fun `mistakes are reported with the species they belong to`() {
        val result = parse(
            """[{ "name": "Grevillea bad", "common": ["Bad"], "description": "Bad.", "water": "soggy",
                  "wildlife": { "birds": 5 }, "flowers": ["octember"], "height": [3000, 1000] },
                { "name": "Mystery plant", "common": ["Mystery"], "description": "No family." }]""",
        )
        val text = result.problems.joinToString("\n")
        assertTrue(text, text.contains("Grevillea bad: water has an unknown value \"soggy\""))
        assertTrue(text, text.contains("wildlife.birds must be 0 to 3"))
        assertTrue(text, text.contains("unknown month \"octember\""))
        assertTrue(text, text.contains("height must be [smallest, largest]"))
        assertTrue(text, text.contains("Mystery plant: no family"))
    }

    @Test
    fun `unknown keys and duplicate names are reported`() {
        val result = parse(
            """[{ "name": "Grevillea one", "common": ["One"], "description": "x", "colour": "red" }]""",
        )
        assertTrue(result.problems.joinToString(), result.problems.any { it.contains("colour") })

        val duplicates = parse(
            """[{ "name": "Grevillea one", "common": ["One"], "description": "x" },
                { "name": "Grevillea two", "common": ["Two"], "description": "x", "synonyms": ["Grevillea One"] }]""",
        )
        assertTrue(duplicates.problems.joinToString(), duplicates.problems.any { it.contains("is used by both") })
    }

    @Test
    fun `companions must name a known species, genus or family`() {
        val result = parse(
            """[{ "name": "Grevillea one", "common": ["One"], "description": "x" }]""",
            """[{ "a": "Grevillea", "b": "Unknownia", "relation": "good", "reason": "x" }]""",
        )
        assertTrue(result.problems.joinToString(), result.problems.any { it.contains("Unknownia is not a species, genus or family") })
    }

    @Test
    fun `search ranks names that start with the query first`() {
        val kb = parse(
            """[{ "name": "Grevillea robusta", "common": ["Silky oak"], "description": "x" },
                { "name": "Grevillea 'Robyn Gordon'", "common": ["Grevillea Robyn Gordon"], "description": "x" },
                { "name": "Salvia rosmarinus", "family": "Lamiaceae", "common": ["Rosemary"], "description": "x" }]""",
        ).knowledgeBase
        assertEquals(listOf("Grevillea 'Robyn Gordon'", "Grevillea robusta"), kb.search("rob").map { it.name })
        assertEquals(listOf("Salvia rosmarinus"), kb.search("rosem").map { it.name })
        assertEquals(2, kb.search("grevillea").size)
        assertEquals(2, kb.search("Proteaceae").size)
        assertTrue(kb.search("   ").isEmpty())
        assertNull(kb.species("Grevillea"))
        assertTrue(kb.isKnownGenus("grevillea"))
        assertFalse(kb.isKnownGenus("Acacia"))
    }

    @Test
    fun `names are normalised for lookups`() {
        assertEquals("citrus × limon", KnowledgeBase.normalise(" Citrus  x limon "))
        assertEquals("grevillea 'robyn gordon'", KnowledgeBase.normalise("Grevillea ‘Robyn Gordon’"))
        assertEquals("× chitalpa", KnowledgeBase.normalise("x Chitalpa"))
    }
}
