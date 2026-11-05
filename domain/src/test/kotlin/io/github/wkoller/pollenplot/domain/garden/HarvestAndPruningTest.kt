package io.github.wkoller.pollenplot.domain.garden

import io.github.wkoller.pollenplot.domain.season.Hemisphere
import io.github.wkoller.pollenplot.domain.season.MonthSet
import io.github.wkoller.pollenplot.domain.season.Season
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.Month

class HarvestAndPruningTest {
    private fun day(month: Int, date: Int, year: Int = 2026) = LocalDate.of(year, month, date)

    @Test
    fun `harvests add up by season, a southern summer running into the new year`() {
        val harvests = listOf(
            Harvest("a", "p", day(12, 20, 2025), weightG = 400, count = 3),
            Harvest("b", "p", day(1, 10), weightG = 600, count = 5, rating = 4),
            Harvest("c", "p", day(10, 1), count = 2),
        )
        val seasons = Harvests.bySeason(harvests, Hemisphere.SOUTHERN)
        assertEquals(listOf(Season.SPRING, Season.SUMMER), seasons.map { it.season })
        val summer = seasons[1]
        assertEquals(day(12, 1, 2025), summer.start)
        assertTrue(summer.crossesYear)
        assertEquals(1_000L, summer.weightG)
        assertEquals(8, summer.count)
        assertEquals(2, summer.harvests)
        assertFalse(seasons[0].crossesYear)
        assertEquals(MonthSet.of(Month.DECEMBER, Month.JANUARY, Month.OCTOBER), Harvests.months(harvests))

        // In the north the same January is winter, which also began in December.
        assertEquals(Season.WINTER to day(12, 1, 2025), Harvests.seasonOf(day(1, 10), Hemisphere.NORTHERN))
    }

    @Test
    fun `a harvest is offered for edible plants and in veg patches and orchards`() {
        assertTrue(Harvests.offered("fruit", ZoneKind.GARDEN_BED))
        assertTrue(Harvests.offered(null, ZoneKind.VEG_PATCH))
        assertTrue(Harvests.offered(null, ZoneKind.ORCHARD))
        assertFalse(Harvests.offered(null, ZoneKind.GARDEN_BED))
        assertFalse(Harvests.offered(null, null))
    }

    @Test
    fun `after flowering is the month after each flowering run ends`() {
        val autumnToWinter = MonthSet.range(Month.MARCH, Month.AUGUST)
        assertEquals(MonthSet.of(Month.SEPTEMBER), PruneTasks.afterFlowering(autumnToWinter))
        assertEquals("A run across the new year", MonthSet.of(Month.MARCH), PruneTasks.afterFlowering(MonthSet.range(Month.NOVEMBER, Month.FEBRUARY)))
        assertEquals(MonthSet.NONE, PruneTasks.afterFlowering(MonthSet.ALL))
        assertEquals(MonthSet.of(Month.MAY, Month.DECEMBER), PruneTasks.afterFlowering(MonthSet.of(Month.APRIL, Month.OCTOBER, Month.NOVEMBER)))
    }

    @Test
    fun `pruning months come from the user, then the schedule, flipped for the north`() {
        val roses = PruneSchedule("Rosa", PruneTiming.WINTER, MonthSet.of(Month.JULY, Month.AUGUST), "Rose Pruning Techniques", "https://example.org")
        val natives = PruneSchedule("Correa", PruneTiming.AFTER_FLOWERING, null, "Pruning Australian Plants", "https://example.org")
        val citrus = PruneSchedule("Citrus", PruneTiming.AFTER_HARVEST, null, "Citrus pruning", "https://example.org")
        val south = Hemisphere.SOUTHERN
        assertEquals(MonthSet.of(Month.JULY, Month.AUGUST), PruneTasks.months(roses, null, null, south))
        assertEquals(MonthSet.of(Month.JANUARY, Month.FEBRUARY), PruneTasks.months(roses, null, null, Hemisphere.NORTHERN))
        assertEquals(MonthSet.of(Month.SEPTEMBER), PruneTasks.months(natives, null, MonthSet.range(Month.MARCH, Month.AUGUST), south))
        assertNull("No flowering months, no month after them", PruneTasks.months(natives, null, null, south))
        assertNull(PruneTasks.months(citrus, null, null, south))
        assertEquals("The user's own months come first", MonthSet.of(Month.OCTOBER), PruneTasks.months(citrus, MonthSet.of(Month.OCTOBER), null, south))
        assertNull(PruneTasks.months(null, null, MonthSet.ALL, south))
    }

    @Test
    fun `a prune is due in its months until one is recorded`() {
        val months = MonthSet.of(Month.JULY, Month.AUGUST)
        val today = day(8, 10)
        assertEquals(day(7, 1), PruneTasks.runStart(months, today))
        assertNull(PruneTasks.runStart(months, day(9, 1)))
        assertTrue(PruneTasks.due(months, emptyList(), today))
        assertFalse(PruneTasks.due(months, emptyList(), day(9, 1)))
        assertFalse(PruneTasks.due(null, emptyList(), today))

        val julyPrune = PlantEvent("e", "p", PlantEventKind.PRUNED, day(7, 20))
        assertFalse(PruneTasks.due(months, listOf(julyPrune), today))
        val lastYear = julyPrune.copy(date = day(7, 20, 2025))
        assertTrue("Last winter's prune does not count", PruneTasks.due(months, listOf(lastYear), today))
        val repot = julyPrune.copy(kind = PlantEventKind.REPOTTED)
        assertTrue("Only pruning ends it", PruneTasks.due(months, listOf(repot), today))
        assertFalse(PruneTasks.due(months, listOf(julyPrune.copy(kind = PlantEventKind.TIP_PRUNED)), today))

        // A run across the new year started in the December before.
        assertEquals(day(12, 1, 2025), PruneTasks.runStart(MonthSet.of(Month.DECEMBER, Month.JANUARY), day(1, 5)))
        assertEquals("A whole year goes back eleven months", day(2, 1, 2025), PruneTasks.runStart(MonthSet.ALL, day(1, 5)))
    }

    @Test
    fun `the pruning file is sound`() {
        val dir = File("../app/src/main/assets/kb")
        val (guide, problems) = PruningGuide.parse(File(dir, "pruning.json").readText())
        assertEquals(emptyList<String>(), problems)
        assertTrue(guide.schedules.isNotEmpty())
        assertEquals(PruneTiming.WINTER, guide.forPlant("Rosa 'Iceberg'", "Rosa", "Rosaceae")?.timing)
        assertEquals(PruneTiming.AFTER_FLOWERING, guide.forPlant("Correa reflexa", "Correa", "Rutaceae")?.timing)
        assertEquals("Found by its genus", PruneTiming.AFTER_HARVEST, guide.forPlant("Citrus × limon", "Citrus", "Rutaceae")?.timing)
    }
}
