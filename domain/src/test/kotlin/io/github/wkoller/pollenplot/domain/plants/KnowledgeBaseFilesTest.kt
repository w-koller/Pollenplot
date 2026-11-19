package io.github.wkoller.pollenplot.domain.plants

import io.github.wkoller.pollenplot.domain.season.MonthSet
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
        assertTrue("Expected at least 780 species, found ${kb.allSpecies.size}", kb.allSpecies.size >= 780)
        assertTrue(kb.allSpecies.count { it.isAustralianNative } >= 300)
        assertTrue(kb.companions.size >= 20)
    }

    @Test
    fun `every species resolves sun, water and a description through its genus or family`() {
        val kb = parsed.knowledgeBase
        // Records written from research keep only what their sources said, so they may lack sun or water; the
        // test below holds them to their sources instead.
        val researched = researchedRecords().map { it["name"]!!.jsonPrimitive.content }.toSet()
        val missing = kb.allSpecies.filter { it.name !in researched }.filter { s ->
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

    @Test
    fun `every plant the models can name has its genus and family`() {
        val kb = parsed.knowledgeBase
        assertTrue("Expected the models' plants, found ${kb.taxa.size}", kb.taxa.size >= 8_000)
        val wattle = kb.taxon("Acacia longifolia")
        assertEquals("Acacia", wattle?.genus)
        assertEquals("Fabaceae", wattle?.family)
    }

    @Test
    fun `a plant without a record of its own takes its genus's facts`() {
        val kb = parsed.knowledgeBase
        // A species the knowledge base may never describe, but whose genus it does.
        val taxon = kb.taxa.all.first { it.genus == "Acacia" && kb.species(it.name) == null }
        val care = CareResolver.resolveGroup(kb, taxon.genus, taxon.family)
        assertNotNull(care)
        assertEquals(FactLevel.GENUS, care!!.levelOf(CareField.SUN))
    }

    @Test
    fun `every researched record names the pages it came from and describes the plant`() {
        val records = researchedRecords()
        assertTrue("Expected the researched records, found ${records.size}", records.size >= 380)
        val unsourced = records.filter { record ->
            val sources = record["sources"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content }
            sources.isEmpty() || sources.any { !it.startsWith("https://") && !it.startsWith("http://") }
        }
        assertTrue("Records without sources: ${unsourced.map { it["name"] }}", unsourced.isEmpty())
        val kb = parsed.knowledgeBase
        val bare = records.map { it["name"]!!.jsonPrimitive.content }.filter { kb.species(it)?.care?.description.isNullOrBlank() }
        assertTrue("Records without a description: $bare", bare.isEmpty())
    }

    companion object {
        /** The records in the species files written from research, `researched_*.json`, as their JSON. */
        fun researchedRecords(): List<kotlinx.serialization.json.JsonObject> {
            val dir = File(System.getProperty("kb.dir") ?: error("kb.dir is set by domain/build.gradle.kts"), "species")
            return dir.listFiles { f -> f.name.startsWith("researched_") && f.extension == "json" }!!.flatMap { file ->
                kotlinx.serialization.json.Json.parseToJsonElement(file.readText()).jsonArray.map { it.jsonObject }
            }
        }

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
                    taxa = File(dir, "taxa.tsv").readText(),
                ),
            )
        }
    }
}
