package io.github.wkoller.pollenplot.domain.season

import java.time.Month

/** The half of the world the garden is in. Seasons, flowering months and hive tasks all depend on it. */
enum class Hemisphere {
    SOUTHERN,
    NORTHERN,
    ;

    companion object {
        /**
         * Works out the hemisphere from a latitude in degrees × 10⁷, the way coordinates are stored. A garden on the
         * equator counts as southern, because the app's calendars are written for Australia.
         */
        fun ofLatitude(latitudeE7: Int): Hemisphere = if (latitudeE7 > 0) NORTHERN else SOUTHERN
    }
}

enum class Season { SPRING, SUMMER, AUTUMN, WINTER }

/** Meteorological seasons, as the Bureau of Meteorology uses them: in the south, spring is September to November. */
object Seasons {
    fun of(month: Month, hemisphere: Hemisphere): Season {
        val southern = toSouthern(month, hemisphere)
        return when (southern) {
            Month.SEPTEMBER, Month.OCTOBER, Month.NOVEMBER -> Season.SPRING
            Month.DECEMBER, Month.JANUARY, Month.FEBRUARY -> Season.SUMMER
            Month.MARCH, Month.APRIL, Month.MAY -> Season.AUTUMN
            Month.JUNE, Month.JULY, Month.AUGUST -> Season.WINTER
        }
    }

    /** The three months of [season] in [hemisphere], in calendar order within the season. */
    fun months(season: Season, hemisphere: Hemisphere): List<Month> {
        val southern = when (season) {
            Season.SPRING -> listOf(Month.SEPTEMBER, Month.OCTOBER, Month.NOVEMBER)
            Season.SUMMER -> listOf(Month.DECEMBER, Month.JANUARY, Month.FEBRUARY)
            Season.AUTUMN -> listOf(Month.MARCH, Month.APRIL, Month.MAY)
            Season.WINTER -> listOf(Month.JUNE, Month.JULY, Month.AUGUST)
        }
        return southern.map { fromSouthern(it, hemisphere) }
    }

    /**
     * Moves a month written for the Southern Hemisphere to the matching month in [hemisphere]. The knowledge base
     * records flowering for the south, so a garden in the north sees it six months on.
     */
    fun fromSouthern(month: Month, hemisphere: Hemisphere): Month =
        if (hemisphere == Hemisphere.SOUTHERN) month else month.plus(6)

    /** The reverse of [fromSouthern]: the Southern Hemisphere month that matches [month] in [hemisphere]. */
    fun toSouthern(month: Month, hemisphere: Hemisphere): Month =
        if (hemisphere == Hemisphere.SOUTHERN) month else month.plus(6)
}

/**
 * A set of months stored as twelve bits, January in the lowest bit. Flowering times, forage gaps and hive seasons
 * are all sets of months.
 */
@JvmInline
value class MonthSet(val bits: Int) {
    init {
        require(bits in 0..ALL_BITS) { "A month set uses only the lowest twelve bits" }
    }

    operator fun contains(month: Month): Boolean = bits and bit(month) != 0

    val months: List<Month> get() = Month.entries.filter { it in this }

    val isEmpty: Boolean get() = bits == 0

    val size: Int get() = Integer.bitCount(bits)

    operator fun plus(other: MonthSet) = MonthSet(bits or other.bits)

    operator fun minus(other: MonthSet) = MonthSet(bits and other.bits.inv() and ALL_BITS)

    /** The same months moved to [hemisphere], for a set written for the Southern Hemisphere. */
    fun fromSouthern(hemisphere: Hemisphere): MonthSet =
        if (hemisphere == Hemisphere.SOUTHERN) this else of(months.map { Seasons.fromSouthern(it, hemisphere) })

    companion object {
        private const val ALL_BITS = (1 shl 12) - 1

        val NONE = MonthSet(0)
        val ALL = MonthSet(ALL_BITS)

        private fun bit(month: Month) = 1 shl (month.value - 1)

        fun of(months: Iterable<Month>) = MonthSet(months.fold(0) { acc, month -> acc or bit(month) })

        fun of(vararg months: Month) = of(months.asIterable())

        /**
         * The months from [first] to [last] inclusive, wrapping past December, so a range from November to February is
         * four months long.
         */
        fun range(first: Month, last: Month): MonthSet {
            val months = mutableListOf(first)
            var month = first
            while (month != last) {
                month = month.plus(1)
                months += month
            }
            return of(months)
        }
    }
}
