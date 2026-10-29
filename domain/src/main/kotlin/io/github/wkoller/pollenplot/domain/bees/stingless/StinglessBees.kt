package io.github.wkoller.pollenplot.domain.bees.stingless

import java.time.LocalDate

/** The stingless bees kept in hives in Australia, by their scientific names, since their common names vary. */
enum class StinglessSpecies(val scientificName: String?) {
    TETRAGONULA_CARBONARIA("Tetragonula carbonaria"),
    TETRAGONULA_HOCKINGSI("Tetragonula hockingsi"),
    AUSTROPLEBEIA_AUSTRALIS("Austroplebeia australis"),
    OTHER(null),
    UNKNOWN(null),
}

/**
 * The kinds of box a colony lives in. An OATH box (Original Australian Tetragonula Hive) comes in two halves, so it
 * can be split, and can take a honey compartment on top; a one-part box can only be budded.
 */
enum class NativeBox { OATH, OATH_HONEY_SUPER, ONE_PART, LOG, OTHER }

enum class NativeOrigin { BOUGHT, SPLIT, BUDDED, RESCUED, OTHER }

enum class NativeStatus { ACTIVE, DIED, GONE }

/** The way a hive's entrance faces. */
enum class Facing { N, NE, E, SE, S, SW, W, NW }

/** A colony of stingless bees in a box, in a zone of the garden. */
data class NativeHive(
    val id: String,
    val name: String,
    val species: StinglessSpecies,
    val box: NativeBox,
    val origin: NativeOrigin,
    val zoneId: String?,
    val facing: Facing?,
    val status: NativeStatus,
    val startedOn: LocalDate?,
    val notes: String = "",
)

enum class NativeLogKind {
    /** Bees counted coming home in a minute, the measure of a colony's strength that the guide uses. */
    ACTIVITY,
    WEIGHT,
    SPLIT,
    BUDDING,
    HONEY,
    PEST,
    SWARM,
    MOVE,
    HEAT_PROTECTION,
    COLD_PROTECTION,
}

enum class NativePest { SYRPHID_FLY, PHORID_FLY, SMALL_HIVE_BEETLE, ANTS, BEMBIX_WASP, OTHER }

/**
 * The three kinds of swarm a stingless bee hive can have: another colony trying to take it over, bees keeping out
 * bees from another hive, and males waiting for a new queen to mate.
 */
enum class NativeSwarm { FIGHTING, DEFENDING, MATING }

/** Something seen or done at a hive on a day. Only the field that belongs to its kind is set. */
data class NativeLog(
    val id: String,
    val hiveId: String,
    val date: LocalDate,
    val kind: NativeLogKind,
    val beesPerMinute: Int? = null,
    val weightGrams: Int? = null,
    val honeyMl: Int? = null,
    val pest: NativePest? = null,
    val swarm: NativeSwarm? = null,
    val notes: String = "",
)

/** A hive's latest weight and count of bees coming home, the two measures of its strength. */
data class NativeStrength(val weightGrams: Int?, val weighedOn: LocalDate?, val beesPerMinute: Int?, val countedOn: LocalDate?) {
    companion object {
        fun of(hiveId: String, logs: List<NativeLog>): NativeStrength {
            val own = logs.filter { it.hiveId == hiveId }
            val weight = own.filter { it.weightGrams != null }.maxByOrNull { it.date }
            val count = own.filter { it.beesPerMinute != null }.maxByOrNull { it.date }
            return NativeStrength(weight?.weightGrams, weight?.date, count?.beesPerMinute, count?.date)
        }
    }
}
