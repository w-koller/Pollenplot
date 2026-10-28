package io.github.wkoller.pollenplot.domain.bees.honey

import io.github.wkoller.pollenplot.domain.plants.AuState
import io.github.wkoller.pollenplot.domain.season.Hemisphere
import io.github.wkoller.pollenplot.domain.season.Season
import io.github.wkoller.pollenplot.domain.season.Seasons
import java.time.LocalDate

enum class HiveTaskKind {
    /** A look through the hive at the colony's strength, the queen and whether it has room. */
    INSPECT,

    /** The brood checked for pests and disease, as the Biosecurity Code of Practice asks in section 3.1. */
    BROOD_CHECK,

    /** A check for Varroa and other mites, by alcohol wash, soapy water wash or sugar shake (sections 3.2 and 3.3). */
    MITE_CHECK,
}

/** Something a hive is due for, and the day it is due by. */
data class HiveTask(val hiveId: String, val kind: HiveTaskKind, val due: LocalDate) {
    fun overdue(today: LocalDate): Boolean = due.isBefore(today)
}

/**
 * When each hive is next due for an inspection, a brood check and a mite check, as reminders. The brood and mite checks
 * follow the Australian Honey Bee Industry Biosecurity Code of Practice (version 2.1, 2025): each hive's brood at least
 * three times a year and at least three calendar months apart (3.1), and mites in spring, summer and autumn where
 * Varroa is not established (3.2), and more often where it is (3.3), monthly here. Inspections come every two weeks in
 * spring and summer unless the beekeeper chooses otherwise, when colonies build up and may swarm, every six weeks in
 * autumn, and wait for spring in winter. A check that has never been done was due on the day the hive was added.
 */
object HiveTasks {
    const val INSPECT_DAYS_GROWING = 14L
    const val INSPECT_DAYS_AUTUMN = 42L
    const val BROOD_CHECK_MONTHS = 3L
    const val MITE_CHECK_DAYS_ESTABLISHED = 30L

    /** What a living hive is due for, soonest first; a hive that died or is gone has nothing due. */
    fun of(
        hive: HoneyHive,
        inspections: List<Inspection>,
        checks: List<VarroaCheck>,
        today: LocalDate,
        hemisphere: Hemisphere,
        varroaEstablished: Boolean,
        inspectDays: Long = INSPECT_DAYS_GROWING,
    ): List<HiveTask> {
        if (hive.status == HiveStatus.DEAD || hive.status == HiveStatus.GONE) return emptyList()
        val ownInspections = inspections.filter { it.hiveId == hive.id }
        val lastInspection = ownInspections.maxOfOrNull { it.date }
        val lastBrood = ownInspections.filter { it.brood != null }.maxOfOrNull { it.date }
        val lastCheck = checks.filter { it.hiveId == hive.id }.maxOfOrNull { it.date }
        val season = Seasons.of(today.month, hemisphere)
        // Counting a check never done from the day the hive was added keeps its due date still, so it can fall overdue.
        val added = hive.startedOn?.takeIf { !it.isAfter(today) } ?: today

        val inspect = when (season) {
            Season.SPRING, Season.SUMMER -> lastInspection?.plusDays(inspectDays) ?: added
            Season.AUTUMN -> lastInspection?.plusDays(INSPECT_DAYS_AUTUMN) ?: added
            // A colony is left closed in the cold, so the next inspection is due as spring starts.
            Season.WINTER -> maxOf(start(Season.SPRING, today, hemisphere), lastInspection?.plusDays(inspectDays) ?: added)
        }
        val brood = lastBrood?.plusMonths(BROOD_CHECK_MONTHS) ?: added
        val mites = if (varroaEstablished) {
            lastCheck?.plusDays(MITE_CHECK_DAYS_ESTABLISHED) ?: added
        } else {
            miteCheckWithoutVarroa(lastCheck, today, hemisphere)
        }
        return listOf(
            HiveTask(hive.id, HiveTaskKind.INSPECT, inspect),
            HiveTask(hive.id, HiveTaskKind.BROOD_CHECK, brood),
            HiveTask(hive.id, HiveTaskKind.MITE_CHECK, mites),
        ).sortedBy { it.due }
    }

    /**
     * Once in each of spring, summer and autumn: by the end of this season if it has had no check yet, otherwise by the
     * end of the next of those seasons. In winter, the next is spring's.
     */
    private fun miteCheckWithoutVarroa(lastCheck: LocalDate?, today: LocalDate, hemisphere: Hemisphere): LocalDate {
        val season = Seasons.of(today.month, hemisphere)
        if (season == Season.WINTER) return end(start(Season.SPRING, today, hemisphere))
        val thisStart = start(season, today, hemisphere)
        val checkedThisSeason = lastCheck != null && !lastCheck.isBefore(thisStart)
        if (!checkedThisSeason) return end(thisStart)
        val nextStart = end(thisStart).plusDays(1)
        val next = Seasons.of(nextStart.month, hemisphere)
        return if (next == Season.WINTER) end(start(Season.SPRING, nextStart, hemisphere)) else end(nextStart)
    }

    /** The first day of the [season] that is under way on [day], or, if it is not, the next one to start. */
    private fun start(season: Season, day: LocalDate, hemisphere: Hemisphere): LocalDate {
        val first = Seasons.months(season, hemisphere).first()
        var month = day.withDayOfMonth(1)
        // Back to this season's first month if the season is under way, else on to the next time it starts.
        if (Seasons.of(day.month, hemisphere) == season) {
            while (month.month != first) month = month.minusMonths(1)
        } else {
            while (month.month != first) month = month.plusMonths(1)
        }
        return month
    }

    /** The last day of the season that starts on [start]. */
    private fun end(start: LocalDate): LocalDate = start.plusMonths(3).minusDays(1)
}

/**
 * Each state and territory's page on registering as a beekeeper, which says who must register and how, checked on 29
 * October 2026. The rules change, so the app links to them rather than repeating them.
 */
object BeekeeperRegistration {
    fun page(state: AuState): String = when (state) {
        AuState.QLD -> "https://www.business.qld.gov.au/industries/farms-fishing-forestry/agriculture/animal/industries/bees/register"
        AuState.NSW -> "https://www.dpird.nsw.gov.au/animals-and-livestock/bees/beekeeper-registration"
        AuState.VIC -> "https://agriculture.vic.gov.au/livestock-and-animals/honey-bees/beekeeper-registration-and-hive-disposal"
        AuState.TAS -> "https://nre.tas.gov.au/biosecurity-tasmania/animal-biosecurity/bees/beekeeper-registration"
        AuState.SA -> "https://pir.sa.gov.au/animal-management/animal-health/species/bees/beekeeper-registration"
        AuState.WA -> "https://www.dpird.wa.gov.au/businesses/beekeeping/beekeeper-registration-and-responsibilities/"
        AuState.NT -> "https://nt.gov.au/industry/agriculture/livestock-and-animals/honey-bees-and-beekeeping"
        AuState.ACT -> "https://www.cityservices.act.gov.au/pets-and-wildlife/wildlife/pest-insects/bees"
    }

    /** The industry's Biosecurity Code of Practice, which the reminders follow. */
    const val CODE_OF_PRACTICE = "https://honeybee.org.au/biosecurity/"
}
