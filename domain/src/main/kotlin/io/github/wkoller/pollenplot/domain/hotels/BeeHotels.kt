package io.github.wkoller.pollenplot.domain.hotels

import io.github.wkoller.pollenplot.domain.bees.stingless.Facing
import io.github.wkoller.pollenplot.domain.season.Hemisphere
import io.github.wkoller.pollenplot.domain.season.Season
import io.github.wkoller.pollenplot.domain.season.Seasons
import java.time.LocalDate

/** What a part of a hotel is made of. */
enum class HotelMaterial { DRILLED_TIMBER, BAMBOO, PITHY_STEMS, CLAY_BLOCK, PAPER_TUBES, OTHER }

/** A part of a hotel with holes of one kind: what it is made of, how wide and deep its holes are, and how many. */
data class HotelSection(val material: HotelMaterial, val holeMm: Int?, val depthMm: Int?, val holes: Int)

/** A bee and insect hotel in a zone of the garden. */
data class BeeHotel(
    val id: String,
    val name: String,
    val zoneId: String?,
    val facing: Facing?,
    val heightMm: Int?,
    val installedOn: LocalDate?,
    val sections: List<HotelSection>,
    val notes: String = "",
) {
    val holes: Int get() = sections.sumOf { it.holes }
}

/** What a finished nest in a hotel is sealed with. */
enum class Seal { LEAF, RESIN, MEMBRANE, CLAY, GRASS, OTHER }

enum class HotelPest { CUCKOO_BEES, PARASITIC_WASPS, MITES, MOULD, ANTS, SPIDERS }

/** A look at a hotel: how many holes are sealed with each seal, how many are busy, how many bees came out, and pests. */
data class HotelObservation(
    val id: String,
    val hotelId: String,
    val date: LocalDate,
    val sealed: Map<Seal, Int>,
    val busy: Int?,
    val emerged: Int?,
    val pests: Set<HotelPest>,
    val notes: String = "",
) {
    val sealedHoles: Int get() = sealed.values.sum()
}

enum class UpkeepKind { CLEANED, NEW_LINERS, FROZE_NESTS, REPLACED_NESTS, MOVED, RAIN_COVER }

data class HotelUpkeep(val id: String, val hotelId: String, val date: LocalDate, val kind: UpkeepKind, val notes: String = "")

/** Who is likely to have sealed a hole, by what the seal is made of, from Aussie Bee's Bee Hotel Guide. */
enum class Occupant { LEAFCUTTER_BEE, RESIN_BEE, MASKED_BEE, WASP_MIMIC_BEE, POTTER_WASP, GRASS_CARRYING_WASP }

object SealGuide {
    fun likely(seal: Seal): List<Occupant> = when (seal) {
        Seal.LEAF -> listOf(Occupant.LEAFCUTTER_BEE)
        Seal.RESIN -> listOf(Occupant.RESIN_BEE)
        Seal.MEMBRANE -> listOf(Occupant.MASKED_BEE, Occupant.WASP_MIMIC_BEE)
        Seal.CLAY -> listOf(Occupant.POTTER_WASP)
        Seal.GRASS -> listOf(Occupant.GRASS_CARRYING_WASP)
        Seal.OTHER -> emptyList()
    }

    /** Whether the occupant is a bee; wasps share bee hotels too, and catch insects for their young. */
    fun isBee(occupant: Occupant): Boolean = occupant != Occupant.POTTER_WASP && occupant != Occupant.GRASS_CARRYING_WASP
}

/** What a hotel's records show: the latest look and the latest clean-out. */
data class HotelState(val latest: HotelObservation?, val lastCleaned: LocalDate?) {
    companion object {
        /** The upkeep that clears old nest material out of a hotel. */
        private val CLEANING = setOf(UpkeepKind.CLEANED, UpkeepKind.NEW_LINERS, UpkeepKind.FROZE_NESTS, UpkeepKind.REPLACED_NESTS)

        fun of(hotelId: String, observations: List<HotelObservation>, upkeep: List<HotelUpkeep>): HotelState = HotelState(
            latest = observations.filter { it.hotelId == hotelId }.maxByOrNull { it.date },
            lastCleaned = upkeep.filter { it.hotelId == hotelId && it.kind in CLEANING }.maxOfOrNull { it.date },
        )
    }
}

/**
 * Spring is when Aussie Bee's guide replaces a hotel's paper liners and clears out the old nest material, once the
 * bees have come out, so in spring a hotel not cleaned in the last six months is worth a look.
 */
object HotelCare {
    fun cleanInSpring(state: HotelState, today: LocalDate, hemisphere: Hemisphere): Boolean {
        if (Seasons.of(today.month, hemisphere) != Season.SPRING) return false
        val lastCleaned = state.lastCleaned ?: return true
        return lastCleaned.isBefore(today.minusMonths(6))
    }
}
