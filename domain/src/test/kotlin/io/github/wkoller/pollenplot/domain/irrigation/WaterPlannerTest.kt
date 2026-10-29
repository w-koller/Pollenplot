package io.github.wkoller.pollenplot.domain.irrigation

import io.github.wkoller.pollenplot.domain.plants.GrowthForm
import io.github.wkoller.pollenplot.domain.plants.Soil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WaterPlannerTest {
    private val today = LocalDate.of(2026, 10, 28)

    /** A lemon tree 3 m wide in loam: about 7.07 m² of canopy and 54 mm of readily available water in its roots. */
    private val lemon = WaterPlant("lemon", "Lemon", "citrus", plantFactorBp = 5_000, widthMm = 3_000, form = GrowthForm.TREE, quantity = 1, soil = Soil.LOAM)

    private fun days(from: LocalDate, count: Int, et0Um: Int = 5_000, rain: Map<LocalDate, Int> = emptyMap(), chance: Map<LocalDate, Int> = emptyMap()) =
        (0 until count).map { offset ->
            val day = from.plusDays(offset.toLong())
            WeatherDay(day, et0Um, rain[day] ?: 0, chance[day], 300)
        }

    @Test
    fun `the soil's readily available water depends on the soil and the roots`() {
        // 90 mm/m in loam × 0.6 m of tree roots = 54 mm over 70,685 cm², about 382 L.
        assertEquals(381_699, SoilWater.rawMl(lemon))
        assertEquals("Sand holds a third as much", 127_233, SoilWater.rawMl(lemon.copy(soil = Soil.SAND)))
        assertEquals(0, SoilWater.rawMl(lemon.copy(widthMm = null)))
    }

    @Test
    fun `a dry fortnight brings the watering due, and the run time follows the line's flow`() {
        // The lemon uses 2.5 mm a day over 7.07 m², about 17.7 L, so its 382 L lasts 21 days from full.
        val weather = days(today.minusDays(7), 21)
        val plan = WaterPlanner.plan(WaterMethod.DRIPPERS, flowMlPerHour = 32_000, plants = listOf(lemon), weather = weather, today = today, lastWatered = today.minusDays(20))
        assertEquals("Full a week ago, the window starts there, and 22 days would run past the forecast", PlanReason.NOT_YET, plan.reason)
        assertNull(plan.dueOn)

        val sandy = listOf(lemon.copy(soil = Soil.SAND))
        val dry = WaterPlanner.plan(WaterMethod.DRIPPERS, 32_000, sandy, weather, today, lastWatered = null)
        // 127 L lasts about 7.2 days from a week ago: it empties during today, so it is due tomorrow.
        assertEquals(today.plusDays(1), dry.dueOn)
        assertEquals("127 L at 90% efficiency is about 141 L", 141_370, dry.applyMl)
        assertEquals("141 L at 32 L an hour", 266, dry.minutes)
    }

    @Test
    fun `rain refills the soil, and only rain that counts`() {
        val sandy = listOf(lemon.copy(soil = Soil.SAND))
        val weather = days(today.minusDays(7), 14, rain = mapOf(today.minusDays(3) to 12_000, today.minusDays(2) to 1_500))
        val plan = WaterPlanner.plan(WaterMethod.DRIPPERS, 32_000, sandy, weather, today, null)
        assertEquals("12 mm counts as 9.6 mm; 1.5 mm does not count", 9_600, plan.rainLastWeekUm)
        assertTrue("The rain put off the watering", plan.dueOn!!.isAfter(today.plusDays(1)))
    }

    @Test
    fun `watering waits when good rain is likely tomorrow`() {
        val sandy = listOf(lemon.copy(soil = Soil.SAND))
        // At 6 mm a day the sandy lemon has used its 127 L by this morning, so watering is due today.
        val weather = days(today.minusDays(7), 14, et0Um = 6_000, rain = mapOf(today.plusDays(1) to 15_000), chance = mapOf(today.plusDays(1) to 8_000))
        val plan = WaterPlanner.plan(WaterMethod.DRIPPERS, 32_000, sandy, weather, today, null)
        assertEquals(PlanReason.WAITING_FOR_RAIN, plan.reason)
        assertEquals(today, plan.dueOn)
    }

    @Test
    fun `the days ahead hold each watering due over the forecast`() {
        val sandy = listOf(lemon.copy(soil = Soil.SAND))
        val weather = days(today.minusDays(7), 21)
        val week = WaterPlanner.week(WaterMethod.DRIPPERS, 32_000, sandy, weather, today, lastWatered = null)
        // The 127 L lasts about 7.2 days: due tomorrow, then full again after that watering and due 8 days later.
        assertEquals(listOf(today.plusDays(1), today.plusDays(9)), week.map { it.date })
        assertEquals(WaterPlanner.plan(WaterMethod.DRIPPERS, 32_000, sandy, weather, today, null).dueOn, week.first().date)
        assertTrue(week.all { it.applyMl == 141_370L && it.minutes == 266 && !it.rainLikely })
    }

    @Test
    fun `a watering with good rain likely the next day waits for the rain`() {
        val sandy = listOf(lemon.copy(soil = Soil.SAND))
        // At 6 mm a day the soil is used up by this morning, and 20 mm is likely tomorrow, of which 16 mm counts.
        val weather = days(today.minusDays(7), 14, et0Um = 6_000, rain = mapOf(today.plusDays(1) to 20_000), chance = mapOf(today.plusDays(1) to 8_000))
        val week = WaterPlanner.week(WaterMethod.DRIPPERS, 32_000, sandy, weather, today, lastWatered = null)
        assertEquals(PlanReason.WAITING_FOR_RAIN, WaterPlanner.plan(WaterMethod.DRIPPERS, 32_000, sandy, weather, today, null).reason)
        // The rainy day is not planned, and the rain fills the soil enough to put the next watering off for days.
        assertEquals(listOf(today to true, today.plusDays(5) to false), week.map { it.date to it.rainLikely })
    }

    @Test
    fun `nothing is planned for plants the plan cannot count`() {
        val unknown = WaterPlant("x", "Mystery shrub", "citrus", plantFactorBp = null, widthMm = 1_000, form = GrowthForm.SHRUB, quantity = 1, soil = null)
        assertTrue(WaterPlanner.week(WaterMethod.HOSE, null, listOf(unknown), days(today.minusDays(7), 14), today, null).isEmpty())
    }

    @Test
    fun `a watering window runs forwards within a day`() {
        assertEquals(300, WateringWindow.DEFAULT.fromMinute)
        assertEquals(540, WateringWindow.DEFAULT.toMinute)
        listOf(540 to 300, 300 to 300, -1 to 60, 60 to 24 * 60 + 1).forEach { (from, to) ->
            assertTrue("$from to $to", runCatching { WateringWindow(from, to) }.isFailure)
        }
        assertEquals(24 * 60, WateringWindow(18 * 60, 24 * 60).toMinute)
    }

    @Test
    fun `a new start or end time always leaves a window that runs forwards`() {
        val early = WateringWindow.DEFAULT
        assertEquals(WateringWindow(6 * 60, 9 * 60), early.startingAt(6 * 60))
        assertEquals("Starting after it ends moves the end an hour on", WateringWindow(10 * 60, 11 * 60), early.startingAt(10 * 60))
        assertEquals("but no later than midnight", WateringWindow(23 * 60 + 30, 24 * 60), early.startingAt(23 * 60 + 30))
        assertEquals(WateringWindow(5 * 60, 10 * 60), early.endingAt(10 * 60))
        assertEquals("Ending before it starts moves the start an hour back", WateringWindow(3 * 60, 4 * 60), early.endingAt(4 * 60))
        assertEquals("Midnight at the end of the day", WateringWindow(5 * 60, 24 * 60), early.endingAt(0))
        assertEquals("but no earlier than midnight", WateringWindow(0, 30), early.endingAt(30))
    }

    @Test
    fun `a plant that needs a third as much as the thirstiest on its line is flagged`() {
        val grevillea = WaterPlant("grevillea", "Grevillea", "citrus", plantFactorBp = 1_000, widthMm = 1_500, form = GrowthForm.SHRUB, quantity = 1, soil = Soil.LOAM)
        val salvia = WaterPlant("salvia", "Salvia", "citrus", plantFactorBp = 2_500, widthMm = 800, form = GrowthForm.SHRUB, quantity = 1, soil = Soil.LOAM)
        val notes = Pairing.check(listOf(lemon, grevillea, salvia))
        assertEquals("Only the grevillea is at a third or less", listOf("grevillea"), notes.map { it.plant.id })
        assertEquals(5, notes.single().timesNeeded)
        assertEquals("lemon", notes.single().thirstiest.id)
        assertTrue(Pairing.check(listOf(lemon, salvia)).isEmpty())
    }

    @Test
    fun `plants without a water need or size are left out and named`() {
        val unknown = WaterPlant("x", "Mystery shrub", "citrus", plantFactorBp = null, widthMm = 1_000, form = GrowthForm.SHRUB, quantity = 1, soil = null)
        val plan = WaterPlanner.plan(WaterMethod.HOSE, null, listOf(unknown), days(today, 7), today, null)
        assertEquals(PlanReason.NOTHING_TO_COUNT, plan.reason)
        assertEquals(listOf("Mystery shrub"), plan.uncounted)
        val handPlan = WaterPlanner.plan(WaterMethod.HOSE, null, listOf(lemon.copy(soil = Soil.SAND), unknown), days(today.minusDays(7), 14), today, null)
        assertNull("A hose has no flow to time", handPlan.minutes)
        assertEquals(handPlan.applyMl, handPlan.perPlantMl.getValue("lemon"))
        assertEquals(listOf("Mystery shrub"), handPlan.uncounted)
    }
}
