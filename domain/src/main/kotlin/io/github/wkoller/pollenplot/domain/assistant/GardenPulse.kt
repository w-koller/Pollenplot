package io.github.wkoller.pollenplot.domain.assistant

import io.github.wkoller.pollenplot.domain.rhythms.BeeGroup
import io.github.wkoller.pollenplot.domain.season.Season
import io.github.wkoller.pollenplot.domain.wildlife.LifeGroup
import java.time.LocalDate
import java.time.Month

/**
 * What the garden is doing now: what is in flower, who has turned up, what the bees lack and what is due. The Ask
 * screen turns it into questions worth asking, and the model is told it with every question about the garden.
 */
data class GardenPulse(
    val today: LocalDate,
    val season: Season,
    val partOfDay: PartOfDay = PartOfDay.MORNING,
    /** The plants in flower this month, the most likely first. */
    val inFlower: List<PulsePlant> = emptyList(),
    /** Kinds of animal first seen in the garden this year that were seen in the last month, the latest first. */
    val visitors: List<PulseVisitor> = emptyList(),
    /** For each group of bees the garden feeds or could feed, the next month nothing flowers for it. */
    val gaps: List<PulseGap> = emptyList(),
    /** Days ahead hot enough to warn about for the stingless bee hives. */
    val hotDays: List<PulseHotDay> = emptyList(),
    /** Problems on plants due a second look. */
    val followUps: List<PulseFollowUp> = emptyList(),
    val honeyHives: Int = 0,
    val stinglessHives: Int = 0,
)

/** The time of day, for the greeting. */
enum class PartOfDay {
    MORNING,
    AFTERNOON,
    EVENING,
    ;

    companion object {
        fun of(hour: Int): PartOfDay = when (hour) {
            in 5..11 -> MORNING
            in 12..17 -> AFTERNOON
            else -> EVENING
        }
    }
}

/** A plant in flower, with the zone it grows in when it has one. */
data class PulsePlant(val id: String, val name: String, val zoneName: String?)

/** A kind of animal seen lately: its name, its latest sighting, and the plant it was seen on when there was one. */
data class PulseVisitor(val name: String, val group: LifeGroup, val sightingId: String, val plantId: String? = null, val plantName: String? = null)

/**
 * The next [month] in which nothing in the garden flowers for [group], or a null month when nothing in it flowers for
 * the group at all, with plants Pollen Rhythms suggests for it.
 */
data class PulseGap(val group: BeeGroup, val month: Month?, val plants: List<String> = emptyList())

/** A day forecast to be hot enough to warn the stingless bee keeper about. */
data class PulseHotDay(val date: LocalDate, val maxTenthsC: Int)

/** A problem on a plant that is due a second look. */
data class PulseFollowUp(val problem: String, val plantId: String?, val plantName: String?, val due: LocalDate)
