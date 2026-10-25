package io.github.wkoller.pollenplot.domain.plants

/**
 * A plant's roles in the garden's ecosystem, shown as badges. They are listed in the order badges are shown, so safety
 * and problems come before benefits.
 */
enum class EcoRole {
    WEED_RISK,
    TOXIC_TO_PETS,
    OVERWATERING_RISK,
    POLLINATOR_MAGNET,
    NATIVE_BEE_FAVOURITE,
    BIRD_FEEDER,
    BUTTERFLY_HOST,
    FROG_SAFE,
    BENEFICIAL_HAVEN,
    LOCAL_NATIVE,
    EDIBLE,
    HIGH_THIRST,
    DROUGHT_HARDY,
    GROUND_COVER,
}

object EcoRoles {
    /**
     * The roles for a plant, in badge order. [state] is the garden's state, so "Local native" means native to that
     * state; without a state it means native to Australia. [weedRisk] and [overwatering] come from the Weed identifier
     * and from irrigation pairing.
     */
    fun of(
        species: Species?,
        care: CareProfile,
        state: AuState?,
        weedRisk: Boolean = false,
        overwatering: Boolean = false,
    ): List<EcoRole> {
        val roles = mutableSetOf<EcoRole>()
        val wildlife = care.wildlife
        val forage = care.forage
        if (weedRisk || species?.wons == true) roles += EcoRole.WEED_RISK
        if (care.toxicPets == Toxicity.MILD || care.toxicPets == Toxicity.SEVERE) roles += EcoRole.TOXIC_TO_PETS
        if (overwatering) roles += EcoRole.OVERWATERING_RISK
        // A buzz-pollinated flower such as a tomato's draws only the few bees that can buzz it, so on its own that makes
        // the plant a native bee favourite rather than a pollinator magnet.
        val broadNativeAppeal = forage != null && forage.nativeBest >= 3 && !forage.buzz
        if ((wildlife?.pollinator ?: 0) >= 3 || (forage?.honeyBest ?: 0) >= 3 || broadNativeAppeal) roles += EcoRole.POLLINATOR_MAGNET
        if (forage != null && (forage.nativeBest >= 3 || (forage.buzz && forage.nativeBest >= 2))) roles += EcoRole.NATIVE_BEE_FAVOURITE
        if ((wildlife?.birds ?: 0) >= 2) roles += EcoRole.BIRD_FEEDER
        if (wildlife?.butterflyHost == true) roles += EcoRole.BUTTERFLY_HOST
        if ((wildlife?.frogs ?: 0) >= 2) roles += EcoRole.FROG_SAFE
        if ((wildlife?.beneficials ?: 0) >= 2) roles += EcoRole.BENEFICIAL_HAVEN
        if (species != null && (if (state != null) state in species.nativeStates else species.isAustralianNative)) roles += EcoRole.LOCAL_NATIVE
        if (!care.edible.isNullOrBlank()) roles += EcoRole.EDIBLE
        if (care.water == WaterNeed.HIGH) roles += EcoRole.HIGH_THIRST
        if ((care.water == WaterNeed.VERY_LOW || care.water == WaterNeed.LOW) && (care.drought ?: 0) >= 2) roles += EcoRole.DROUGHT_HARDY
        if (care.groundCover == true) roles += EcoRole.GROUND_COVER
        return EcoRole.entries.filter { it in roles }
    }
}
