package io.github.wkoller.pollenplot.domain.plants

/** Where a fact came from: the species itself, or its genus or family when the species has no record of its own. */
enum class FactLevel { SPECIES, GENUS, FAMILY }

enum class CareField {
    SUN, WATER, DROUGHT, FROST, SALT, SOILS, PH, FERTILISER, COMPOST, WILDLIFE, FORAGE, GROUND_COVER, HEIGHT, WIDTH,
    FLOWERING, PRUNING, TOXICITY, EDIBLE, DESCRIPTION, CARE_NOTE,
}

/** A plant's care profile with every gap filled where possible, and the level each fact came from. */
data class ResolvedCare(val care: CareProfile, val levels: Map<CareField, FactLevel>) {
    fun levelOf(field: CareField): FactLevel? = levels[field]

    /** True when at least one fact had to come from the genus or family. */
    val usesFallback: Boolean get() = levels.values.any { it != FactLevel.SPECIES }
}

/**
 * Fills gaps in a species' care profile from its genus, then its family. A plant known only by its genus, such as an
 * identification that stopped at "Grevillea", gets the genus and family facts.
 */
object CareResolver {
    fun resolve(kb: KnowledgeBase, species: Species): ResolvedCare =
        merge(species.care, kb.genus(species.genus)?.care, kb.family(species.family)?.care, FactLevel.SPECIES)

    fun resolveGenus(kb: KnowledgeBase, genus: String): ResolvedCare? {
        val group = kb.genus(genus) ?: return null
        val family = group.family?.let { kb.family(it) }
        return merge(group.care, null, family?.care, FactLevel.GENUS)
    }

    private fun merge(first: CareProfile, genus: CareProfile?, family: CareProfile?, firstLevel: FactLevel): ResolvedCare {
        val levels = mutableMapOf<CareField, FactLevel>()
        val layers = listOfNotNull(
            first to firstLevel,
            genus?.let { it to FactLevel.GENUS },
            family?.let { it to FactLevel.FAMILY },
        )

        fun <T> pick(field: CareField, get: (CareProfile) -> T?): T? {
            for ((profile, level) in layers) {
                val value = get(profile)
                if (value != null) {
                    levels[field] = level
                    return value
                }
            }
            return null
        }

        // Facts that belong together, such as a value and its note, come from the same layer.
        fun <T> pickGroup(field: CareField, present: (CareProfile) -> Boolean, get: (CareProfile) -> T): T? =
            layers.firstOrNull { present(it.first) }?.let { (profile, level) ->
                levels[field] = level
                get(profile)
            }

        val compost = pickGroup(CareField.COMPOST, { it.compostLikes != null || it.compostSafe != null }) { it }
        val fertiliser = pickGroup(CareField.FERTILISER, { it.fertiliser != null }) { it }
        val ph = pickGroup(CareField.PH, { it.phMinTenths != null }) { it }
        val toxic = pickGroup(CareField.TOXICITY, { it.toxicPets != null || it.toxicPeople != null }) { it }
        val flowering = pickGroup(CareField.FLOWERING, { it.flowering != null }) { it }
        val care = CareProfile(
            sun = pick(CareField.SUN) { it.sun },
            water = pick(CareField.WATER) { it.water },
            drought = pick(CareField.DROUGHT) { it.drought },
            frost = pick(CareField.FROST) { it.frost },
            salt = pick(CareField.SALT) { it.salt },
            soils = pick(CareField.SOILS) { it.soils },
            phMinTenths = ph?.phMinTenths,
            phMaxTenths = ph?.phMaxTenths,
            fertiliser = fertiliser?.fertiliser,
            fertiliserNote = fertiliser?.fertiliserNote,
            compostLikes = compost?.compostLikes,
            compostSafe = compost?.compostSafe,
            compostNote = compost?.compostNote,
            wildlife = pick(CareField.WILDLIFE) { it.wildlife },
            forage = pick(CareField.FORAGE) { it.forage },
            groundCover = pick(CareField.GROUND_COVER) { it.groundCover },
            height = pick(CareField.HEIGHT) { it.height },
            width = pick(CareField.WIDTH) { it.width },
            flowering = flowering?.flowering,
            peakFlowering = flowering?.peakFlowering,
            pruning = pick(CareField.PRUNING) { it.pruning },
            toxicPets = toxic?.toxicPets,
            toxicPeople = toxic?.toxicPeople,
            toxicNote = toxic?.toxicNote,
            edible = pick(CareField.EDIBLE) { it.edible },
            description = pick(CareField.DESCRIPTION) { it.description },
            careNote = pick(CareField.CARE_NOTE) { it.careNote },
        )
        return ResolvedCare(care, levels)
    }
}
