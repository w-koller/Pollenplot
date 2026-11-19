package io.github.wkoller.pollenplot.domain.compost

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CompostStagesTest {
    private val filled = LocalDate.of(2026, 9, 1)
    private val bin = CompostBin("bin", "Back bin")
    private val batch = CompostBatch("batch", "bin", startedOn = LocalDate.of(2026, 8, 1), filledOn = filled)

    private fun reading(day: LocalDate, tenthsC: Int) = CompostEvent("r$day", "batch", CompostEventKind.TEMPERATURE, day, tenthsC)

    @Test
    fun aBatchStillBeingFilledIsFillingAndHasNoJobs() {
        val open = batch.copy(filledOn = null)
        assertEquals(CompostPhase.FILLING, CompostStages.phase(open, emptyList(), filled))
        assertEquals(CompostStep.FILL, CompostStages.phase(open, emptyList(), filled).step)
        assertTrue(CompostStages.tasks(bin, open, emptyList(), filled, filled.plusMonths(3)).isEmpty())
    }

    @Test
    fun withoutReadingsThePhaseFollowsTheDaysSinceFilling() {
        assertEquals(CompostPhase.WARMING, CompostStages.phase(batch, emptyList(), filled.plusDays(1)))
        assertEquals(CompostPhase.HOT, CompostStages.phase(batch, emptyList(), filled.plusDays(10)))
        assertEquals(CompostPhase.COOLING, CompostStages.phase(batch, emptyList(), filled.plusDays(30)))
        assertEquals(CompostPhase.CURING, CompostStages.phase(batch, emptyList(), filled.plusDays(50)))
        assertEquals(CompostPhase.READY, CompostStages.phase(batch, emptyList(), filled.plusDays(56)))
        assertEquals(CompostStep.EMPTY, CompostPhase.READY.step)
        assertEquals(CompostPhase.EMPTIED, CompostStages.phase(batch.copy(emptiedOn = filled.plusDays(70)), emptyList(), filled.plusDays(70)))
    }

    @Test
    fun aRecentReadingComesBeforeTheEstimate() {
        val day = filled.plusDays(35)
        assertEquals("Still hot at 55 °C", CompostPhase.HOT, CompostStages.phase(batch, listOf(reading(day, 550)), day))
        assertEquals("Cooler than 40 °C early on", CompostPhase.COOLING, CompostStages.phase(batch, listOf(reading(filled.plusDays(10), 300)), filled.plusDays(10)))
        assertEquals("An old reading is not used", CompostPhase.COOLING, CompostStages.phase(batch, listOf(reading(day.minusDays(10), 600)), day))
    }

    @Test
    fun aTurnFallsDueEveryFortnightFromTheLastTurnUntilItMayBeReady() {
        val turns = CompostStages.tasks(bin, batch, emptyList(), filled, filled.plusDays(60)).filter { it.kind == CompostTask.Kind.TURN }
        assertEquals(listOf(14L, 28L, 42L).map { filled.plusDays(it) }, turns.map { it.due })
        val turned = CompostEvent("t", "batch", CompostEventKind.TURNED, filled.plusDays(20))
        val after = CompostStages.tasks(bin, batch, listOf(turned), filled.plusDays(20), filled.plusDays(60)).filter { it.kind == CompostTask.Kind.TURN }
        assertEquals(filled.plusDays(34), after.first().due)
    }

    @Test
    fun anOverdueTurnIsOneJob() {
        val today = filled.plusDays(40)
        val due = CompostStages.due(bin, batch, emptyList(), today)
        assertEquals(1, due.count { it.kind == CompostTask.Kind.TURN })
        assertEquals(filled.plusDays(14), due.single { it.kind == CompostTask.Kind.TURN }.due)
    }

    @Test
    fun aLookWhetherItIsReadyComesAfterEightWeeksAndEveryTwoWeeksAfter() {
        val checks = CompostStages.tasks(bin, batch, emptyList(), filled, filled.plusDays(85)).filter { it.kind == CompostTask.Kind.CHECK_READY }
        assertEquals(listOf(56L, 70L, 84L).map { filled.plusDays(it) }, checks.map { it.due })
        val later = CompostStages.due(bin, batch, emptyList(), filled.plusDays(75))
        assertEquals("Only the latest look is due", listOf(filled.plusDays(70)), later.filter { it.kind == CompostTask.Kind.CHECK_READY }.map { it.due })
    }
}
