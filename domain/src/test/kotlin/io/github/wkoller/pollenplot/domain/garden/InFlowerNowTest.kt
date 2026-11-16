package io.github.wkoller.pollenplot.domain.garden

import io.github.wkoller.pollenplot.domain.season.MonthSet
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.Month

class InFlowerNowTest {
    private val today = LocalDate.of(2026, 10, 28)

    @Test
    fun aPlantFloweringThisMonthIsInFlower() {
        assertTrue(FloweringSeen.inFlowerNow(MonthSet.of(listOf(Month.SEPTEMBER, Month.OCTOBER)), emptyList(), today))
        assertFalse(FloweringSeen.inFlowerNow(MonthSet.of(listOf(Month.JANUARY)), emptyList(), today))
    }

    @Test
    fun aPlantSeenInFlowerLatelyIsInFlower() {
        assertTrue("Seen three weeks ago", FloweringSeen.inFlowerNow(null, listOf(today.minusDays(21)), today))
        assertFalse("Seen two months ago", FloweringSeen.inFlowerNow(null, listOf(today.minusDays(60)), today))
        assertFalse("Nothing known", FloweringSeen.inFlowerNow(null, emptyList(), today))
    }
}
