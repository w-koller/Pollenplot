package io.github.wkoller.pollenplot.domain.garden

import java.time.LocalDate

enum class PotMaterial { TERRACOTTA, PLASTIC, GLAZED, FABRIC, METAL, TIMBER, CONCRETE }

enum class PotMix { PREMIUM, NATIVE, CITRUS, CACTUS, ORCHID, OTHER }

/** The pot a plant grows in. Sizes are in millimetres and millilitres, and any of them may be unknown. */
data class PlantPot(
    val id: String,
    val plantId: String,
    val diameterMm: Int? = null,
    val volumeMl: Long? = null,
    val material: PotMaterial? = null,
    val mix: PotMix? = null,
    /** The mix's brand or what it was blended from, in the user's words. */
    val mixNote: String = "",
) {
    init {
        require(diameterMm == null || diameterMm > 0) { "A pot has a size" }
        require(volumeMl == null || volumeMl > 0) { "A pot holds something" }
    }
}

/** Something done to a plant on a day, kept in its history. */
enum class PlantEventKind {
    POTTED_UP,
    REPOTTED,
    ROOT_PRUNED,

    /** Fresh mix on top of the pot, which refreshes its surface but does not replace the mix. */
    TOP_DRESSED,

    /** Water run through the pot to wash out the salts that fertiliser and tap water leave. */
    FLUSHED,
    ;

    /** The pot got fresh mix all through, which starts both the flush and the repot count again. */
    val freshMix: Boolean get() = this == POTTED_UP || this == REPOTTED
}

data class PlantEvent(val id: String, val plantId: String, val kind: PlantEventKind, val date: LocalDate, val notes: String = "")

enum class PotTaskKind { FLUSH, REPOT }

/** A pot job and the day it falls due. */
data class PotTask(val kind: PotTaskKind, val due: LocalDate) {
    fun isDue(today: LocalDate): Boolean = !due.isAfter(today)
}

/**
 * When a potted plant's pot is due a flush or a look at repotting.
 *
 * - Flushing: the University of New Hampshire Cooperative Extension recommends leaching pots "every four months or
 *   so" to stop salts building up (Fertilizing Houseplants, 2018), so a flush falls due [FLUSH_EVERY_MONTHS] months after the
 *   last flush or fresh mix.
 * - Repotting: Scotts' Australian guide suggests considering repotting every two to three years, sooner when roots are
 *   crowded or the mix has broken down, and says topping up does not fix compacted mix (Repotting keeps plants healthy,
 *   Evergreen Garden Care Australia). A repot check falls due [REPOT_AFTER_YEARS] years after the plant was last potted up or
 *   repotted, at the start of that range.
 *
 * Without a recorded event or a planting date there is nothing to count from, so nothing falls due.
 */
object PotCare {
    const val FLUSH_EVERY_MONTHS = 4L
    const val REPOT_AFTER_YEARS = 2L

    fun tasks(events: List<PlantEvent>, plantedOn: LocalDate?): List<PotTask> {
        val lastFreshMix = events.filter { it.kind.freshMix }.maxOfOrNull { it.date } ?: plantedOn
        val lastFlush = listOfNotNull(events.filter { it.kind == PlantEventKind.FLUSHED }.maxOfOrNull { it.date }, lastFreshMix).maxOrNull()
        return listOfNotNull(
            lastFlush?.let { PotTask(PotTaskKind.FLUSH, it.plusMonths(FLUSH_EVERY_MONTHS)) },
            lastFreshMix?.let { PotTask(PotTaskKind.REPOT, it.plusYears(REPOT_AFTER_YEARS)) },
        )
    }

    /** Whether a plant is potted: it has a pot record, or it grows in a zone of pots. */
    fun isPotted(pot: PlantPot?, inPotZone: Boolean): Boolean = pot != null || inPotZone
}
