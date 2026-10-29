package io.github.wkoller.pollenplot.domain.irrigation

import io.github.wkoller.pollenplot.domain.plants.GrowthForm
import io.github.wkoller.pollenplot.domain.plants.Soil
import java.time.LocalDate

/**
 * How a line or a zone is watered. Each method loses some water on the way, which its efficiency allows for. Sprays and
 * sprinklers [spreads] water over an area, so their rate can be compared with the soil's (see [Runoff]).
 */
enum class WaterMethod(val efficiencyBp: Int, val byHand: Boolean, val spreads: Boolean = false) {
    DRIP_LINE(9_000, false),
    DRIPPERS(9_000, false),
    MICRO_SPRAYS(7_500, false, spreads = true),
    SPRINKLER(7_000, false, spreads = true),
    HOSE(8_000, true),
    WATERING_CAN(9_000, true),
}

/**
 * A tap timer, valve or hose run that waters one or more zones. Every emitter on it runs for the same time, so its
 * [flowMlPerHour] is the whole line's, as a bucket test measures it.
 */
data class WateringLine(
    val id: String,
    val name: String,
    val method: WaterMethod,
    val zoneIds: Set<String>,
    val flowMlPerHour: Int?,
    val notes: String = "",
    /** How long the line runs before water starts to pool, as the user timed it, for splitting longer runs. */
    val poolingMinutes: Int? = null,
)

/** A day's weather for the garden: reference evapotranspiration (ET₀) and rain, both in µm, and the chance of rain. */
data class WeatherDay(val date: LocalDate, val et0Um: Int, val rainUm: Int, val rainChanceBp: Int?, val maxTenthsC: Int?)

/** A plant as the watering plan sees it: how thirsty it is, how big, how many, and the soil of its zone. */
data class WaterPlant(
    val id: String,
    val title: String,
    val zoneId: String?,
    /** Its water need as a share of ET₀, in basis points, or null when the knowledge base does not record it. */
    val plantFactorBp: Int?,
    /** Its mature width in mm, or null when it is not recorded. */
    val widthMm: Int?,
    val form: GrowthForm?,
    val quantity: Int,
    val soil: Soil?,
) {
    /** Its canopy in cm², as a circle as wide as the plant, for all of them; null without a width. */
    val canopyCm2: Long?
        get() = widthMm?.let { width -> (Math.PI * (width / 20.0) * (width / 20.0) * quantity).toLong() }

    /** Whether the plan can count it: it needs a water need and a size. */
    val counted: Boolean get() = plantFactorBp != null && widthMm != null
}

/**
 * The soil figures the plan works from. Readily available water (RAW) per metre of root zone comes from the
 * Tasmanian Institute of Agriculture's Soil Water factsheet, by soil texture; the effective root zone is about two
 * thirds of the deepest roots. The root depths for each kind of plant are Pollenplot's own estimates.
 */
object SoilWater {
    /** RAW in mm per metre of root zone. A soil not recorded is taken as loam, and potting mix as sand, which holds least. */
    fun rawMmPerMetre(soil: Soil?): Int = when (soil) {
        Soil.SAND, Soil.POTTING_MIX -> 30
        Soil.CLAY, Soil.WET -> 50
        Soil.LOAM, null -> 90
    }

    /** The effective root zone, in mm, by the kind of plant. */
    fun rootDepthMm(form: GrowthForm?): Int = when (form) {
        GrowthForm.TREE, GrowthForm.PALM -> 600
        GrowthForm.SHRUB, GrowthForm.CLIMBER -> 400
        GrowthForm.GRASS, GrowthForm.LAWN, GrowthForm.GROUNDCOVER, GrowthForm.FERN, GrowthForm.SUCCULENT -> 200
        else -> 300
    }

    /** The water a plant's root zone holds that it can readily use, in mL. */
    fun rawMl(plant: WaterPlant): Long {
        val canopy = plant.canopyCm2 ?: return 0
        // mm/m × m of roots = mm of water; mm over cm² is 0.1 mL per cm² per mm.
        val waterMmTenths = rawMmPerMetre(plant.soil) * rootDepthMm(plant.form) / 100
        return canopy * waterMmTenths / 100
    }
}
