package io.github.wkoller.pollenplot.domain.plants

import io.github.wkoller.pollenplot.domain.season.MonthSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Month

/** Checks the knowledge base the app ships, so a mistake in the JSON fails the build instead of reaching a phone. */
class KnowledgeBaseFilesTest {
    private val parsed = load()

    @Test
    fun `the shipped knowledge base has no problems`() {
        assertTrue("Problems in the knowledge base:\n" + parsed.problems.joinToString("\n"), parsed.problems.isEmpty())
    }

    @Test
    fun `it covers a useful number of plants`() {
        val kb = parsed.knowledgeBase
        assertTrue("Expected at least 150 species, found ${kb.allSpecies.size}", kb.allSpecies.size >= 150)
        assertTrue(kb.allSpecies.count { it.isAustralianNative } >= 60)
        assertTrue(kb.companions.size >= 20)
    }

    @Test
    fun `every species resolves sun, water and a description through its genus or family`() {
        val kb = parsed.knowledgeBase
        val missing = kb.allSpecies.filter { s ->
            val care = CareResolver.resolve(kb, s).care
            care.sun == null || care.water == null || care.description.isNullOrBlank()
        }
        assertTrue("Species without sun, water or a description: ${missing.map { it.name }}", missing.isEmpty())
    }

    @Test
    fun `lookups work by name, synonym and hybrid sign`() {
        val kb = parsed.knowledgeBase
        assertEquals("Melaleuca viminalis", kb.species("Callistemon viminalis")?.name)
        assertEquals("Citrus × limon", kb.species("citrus x limon")?.name)
        assertNotNull(kb.species("Grevillea ‘Robyn Gordon’"))
        assertEquals(Fertiliser.LOW_PHOSPHORUS, CareResolver.resolve(kb, kb.species("Banksia integrifolia")!!).care.fertiliser)
    }

    @Test
    fun `facts that matter for safety are present`() {
        val kb = parsed.knowledgeBase
        val onion = CareResolver.resolve(kb, kb.species("Allium cepa")!!)
        assertEquals(Toxicity.SEVERE, onion.care.toxicPets)
        assertEquals(FactLevel.GENUS, onion.levelOf(CareField.TOXICITY))
        assertEquals(Toxicity.SEVERE, kb.species("Macadamia integrifolia")!!.care.toxicPets)
        assertTrue(kb.species("Lantana camara")!!.wons)
        val tomato = CareResolver.resolve(kb, kb.species("Solanum lycopersicum")!!).care
        assertTrue("Tomatoes are buzz-pollinated", tomato.forage!!.buzz)
        assertTrue(Month.OCTOBER in (tomato.flowering ?: MonthSet.NONE))
    }

    companion object {
        fun load(): ParseResult {
            val dir = File(System.getProperty("kb.dir") ?: error("kb.dir is set by domain/build.gradle.kts"))
            val species = File(dir, "species").listFiles { f -> f.extension == "json" }!!.sortedBy { it.name }
                .associate { it.name to it.readText() }
            return KnowledgeBase.parse(
                KbFiles(
                    meta = File(dir, "meta.json").readText(),
                    species = species,
                    genera = File(dir, "genera.json").readText(),
                    families = File(dir, "families.json").readText(),
                    companions = File(dir, "companions.json").readText(),
                ),
            )
        }
    }
}
