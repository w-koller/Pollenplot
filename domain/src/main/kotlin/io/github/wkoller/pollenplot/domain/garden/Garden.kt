package io.github.wkoller.pollenplot.domain.garden

import io.github.wkoller.pollenplot.domain.plants.Soil
import io.github.wkoller.pollenplot.domain.plants.Sun
import java.time.LocalDate

enum class ZoneKind { GARDEN_BED, VEG_PATCH, ORCHARD, LAWN, POTS, POND, HIVE_CORNER, OTHER }

/** A zone's place on the Plot canvas, in centimetres of real yard, measured from the top left of the canvas. */
data class ZoneRect(val xCm: Int, val yCm: Int, val widthCm: Int, val heightCm: Int) {
    init {
        require(widthCm > 0 && heightCm > 0) { "A zone needs a size" }
    }

    val areaCm2: Long get() = widthCm.toLong() * heightCm
    val rightCm: Int get() = xCm + widthCm
    val bottomCm: Int get() = yCm + heightCm

    fun overlaps(other: ZoneRect): Boolean =
        xCm < other.rightCm && other.xCm < rightCm && yCm < other.bottomCm && other.yCm < bottomCm
}

/** An area of the yard, such as "Front Native Strip" or "Hive Corner". */
data class Zone(
    val id: String,
    val name: String,
    val kind: ZoneKind,
    val rect: ZoneRect,
    val sun: Sun? = null,
    val soil: Soil? = null,
    val mulched: Boolean = false,
    /** An area the user measured, which takes the place of the area worked out from the canvas. */
    val measuredAreaCm2: Long? = null,
    val notes: String = "",
) {
    val areaCm2: Long get() = measuredAreaCm2 ?: rect.areaCm2
}

enum class PlantStatus { GROWING, REMOVED, DIED }

/**
 * A plant in the garden. [speciesName] is the knowledge base's scientific name, or null for a plant the user named
 * themselves. [customName] is the user's own name for it, such as "Mum's lemon tree".
 */
data class GardenPlant(
    val id: String,
    val speciesName: String?,
    val customName: String?,
    val zoneId: String?,
    val quantity: Int = 1,
    val plantedOn: LocalDate? = null,
    val status: PlantStatus = PlantStatus.GROWING,
    val notes: String = "",
) {
    init {
        require(speciesName != null || !customName.isNullOrBlank()) { "A plant needs a species or a name" }
        require(quantity >= 1) { "A plant entry counts at least one plant" }
    }
}
