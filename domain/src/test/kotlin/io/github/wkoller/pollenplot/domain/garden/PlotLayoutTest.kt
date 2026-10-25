package io.github.wkoller.pollenplot.domain.garden

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlotLayoutTest {
    @Test
    fun `positions and sizes snap to the 50 cm grid`() {
        assertEquals(ZoneRect(100, 0, 350, 50), PlotLayout.snapped(ZoneRect(124, -30, 337, 10)))
        assertEquals(150, PlotLayout.snap(126))
        assertEquals(100, PlotLayout.snap(124))
    }

    @Test
    fun `nudging moves one grid step and stops at the canvas edge`() {
        val rect = ZoneRect(0, 100, 400, 300)
        assertEquals(ZoneRect(50, 50, 400, 300), PlotLayout.nudged(rect, stepsRight = 1, stepsDown = -1))
        assertEquals(ZoneRect(0, 100, 400, 300), PlotLayout.nudged(rect, stepsRight = -1, stepsDown = 0))
    }

    @Test
    fun `resizing never goes below the smallest side`() {
        val rect = ZoneRect(0, 0, 100, 100)
        assertEquals(ZoneRect(0, 0, 150, 50), PlotLayout.resizedBySteps(rect, widerSteps = 1, tallerSteps = -1))
        assertEquals(ZoneRect(0, 0, 50, 50), PlotLayout.resizedBySteps(rect, widerSteps = -5, tallerSteps = -5))
    }

    @Test
    fun `new zones fill a tidy grid without overlapping`() {
        val placed = mutableListOf<ZoneRect>()
        repeat(5) { placed += PlotLayout.placeNew(placed) }
        assertEquals(ZoneRect(0, 0, 400, 300), placed[0])
        assertEquals(ZoneRect(500, 0, 400, 300), placed[1])
        assertEquals(ZoneRect(1000, 0, 400, 300), placed[2])
        assertEquals(ZoneRect(0, 400, 400, 300), placed[3])
        for (i in placed.indices) for (j in placed.indices) if (i != j) assertFalse(placed[i].overlaps(placed[j]))
    }

    @Test
    fun `new zones go around a zone the user moved`() {
        val moved = ZoneRect(0, 0, 900, 300)
        val next = PlotLayout.placeNew(listOf(moved))
        assertFalse(next.overlaps(moved))
        assertEquals(ZoneRect(1000, 0, 400, 300), next)
    }

    @Test
    fun `overlap and bounds`() {
        assertTrue(ZoneRect(0, 0, 100, 100).overlaps(ZoneRect(50, 50, 100, 100)))
        assertFalse("Touching edges do not overlap", ZoneRect(0, 0, 100, 100).overlaps(ZoneRect(100, 0, 100, 100)))
        assertEquals(ZoneRect(0, 0, 600, 500), PlotLayout.bounds(listOf(ZoneRect(0, 0, 100, 100), ZoneRect(200, 200, 400, 300))))
        assertNull(PlotLayout.bounds(emptyList()))
    }

    @Test
    fun `a measured area takes the place of the canvas area`() {
        val zone = Zone("z1", "Back Citrus Patch", ZoneKind.ORCHARD, ZoneRect(0, 0, 400, 300))
        assertEquals(120_000L, zone.areaCm2)
        assertEquals(90_000L, zone.copy(measuredAreaCm2 = 90_000).areaCm2)
    }
}
