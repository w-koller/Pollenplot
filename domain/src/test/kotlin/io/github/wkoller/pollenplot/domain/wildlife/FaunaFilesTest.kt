package io.github.wkoller.pollenplot.domain.wildlife

import io.github.wkoller.pollenplot.domain.plants.AuState
import io.github.wkoller.pollenplot.domain.plants.KnowledgeBaseFilesTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Checks the animal knowledge base the app ships, in app/src/main/assets/kb/fauna. */
class FaunaFilesTest {
    private val dir = File(System.getProperty("kb.dir") ?: error("kb.dir is set by domain/build.gradle.kts"), "fauna")
    private val result = FaunaBase.parse(dir.listFiles { f -> f.name.endsWith(".json") }!!.sortedBy { it.name }.associate { it.name to it.readText() })
    private val fauna = result.fauna
    private val plants = KnowledgeBaseFilesTest.load().knowledgeBase

    @Test
    fun `the files parse without problems`() {
        assertEquals(emptyList<String>(), result.problems)
        assertTrue("Expected a useful start, found ${fauna.all.size}", fauna.all.size >= 70)
    }

    @Test
    fun `every wildlife group has animals`() {
        val groups = fauna.all.map { it.group }.toSet()
        assertEquals(LifeGroup.entries.filter { it.isWildlife && it != LifeGroup.OTHER }.toSet(), groups)
    }

    @Test
    fun `plants that animals use are in the plant knowledge base`() {
        fauna.all.forEach { animal ->
            animal.plants.forEach { plant ->
                assertTrue(
                    "${animal.name} uses $plant, which is not a species, genus or family in the plant knowledge base",
                    plants.species(plant) != null || plants.isKnownGenus(plant) || plants.family(plant) != null,
                )
            }
        }
    }

    @Test
    fun `dangerous animals say what to do`() {
        val dangerous = fauna.all.filter { it.hazard == Hazard.VENOMOUS }
        assertTrue(dangerous.size >= 4)
        dangerous.forEach { assertTrue("${it.name} needs first aid or a number to call", "000" in it.hazardNote.orEmpty()) }
        assertNotNull(fauna.animal("Litoria caerulea"))
        assertEquals("Ranoidea caerulea", fauna.animal("litoria  caerulea")?.name)
    }

    @Test
    fun `rules apply only in their states`() {
        val fireAnts = fauna.animal("Solenopsis invicta")!!
        assertTrue(fireAnts.rulesIn(AuState.QLD).single().report)
        assertTrue("13 22 68" in fireAnts.rulesIn(AuState.QLD).single().text)
        assertEquals(emptyList<AnimalRule>(), fireAnts.rulesIn(AuState.VIC))
        assertEquals(emptyList<AnimalRule>(), fireAnts.rulesIn(null))
    }

    @Test
    fun `garden pests have something in the knowledge base that eats them`() {
        val aphid = fauna.animal("Myzus persicae")!!
        assertEquals(setOf("Coccinella transversalis", "Ischiodon scutellaris"), fauna.predatorsOf(aphid).map { it.name }.toSet())
        assertEquals(listOf("Tiliqua scincoides"), fauna.predatorsOf(fauna.animal("Cornu aspersum")!!).map { it.name })
    }

    @Test
    fun `what an animal eats must be in the knowledge base`() {
        val text = """[{"name": "Coccinella transversalis", "common": ["Transverse ladybird"], "group": "insect", "family": "Coccinellidae",
            "native": ["QLD"], "eats": ["Aphis gossypii"], "description": "A ladybird."}]"""
        assertEquals(listOf("Coccinella transversalis eats Aphis gossypii, which is not an animal or genus in the knowledge base"), FaunaBase.parse(mapOf("a.json" to text)).problems)
    }

    @Test
    fun `animals are found by the plants they use`() {
        val citrusUsers = fauna.usersOf("Citrus × limon").map { it.name }
        assertTrue("Papilio aegeus" in citrusUsers && "Musgraveia sulciventris" in citrusUsers)
    }
}
