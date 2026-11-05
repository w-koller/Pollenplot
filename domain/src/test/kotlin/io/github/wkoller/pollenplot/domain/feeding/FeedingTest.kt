package io.github.wkoller.pollenplot.domain.feeding

import io.github.wkoller.pollenplot.domain.plants.Fertiliser
import io.github.wkoller.pollenplot.domain.plants.KnowledgeBaseFilesTest
import io.github.wkoller.pollenplot.domain.season.Hemisphere
import io.github.wkoller.pollenplot.domain.season.MonthSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.Month

class FeedingTest {
    private val citrus = FeedSchedule("Citrus", MonthSet.range(Month.SEPTEMBER, Month.MAY), 42, "Citrus Care", "https://example.org")
    private val kinds = mapOf("powerfeed" to ProductKind.LIQUID, "seasol" to ProductKind.CONDITIONER)
    private val lemon = FeedPlant("Mum's lemon", "z1", citrus)
    private val nov = LocalDate.of(2026, 11, 5)

    private fun feed(key: String, date: LocalDate, vararg zones: String) = Feed("$key$date", key, date, zones.toSet())

    @Test
    fun `a zone never fed is due from the first of a feeding month`() {
        val due = FeedPlanner.due(listOf(lemon), emptyList(), kinds, nov, Hemisphere.SOUTHERN).single()
        assertEquals(LocalDate.of(2026, 11, 1), due.due)
        assertEquals(listOf("Mum's lemon"), due.plants)
    }

    @Test
    fun `a feed holds the zone off for the schedule's days, and a conditioner does not count`() {
        assertTrue(FeedPlanner.due(listOf(lemon), listOf(feed("powerfeed", LocalDate.of(2026, 10, 1), "z1")), kinds, nov, Hemisphere.SOUTHERN).isEmpty())
        val due = FeedPlanner.due(listOf(lemon), listOf(feed("powerfeed", LocalDate.of(2026, 9, 20), "z1"), feed("seasol", nov, "z1")), kinds, nov, Hemisphere.SOUTHERN)
        assertEquals(LocalDate.of(2026, 11, 1), due.single().due)
    }

    @Test
    fun `winter rests citrus, and the north rests six months on`() {
        assertTrue(FeedPlanner.due(listOf(lemon), emptyList(), kinds, LocalDate.of(2026, 7, 1), Hemisphere.SOUTHERN).isEmpty())
        // September to May in the south is March to November in the north, so a northern January rests.
        assertTrue(FeedPlanner.due(listOf(lemon), emptyList(), kinds, LocalDate.of(2027, 1, 10), Hemisphere.NORTHERN).isEmpty())
        assertEquals(1, FeedPlanner.due(listOf(lemon), emptyList(), kinds, LocalDate.of(2026, 7, 1), Hemisphere.NORTHERN).size)
    }

    @Test
    fun `plants without a schedule are never due`() {
        assertTrue(FeedPlanner.due(listOf(FeedPlant("Grevillea", "z1", null)), emptyList(), kinds, nov, Hemisphere.SOUTHERN).isEmpty())
    }

    @Test
    fun `phosphorus above about four per cent warns about sensitive plants`() {
        val plants = listOf("Grevillea" to Fertiliser.LOW_PHOSPHORUS, "Lemon" to Fertiliser.CITRUS, "Banksia" to Fertiliser.LOW_PHOSPHORUS)
        assertEquals(listOf("Grevillea", "Banksia"), PhosphorusGuard.sensitive(Npk(1_000, 600, 500), plants))
        assertTrue(PhosphorusGuard.sensitive(Npk(1_400, 400, 800), plants).isEmpty())
        assertTrue("An unknown analysis does not warn", PhosphorusGuard.sensitive(null, plants).isEmpty())
    }

    @Test
    fun `a conditioner reminder needs a hot day soon and none given lately`() {
        val forecast = listOf(nov to 300, nov.plusDays(2) to 372, nov.plusDays(5) to 400)
        assertEquals(nov.plusDays(2) to 372, ConditionerBeforeHeat.remind(forecast, emptyList(), kinds, nov))
        assertNull(ConditionerBeforeHeat.remind(forecast, listOf(feed("seasol", nov.minusDays(10), "z1")), kinds, nov))
        assertNotNull(ConditionerBeforeHeat.remind(forecast, listOf(feed("powerfeed", nov, "z1")), kinds, nov))
        assertNull(ConditionerBeforeHeat.remind(listOf(nov.plusDays(5) to 400), emptyList(), kinds, nov))
    }

    @Test
    fun `the product and feeding files are complete and sourced`() {
        val dir = File(System.getProperty("kb.dir")!!)
        val products = Products.parse(File(dir, "products.json").readText())
        assertEquals(emptyList<String>(), products.problems)
        assertTrue(products.products.size >= 5)
        assertTrue("Every product links its maker's page", products.products.all { it.source!!.startsWith("https://") })
        val (guide, problems) = FeedingGuide.parse(File(dir, "feeding.json").readText())
        assertEquals(emptyList<String>(), problems)
        val kb = KnowledgeBaseFilesTest.load().knowledgeBase
        guide.schedules.forEach { s ->
            assertTrue("${s.group} is a species, genus or family in the knowledge base", kb.species(s.group) != null || kb.isKnownGenus(s.group) || kb.family(s.group) != null)
        }
        assertEquals(42, guide.forPlant("Citrus × limon", "Citrus", "Rutaceae")?.everyDays)
    }
}
