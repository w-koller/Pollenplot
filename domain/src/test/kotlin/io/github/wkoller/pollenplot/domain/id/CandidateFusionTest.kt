package io.github.wkoller.pollenplot.domain.id

import io.github.wkoller.pollenplot.domain.plants.KnowledgeBaseFilesTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CandidateFusionTest {
    private val kb = KnowledgeBaseFilesTest.load().knowledgeBase

    @Test
    fun `labels are matched to the knowledge base, including through synonyms`() {
        val fused = CandidateFusion.fuse(
            mapOf(IdSource.AIY_PLANTS to listOf(LabelScore("Callistemon citrinus", 6_000), LabelScore("Betula lenta", 1_000))),
            kb,
            garden = emptySet(),
        )
        assertEquals("Melaleuca citrina", fused[0].speciesName)
        assertEquals(6_000, fused[0].scoreBp)
        assertEquals("Melaleuca", fused[0].genus)
        assertNull("A species the knowledge base does not know keeps the model's label", fused[1].speciesName)
        assertEquals("Betula lenta", fused[1].label)
    }

    @Test
    fun `a model's common name is kept for a label the knowledge base does not have`() {
        val fused = CandidateFusion.fuse(
            mapOf(
                IdSource.AIY_INSECTS to listOf(LabelScore("Mopsus mormon", 2_000)),
                IdSource.BIOCAP to listOf(LabelScore("Mopsus mormon", 7_000, common = "Green jumping spider")),
            ),
            nameOf = { null },
            garden = emptySet(),
        )
        assertEquals("Green jumping spider", fused.single().common)
        assertEquals("Mopsus mormon", fused.single().label)
    }

    @Test
    fun `a species already in the garden is boosted`() {
        val results = mapOf(IdSource.AIY_PLANTS to listOf(LabelScore("Lantana camara", 4_000), LabelScore("Salvia rosmarinus", 3_000)))
        assertEquals("Lantana camara", CandidateFusion.fuse(results, kb, emptySet())[0].speciesName)
        val boosted = CandidateFusion.fuse(results, kb, setOf("Salvia rosmarinus"))
        assertEquals("Salvia rosmarinus", boosted[0].speciesName)
        assertEquals(4_500, boosted[0].scoreBp)
    }

    @Test
    fun `models are weighted and their agreement is kept`() {
        val fused = CandidateFusion.fuse(
            mapOf(
                IdSource.AIY_PLANTS to listOf(LabelScore("Rosmarinus officinalis", 9_000)),
                IdSource.BIOCAP to listOf(LabelScore("Salvia rosmarinus", 6_000), LabelScore("Lavandula dentata", 3_000)),
            ),
            kb,
            emptySet(),
        )
        // (9000 × 3 + 6000 × 6) / 9 = 7000 for rosemary, named both ways; lavender has only BioCAP's 3000 × 6 / 9.
        assertEquals("Salvia rosmarinus", fused[0].speciesName)
        assertEquals(7_000, fused[0].scoreBp)
        assertEquals(setOf(IdSource.AIY_PLANTS, IdSource.BIOCAP), fused[0].sources)
        assertEquals(2_000, fused[1].scoreBp)
        assertTrue(CandidateFusion.fuse(emptyMap(), kb, emptySet()).isEmpty())
    }

    @Test
    fun `the fast classifier's labels line up with the knowledge base`() {
        val labels = File(System.getProperty("ml.dir") ?: error("ml.dir is set by domain/build.gradle.kts"), "aiy_plants_v1_labels.txt").readLines()
        assertEquals(2_102, labels.size)
        assertEquals("The background class is last", "None", labels.last())
        val named = labels.mapNotNull { kb.species(it)?.name }.toSet()
        assertTrue("Expected at least 40 knowledge base species among the labels, found ${named.size}", named.size >= 40)
        assertTrue("Melaleuca citrina" in named && "Salvia rosmarinus" in named)
    }

    @Test
    fun `a species recorded near the garden is boosted less than one growing in it`() {
        val results = mapOf(IdSource.BIOCAP to listOf(LabelScore("Grevillea banksii", 4_000), LabelScore("Grevillea robusta", 3_500)))
        val nearby = { name: String -> if (name == "Grevillea robusta") 12 else 1 }
        val fused = CandidateFusion.fuse(results, kb, emptySet(), nearby = nearby)
        assertEquals("Recorded nearby, robusta overtakes banksii", "Grevillea robusta", fused[0].speciesName ?: fused[0].label)
        assertEquals(4_200, fused[0].scoreBp)
        assertEquals("One record is not enough", 4_000, fused[1].scoreBp)
        val both = CandidateFusion.fuse(results, kb, setOf("Grevillea robusta"), nearby = nearby)
        assertEquals(6_300, both[0].scoreBp)
        assertEquals(3_500, CandidateFusion.withoutBoost(both[0], setOf("Grevillea robusta"), nearby))
    }

    @Test
    fun `a stored score loses the garden boost so it is not boosted twice`() {
        val boosted = Candidate("Rosmarinus officinalis", "Salvia rosmarinus", 6_450, setOf(IdSource.AIY_PLANTS))
        assertEquals(4_300, CandidateFusion.withoutBoost(boosted, garden = setOf("Salvia rosmarinus")))
        assertEquals(6_450, CandidateFusion.withoutBoost(boosted, garden = emptySet()))
    }

    @Test
    fun `a second opinion is one more vote, at Gemma's weight`() {
        val candidates = listOf(
            Candidate("Grevillea banksii", "Grevillea banksii", 4_500, setOf(IdSource.BIOCAP, IdSource.AIY_PLANTS)),
            Candidate("Grevillea robusta", "Grevillea robusta", 4_000, setOf(IdSource.BIOCAP)),
        )
        assertTrue(CandidateFusion.needsSecondOpinion(candidates))
        // BioCAP and AIY weigh 6 and 3, Gemma 1: the chosen one gains (10,000 - 4,000) / 10, the other loses a tenth.
        val chosen = CandidateFusion.withSecondOpinion(candidates, chosen = 1, garden = emptySet())
        assertEquals(listOf("Grevillea robusta", "Grevillea banksii"), chosen.map { it.label })
        assertEquals(4_600, chosen[0].scoreBp)
        assertEquals(4_050, chosen[1].scoreBp)
        assertTrue(IdSource.GEMMA in chosen[0].sources)
        val none = CandidateFusion.withSecondOpinion(candidates, chosen = null, garden = emptySet())
        assertEquals("None of them lowers each a little", listOf(4_050, 3_600), none.map { it.scoreBp })
        assertFalse("A sure suggestion needs no second opinion", CandidateFusion.needsSecondOpinion(listOf(candidates[0].copy(scoreBp = 7_000), candidates[1])))
    }
}
