package io.github.wkoller.pollenplot.domain.garden

import io.github.wkoller.pollenplot.domain.season.Hemisphere
import io.github.wkoller.pollenplot.domain.season.MonthSet
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.Month

/** When a plant is pruned, as its source puts it. */
enum class PruneTiming {
    /** In the months the source names, in winter. */
    WINTER,

    /** In the month after each flowering season ends, worked out from the plant's own flowering months. */
    AFTER_FLOWERING,

    /** After the crop is picked. The source gives no months, so nothing falls due. */
    AFTER_HARVEST,
}

/** A pruning time for a species, genus or family, with the page it comes from. [months] are Southern Hemisphere months. */
data class PruneSchedule(val group: String, val timing: PruneTiming, val months: MonthSet?, val sourceTitle: String, val sourceUrl: String)

@Serializable
internal data class PruneScheduleJson(
    val group: String,
    val timing: String,
    val months: List<String> = emptyList(),
    val source: String,
    val url: String,
)

/** The pruning times from assets/kb/pruning.json, found for a plant by its species, then genus, then family. */
class PruningGuide(val schedules: List<PruneSchedule>) {
    private val byGroup = schedules.associateBy { it.group.lowercase() }

    fun forPlant(speciesName: String?, genus: String?, family: String?): PruneSchedule? =
        listOfNotNull(speciesName, genus, family).firstNotNullOfOrNull { byGroup[it.lowercase()] }

    companion object {
        val EMPTY = PruningGuide(emptyList())
        private val MONTHS = Month.entries.associateBy { it.name.take(3).lowercase() }

        fun parse(text: String): Pair<PruningGuide, List<String>> {
            val problems = mutableListOf<String>()
            val raw = runCatching { Json.decodeFromString(ListSerializer(PruneScheduleJson.serializer()), text) }
                .getOrElse { return EMPTY to listOf("pruning.json: ${it.message}") }
            val schedules = raw.mapNotNull { s ->
                val timing = PruneTiming.entries.firstOrNull { it.name.lowercase() == s.timing }
                if (timing == null) problems += "pruning.json ${s.group}: unknown timing ${s.timing}"
                val months = s.months.map { name -> MONTHS[name.lowercase()].also { if (it == null) problems += "pruning.json ${s.group}: unknown month $name" } }
                if (timing == PruneTiming.WINTER && months.isEmpty()) problems += "pruning.json ${s.group}: winter pruning names its months"
                if (timing != PruneTiming.WINTER && months.isNotEmpty()) problems += "pruning.json ${s.group}: only winter pruning names months"
                if (!s.url.startsWith("https://")) problems += "pruning.json ${s.group}: the source must be an https link"
                if (s.source.isBlank()) problems += "pruning.json ${s.group}: the source needs a title"
                if (timing == null || months.any { it == null }) {
                    null
                } else {
                    PruneSchedule(s.group, timing, months.takeIf { it.isNotEmpty() }?.let { MonthSet.of(it.filterNotNull()) }, s.source, s.url)
                }
            }
            val repeated = schedules.groupBy { it.group.lowercase() }.filterValues { it.size > 1 }.keys
            repeated.forEach { problems += "pruning.json: $it is listed more than once" }
            return PruningGuide(schedules) to problems
        }
    }
}

/**
 * When a plant is due a prune: in its pruning months, until a prune is recorded. A user's own pruning months come
 * first. After flowering means the month after each run of flowering months ends, Pollenplot's reading of "after
 * flowering"; a plant in flower all year, or with no flowering months, has no such month.
 */
object PruneTasks {
    /** The months to prune in, in the garden's [hemisphere], or null when nothing gives them. */
    fun months(schedule: PruneSchedule?, own: MonthSet?, flowering: MonthSet?, hemisphere: Hemisphere): MonthSet? {
        val southern = own ?: when (schedule?.timing) {
            PruneTiming.WINTER -> schedule.months
            PruneTiming.AFTER_FLOWERING -> flowering?.let(::afterFlowering)?.takeUnless { it.isEmpty }
            PruneTiming.AFTER_HARVEST, null -> null
        }
        return southern?.fromSouthern(hemisphere)
    }

    /** The month after each run of [flowering] months ends. */
    fun afterFlowering(flowering: MonthSet): MonthSet =
        if (flowering == MonthSet.ALL) MonthSet.NONE else MonthSet.of(Month.entries.filter { it in flowering && it.plus(1) !in flowering }.map { it.plus(1) })

    /** The first day of the run of [months] that [today] falls in, or null when [today] is outside them. */
    fun runStart(months: MonthSet, today: LocalDate): LocalDate? {
        if (today.month !in months) return null
        var start = today.withDayOfMonth(1)
        repeat(11) {
            val before = start.minusMonths(1)
            if (before.month !in months) return start
            start = before
        }
        return start
    }

    /** Whether a plant pruned in [months] is due today: in a pruning month, with no prune recorded since its run began. */
    fun due(months: MonthSet?, events: List<PlantEvent>, today: LocalDate): Boolean {
        val start = months?.let { runStart(it, today) } ?: return false
        return events.none { it.kind.pruning && !it.date.isBefore(start) && !it.date.isAfter(today) }
    }
}
