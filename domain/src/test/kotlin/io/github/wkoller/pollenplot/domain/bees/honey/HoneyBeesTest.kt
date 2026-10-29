package io.github.wkoller.pollenplot.domain.bees.honey

import io.github.wkoller.pollenplot.domain.plants.AuState
import io.github.wkoller.pollenplot.domain.season.Hemisphere
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HoneyBeesTest {
    private val hive = HoneyHive("h1", "Hive 1", HiveType.LANGSTROTH_8, null, null, 1, 1, 2025, HiveStatus.ACTIVE, null)

    private fun inspection(date: LocalDate, brood: BroodPattern? = BroodPattern.SOLID) = Inspection(
        "i$date", "h1", date, queenSeen = true, eggsSeen = true, brood = brood, temperament = Temperament.CALM, framesOfBees = 6,
        framesOfStores = 3, queenCells = QueenCells.NONE, signs = emptySet(), actions = "",
    )

    private fun check(date: LocalDate) = VarroaCheck("v$date", "h1", date, VarroaMethod.ALCOHOL_WASH, 300, 0, null)

    private fun tasks(today: LocalDate, inspections: List<Inspection> = emptyList(), checks: List<VarroaCheck> = emptyList(), established: Boolean = false) =
        HiveTasks.of(hive, inspections, checks, today, Hemisphere.SOUTHERN, established).associate { it.kind to it.due }

    @Test
    fun `queens are marked by the colour of their year`() {
        assertEquals(QueenColour.BLUE, QueenColour.of(2025))
        assertEquals(QueenColour.WHITE, QueenColour.of(2026))
        assertEquals(QueenColour.YELLOW, QueenColour.of(2027))
        assertEquals(QueenColour.GREEN, hive.copy(queenYear = 2029).queenColour)
    }

    @Test
    fun `mites are counted per 100 bees for a wash, and per day for a mat`() {
        assertEquals("4 mites in 300 bees is 1.3 per 100", 13, VarroaCheck("v", "h1", LocalDate.of(2026, 10, 1), VarroaMethod.ALCOHOL_WASH, 300, 4, null).mitesPer100BeesTenths)
        assertNull(VarroaCheck("v", "h1", LocalDate.of(2026, 10, 1), VarroaMethod.STICKY_MAT, 3, 4, null).mitesPer100BeesTenths)
        assertEquals("4 mites over 3 days is 1.3 a day", 13, VarroaCheck("v", "h1", LocalDate.of(2026, 10, 1), VarroaMethod.STICKY_MAT, 3, 4, null).mitesPerDayTenths)
        assertEquals("A soapy water wash counts like an alcohol wash", 13, VarroaCheck("v", "h1", LocalDate.of(2026, 10, 1), VarroaMethod.SOAPY_WATER_WASH, 300, 4, null).mitesPer100BeesTenths)
        assertNull("Drone uncapping samples no number of bees", VarroaCheck("v", "h1", LocalDate.of(2026, 10, 1), VarroaMethod.DRONE_UNCAPPING, 0, 4, null).mitesPer100BeesTenths)
    }

    @Test
    fun `spring inspections are a fortnight apart and brood checks three months`() {
        val due = tasks(LocalDate.of(2026, 10, 28), inspections = listOf(inspection(LocalDate.of(2026, 10, 20))))
        assertEquals(LocalDate.of(2026, 11, 3), due[HiveTaskKind.INSPECT])
        assertEquals(LocalDate.of(2027, 1, 20), due[HiveTaskKind.BROOD_CHECK])
    }

    @Test
    fun `an inspection that did not look at the brood is not a brood check`() {
        val due = tasks(LocalDate.of(2026, 10, 28), inspections = listOf(inspection(LocalDate.of(2026, 6, 1)), inspection(LocalDate.of(2026, 10, 20), brood = null)))
        assertEquals(LocalDate.of(2026, 9, 1), due[HiveTaskKind.BROOD_CHECK])
    }

    @Test
    fun `without Varroa, mites are checked once each in spring, summer and autumn`() {
        val spring = LocalDate.of(2026, 10, 28)
        assertEquals("Not yet checked this spring: by its end", LocalDate.of(2026, 11, 30), tasks(spring)[HiveTaskKind.MITE_CHECK])
        assertEquals("Checked this spring: by the end of summer", LocalDate.of(2027, 2, 28), tasks(spring, checks = listOf(check(LocalDate.of(2026, 9, 15))))[HiveTaskKind.MITE_CHECK])
        val autumn = LocalDate.of(2027, 4, 10)
        assertEquals("Checked this autumn: winter is skipped, so by the end of spring", LocalDate.of(2027, 11, 30), tasks(autumn, checks = listOf(check(LocalDate.of(2027, 3, 20))))[HiveTaskKind.MITE_CHECK])
        assertEquals("In winter, the next is spring's", LocalDate.of(2027, 11, 30), tasks(LocalDate.of(2027, 7, 1))[HiveTaskKind.MITE_CHECK])
    }

    @Test
    fun `with Varroa established, mites are checked monthly`() {
        assertEquals(LocalDate.of(2026, 11, 14), tasks(LocalDate.of(2026, 10, 28), checks = listOf(check(LocalDate.of(2026, 10, 15))), established = true)[HiveTaskKind.MITE_CHECK])
    }

    @Test
    fun `in winter the hive stays closed until spring`() {
        assertEquals(LocalDate.of(2027, 9, 1), tasks(LocalDate.of(2027, 7, 1), inspections = listOf(inspection(LocalDate.of(2027, 5, 1))))[HiveTaskKind.INSPECT])
    }

    @Test
    fun `a beekeeper can choose to inspect more often in spring and summer`() {
        val due = HiveTasks.of(hive, listOf(inspection(LocalDate.of(2026, 10, 20))), emptyList(), LocalDate.of(2026, 10, 28), Hemisphere.SOUTHERN, false, inspectDays = 7)
        assertEquals(LocalDate.of(2026, 10, 27), due.single { it.kind == HiveTaskKind.INSPECT }.due)
    }

    @Test
    fun `a check never done was due on the day the hive was added`() {
        val added = hive.copy(startedOn = LocalDate.of(2026, 10, 20))
        val due = HiveTasks.of(added, emptyList(), emptyList(), LocalDate.of(2026, 10, 28), Hemisphere.SOUTHERN, true).associate { it.kind to it.due }
        assertEquals(LocalDate.of(2026, 10, 20), due[HiveTaskKind.INSPECT])
        assertEquals(LocalDate.of(2026, 10, 20), due[HiveTaskKind.BROOD_CHECK])
        assertEquals(LocalDate.of(2026, 10, 20), due[HiveTaskKind.MITE_CHECK])
        assertTrue(HiveTasks.of(added, emptyList(), emptyList(), LocalDate.of(2026, 10, 28), Hemisphere.SOUTHERN, true).all { it.overdue(LocalDate.of(2026, 10, 28)) })
        assertEquals("Without the day, it is due today", LocalDate.of(2026, 10, 28), tasks(LocalDate.of(2026, 10, 28))[HiveTaskKind.INSPECT])
    }

    @Test
    fun `each check is told about once while it is due`() {
        val today = LocalDate.of(2026, 10, 28)
        val due = HiveTasks.of(hive, listOf(inspection(LocalDate.of(2026, 10, 10))), listOf(check(LocalDate.of(2026, 9, 15))), today, Hemisphere.SOUTHERN, false)
        val first = HiveReminders.toTell(due, today, emptySet())
        assertEquals("The inspection is due, the brood and mite checks are not", listOf(HiveTaskKind.INSPECT), first.map { it.kind })
        val told = HiveReminders.remembered(due, today, emptySet(), first)
        assertTrue("Not again the next morning", HiveReminders.toTell(due, today.plusDays(1), told).isEmpty())

        // Once the inspection is done, it is forgotten, and told about again when the next one falls due.
        val after = HiveTasks.of(hive, listOf(inspection(today)), listOf(check(LocalDate.of(2026, 9, 15))), today, Hemisphere.SOUTHERN, false)
        val forgotten = HiveReminders.remembered(after, today, told, emptyList())
        assertTrue(forgotten.isEmpty())
        val fortnight = today.plusDays(14)
        val later = HiveTasks.of(hive, listOf(inspection(today)), listOf(check(LocalDate.of(2026, 9, 15))), fortnight, Hemisphere.SOUTHERN, false)
        assertEquals(listOf(HiveTaskKind.INSPECT), HiveReminders.toTell(later, fortnight, forgotten).map { it.kind })
    }

    @Test
    fun `a hive that died has nothing due`() {
        assertTrue(HiveTasks.of(hive.copy(status = HiveStatus.DEAD), emptyList(), emptyList(), LocalDate.of(2026, 10, 28), Hemisphere.SOUTHERN, false).isEmpty())
    }

    @Test
    fun `mite checks are monthly in Queensland until the beekeeper chooses`() {
        assertTrue(StateMiteChecks.monthly(null, AuState.QLD))
        assertTrue(!StateMiteChecks.monthly(null, AuState.NSW))
        assertTrue(!StateMiteChecks.monthly(null, null))
        assertTrue(!StateMiteChecks.monthly(false, AuState.QLD))
        assertTrue(StateMiteChecks.monthly(true, AuState.TAS))
        assertTrue(StateMiteChecks.of(AuState.QLD)!!.reportPage.startsWith("https://www.business.qld.gov.au/"))
    }

    @Test
    fun `every state has a registration page`() {
        AuState.entries.forEach { assertTrue(BeekeeperRegistration.page(it).startsWith("https://")) }
    }
}
