package io.github.wkoller.pollenplot.domain.garden

import io.github.wkoller.pollenplot.domain.plants.EcoRole
import io.github.wkoller.pollenplot.domain.plants.WaterNeed

/** Badges for a whole zone, shown on its tile on the Plot. Problems come first. */
enum class ZoneBadge { WEED_FOUND, MIXED_THIRST, POLLINATOR_HOTSPOT }

/** What a zone's badges are worked out from, for one plant in it. */
data class PlantFacts(val water: WaterNeed?, val roles: List<EcoRole>)

object ZoneBadges {
    /** A zone with at least this many plants that feed bees counts as a pollinator hotspot. */
    const val HOTSPOT_PLANTS = 3

    /**
     * Plants on the same watering whose plant factors differ by this ratio or more cannot both get the right amount,
     * for example drought-hardy natives (0.1) beside citrus (0.5).
     */
    const val MIXED_THIRST_RATIO = 3

    fun of(plants: List<PlantFacts>): List<ZoneBadge> {
        val badges = mutableSetOf<ZoneBadge>()
        if (plants.any { EcoRole.WEED_RISK in it.roles }) badges += ZoneBadge.WEED_FOUND
        val factors = plants.mapNotNull { it.water?.plantFactorBp }
        if (factors.isNotEmpty() && factors.max() >= MIXED_THIRST_RATIO * factors.min()) badges += ZoneBadge.MIXED_THIRST
        val feeders = plants.count { EcoRole.POLLINATOR_MAGNET in it.roles || EcoRole.NATIVE_BEE_FAVOURITE in it.roles }
        if (feeders >= HOTSPOT_PLANTS) badges += ZoneBadge.POLLINATOR_HOTSPOT
        return ZoneBadge.entries.filter { it in badges }
    }
}
