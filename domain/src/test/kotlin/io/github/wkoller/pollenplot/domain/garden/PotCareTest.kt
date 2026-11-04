package io.github.wkoller.pollenplot.domain.garden

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PotCareTest {
    private fun event(kind: PlantEventKind, date: String) = PlantEvent(kind.name + date, "p", kind, LocalDate.parse(date))

    @Test
    fun `nothing falls due without a day to count from`() {
        assertTrue(PotCare.tasks(emptyList(), null).isEmpty())
        assertTrue(PotCare.tasks(listOf(event(PlantEventKind.ROOT_PRUNED, "2026-01-01")), null).isEmpty())
    }

    @Test
    fun `a planting date starts both counts`() {
        val tasks = PotCare.tasks(emptyList(), LocalDate.of(2026, 1, 1))
        assertEquals(PotTask(PotTaskKind.FLUSH, LocalDate.of(2026, 5, 1)), tasks.single { it.kind == PotTaskKind.FLUSH })
        assertEquals(PotTask(PotTaskKind.REPOT, LocalDate.of(2028, 1, 1)), tasks.single { it.kind == PotTaskKind.REPOT })
    }

    @Test
    fun `a flush starts the flush count again but not the repot one`() {
        val tasks = PotCare.tasks(listOf(event(PlantEventKind.REPOTTED, "2025-06-01"), event(PlantEventKind.FLUSHED, "2026-09-01")), LocalDate.of(2020, 1, 1))
        assertEquals(LocalDate.of(2027, 1, 1), tasks.single { it.kind == PotTaskKind.FLUSH }.due)
        assertEquals(LocalDate.of(2027, 6, 1), tasks.single { it.kind == PotTaskKind.REPOT }.due)
    }

    @Test
    fun `fresh mix starts both counts, and topping up starts neither`() {
        val tasks = PotCare.tasks(
            listOf(event(PlantEventKind.FLUSHED, "2025-01-01"), event(PlantEventKind.POTTED_UP, "2026-03-01"), event(PlantEventKind.TOP_DRESSED, "2026-08-01")),
            null,
        )
        assertEquals(LocalDate.of(2026, 7, 1), tasks.single { it.kind == PotTaskKind.FLUSH }.due)
        assertEquals(LocalDate.of(2028, 3, 1), tasks.single { it.kind == PotTaskKind.REPOT }.due)
    }

    @Test
    fun `a task is due on its day and after`() {
        val task = PotTask(PotTaskKind.FLUSH, LocalDate.of(2026, 11, 5))
        assertFalse(task.isDue(LocalDate.of(2026, 11, 4)))
        assertTrue(task.isDue(LocalDate.of(2026, 11, 5)))
        assertTrue(task.isDue(LocalDate.of(2027, 1, 1)))
    }

    @Test
    fun `a plant is potted with a pot record or in a zone of pots`() {
        assertTrue(PotCare.isPotted(PlantPot("pot", "p"), inPotZone = false))
        assertTrue(PotCare.isPotted(null, inPotZone = true))
        assertFalse(PotCare.isPotted(null, inPotZone = false))
    }
}
