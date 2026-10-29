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

        val raw = counted.sumOf { SoilWater.rawMl(it) }
        // mL used per µm of ET₀: plant factor × canopy (cm²) × 0.1 mL per cm² per mm, over 1000 µm per mm.
        val usePerUm = counted.sumOf { (it.plantFactorBp!!.toLong() * it.canopyCm2!!) } / 10_000.0 / 10_000.0
        val canopy = counted.sumOf { it.canopyCm2!! }
        val start = maxOf(lastWatered ?: today.minusDays(START_DAYS), today.minusDays(START_DAYS))
        var used = 0.0
        var dueOn: LocalDate? = null
        var usedToday = 0.0
        for (day in days.filter { !it.date.isBefore(start) }) {
            if (day.date == lastWatered) used = 0.0
            // Rain falls on the whole canopy: µm over cm² is 0.0001 mL per cm² per µm.
            used += day.et0Um * usePerUm - effectiveRainUm(day) * canopy / 10_000.0
            used = used.coerceAtLeast(0.0)
            if (day.date == today.minusDays(1)) usedToday = used
            if (!day.date.isBefore(today.minusDays(1)) && used >= raw && dueOn == null) dueOn = maxOf(day.date.plusDays(1), today)
        }
        val applyMl = (raw * 10_000L / method.efficiencyBp)
        val minutes = flowMlPerHour?.takeIf { it > 0 }?.let { flow -> ((applyMl * 60 + flow - 1) / flow).toInt() }
        val share = counted.associate { plant ->
            val part = SoilWater.rawMl(plant).toDouble() / raw
            plant.id to (applyMl * part).toLong()
        }
        val usedBp = if (raw == 0L) 0 else (usedToday / raw * 10_000).toInt().coerceIn(0, 10_000)
        val tomorrow = days.firstOrNull { it.date == today.plusDays(1) }
        val reason = when {
            dueOn == null -> PlanReason.NOT_YET
            dueOn == today && tomorrow != null && tomorrow.rainUm >= RAIN_WAIT_UM && (tomorrow.rainChanceBp ?: 0) >= RAIN_LIKELY_BP -> PlanReason.WAITING_FOR_RAIN
            dueOn == today -> PlanReason.DUE
            else -> PlanReason.NOT_YET
        }
        return WaterPlan(reason, dueOn, applyMl, minutes, rainLastWeek, usedBp, share, uncounted, tomorrow?.rainUm ?: 0)
    }

    /** The rain that counts for plants on a day, in µm. */
    fun effectiveRainUm(day: WeatherDay): Int = if (day.rainUm < RAIN_COUNTS_FROM_UM) 0 else (day.rainUm.toLong() * RAIN_SHARE_BP / 10_000).toInt()
}
