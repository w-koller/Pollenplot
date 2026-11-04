package io.github.wkoller.pollenplot.domain.plants

import io.github.wkoller.pollenplot.domain.assistant.AssistantFacts
import io.github.wkoller.pollenplot.domain.garden.FloweringSeen
import io.github.wkoller.pollenplot.domain.season.MonthSet
import io.github.wkoller.pollenplot.domain.semantic.CorrectionGraph
import io.github.wkoller.pollenplot.domain.semantic.Term
import io.github.wkoller.pollenplot.domain.semantic.Vocab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.Month

class CareCorrectionsTest {
    private val proteaceae = GroupProfile("Proteaceae", null, null, CareProfile(fertiliser = Fertiliser.LOW_PHOSPHORUS, fertiliserNote = "No phosphorus."))
    private val grevillea = GroupProfile("Grevillea", "Proteaceae", GrowthForm.SHRUB, CareProfile(sun = setOf(Sun.FULL_SUN), frost = 2))
    private val robusta = Species(
        "Grevillea robusta", "Grevillea", "Proteaceae", listOf("Silky oak"), GrowthForm.TREE, setOf(AuState.QLD), false, emptyList(),
        CareProfile(
            water = WaterNeed.VERY_LOW, height = SizeRange(15_000, 30_000),
            flowering = MonthSet.of(Month.OCTOBER, Month.NOVEMBER), peakFlowering = MonthSet.of(Month.NOVEMBER),
        ),
    )
    private val kb = KnowledgeBase(1, listOf(robusta), listOf(grevillea), listOf(proteaceae), emptyList())

    private fun corrected(vararg pairs: Pair<CareField, CareProfile>): KnowledgeBase {
        val stored = pairs.associate { (field, care) -> field to CareCorrections.encode(field, care)!! }
        return kb.withCorrections(mapOf("grevillea ROBUSTA" to CareCorrections.decode(stored)))
    }

    @Test
    fun `a correction comes before the species, the genus and the family`() {
        val corrected = corrected(
            CareField.WATER to CareProfile(water = WaterNeed.MODERATE),
            CareField.SUN to CareProfile(sun = setOf(Sun.PART_SHADE, Sun.FULL_SUN)),
            CareField.FERTILISER to CareProfile(fertiliser = Fertiliser.NONE),
        )
        val resolved = CareResolver.resolve(corrected, robusta)
        assertEquals(WaterNeed.MODERATE, resolved.care.water)
        assertEquals(FactLevel.USER, resolved.levelOf(CareField.WATER))
        assertEquals(setOf(Sun.FULL_SUN, Sun.PART_SHADE), resolved.care.sun)
        assertEquals(Fertiliser.NONE, resolved.care.fertiliser)
        assertNull("A note belongs to the fact it came with, so the family's note goes with the family's fertiliser", resolved.care.fertiliserNote)
        assertEquals(setOf(CareField.WATER, CareField.SUN, CareField.FERTILISER), resolved.corrected)
        // Facts not corrected still come from the knowledge base, at their own level.
        assertEquals(2, resolved.care.frost)
        assertEquals(FactLevel.GENUS, resolved.levelOf(CareField.FROST))
        assertEquals(SizeRange(15_000, 30_000), resolved.care.height)
    }

    @Test
    fun `the knowledge base's own value is still there without corrections`() {
        val corrected = corrected(CareField.WATER to CareProfile(water = WaterNeed.HIGH))
        val plain = CareResolver.resolve(corrected, robusta, corrected = false)
        assertEquals(WaterNeed.VERY_LOW, plain.care.water)
        assertEquals(FactLevel.SPECIES, plain.levelOf(CareField.WATER))
        assertTrue(plain.corrected.isEmpty())
    }

    @Test
    fun `corrected flowering months replace the peak too`() {
        val corrected = corrected(CareField.FLOWERING to CareProfile(flowering = MonthSet.of(Month.AUGUST, Month.SEPTEMBER)))
        val care = CareResolver.resolve(corrected, robusta).care
        assertEquals(MonthSet.of(Month.AUGUST, Month.SEPTEMBER), care.flowering)
        assertNull(care.peakFlowering)
    }

    @Test
    fun `a correction is stored as the knowledge base writes the field`() {
        assertEquals("""{"sun":["full_sun","part_shade"]}""", CareCorrections.encode(CareField.SUN, CareProfile(sun = setOf(Sun.PART_SHADE, Sun.FULL_SUN))))
        assertEquals("""{"flowers":["aug","sep"]}""", CareCorrections.encode(CareField.FLOWERING, CareProfile(flowering = MonthSet.of(Month.SEPTEMBER, Month.AUGUST))))
        assertEquals("""{"height":[500,1200]}""", CareCorrections.encode(CareField.HEIGHT, CareProfile(height = SizeRange(500, 1_200))))
        assertNull("Safety facts cannot be corrected", CareCorrections.encode(CareField.TOXICITY, CareProfile(toxicPets = Toxicity.NONE)))
        assertNull(CareCorrections.encode(CareField.WATER, CareProfile()))
        assertTrue(CareCorrections.same(CareField.WATER, robusta.care, CareProfile(water = WaterNeed.VERY_LOW)))
        assertFalse(CareCorrections.same(CareField.WATER, robusta.care, CareProfile(water = WaterNeed.LOW)))
    }

    @Test
    fun `a stored value that no longer reads is left out`() {
        val care = CareCorrections.decode(
            mapOf(
                CareField.WATER to """{"water":"soggy"}""",
                CareField.FROST to "not json",
                CareField.SOILS to """{"soil":["clay"]}""",
                CareField.TOXICITY to """{"toxic":{"pets":"none"}}""",
            ),
        )
        assertNull(care.water)
        assertNull(care.frost)
        assertNull(care.toxicPets)
        assertEquals(setOf(Soil.CLAY), care.soils)
    }

    @Test
    fun `the assistant is told which facts are the gardener's`() {
        val corrected = corrected(CareField.WATER to CareProfile(water = WaterNeed.MODERATE))
        val resolved = CareResolver.resolve(corrected, robusta)
        val lines = AssistantFacts.plant("Silky oak", robusta, resolved.care, null, 1, null, corrected = resolved.corrected)
        assertTrue(lines.contains("Water need once established: moderate (the gardener's own correction)"))
        assertTrue(lines.any { it.startsWith("Light: full sun") && !it.contains("correction") })
    }

    @Test
    fun `a correction is a statement about the taxon`() {
        val correction = CareCorrection("0b9c", "Grevillea robusta", CareField.WATER, """{"water":"moderate"}""", Instant.parse("2026-11-05T01:00:00Z"))
        val triples = CorrectionGraph.correction(correction)
        val iri = Vocab.recordIri("0b9c")
        assertTrue(triples.any { it.subject == iri && it.predicate == Vocab.DWC + "scientificName" && (it.obj as Term.Literal).value == "Grevillea robusta" })
        assertTrue(triples.any { it.subject == iri && it.predicate == Vocab.PP + "careField" && (it.obj as Term.Literal).value == "water" })
    }

    @Test
    fun `months seen in flower cover only the last year`() {
        val today = LocalDate.of(2026, 11, 5)
        val days = listOf(LocalDate.of(2025, 10, 20), LocalDate.of(2025, 11, 6), LocalDate.of(2026, 3, 1), LocalDate.of(2026, 11, 5), LocalDate.of(2026, 12, 1))
        assertEquals(MonthSet.of(Month.NOVEMBER, Month.MARCH), FloweringSeen.lastYear(days, today))
    }
}
