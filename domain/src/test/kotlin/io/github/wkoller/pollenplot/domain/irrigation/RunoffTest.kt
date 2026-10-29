package io.github.wkoller.pollenplot.domain.irrigation

import io.github.wkoller.pollenplot.domain.plants.Soil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RunoffTest {
    /** 20 m² of lawn, in cm². */
    private val lawn = 200_000L

    @Test
    fun `a sprinkler's rate is its flow over the area it waters`() {
        // 400 L an hour over 20 m² is 20 mm an hour.
        assertEquals(20_000, Runoff.applicationUmPerHour(WaterMethod.SPRINKLER, 400_000, lawn))
        assertNull("Drippers water points, not an area", Runoff.applicationUmPerHour(WaterMethod.DRIPPERS, 400_000, lawn))
        assertNull(Runoff.applicationUmPerHour(WaterMethod.SPRINKLER, null, lawn))
        assertNull(Runoff.applicationUmPerHour(WaterMethod.SPRINKLER, 400_000, 0))
    }

    @Test
    fun `a line is flagged only when it is faster than the slowest soil it waters`() {
        val check = Runoff.check(WaterMethod.SPRINKLER, 400_000, lawn, listOf(Soil.LOAM, Soil.CLAY), minutes = 15, poolingMinutes = null)!!
        assertEquals(Soil.CLAY, check.soil)
        assertEquals(1_000..5_000, check.soilUmPerHour)
        assertNull("The cycles wait for the line's pooling time", check.cycles)
        // Loam takes in up to 20 mm an hour, so 20 mm an hour is not flagged.
        assertNull(Runoff.check(WaterMethod.SPRINKLER, 400_000, lawn, listOf(Soil.LOAM), 15, null))
        assertNull("FAO gives no figure for potting mix", Runoff.check(WaterMethod.SPRINKLER, 400_000, lawn, listOf(Soil.POTTING_MIX, null), 15, null))
        assertNull(Runoff.check(WaterMethod.DRIP_LINE, 400_000, lawn, listOf(Soil.CLAY), 15, null))
    }

    @Test
    fun `a run longer than the pooling time is split into even cycles`() {
        assertEquals(listOf(5, 5, 5), Runoff.cycles(15, 5))
        assertEquals("As even as whole minutes allow", listOf(6, 5, 5), Runoff.cycles(16, 6))
        assertEquals("A run that pools no sooner than it ends stays whole", listOf(10), Runoff.cycles(10, 12))
        assertEquals(listOf(5, 5, 5), Runoff.check(WaterMethod.MICRO_SPRAYS, 400_000, lawn, listOf(Soil.CLAY), 15, 5)!!.cycles)
    }
}
