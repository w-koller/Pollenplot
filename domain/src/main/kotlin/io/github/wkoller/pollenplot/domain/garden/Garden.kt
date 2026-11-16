package io.github.wkoller.pollenplot.domain.garden

import io.github.wkoller.pollenplot.domain.plants.Soil
import io.github.wkoller.pollenplot.domain.plants.Sun
import java.time.LocalDate
import java.time.Period

enum class ZoneKind { GARDEN_BED, VEG_PATCH, ORCHARD, LAWN, POTS, POND, HIVE_CORNER, OTHER }

/**
 * A zone's place on the Plot canvas, in centimetres of real yard. The origin is only where the first zones were placed,
 * so a position may be negative.
 */
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
    /** Mulch or leaf litter on the ground, which is shelter for small animals as well as a cover for the soil. */
    val mulched: Boolean = false,
    val rocksAndLogs: Boolean = false,
    val denseShrubs: Boolean = false,
    /** An area the user measured, which takes the place of the area worked out from the canvas. */
    val measuredAreaCm2: Long? = null,
    val notes: String = "",
) {
    val areaCm2: Long get() = measuredAreaCm2 ?: rect.areaCm2
}

enum class PlantStatus { GROWING, REMOVED, DIED }

/**
 * What adding a plant to several zones makes. [ONE_PLANT] is one record that grows in each of the zones, for the same
 * plant with the same care in several places; [PLANT_EACH] is a record of its own in each zone, each with its own
 * count, pot, pruning and history; [ASK] offers both each time.
 */
enum class ManyZones { ASK, ONE_PLANT, PLANT_EACH }

/**
 * A plant in the garden. [speciesName] is the knowledge base's scientific name, or null for a plant the user named
 * themselves. [customName] is the user's own name for it, such as "Mum's lemon tree". [zoneIds] are the zones it grows
 * in, the first chosen first, and none for a plant with no zone. [quantity] is how many there are in all, and
 * [counts] how many are in each zone when the user counted them zone by zone; it is empty when they gave a total.
 */
data class GardenPlant(
    val id: String,
    val speciesName: String?,
    val customName: String?,
    val zoneIds: List<String> = emptyList(),
    val quantity: Int = 1,
    val plantedOn: LocalDate? = null,
    val status: PlantStatus = PlantStatus.GROWING,
    val notes: String = "",
    /** The plant's cut-out picture, as the app stores it, or null before it has one. */
    val thumbnail: String? = null,
    val counts: Map<String, Int> = emptyMap(),
    /** The day the plant died or was removed; null while it grows, or when the day was not kept. */
    val endedOn: LocalDate? = null,
) {
    init {
        require(speciesName != null || !customName.isNullOrBlank()) { "A plant needs a species or a name" }
        require(quantity >= 1) { "A plant entry counts at least one plant" }
    }

    /**
     * How many grow in [zoneId]: the zone's own count when the user gave one, or else the total shared evenly across
     * the plant's zones, at least one. Sharing the total evenly is Pollenplot's reading of a total given for several
     * zones; it is what the watering plan and the zone's list use.
     */
    fun countIn(zoneId: String): Int {
        counts[zoneId]?.let { return it }
        val zones = zoneIds.size.coerceAtLeast(1)
        return ((quantity + zones / 2) / zones).coerceAtLeast(1)
    }

    /** How long the plant lived in the garden, from its planted day to the day it ended, when both are known. */
    val lived: Period?
        get() {
            val from = plantedOn ?: return null
            val to = endedOn ?: return null
            return if (to.isBefore(from)) null else Period.between(from, to)
        }

    /** The zones of one record to make, how many are in each zone when counted zone by zone, and how many in all. */
    data class Planting(val zoneIds: List<String>, val counts: Map<String, Int>, val quantity: Int)

    companion object {
        /**
         * The records to make when a plant is added to [zoneIds]: one record in all of them, or with [each] a record
         * for every zone. [counts] are how many go in each zone, when the user counted zone by zone; otherwise
         * [total] is how many there are in all, or with [each] how many go in each zone. No zone gives one record.
         */
        fun plantings(zoneIds: List<String>, each: Boolean, total: Int, counts: Map<String, Int>? = null): List<Planting> {
            val zones = zoneIds.distinct()
            fun countFor(zone: String) = (counts?.get(zone) ?: total).coerceAtLeast(1)
            return when {
                each && zones.size > 1 -> zones.map { Planting(listOf(it), emptyMap(), countFor(it)) }
                counts != null && zones.size > 1 -> {
                    val own = zones.associateWith(::countFor)
                    listOf(Planting(zones, own, own.values.sum()))
                }
                else -> listOf(Planting(zones, emptyMap(), total.coerceAtLeast(1)))
            }
        }
    }
}
