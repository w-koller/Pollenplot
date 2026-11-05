package io.github.wkoller.pollenplot.domain.garden

import io.github.wkoller.pollenplot.domain.plants.GrowthForm
import java.time.Instant
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The three layers of a garden for wildlife, as NSW Environment's "Backyards for nature" draws them: a canopy of tree
 * tops, a midstorey of tall trees' branches and bushes, and an understorey with groundcover.
 */
enum class HabitatLayer { CANOPY, MIDSTOREY, GROUNDCOVER }

/** Shelter on and near the ground that the same page shows: mulch or leaf litter, rocks and logs, and dense shrubs. */
enum class GroundFeature { LEAF_LITTER, ROCKS_AND_LOGS, DENSE_SHRUBS }

/** Water for wildlife. NSW Environment names bird baths and ponds; bee water is what the bee Code of Practice asks for. */
enum class WaterKind { BIRD_BATH, POND, BEE_WATER, OTHER }

/** Water set out in a zone, such as a bird bath, with when the user last cleaned it. */
data class WaterFeature(
    val id: String,
    val zoneId: String,
    val kind: WaterKind,
    val notes: String = "",
    val cleanedOn: LocalDate? = null,
    val createdAt: Instant = Instant.EPOCH,
)

/**
 * What a zone offers wildlife: the layers its plants make, the ground features the user marked, the water set out in it,
 * and how far it is to the nearest water elsewhere in the yard.
 */
data class ZoneHabitat(
    val layers: Set<HabitatLayer>,
    val features: Set<GroundFeature>,
    val water: List<WaterFeature>,
    /** The gap to the nearest zone with water, in centimetres: 0 for water in this zone or one touching it. */
    val nearestWaterCm: Int?,
    /** The nearest zone with water, or null for water in this zone or none in the yard. */
    val nearestWaterZoneId: String?,
) {
    val missingLayers: List<HabitatLayer> get() = HabitatLayer.entries.filterNot { it in layers }
    val missingFeatures: List<GroundFeature> get() = GroundFeature.entries.filterNot { it in features }

    /** How many of the layers, ground features and water the zone has. A count, never a score. */
    val present: Int get() = layers.size + features.size + (if (water.isNotEmpty()) 1 else 0)

    companion object {
        /** The most [present] can be: every layer, every ground feature and water. */
        val POSSIBLE: Int = HabitatLayer.entries.size + GroundFeature.entries.size + 1
    }
}

/** The whole yard as a corridor for wildlife: which layers and features it has anywhere, and how many zones have every layer. */
data class CorridorSummary(
    val layers: Set<HabitatLayer>,
    val features: Set<GroundFeature>,
    val waterFeatures: Int,
    val layeredZones: Int,
    val zones: Int,
)

object Corridor {
    /**
     * The layer a plant of [form] makes. NSW Environment names the layers by the plants in them, from groundcovers and
     * shrubs to a canopy of taller trees; which growth form goes in which layer is Pollenplot's reading of that. Lawn,
     * vegetables and water plants make none.
     */
    fun layer(form: GrowthForm?): HabitatLayer? = when (form) {
        GrowthForm.TREE, GrowthForm.PALM -> HabitatLayer.CANOPY
        GrowthForm.SHRUB, GrowthForm.CLIMBER -> HabitatLayer.MIDSTOREY
        GrowthForm.GROUNDCOVER, GrowthForm.GRASS, GrowthForm.STRAPPY, GrowthForm.PERENNIAL, GrowthForm.ANNUAL,
        GrowthForm.SUCCULENT, GrowthForm.FERN, GrowthForm.HERB, GrowthForm.BULB,
        -> HabitatLayer.GROUNDCOVER
        GrowthForm.AQUATIC, GrowthForm.VEGETABLE, GrowthForm.LAWN, null -> null
    }

    /** The layers that plants of [forms] make together. */
    fun layers(forms: List<GrowthForm?>): Set<HabitatLayer> = forms.mapNotNull(::layer).toSet()

    /** The ground features the user marked on [zone]. */
    fun features(zone: Zone): Set<GroundFeature> = buildSet {
        if (zone.mulched) add(GroundFeature.LEAF_LITTER)
        if (zone.rocksAndLogs) add(GroundFeature.ROCKS_AND_LOGS)
        if (zone.denseShrubs) add(GroundFeature.DENSE_SHRUBS)
    }

    /** What [zone] offers wildlife, with plants of [forms] growing in it, among the yard's [zones] and [water]. */
    fun habitat(zone: Zone, forms: List<GrowthForm?>, zones: List<Zone>, water: List<WaterFeature>): ZoneHabitat {
        val here = water.filter { it.zoneId == zone.id }
        val wet = water.map { it.zoneId }.toSet()
        val nearest = if (here.isNotEmpty()) {
            null
        } else {
            zones.filter { it.id != zone.id && it.id in wet }.minByOrNull { distanceCm(zone.rect, it.rect) }
        }
        return ZoneHabitat(
            layers = layers(forms),
            features = features(zone),
            water = here,
            nearestWaterCm = if (here.isNotEmpty()) 0 else nearest?.let { distanceCm(zone.rect, it.rect) },
            nearestWaterZoneId = nearest?.id,
        )
    }

    /** The straight gap between two zones on the Plot, in centimetres: 0 when they touch or overlap. */
    fun distanceCm(a: ZoneRect, b: ZoneRect): Int {
        val dx = maxOf(0, a.xCm - b.rightCm, b.xCm - a.rightCm).toDouble()
        val dy = maxOf(0, a.yCm - b.bottomCm, b.yCm - a.bottomCm).toDouble()
        return sqrt(dx * dx + dy * dy).roundToInt()
    }

    /** The yard as a whole, from each zone's [habitats]. */
    fun summary(habitats: List<ZoneHabitat>): CorridorSummary = CorridorSummary(
        layers = habitats.flatMap { it.layers }.toSet(),
        features = habitats.flatMap { it.features }.toSet(),
        waterFeatures = habitats.sumOf { it.water.size },
        layeredZones = habitats.count { it.missingLayers.isEmpty() },
        zones = habitats.size,
    )
}
