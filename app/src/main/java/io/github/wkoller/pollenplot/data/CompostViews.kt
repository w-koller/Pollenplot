package io.github.wkoller.pollenplot.data

import io.github.wkoller.pollenplot.domain.compost.CompostBatch
import io.github.wkoller.pollenplot.domain.compost.CompostBin
import io.github.wkoller.pollenplot.domain.compost.CompostEvent
import io.github.wkoller.pollenplot.domain.compost.CompostPhase
import io.github.wkoller.pollenplot.domain.compost.CompostStages
import io.github.wkoller.pollenplot.domain.compost.CompostTask
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate

/**
 * A bin with its zone's name, its open batch, what that batch is doing, and its jobs: those due now and the next one
 * of each kind.
 */
data class CompostBinView(
    val bin: CompostBin,
    val zoneName: String?,
    /** The fill under way, or null between fills. */
    val batch: CompostBatch?,
    val phase: CompostPhase?,
    /** The open batch's events, the latest first. */
    val events: List<CompostEvent>,
    val due: List<CompostTask>,
    val next: List<CompostTask>,
    /** How many fills of the bin were emptied. */
    val emptied: Int,
)

/** A compost job due, with its bin's name, for the Today card, the morning's notification and the calendar. */
data class CompostDue(val binId: String, val binName: String, val task: CompostTask) {
    val key: String get() = task.key
}

/** Compost bins as the screens show them, from [CompostStages]. */
class CompostViews(repository: CompostRepository, views: GardenViews, private val clock: Clock) {
    fun today(): LocalDate = LocalDate.now(clock)

    val bins: Flow<List<CompostBinView>> = combine(repository.bins, repository.batches, repository.events, views.zones) { bins, batches, events, zones ->
        val names = zones.associate { it.zone.id to it.zone.name }
        val today = today()
        bins.map { bin ->
            val mine = batches.filter { it.binId == bin.id }
            val open = mine.lastOrNull { it.isOpen }
            val ofOpen = open?.let { b -> events.filter { it.batchId == b.id } }.orEmpty()
            val ahead = open?.let { CompostStages.tasks(bin, it, ofOpen, today, today.plusDays(CompostStages.READY_AFTER_DAYS)) }.orEmpty()
            CompostBinView(
                bin = bin,
                zoneName = bin.zoneId?.let(names::get),
                batch = open,
                phase = open?.let { CompostStages.phase(it, ofOpen, today) },
                events = ofOpen.sortedByDescending { it.date },
                due = ahead.filter { it.isDue(today) },
                next = ahead.filter { !it.isDue(today) }.groupBy { it.kind }.values.map { it.first() }.sortedBy { it.due },
                emptied = mine.count { !it.isOpen },
            )
        }
    }

    fun bin(id: String): Flow<CompostBinView?> = bins.map { list -> list.firstOrNull { it.bin.id == id } }

    /** Every compost job due today or earlier. */
    val due: Flow<List<CompostDue>> = bins.map { list -> list.flatMap { view -> view.due.map { CompostDue(view.bin.id, view.bin.name, it) } } }

    /** Every compost job from today to [until], for the calendar. */
    fun tasks(until: LocalDate): Flow<List<CompostDue>> = combine(bins, kotlinx.coroutines.flow.flowOf(until)) { list, end ->
        val today = today()
        list.flatMap { view ->
            val batch = view.batch ?: return@flatMap emptyList()
            CompostStages.tasks(view.bin, batch, view.events, today, end).map { CompostDue(view.bin.id, view.bin.name, it) }
        }
    }
}
