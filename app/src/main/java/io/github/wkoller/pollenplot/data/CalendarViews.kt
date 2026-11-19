package io.github.wkoller.pollenplot.data

import io.github.wkoller.pollenplot.data.prefs.SettingsStore
import io.github.wkoller.pollenplot.domain.bees.honey.HiveTaskKind
import io.github.wkoller.pollenplot.domain.compost.CompostTask
import io.github.wkoller.pollenplot.domain.garden.PotTaskKind
import io.github.wkoller.pollenplot.domain.hotels.HotelCare
import io.github.wkoller.pollenplot.domain.irrigation.WeatherDay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.LocalDate

/** The kinds of job the calendar shows, each with its own icon and words. */
enum class CalendarKind { WATER, HIVE, POT, FEED, PROBLEM, PRUNE, HOTEL, COMPOST }

/**
 * One job on one day of the calendar: [subject] is what it is for (a plant, zone, line, hive, hotel or bin), [target]
 * the record a press opens, and [detail] what kind of job it is within its [kind], such as a flush or a repot.
 */
data class CalendarItem(
    val date: LocalDate,
    val kind: CalendarKind,
    val subject: String,
    val target: String?,
    val detail: String? = null,
    /** More words, such as a watering's minutes or a follow-up's plant. */
    val extra: String? = null,
    /** The names a job is for, such as the plants a feed is for. */
    val names: List<String> = emptyList(),
)

/** A day of the calendar: its jobs and, within the forecast, its weather. */
data class CalendarDay(val date: LocalDate, val items: List<CalendarItem>, val weather: WeatherDay?)

/**
 * Every job in the garden from today on, from the same rules that decide what is due today: watering in the week the
 * forecast covers, hive checks, pot jobs, feeds, follow-ups, prunes, hotel clean-outs and compost. A job already due
 * is shown on today. Days ahead are a forecast: they assume each job is done on its day.
 */
class CalendarViews(
    private val bees: BeeViews,
    private val pots: PotViews,
    private val feeds: FeedViews,
    private val problems: ProblemViews,
    private val harvests: HarvestViews,
    private val water: WaterViews,
    private val weather: Flow<List<WeatherDay>>,
    private val compost: CompostViews,
    private val settings: SettingsStore,
    private val clock: Clock,
) {
    fun today(): LocalDate = LocalDate.now(clock)

    /** The jobs from today to [until], each on its day, or on today when it is overdue. */
    fun items(until: LocalDate): Flow<List<CalendarItem>> {
        val beeItems = combine(bees.hives, bees.hotels, settings.settings) { hives, hotels, current ->
            val today = today()
            hives.flatMap { view ->
                view.tasks.filter { !it.due.isAfter(until) }.map { task ->
                    CalendarItem(maxOf(task.due, today), CalendarKind.HIVE, view.hive.name, view.hive.id, task.kind.name)
                }
            } + hotels.mapNotNull { view ->
                HotelCare.nextClean(view.state, today, until, current.hemisphere)?.let { CalendarItem(it, CalendarKind.HOTEL, view.hotel.name, view.hotel.id) }
            }
        }
        val plantItems = combine(pots.upcoming, feeds.upcoming(until), problems.upcoming, harvests.upcoming(until)) { potJobs, feedJobs, followUps, prunes ->
            val today = today()
            potJobs.filter { !it.task.due.isAfter(until) }.map {
                CalendarItem(maxOf(it.task.due, today), CalendarKind.POT, it.title, it.plantId, it.task.kind.name)
            } + feedJobs.map {
                CalendarItem(it.due.due, CalendarKind.FEED, it.zoneName, it.due.zoneId, names = it.due.plants)
            } + followUps.filter { !it.due.isAfter(until) }.map {
                CalendarItem(maxOf(it.due, today), CalendarKind.PROBLEM, it.name, it.problemId, extra = it.plantTitle)
            } + prunes.map { CalendarItem(it.since, CalendarKind.PRUNE, it.title, it.plantId) }
        }
        val otherItems = combine(water.state, compost.tasks(until)) { state, bins ->
            state.lines.flatMap { line ->
                line.week.filter { !it.date.isAfter(until) }.map { CalendarItem(it.date, CalendarKind.WATER, line.line.name, null, extra = it.minutes?.toString()) }
            } + state.byHand.flatMap { hand ->
                hand.week.filter { !it.date.isAfter(until) }.map { CalendarItem(it.date, CalendarKind.WATER, hand.zone.name, null) }
            } + bins.map {
                CalendarItem(maxOf(it.task.due, today()), CalendarKind.COMPOST, it.binName, it.binId, it.task.kind.name)
            }
        }
        return combine(beeItems, plantItems, otherItems) { a, b, c -> (a + b + c).sortedWith(compareBy({ it.date }, { it.kind.ordinal }, { it.subject })) }
    }

    /** The days from [from] to [until], each with its jobs and its weather where the forecast covers it. */
    fun days(from: LocalDate, until: LocalDate): Flow<List<CalendarDay>> = combine(items(until), weather) { items, days ->
        val byDate = items.groupBy { it.date }
        val weatherBy = days.associateBy { it.date }
        generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(until) }
            .map { CalendarDay(it, byDate[it].orEmpty(), weatherBy[it]) }.toList()
    }

    companion object {
        /** Whether a compost item is a turn. */
        fun isTurn(item: CalendarItem): Boolean = item.detail == CompostTask.Kind.TURN.name

        fun potKind(item: CalendarItem): PotTaskKind? = PotTaskKind.entries.firstOrNull { it.name == item.detail }

        fun hiveKind(item: CalendarItem): HiveTaskKind? = HiveTaskKind.entries.firstOrNull { it.name == item.detail }
    }
}
