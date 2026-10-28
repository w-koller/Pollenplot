package io.github.wkoller.pollenplot.domain.bees.honey

import java.time.LocalDate

/** A place where hives are kept, with the beekeeper registration number the state issued for it, if any. */
data class Apiary(val id: String, val name: String, val registration: String?, val notes: String = "")

enum class HiveType { LANGSTROTH_8, LANGSTROTH_10, FLOW, WARRE, TOP_BAR, NUCLEUS, OTHER }

enum class HiveStatus { ACTIVE, QUEENLESS, DEAD, GONE }

/**
 * The colour a queen is marked with for the year she was born, by the international code: white for years ending in
 * 1 or 6, yellow for 2 or 7, red for 3 or 8, green for 4 or 9, and blue for 5 or 0.
 */
enum class QueenColour { WHITE, YELLOW, RED, GREEN, BLUE;

    companion object {
        fun of(year: Int): QueenColour = when (Math.floorMod(year, 5)) {
            1 -> WHITE
            2 -> YELLOW
            3 -> RED
            4 -> GREEN
            else -> BLUE
        }
    }
}

/** A hive of European honey bees, in a zone of the garden or an apiary elsewhere. */
data class HoneyHive(
    val id: String,
    val name: String,
    val type: HiveType,
    val apiaryId: String?,
    val zoneId: String?,
    val broodBoxes: Int,
    val supers: Int,
    /** The year the queen was born or introduced, when known. */
    val queenYear: Int?,
    val status: HiveStatus,
    val startedOn: LocalDate?,
    val notes: String = "",
) {
    val queenColour: QueenColour? get() = queenYear?.let(QueenColour::of)
}

enum class BroodPattern { SOLID, PATCHY, SPOTTY }

enum class Temperament { CALM, NERVOUS, DEFENSIVE }

enum class QueenCells { NONE, SWARM, SUPERSEDURE, EMERGENCY }

/** What a beekeeper can see in a hive that may need looking into. */
enum class HiveSign { CHALKBROOD, SACBROOD, SUNKEN_CAPPINGS, ROPY_BROOD, TWISTED_LARVAE, DEFORMED_WINGS, SMALL_HIVE_BEETLE, WAX_MOTH, ANTS }

data class Inspection(
    val id: String,
    val hiveId: String,
    val date: LocalDate,
    val queenSeen: Boolean,
    val eggsSeen: Boolean,
    val brood: BroodPattern?,
    val temperament: Temperament?,
    val framesOfBees: Int?,
    val framesOfStores: Int?,
    val queenCells: QueenCells,
    val signs: Set<HiveSign>,
    val actions: String,
    val notes: String = "",
) {
    /**
     * Whether what was seen could be American or European foulbrood, which are notifiable: sunken or perforated
     * cappings, ropy brood or twisted larvae. Only a test can tell, so the app says to report and test it.
     */
    val possibleFoulbrood: Boolean get() = signs.any { it == HiveSign.SUNKEN_CAPPINGS || it == HiveSign.ROPY_BROOD || it == HiveSign.TWISTED_LARVAE }
}

enum class VarroaMethod {
    /** About 300 bees from a brood frame washed in alcohol, which kills them and counts the mites well. */
    ALCOHOL_WASH,

    /** The same wash in soapy water. */
    SOAPY_WATER_WASH,

    /** About 300 bees shaken in icing sugar, which leaves them alive and counts most of the mites. */
    SUGAR_SHAKE,

    /** Mites that fall onto a board under the hive over some days, which shows whether there are mites but not how many. */
    STICKY_MAT,

    /** Drone brood uncapped and looked at for mites. */
    DRONE_UNCAPPING,
}

data class VarroaCheck(
    val id: String,
    val hiveId: String,
    val date: LocalDate,
    val method: VarroaMethod,
    /** The bees in the sample, for a wash or shake; the days the mat was in, for a sticky mat. */
    val sample: Int,
    val mites: Int,
    /** When the result was reported to the state's online system, if it was. */
    val reportedOn: LocalDate?,
) {
    /**
     * Mites per 100 bees, in tenths, for a wash or shake, such as 13 for 1.3 mites per 100 bees; null for the other
     * methods, which do not sample a number of bees.
     */
    val mitesPer100BeesTenths: Int?
        get() = if (method in SAMPLED && sample > 0) {
            ((mites * 1000L + sample / 2) / sample).toInt()
        } else {
            null
        }

    private companion object {
        /** The methods that count the mites on a sample of bees. */
        val SAMPLED = setOf(VarroaMethod.ALCOHOL_WASH, VarroaMethod.SOAPY_WATER_WASH, VarroaMethod.SUGAR_SHAKE)
    }

    /** Mites that fell per day, in tenths, for a sticky mat; null for the other methods. */
    val mitesPerDayTenths: Int?
        get() = if (method == VarroaMethod.STICKY_MAT && sample > 0) ((mites * 10L + sample / 2) / sample).toInt() else null
}

/** A treatment put in a hive, as its label says to use it. */
data class Treatment(
    val id: String,
    val hiveId: String,
    val product: String,
    val activeIngredient: String,
    val started: LocalDate,
    val ended: LocalDate?,
    /** Honey supers came off before the treatment, as most labels require. */
    val supersRemoved: Boolean,
    val notes: String = "",
)

enum class FeedKind { SYRUP_1_TO_1, SYRUP_2_TO_1, POLLEN_SUBSTITUTE, FONDANT }

data class Feeding(val id: String, val hiveId: String, val date: LocalDate, val kind: FeedKind, val amount: Int, val notes: String = "")

data class Harvest(val id: String, val hiveId: String, val date: LocalDate, val frames: Int, val honeyGrams: Int, val notes: String = "")

enum class ColonyEventKind { SWARMED, SPLIT, COMBINED, REQUEENED, MOVED, DIED }

data class ColonyEvent(val id: String, val hiveId: String, val date: LocalDate, val kind: ColonyEventKind, val notes: String = "")
