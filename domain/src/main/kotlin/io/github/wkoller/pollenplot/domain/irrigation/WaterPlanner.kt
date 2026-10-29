package io.github.wkoller.pollenplot.domain.irrigation

import java.time.LocalDate

/** Why a plan says what it says, for the sentence the screen shows. */
enum class PlanReason {
    /** The plants have used the water their roots can readily reach. */
    DUE,

    /** Watering is due, but enough rain is likely tomorrow. */
    WAITING_FOR_RAIN,

    /** The soil still holds enough; the next watering is on [WaterPlan.dueOn]. */
    NOT_YET,

    /** Nothing on the line can be counted: no plants, or none with a recorded water need and size. */
    NOTHING_TO_COUNT,

    /** There is no weather to work from. */
    NO_WEATHER,
}

/** The plan for a line or a hand-watered zone. */
data class WaterPlan(
    val reason: PlanReason,
    /** The day watering is due, today or later, within the forecast; null when it is not due within it. */
    val dueOn: LocalDate?,
    /** What to put on when it is due, allowing for the method's losses, in mL. */
    val applyMl: Long,
    /** How long to run the line for that, when its flow is known. */
    val minutes: Int?,
    /** The rain that counted in the last week, in µm. */
    val rainLastWeekUm: Int,
    /** How much of the readily available water had been used by this morning, in basis points. */
    val usedTodayBp: Int,
    /** Each counted plant's share of the water to apply, for watering by hand, in mL. */
    val perPlantMl: Map<String, Long>,
    /** Plants left out because their water need or size is not recorded. */
    val uncounted: List<String>,
    /** The rain likely tomorrow, in µm, which a plan waiting for rain waits for. */
    val rainTomorrowUm: Int = 0,
)

/** A watering the days ahead hold: the day, what to put on, for how long, and whether good rain may do it instead. */
data class PlannedWatering(
    val date: LocalDate,
    val applyMl: Long,
    val minutes: Int?,
    /** Good rain is likely the next day, so the watering waits for it, as [PlanReason.WAITING_FOR_RAIN] does today. */
    val rainLikely: Boolean,
)

/** The hours a garden is watered in, as minutes after midnight; the user sets them to suit the local water rules. */
data class WateringWindow(val fromMinute: Int, val toMinute: Int) {
    init {
        require(fromMinute in 0 until MINUTES_IN_DAY && toMinute in 1..MINUTES_IN_DAY && fromMinute < toMinute) { "A watering window runs forwards within a day" }
    }

    /** This window starting at [minute]. When it would end first, it ends an hour later instead, or at midnight. */
    fun startingAt(minute: Int): WateringWindow {
        val from = minute.coerceIn(0, MINUTES_IN_DAY - 1)
        return WateringWindow(from, if (toMinute > from) toMinute else (from + HOUR).coerceAtMost(MINUTES_IN_DAY))
    }

    /**
     * This window ending at [minute], where 0 is the midnight that ends the day. When it would start first, it starts
     * an hour earlier instead, or at midnight.
     */
    fun endingAt(minute: Int): WateringWindow {
        val to = if (minute <= 0) MINUTES_IN_DAY else minute.coerceAtMost(MINUTES_IN_DAY)
        return WateringWindow(if (fromMinute < to) fromMinute else (to - HOUR).coerceAtLeast(0), to)
    }

    companion object {
        const val MINUTES_IN_DAY = 24 * 60
        private const val HOUR = 60

        /** Early morning, from 5:00 to 9:00, as Pollenplot suggests until the user sets their own. */
        val DEFAULT = WateringWindow(5 * 60, 9 * 60)
    }
}

/**
 * The watering plan: a daily soil-water bucket for each line, as FAO Irrigation and Drainage Paper 56 describes.
 * Each day the plants use ET₀ × their plant factor over their canopy, and rain refills it: days with less than 2 mm
 * are left out and 80% of the rest counts, Pollenplot's own rule for rain that runs off or evaporates. Watering is due
 * once the plants have used all their readily available water (RAW, the part of the soil's water they can take up
 * without stress; see [SoilWater]), and puts it back, allowing for the method's efficiency. When rain of at least
 * [RAIN_WAIT_UM] is likely tomorrow, watering waits for it. The bucket starts full after the last watering, or a week
 * ago.
 */
object WaterPlanner {
    const val RAIN_COUNTS_FROM_UM = 2_000
    const val RAIN_SHARE_BP = 8_000
    const val RAIN_WAIT_UM = 5_000
    const val RAIN_LIKELY_BP = 6_000
    private const val START_DAYS = 7L

    fun plan(
        method: WaterMethod,
        flowMlPerHour: Int?,
        plants: List<WaterPlant>,
        weather: List<WeatherDay>,
        today: LocalDate,
        lastWatered: LocalDate?,
    ): WaterPlan {
        val counted = plants.filter { it.counted }
        val uncounted = plants.filter { !it.counted }.map { it.title }
        val days = weather.sortedBy { it.date }
        val rainLastWeek = days.filter { it.date.isBefore(today) && !it.date.isBefore(today.minusDays(START_DAYS)) }.sumOf { effectiveRainUm(it) }
        if (counted.isEmpty()) return WaterPlan(PlanReason.NOTHING_TO_COUNT, null, 0, null, rainLastWeek, 0, emptyMap(), uncounted)
        if (days.none { !it.date.isBefore(today) }) return WaterPlan(PlanReason.NO_WEATHER, null, 0, null, rainLastWeek, 0, emptyMap(), uncounted)

        val soil = Account(method, flowMlPerHour, counted)
        val start = maxOf(lastWatered ?: today.minusDays(START_DAYS), today.minusDays(START_DAYS))
        var used = 0.0
        var dueOn: LocalDate? = null
        var usedToday = 0.0
        for (day in days.filter { !it.date.isBefore(start) }) {
            if (day.date == lastWatered) used = 0.0
            used = soil.after(day, used)
            if (day.date == today.minusDays(1)) usedToday = used
            if (!day.date.isBefore(today.minusDays(1)) && used >= soil.raw && dueOn == null) dueOn = maxOf(day.date.plusDays(1), today)
        }
        val share = counted.associate { plant ->
            val part = SoilWater.rawMl(plant).toDouble() / soil.raw
            plant.id to (soil.applyMl * part).toLong()
        }
        val usedBp = if (soil.raw == 0L) 0 else (usedToday / soil.raw * 10_000).toInt().coerceIn(0, 10_000)
        val tomorrow = days.firstOrNull { it.date == today.plusDays(1) }
        val reason = when {
            dueOn == null -> PlanReason.NOT_YET
            dueOn == today && tomorrow != null && rainLikely(tomorrow) -> PlanReason.WAITING_FOR_RAIN
            dueOn == today -> PlanReason.DUE
            else -> PlanReason.NOT_YET
        }
        return WaterPlan(reason, dueOn, soil.applyMl, soil.minutes, rainLastWeek, usedBp, share, uncounted, tomorrow?.rainUm ?: 0)
    }

    /**
     * The waterings due over the forecast, from today. Each time the plants have used their readily available water,
     * as [plan] works it out, the line is watered that morning and the soil starts full again. A watering with good
     * rain likely the next day is marked and leaves the soil for the rain to fill, and the rainy day itself is not
     * planned: whether watering is still due is worked out once the rain has had its turn.
     */
    fun week(
        method: WaterMethod,
        flowMlPerHour: Int?,
        plants: List<WaterPlant>,
        weather: List<WeatherDay>,
        today: LocalDate,
        lastWatered: LocalDate?,
    ): List<PlannedWatering> {
        val counted = plants.filter { it.counted }
        if (counted.isEmpty()) return emptyList()
        val days = weather.sortedBy { it.date }
        val soil = Account(method, flowMlPerHour, counted)
        val start = maxOf(lastWatered ?: today.minusDays(START_DAYS), today.minusDays(START_DAYS))
        val planned = mutableListOf<PlannedWatering>()
        var used = 0.0
        for (day in days.filter { !it.date.isBefore(start) }) {
            if (day.date == lastWatered) used = 0.0
            val morning = planned.lastOrNull()?.takeIf { it.date == day.date }
            if (morning != null && !morning.rainLikely) used = 0.0
            used = soil.after(day, used)
            if (day.date.isBefore(today.minusDays(1)) || used < soil.raw) continue
            val due = maxOf(day.date.plusDays(1), today)
            val last = planned.lastOrNull()
            val waitingForRain = last != null && last.rainLikely && due == last.date.plusDays(1)
            if (waitingForRain || planned.any { it.date == due } || days.none { it.date == due }) continue
            val next = days.firstOrNull { it.date == due.plusDays(1) }
            planned += PlannedWatering(due, soil.applyMl, soil.minutes, rainLikely = next != null && rainLikely(next))
        }
        return planned
    }

    /** The rain that counts for plants on a day, in µm. */
    fun effectiveRainUm(day: WeatherDay): Int = if (day.rainUm < RAIN_COUNTS_FROM_UM) 0 else (day.rainUm.toLong() * RAIN_SHARE_BP / 10_000).toInt()

    /** Whether good rain is likely on [day]: at least [RAIN_WAIT_UM] forecast, at a chance of at least [RAIN_LIKELY_BP]. */
    private fun rainLikely(day: WeatherDay): Boolean = day.rainUm >= RAIN_WAIT_UM && (day.rainChanceBp ?: 0) >= RAIN_LIKELY_BP

    /** The soil-water account of the counted plants on a line: what they hold, what they use and what refills them. */
    private class Account(method: WaterMethod, flowMlPerHour: Int?, counted: List<WaterPlant>) {
        val raw: Long = counted.sumOf { SoilWater.rawMl(it) }

        // mL used per µm of ET₀: plant factor × canopy (cm²) × 0.1 mL per cm² per mm, over 1000 µm per mm.
        private val usePerUm = counted.sumOf { (it.plantFactorBp!!.toLong() * it.canopyCm2!!) } / 10_000.0 / 10_000.0
        private val canopy = counted.sumOf { it.canopyCm2!! }
        val applyMl: Long = raw * 10_000L / method.efficiencyBp
        val minutes: Int? = flowMlPerHour?.takeIf { it > 0 }?.let { flow -> ((applyMl * 60 + flow - 1) / flow).toInt() }

        /** The water used by the end of [day], from [used] at its start, in mL. */
        fun after(day: WeatherDay, used: Double): Double {
            // Rain falls on the whole canopy: µm over cm² is 0.0001 mL per cm² per µm.
            return (used + day.et0Um * usePerUm - effectiveRainUm(day) * canopy / 10_000.0).coerceAtLeast(0.0)
        }
    }
}
