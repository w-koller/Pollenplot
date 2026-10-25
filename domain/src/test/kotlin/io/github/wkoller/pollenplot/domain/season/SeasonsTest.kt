package io.github.wkoller.pollenplot.domain.season

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Month

class SeasonsTest {
    @Test
    fun `latitude decides the hemisphere`() {
        assertEquals(Hemisphere.SOUTHERN, Hemisphere.ofLatitude(-274_698_000)) // Brisbane
        assertEquals(Hemisphere.NORTHERN, Hemisphere.ofLatitude(515_074_000)) // London
        assertEquals(Hemisphere.SOUTHERN, Hemisphere.ofLatitude(0))
    }

    @Test
    fun `southern seasons follow the Bureau of Meteorology`() {
        assertEquals(Season.SPRING, Seasons.of(Month.SEPTEMBER, Hemisphere.SOUTHERN))
        assertEquals(Season.SUMMER, Seasons.of(Month.JANUARY, Hemisphere.SOUTHERN))
        assertEquals(Season.AUTUMN, Seasons.of(Month.APRIL, Hemisphere.SOUTHERN))
        assertEquals(Season.WINTER, Seasons.of(Month.JULY, Hemisphere.SOUTHERN))
    }

    @Test
    fun `northern seasons are six months apart`() {
        assertEquals(Season.SPRING, Seasons.of(Month.MARCH, Hemisphere.NORTHERN))
        assertEquals(Season.SUMMER, Seasons.of(Month.JULY, Hemisphere.NORTHERN))
        assertEquals(Season.AUTUMN, Seasons.of(Month.OCTOBER, Hemisphere.NORTHERN))
        assertEquals(Season.WINTER, Seasons.of(Month.JANUARY, Hemisphere.NORTHERN))
    }

    @Test
    fun `every month belongs to exactly one season`() {
        Hemisphere.entries.forEach { hemisphere ->
            val all = Season.entries.flatMap { Seasons.months(it, hemisphere) }
            assertEquals(Month.entries.toSet(), all.toSet())
            assertEquals(12, all.size)
            all.forEach { month -> assertTrue(month in Seasons.months(Seasons.of(month, hemisphere), hemisphere)) }
        }
    }

    @Test
    fun `month sets hold months and move between hemispheres`() {
        val spring = MonthSet.of(Month.SEPTEMBER, Month.OCTOBER, Month.NOVEMBER)
        assertTrue(Month.OCTOBER in spring)
        assertFalse(Month.MARCH in spring)
        assertEquals(3, spring.size)
        assertEquals(MonthSet.of(Month.MARCH, Month.APRIL, Month.MAY), spring.fromSouthern(Hemisphere.NORTHERN))
        assertEquals(spring, spring.fromSouthern(Hemisphere.SOUTHERN))
    }

    @Test
    fun `ranges wrap past December`() {
        val summer = MonthSet.range(Month.NOVEMBER, Month.FEBRUARY)
        assertEquals(listOf(Month.JANUARY, Month.FEBRUARY, Month.NOVEMBER, Month.DECEMBER), summer.months)
        assertEquals(MonthSet.of(Month.MAY), MonthSet.range(Month.MAY, Month.MAY))
        assertEquals(MonthSet.ALL - summer, MonthSet.range(Month.MARCH, Month.OCTOBER))
    }
}
