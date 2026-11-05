package io.github.wkoller.pollenplot.domain.problems

import io.github.wkoller.pollenplot.domain.season.Hemisphere
import io.github.wkoller.pollenplot.domain.season.MonthSet
import io.github.wkoller.pollenplot.domain.wildlife.FaunaBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.Month

class ProblemsTest {
    private val today = LocalDate.of(2026, 11, 5)
    private val aphids = PlantProblem("p1", "lemon", "aphids", null, LocalDate.of(2026, 10, 20))

    private fun treatment(id: String, applied: LocalDate, followUp: LocalDate?) =
        Treatment(id, "p1", TreatmentMethod.SOAP_SPRAY, "Soap spray", applied, followUp)

    @Test
    fun `a follow-up falls due on its day for an open problem`() {
        assertTrue(ProblemTasks.due(listOf(aphids), listOf(treatment("t1", today.minusDays(3), today.plusDays(4))), today).isEmpty())
        val due = ProblemTasks.due(listOf(aphids), listOf(treatment("t1", today.minusDays(7), today)), today).single()
        assertEquals("t1", due.treatment.id)
        assertTrue("A resolved problem has nothing due", ProblemTasks.due(listOf(aphids.copy(resolvedOn = today)), listOf(treatment("t1", today.minusDays(9), today.minusDays(2))), today).isEmpty())
    }

    @Test
    fun `only the latest treatment counts`() {
        val treatments = listOf(treatment("t1", today.minusDays(14), today.minusDays(7)), treatment("t2", today.minusDays(2), today.plusDays(5)))
        assertTrue(ProblemTasks.due(listOf(aphids), treatments, today).isEmpty())
        assertTrue("No follow-up day means nothing falls due", ProblemTasks.due(listOf(aphids), listOf(treatment("t3", today.minusDays(30), null)), today).isEmpty())
    }

    @Test
    fun `plants in flower by their months or by being seen`() {
        val plants = listOf(
            FlowerPlant("Grevillea", MonthSet.of(Month.NOVEMBER), emptyList()),
            FlowerPlant("Lemon", MonthSet.of(Month.AUGUST), listOf(today.minusDays(12))),
            FlowerPlant("Lomandra", MonthSet.of(Month.AUGUST), listOf(today.minusDays(45))),
            FlowerPlant("Basil", null, emptyList()),
        )
        assertEquals(listOf("Grevillea", "Lemon"), SprayCheck.inFlower(plants, today, Hemisphere.SOUTHERN))
        // November's flowering in the south is May's in the north.
        assertEquals(listOf("Lemon"), SprayCheck.inFlower(plants, today, Hemisphere.NORTHERN))
    }

    @Test
    fun `a problem needs a name`() {
        assertTrue(runCatching { PlantProblem("p", "plant", null, " ", today) }.isFailure)
        assertNotNull(PlantProblem("p", "plant", null, "Galls", today))
    }

    @Test
    fun `the catalogue is complete and sourced, and its animals are in the animal knowledge base`() {
        val dir = File(System.getProperty("kb.dir")!!)
        val (catalogue, problems) = ProblemCatalogue.parse(File(dir, "problems.json").readText())
        assertEquals(emptyList<String>(), problems)
        assertTrue(catalogue.problems.size >= 7)
        assertTrue("Every source is a government page", catalogue.problems.all { it.sourceUrl.contains(".gov.au/") })
        val faunaDir = File(dir, "fauna")
        val fauna = FaunaBase.parse(faunaDir.listFiles { f -> f.name.endsWith(".json") }!!.sortedBy { it.name }.associate { it.name to it.readText() }).fauna
        catalogue.problems.mapNotNull { it.fauna }.forEach { assertNotNull("$it is in kb/fauna", fauna.animal(it)) }
    }
}
