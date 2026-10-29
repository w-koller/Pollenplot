package io.github.wkoller.pollenplot.domain.irrigation

import io.github.wkoller.pollenplot.domain.garden.ZoneKind
import io.github.wkoller.pollenplot.domain.plants.Soil
import java.time.LocalDate

/**
 * Pots dry out quickly in heat. The University of California's Agriculture and Natural Resources advises watching the
 * forecast and watering container plants deeply in the early morning of the day before the heat starts, until water
 * runs out of the bottom of the pot (Caring for Container Plants During Heatwaves, 2024). It gives no temperature, so
 * a day forecast to reach [HOT_TENTHS_C] counts as hot by Pollenplot's own choice, which the guide page says.
 */
object PotsBeforeHeat {
    /** 35 °C, in tenths of a degree. */
    const val HOT_TENTHS_C = 350

    /** Whether a zone holds pots: a zone of pots, or one whose soil is potting mix. */
    fun isPots(kind: ZoneKind, soil: Soil?): Boolean = kind == ZoneKind.POTS || soil == Soil.POTTING_MIX

    /** Tomorrow's forecast top in tenths of a degree, when it makes tomorrow hot; otherwise null. */
    fun hotTomorrow(weather: List<WeatherDay>, today: LocalDate): Int? =
        weather.firstOrNull { it.date == today.plusDays(1) }?.maxTenthsC?.takeIf { it >= HOT_TENTHS_C }
}
