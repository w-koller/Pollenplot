package io.github.wkoller.pollenplot.domain.compost

import java.time.Instant
import java.time.LocalDate

/**
 * A compost bin or heap in the yard. [structureId] is the compost structure drawn on the Plot, when there is one, and
 * [turnEveryDays] how often the heap is turned while it works.
 */
data class CompostBin(
    val id: String,
    val name: String,
    val zoneId: String? = null,
    val structureId: String? = null,
    val turnEveryDays: Int = CompostStages.TURN_EVERY_DAYS,
    val notes: String = "",
    val createdAt: Instant = Instant.EPOCH,
)

/**
 * One fill of a bin, from the first scraps to the day it is emptied. [filledOn] is the day the user said it was full and
 * no more would be added, which starts the wait.
 */
data class CompostBatch(
    val id: String,
    val binId: String,
    val startedOn: LocalDate,
    val filledOn: LocalDate? = null,
    val emptiedOn: LocalDate? = null,
    val notes: String = "",
) {
    val isOpen: Boolean get() = emptiedOn == null
}

enum class CompostEventKind {
    /** Browns, such as dry leaves, shredded cardboard or straw. */
    BROWNS,

    /** Greens, such as food scraps, fresh grass clippings or green prunings. */
    GREENS,
    TURNED,
    WATERED,

    /** A thermometer reading in the middle of the heap, in [CompostEvent.tenthsC]. */
    TEMPERATURE,
}

/** Something done to a batch, or measured in it, on [date]. */
data class CompostEvent(
    val id: String,
    val batchId: String,
    val kind: CompostEventKind,
    val date: LocalDate,
    val tenthsC: Int? = null,
    val notes: String = "",
)

/** The three steps the user follows: fill the bin, wait while it works, then empty it. */
enum class CompostStep { FILL, WAIT, EMPTY }

/** What a batch is doing, as far as its days and readings tell. Each phase belongs to one [CompostStep]. */
enum class CompostPhase(val step: CompostStep) {
    /** Browns and greens are still going in, and the first microbes warm the heap (the mesophilic start). */
    FILLING(CompostStep.FILL),

    /** Full and warming up (mesophilic). */
    WARMING(CompostStep.WAIT),

    /** Hot (thermophilic): the heap is at its hottest and breaks down fastest. */
    HOT(CompostStep.WAIT),

    /** The heat is falling as the easiest food is used up. */
    COOLING(CompostStep.WAIT),

    /** Curing: at about the air's temperature, it matures slowly. */
    CURING(CompostStep.WAIT),

    /** Long enough that it may be ready: dark, crumbly, smelling of earth, with no scraps left to see. */
    READY(CompostStep.EMPTY),
    EMPTIED(CompostStep.EMPTY),
}

/** A job a batch is due for: turning it, or looking whether it is ready to empty. */
data class CompostTask(val binId: String, val batchId: String, val kind: Kind, val due: LocalDate) {
    enum class Kind { TURN, CHECK_READY }

    /** Remembered once told about, so each is told once while it stays due. */
    val key: String get() = "$batchId:$kind:$due"

    fun isDue(today: LocalDate): Boolean = !due.isAfter(today)
}

/**
 * What a batch is doing and what it is due for.
 *
 * The heap warms for a couple of days before heat-loving microbes take over above about 40 °C (Cornell Waste
 * Management Institute, https://compost.css.cornell.edu/microorg.html). A hot heap gives compost in six to eight
 * weeks (University of Maryland Extension, https://extension.umd.edu/resource/how-make-compost-home), so Pollenplot
 * suggests looking at it eight weeks after it was filled, and every two weeks after that until it is emptied. It is
 * ready when it is at the air's temperature, dark and crumbly, smells of earth and no scraps can be recognised (the
 * same page). Turning once a week or so helps (Government of South Australia,
 * https://www.environment.sa.gov.au/goodliving/posts/2019/05/guide-to-composting); a fortnight is the default here,
 * and each bin can set its own.
 *
 * Without a reading the phase is an estimate from the days since the batch was filled: warming for [WARMING_DAYS],
 * hot to half of the eight weeks, cooling to three quarters, then curing. That split is Pollenplot's reading of the
 * sources, which give the stages but not their lengths in a home bin. A reading from the last [READING_DAYS] days
 * comes first: [HOT_TENTHS_C] or more is hot, and anything cooler after the warming days is cooling, or curing once
 * the estimate has reached curing.
 */
object CompostStages {
    const val TURN_EVERY_DAYS = 14
    val TURN_CHOICES = listOf(7, 14, 21)
    const val WARMING_DAYS = 3L
    const val READY_AFTER_DAYS = 56L
    const val CHECK_AGAIN_DAYS = 14L
    const val HOT_TENTHS_C = 400
    const val READING_DAYS = 3L

    fun phase(batch: CompostBatch, events: List<CompostEvent>, today: LocalDate): CompostPhase {
        if (batch.emptiedOn != null) return CompostPhase.EMPTIED
        val filled = batch.filledOn ?: return CompostPhase.FILLING
        val days = java.time.temporal.ChronoUnit.DAYS.between(filled, today).coerceAtLeast(0)
        val estimate = when {
            days < WARMING_DAYS -> CompostPhase.WARMING
            days < READY_AFTER_DAYS / 2 -> CompostPhase.HOT
            days < READY_AFTER_DAYS * 3 / 4 -> CompostPhase.COOLING
            days < READY_AFTER_DAYS -> CompostPhase.CURING
            else -> CompostPhase.READY
        }
        val reading = events
            .filter { it.batchId == batch.id && it.kind == CompostEventKind.TEMPERATURE && it.tenthsC != null }
            .filter { !it.date.isAfter(today) && !it.date.isBefore(today.minusDays(READING_DAYS)) && !it.date.isBefore(filled) }
            .maxByOrNull { it.date }?.tenthsC
        return when {
            reading == null || estimate == CompostPhase.READY -> estimate
            reading >= HOT_TENTHS_C -> CompostPhase.HOT
            days < WARMING_DAYS -> CompostPhase.WARMING
            estimate == CompostPhase.CURING -> CompostPhase.CURING
            else -> CompostPhase.COOLING
        }
    }

    /** The day a batch was last turned, or else the day it was filled. */
    fun lastTurned(batch: CompostBatch, events: List<CompostEvent>): LocalDate? =
        events.filter { it.batchId == batch.id && it.kind == CompostEventKind.TURNED }.maxOfOrNull { it.date }
            ?.let { turned -> batch.filledOn?.let { maxOf(it, turned) } ?: turned }
            ?: batch.filledOn

    /**
     * The jobs an open, filled batch has from [today] up to [until]: a turn every [CompostBin.turnEveryDays] from its
     * last turn until it may be ready, and a look whether it is ready from eight weeks after filling, then every two
     * weeks. The first of each may already be due.
     */
    fun tasks(bin: CompostBin, batch: CompostBatch, events: List<CompostEvent>, today: LocalDate, until: LocalDate = today): List<CompostTask> {
        val filled = batch.filledOn ?: return emptyList()
        if (batch.emptiedOn != null) return emptyList()
        val readyFrom = filled.plusDays(READY_AFTER_DAYS)
        val tasks = mutableListOf<CompostTask>()
        val every = bin.turnEveryDays.coerceAtLeast(1).toLong()
        var turn = (lastTurned(batch, events) ?: filled).plusDays(every)
        while (turn.isBefore(readyFrom) && !turn.isAfter(until)) {
            tasks += CompostTask(bin.id, batch.id, CompostTask.Kind.TURN, turn)
            if (!turn.isAfter(today)) {
                // An overdue turn is one job, not one for each fortnight missed.
                turn = maxOf(turn, today).plusDays(every)
            } else {
                turn = turn.plusDays(every)
            }
        }
        // Each look that found it not ready yet moves the next one on by two weeks.
        var check = readyFrom
        while (check.plusDays(CHECK_AGAIN_DAYS) <= today) check = check.plusDays(CHECK_AGAIN_DAYS)
        while (!check.isAfter(until)) {
            tasks += CompostTask(bin.id, batch.id, CompostTask.Kind.CHECK_READY, check)
            check = check.plusDays(CHECK_AGAIN_DAYS)
        }
        return tasks.sortedBy { it.due }
    }

    /** The jobs due today or earlier, for the Today card and the morning's notification. */
    fun due(bin: CompostBin, batch: CompostBatch, events: List<CompostEvent>, today: LocalDate): List<CompostTask> =
        tasks(bin, batch, events, today).filter { it.isDue(today) }
}
