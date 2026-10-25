package io.github.wkoller.pollenplot.domain.plants

import io.github.wkoller.pollenplot.domain.season.MonthSet

enum class GrowthForm {
    TREE, SHRUB, GROUNDCOVER, GRASS, STRAPPY, CLIMBER, PERENNIAL, ANNUAL, SUCCULENT, FERN, AQUATIC, VEGETABLE, HERB, PALM, BULB, LAWN,
}

enum class Sun { FULL_SUN, PART_SHADE, FULL_SHADE }

enum class Soil { SAND, LOAM, CLAY, WET, POTTING_MIX }

/**
 * How much water a plant needs once established, in WUCOLS-style classes. The plant factor is the share of the
 * reference evapotranspiration (ET₀) the plant uses, in basis points.
 */
enum class WaterNeed(val plantFactorBp: Int) {
    VERY_LOW(1_000),
    LOW(2_500),
    MODERATE(5_000),
    HIGH(8_000),
}

enum class Fertiliser {
    NONE,

    /** Proteaceae such as grevilleas and banksias are harmed by phosphorus, so only a low-phosphorus native food suits them. */
    LOW_PHOSPHORUS,
    GENERAL,
    CITRUS,
    HEAVY_FEEDER,
}

/** Whether the plant does well in soil enriched with compost. */
enum class CompostLikes { YES, SOME, NO }

/** Whether the plant's clippings are safe to put in a home compost heap. */
enum class CompostSafe { YES, CAUTION, NO }

enum class Toxicity { NONE, MILD, SEVERE }

enum class AuState { QLD, NSW, VIC, TAS, SA, WA, NT, ACT }

/** Scores run from 0 (none) to 3 (excellent). */
data class Wildlife(
    val pollinator: Int,
    val butterflies: Int,
    /** Caterpillars of at least one butterfly feed on it. */
    val butterflyHost: Boolean,
    val birds: Int,
    val beneficials: Int,
    val frogs: Int,
)

/**
 * Forage for each group of bees, scored 0 (none) to 3 (excellent). The groups are kept apart because honey bees and
 * native bees prefer different flowers.
 */
data class Forage(
    val honeyNectar: Int,
    val honeyPollen: Int,
    val stinglessNectar: Int,
    val stinglessPollen: Int,
    val stinglessResin: Int,
    val solitaryNectar: Int,
    val solitaryPollen: Int,
    /** The flowers release pollen only when a bee vibrates them, which blue-banded and teddy bear bees do. */
    val buzz: Boolean,
) {
    val honeyBest: Int get() = maxOf(honeyNectar, honeyPollen)
    val nativeBest: Int get() = maxOf(stinglessNectar, stinglessPollen, stinglessResin, solitaryNectar, solitaryPollen)
}

/** A range in millimetres, such as a plant's height from young to mature. */
data class SizeRange(val minMm: Int, val maxMm: Int) {
    init {
        require(minMm in 0..maxMm) { "A size range runs from a smaller to a larger value" }
    }
}

/**
 * What a plant needs and offers. Every field may be unknown (null), and [CareResolver] fills gaps from the genus and
 * family.
 */
data class CareProfile(
    val sun: Set<Sun>? = null,
    val water: WaterNeed? = null,
    val drought: Int? = null,
    val frost: Int? = null,
    val salt: Int? = null,
    val soils: Set<Soil>? = null,
    /** Soil pH in tenths, so 5.5 is 55. */
    val phMinTenths: Int? = null,
    val phMaxTenths: Int? = null,
    val fertiliser: Fertiliser? = null,
    val fertiliserNote: String? = null,
    val compostLikes: CompostLikes? = null,
    val compostSafe: CompostSafe? = null,
    val compostNote: String? = null,
    val wildlife: Wildlife? = null,
    val forage: Forage? = null,
    val groundCover: Boolean? = null,
    val height: SizeRange? = null,
    val width: SizeRange? = null,
    /** Flowering months, written for the Southern Hemisphere. */
    val flowering: MonthSet? = null,
    val peakFlowering: MonthSet? = null,
    val pruning: String? = null,
    val toxicPets: Toxicity? = null,
    val toxicPeople: Toxicity? = null,
    val toxicNote: String? = null,
    val edible: String? = null,
    val description: String? = null,
    val careNote: String? = null,
)

/** One species (or cultivar) in the knowledge base. Its [name] is the scientific name and its key. */
data class Species(
    val name: String,
    val genus: String,
    val family: String,
    val common: List<String>,
    val form: GrowthForm?,
    val nativeStates: Set<AuState>,
    /** A Weed of National Significance. The Weed identifier adds local status from official sources. */
    val wons: Boolean,
    val synonyms: List<String>,
    val care: CareProfile,
) {
    val isAustralianNative: Boolean get() = nativeStates.isNotEmpty()

    /** The name to show first: the main common name, or the scientific name when there is none. */
    val displayName: String get() = common.firstOrNull() ?: name
}

/** Facts shared by a whole genus or family, used where a species has none of its own. */
data class GroupProfile(val name: String, val family: String?, val form: GrowthForm?, val care: CareProfile)

enum class CompanionRelation { GOOD, AVOID }

/** How strong the evidence for a companion pairing is. Most pairings are traditional advice, and the app says so. */
enum class Evidence { TRADITIONAL, SOME_EVIDENCE }

/** A companion pairing between two species or genera, named by scientific name or genus. */
data class CompanionPair(val a: String, val b: String, val relation: CompanionRelation, val reason: String, val evidence: Evidence)
