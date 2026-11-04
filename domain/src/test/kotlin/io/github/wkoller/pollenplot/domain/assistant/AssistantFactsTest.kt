package io.github.wkoller.pollenplot.domain.assistant

import io.github.wkoller.pollenplot.domain.plants.CareProfile
import io.github.wkoller.pollenplot.domain.season.Hemisphere
import io.github.wkoller.pollenplot.domain.season.MonthSet
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Month

class AssistantFactsTest {
    private val spring = CareProfile(flowering = MonthSet.range(Month.SEPTEMBER, Month.OCTOBER))

    @Test
    fun `flowering months are given for the garden's hemisphere`() {
        val south = AssistantFacts.plant("Grevillea", null, spring, null, 1, null)
        assertTrue(south.toString(), "Flowers in: September, October" in south)
        val north = AssistantFacts.plant("Grevillea", null, spring, null, 1, null, Hemisphere.NORTHERN)
        assertTrue(north.toString(), "Flowers in: March, April" in north)
    }
}
