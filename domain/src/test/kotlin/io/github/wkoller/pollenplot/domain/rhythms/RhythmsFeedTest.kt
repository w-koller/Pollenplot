package io.github.wkoller.pollenplot.domain.rhythms

import io.github.wkoller.pollenplot.domain.plants.Forage
import io.github.wkoller.pollenplot.domain.plants.GrowthForm
import io.github.wkoller.pollenplot.domain.season.MonthSet
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.Month

class RhythmsFeedTest {
    private val today = LocalDate.of(2026, 10, 28)

    @Test
    fun `a plant that flowered two weeks earlier than last year is told`() {
        val seen = listOf(
            "bottlebrush" to LocalDate.of(2025, 9, 20), "bottlebrush" to LocalDate.of(2025, 10, 2),
            "bottlebrush" to LocalDate.of(2026, 9, 6), "bottlebrush" to LocalDate.of(2026, 9, 30),
            "lemon" to LocalDate.of(2025, 8, 10), "lemon" to LocalDate.of(2026, 8, 13),
            "salvia" to LocalDate.of(2026, 10, 1),
        )
        assertEquals("The lemon's three days are too few, and the salvia has no last year", listOf(TimingShift("bottlebrush", -14)), RhythmsFeed.shifts(seen, today))
    }

    @Test
    fun `rich nectar and stingless forage come from what is flowering this month`() {
        fun plant(id: String, honeyNectar: Int, stingless: Int, months: MonthSet) =
            RhythmPlant(id, id, null, GrowthForm.TREE, 1, months, MonthSet.NONE, MonthSet.NONE, Forage(honeyNectar, 1, stingless, stingless, 0, 0, 0, false))
        val plants = listOf(
            plant("lemon", 3, 2, MonthSet.of(Month.OCTOBER)),
            plant("grevillea", 2, 3, MonthSet.of(Month.OCTOBER)),
            plant("banksia", 3, 3, MonthSet.of(Month.JUNE)),
        )
        assertEquals(listOf("lemon"), RhythmsFeed.richNectar(plants, Month.OCTOBER).map { it.id })
        assertEquals(listOf("lemon", "grevillea"), RhythmsFeed.forStingless(plants, Month.OCTOBER).map { it.id })
    }
}
