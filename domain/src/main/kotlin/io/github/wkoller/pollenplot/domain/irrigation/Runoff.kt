package io.github.wkoller.pollenplot.domain.irrigation

import io.github.wkoller.pollenplot.domain.plants.Soil

/** A line that puts water on faster than its soil takes it in, and the cycles to run it in once its pooling time is known. */
data class RunoffCheck(
    /** The rate the line puts water on over its zones, in µm an hour. */
    val rateUmPerHour: Int,
    /** The slowest soil among the line's zones. */
    val soil: Soil,
    /** That soil's basic infiltration rate, as FAO gives its range, in µm an hour. */
    val soilUmPerHour: IntRange,
    /** The run split into cycles, in minutes each, once the line's pooling time is recorded; null before that. */
    val cycles: List<Int>?,
)

/**
 * Water put on faster than the soil can take it in pools and runs off. FAO's Irrigation Water Management: Irrigation
 * Methods (Training manual no. 5) gives each soil's basic infiltration rate (Annex 2, Table 7) and says a sprinkler's
 * application rate should be less than it (chapter 5). For a line that cannot be slowed, the US EPA's WaterSense
 * programme describes cycle-and-soak: time how long the line runs before water starts to pool, run it in cycles no
 * longer than that, and leave 30 minutes to an hour between them.
 *
 * Only sprays and sprinklers spread water over an area, so only their rate is compared with the soil's. The rate is
 * Pollenplot's estimate: the line's flow over the zones it waters.
 */
object Runoff {
    /** The shortest soak between cycles that WaterSense gives. */
    const val SOAK_MINUTES = 30

    /** The longest soak between cycles that WaterSense gives. */
    const val SOAK_MAX_MINUTES = 60

    /** A soil's basic infiltration rate as FAO's Table 7 gives it, in µm an hour, or null for a soil it does not cover. */
    fun infiltrationUmPerHour(soil: Soil?): IntRange? = when (soil) {
        Soil.LOAM -> 10_000..20_000
        Soil.CLAY -> 1_000..5_000
        else -> null
    }

    /** The rate a line spreads water over [areaCm2], in µm an hour: 1 L over 1 m² is 1 mm. Null for drip or by hand. */
    fun applicationUmPerHour(method: WaterMethod, flowMlPerHour: Int?, areaCm2: Long): Int? {
        if (!method.spreads || flowMlPerHour == null || flowMlPerHour <= 0 || areaCm2 <= 0) return null
        return (flowMlPerHour.toLong() * 10_000 / areaCm2).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    /**
     * Whether a line on zones of [soils] over [areaCm2] puts water on faster than the slowest of them takes it in, by
     * the top of FAO's range, so that only a clear case is flagged. [minutes] is a run, which is split into cycles of at
     * most [poolingMinutes] when those are known.
     */
    fun check(method: WaterMethod, flowMlPerHour: Int?, areaCm2: Long, soils: Collection<Soil?>, minutes: Int?, poolingMinutes: Int?): RunoffCheck? {
        val rate = applicationUmPerHour(method, flowMlPerHour, areaCm2) ?: return null
        val slowest = soils.mapNotNull { soil -> soil?.let { s -> infiltrationUmPerHour(s)?.let { s to it } } }.minByOrNull { it.second.last } ?: return null
        if (rate <= slowest.second.last) return null
        val cycles = if (minutes != null && poolingMinutes != null) cycles(minutes, poolingMinutes) else null
        return RunoffCheck(rate, slowest.first, slowest.second, cycles)
    }

    /** A run of [minutes] in cycles of at most [poolingMinutes] each, as even as whole minutes allow. */
    fun cycles(minutes: Int, poolingMinutes: Int): List<Int> {
        if (poolingMinutes <= 0 || minutes <= poolingMinutes) return listOf(minutes)
        val count = (minutes + poolingMinutes - 1) / poolingMinutes
        return List(count) { i -> minutes / count + if (i < minutes % count) 1 else 0 }
    }
}
