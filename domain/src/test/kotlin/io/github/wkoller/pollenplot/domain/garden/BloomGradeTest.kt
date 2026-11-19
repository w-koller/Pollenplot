package io.github.wkoller.pollenplot.domain.garden

import io.github.wkoller.pollenplot.domain.season.MonthSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.Month

class BloomGradeTest {
    @Test
    fun aZoneWithNoKnownFloweringHasNoGrade() {
        assertNull(BloomGrade.of(inFlower = 0, known = 0))
    }

    @Test
    fun theGradeFollowsTheShareOfKnownPlantsInFlower() {
        assertEquals(BloomGrade.NONE, BloomGrade.of(0, 6))
        assertEquals(BloomGrade.FEW, BloomGrade.of(1, 6))
        assertEquals("A third is some", BloomGrade.SOME, BloomGrade.of(2, 6))
        assertEquals(BloomGrade.SOME, BloomGrade.of(3, 6))
        assertEquals("Two thirds is most", BloomGrade.MOST, BloomGrade.of(4, 6))
        assertEquals(BloomGrade.MOST, BloomGrade.of(6, 6))
        assertEquals("One plant of one", BloomGrade.MOST, BloomGrade.of(1, 1))
    }

    @Test
    fun aPlantFlowersInALaterMonthFromItsMonthsOrFromLastYearsSightings() {
        val spring = MonthSet.of(listOf(Month.SEPTEMBER, Month.OCTOBER))
        assertTrue(FloweringSeen.inFlowerIn(spring, MonthSet.NONE, Month.OCTOBER))
        assertFalse(FloweringSeen.inFlowerIn(spring, MonthSet.NONE, Month.DECEMBER))
        assertTrue("Seen in flower last December", FloweringSeen.inFlowerIn(null, MonthSet.of(listOf(Month.DECEMBER)), Month.DECEMBER))
    }

    @Test
    fun floweringIsKnownFromItsMonthsOrADaySeenInFlower() {
        assertTrue(FloweringSeen.known(MonthSet.of(listOf(Month.MAY)), emptyList()))
        assertTrue(FloweringSeen.known(null, listOf(LocalDate.of(2026, 5, 1))))
        assertFalse(FloweringSeen.known(MonthSet.NONE, emptyList()))
        assertFalse(FloweringSeen.known(null, emptyList()))
    }
}
