package io.github.wkoller.pollenplot.domain.feeding

import io.github.wkoller.pollenplot.domain.plants.Fertiliser
import io.github.wkoller.pollenplot.domain.season.Hemisphere
import io.github.wkoller.pollenplot.domain.season.MonthSet
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.Month
import java.time.temporal.ChronoUnit

/** A feed given on a day to one or more zones, with the rate the user typed (the app never suggests one). */
data class Feed(
    val id: String,
    /** A built-in product's key, or the id of one the user added. */
    val productKey: String,
    val date: LocalDate,
    val zoneIds: Set<String>,
    val rateText: String = "",
    val notes: String = "",
)

/**
 * When a group of plants is fed: in which months, written for the Southern Hemisphere, and how many days apart. Each
 * entry names the page it comes from.
 */
data class FeedSchedule(val group: String, val months: MonthSet, val everyDays: Int, val sourceTitle: String, val sourceUrl: String)

@Serializable
internal data class FeedScheduleJson(
    val group: String,
    val months: List<String>,
    @SerialName("every_days") val everyDays: Int,
    val source: String,
    val url: String,
)

/** The feeding schedules from assets/kb/feeding.json, found for a plant by its species, then genus, then family. */
class FeedingGuide(val schedules: List<FeedSchedule>) {
    private val byGroup = schedules.associateBy { it.group.lowercase() }

    fun forPlant(speciesName: String?, genus: String?, family: String?): FeedSchedule? =
        listOfNotNull(speciesName, genus, family).firstNotNullOfOrNull { byGroup[it.lowercase()] }

    companion object {
        val EMPTY = FeedingGuide(emptyList())
        private val MONTHS = Month.entries.associateBy { it.name.take(3).lowercase() }

        fun parse(text: String): Pair<FeedingGuide, List<String>> {
            val problems = mutableListOf<String>()
            val raw = runCatching { Json.decodeFromString(ListSerializer(FeedScheduleJson.serializer()), text) }
                .getOrElse { return EMPTY to listOf("feeding.json: ${it.message}") }
            val schedules = raw.mapNotNull { s ->
                val months = s.months.map { name -> MONTHS[name.lowercase()].also { if (it == null) problems += "feeding.json ${s.group}: unknown month $name" } }
                if (s.everyDays !in 7..365) problems += "feeding.json ${s.group}: every_days must be from 7 to 365"
                if (!s.url.startsWith("https://")) problems += "feeding.json ${s.group}: the source must be an https link"
                if (months.any { it == null } || s.everyDays !in 7..365) null else FeedSchedule(s.group, MonthSet.of(months.filterNotNull()), s.everyDays, s.source, s.url)
            }
            return FeedingGuide(schedules) to problems
        }
    }
}

/** A zone due a feed: the plants that want one and the day it fell due. */
data class FeedDue(val zoneId: String, val plants: List<String>, val due: LocalDate) {
    val key: String get() = "$zoneId:$due"
}

/** A plant in a zone as feeding sees it. */
data class FeedPlant(val title: String, val zoneId: String, val schedule: FeedSchedule?)

/**
 * Which zones are due a feed today. A zone is due when one of its plants has a schedule whose months include this
 * month in the garden's hemisphere, and the zone's last feed (of anything but a conditioner) was at least that many
 * days ago. A zone never fed is due from the first day of the month.
 */
object FeedPlanner {
    fun due(plants: List<FeedPlant>, feeds: List<Feed>, kinds: Map<String, ProductKind>, today: LocalDate, hemisphere: Hemisphere): List<FeedDue> =
        plants.filter { it.schedule != null }.groupBy { it.zoneId }.mapNotNull { (zoneId, inZone) ->
            val wanting = inZone.filter { today.month in it.schedule!!.months.fromSouthern(hemisphere) }
            if (wanting.isEmpty()) return@mapNotNull null
            val every = wanting.minOf { it.schedule!!.everyDays }
            val last = lastFed(zoneId, feeds, kinds)
            val dueOn = last?.plusDays(every.toLong()) ?: today.withDayOfMonth(1)
            if (dueOn.isAfter(today)) null else FeedDue(zoneId, wanting.map { it.title }.distinct(), dueOn)
        }

    /** The last day a zone got a feed that was not just a conditioner. */
    fun lastFed(zoneId: String, feeds: List<Feed>, kinds: Map<String, ProductKind>): LocalDate? =
        feeds.filter { zoneId in it.zoneIds && kinds[it.productKey] != ProductKind.CONDITIONER }.maxOfOrNull { it.date }

    /** Days since a zone's last feed of any kind, and that feed, for the "Fed 12 days ago" line. */
    fun lastFeed(zoneId: String, feeds: List<Feed>): Feed? = feeds.filter { zoneId in it.zoneIds }.maxByOrNull { it.date }

    fun daysSince(day: LocalDate, today: LocalDate): Long = ChronoUnit.DAYS.between(day, today)
}

/**
 * A warning before a feed with a lot of phosphorus goes on plants that are harmed by it. An Australian Native Plants
 * Society article (Mulqueeny, Using Fertilisers in the Native Garden, 2006) takes every Proteaceae genus to be
 * phosphorus-sensitive, and calls a fertiliser of about 4% phosphorus safe for them, so a product above
 * [SAFE_P_BP] warns about the zones' plants that resolve to [Fertiliser.LOW_PHOSPHORUS]. A product without a known
 * analysis does not warn.
 */
object PhosphorusGuard {
    const val SAFE_P_BP = 400

    fun sensitive(npk: Npk?, plants: List<Pair<String, Fertiliser?>>): List<String> =
        if (npk == null || npk.pBp <= SAFE_P_BP) emptyList() else plants.filter { it.second == Fertiliser.LOW_PHOSPHORUS }.map { it.first }.distinct()
}

/**
 * The user's own reminder to give a conditioner before hot days: on when a day of [HOT_TENTHS_C] or more is forecast
 * within [WITHIN_DAYS] days and no conditioner was given in the last [SINCE_DAYS] days. These numbers are Pollenplot's
 * choice; no source given here says a conditioner helps plants through heat.
 */
object ConditionerBeforeHeat {
    const val HOT_TENTHS_C = 350
    const val WITHIN_DAYS = 3L
    const val SINCE_DAYS = 14L

    /** The first hot day ahead and its top, or null when no reminder is needed. */
    fun remind(forecast: List<Pair<LocalDate, Int?>>, feeds: List<Feed>, kinds: Map<String, ProductKind>, today: LocalDate): Pair<LocalDate, Int>? {
        val hot = forecast.filter { (day, max) -> max != null && max >= HOT_TENTHS_C && !day.isBefore(today) && !day.isAfter(today.plusDays(WITHIN_DAYS)) }
            .minByOrNull { it.first } ?: return null
        val recent = feeds.any { kinds[it.productKey] == ProductKind.CONDITIONER && !it.date.isBefore(today.minusDays(SINCE_DAYS)) }
        return if (recent) null else hot.first to hot.second!!
    }
}
