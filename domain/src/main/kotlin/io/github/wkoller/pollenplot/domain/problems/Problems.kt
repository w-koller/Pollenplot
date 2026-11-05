package io.github.wkoller.pollenplot.domain.problems

import io.github.wkoller.pollenplot.domain.season.Hemisphere
import io.github.wkoller.pollenplot.domain.season.MonthSet
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.LocalDate

enum class ProblemKind { PEST, DISEASE, DEFICIENCY }

/**
 * A pest, disease or deficiency as the catalogue in assets/kb/problems.json describes it, from the government page it
 * names. [fauna] is the animal's scientific name in the animal knowledge base, when it has a page.
 */
data class ProblemInfo(
    val key: String,
    val name: String,
    val kind: ProblemKind,
    val description: String,
    val advice: String,
    val sourceTitle: String,
    val sourceUrl: String,
    val fauna: String? = null,
)

@Serializable
internal data class ProblemJson(
    val key: String,
    val name: String,
    val kind: String,
    val description: String,
    val advice: String,
    val source: String,
    val url: String,
    val fauna: String? = null,
)

/** The problem catalogue. */
class ProblemCatalogue(val problems: List<ProblemInfo>) {
    private val byKey = problems.associateBy { it.key }

    fun problem(key: String): ProblemInfo? = byKey[key]

    companion object {
        val EMPTY = ProblemCatalogue(emptyList())

        fun parse(text: String): Pair<ProblemCatalogue, List<String>> {
            val problems = mutableListOf<String>()
            val raw = runCatching { Json.decodeFromString(ListSerializer(ProblemJson.serializer()), text) }
                .getOrElse { return EMPTY to listOf("problems.json: ${it.message}") }
            val list = raw.mapNotNull { p ->
                val where = "problems.json ${p.key}"
                val kind = ProblemKind.entries.firstOrNull { it.name.equals(p.kind, ignoreCase = true) }
                if (kind == null) problems += "$where: unknown kind \"${p.kind}\""
                if (!p.url.startsWith("https://")) problems += "$where: the source must be an https link"
                if (p.description.isBlank() || p.advice.isBlank()) problems += "$where: needs a description and advice"
                kind?.let { ProblemInfo(p.key, p.name, it, p.description, p.advice, p.source, p.url, p.fauna) }
            }
            list.groupBy { it.key }.filterValues { it.size > 1 }.keys.forEach { problems += "problems.json: $it appears twice" }
            return ProblemCatalogue(list) to problems
        }
    }
}

/** A problem seen on a plant: one from the catalogue by [problemKey], or the user's own [otherName]. */
data class PlantProblem(
    val id: String,
    val plantId: String,
    val problemKey: String?,
    val otherName: String?,
    val observedOn: LocalDate,
    /** A photo of it, as the app stores it. */
    val photo: String? = null,
    val notes: String = "",
    val resolvedOn: LocalDate? = null,
) {
    init {
        require(problemKey != null || !otherName.isNullOrBlank()) { "A problem needs a name" }
    }

    val open: Boolean get() = resolvedOn == null
}

/** How a problem was treated. None of these carry an amount: the app never suggests one. */
enum class TreatmentMethod(val spray: Boolean) {
    WHITE_OIL(true),
    SOAP_SPRAY(true),
    POTASSIUM_BICARBONATE(true),
    HOSE_OFF(false),
    HAND_REMOVE(false),
    PRUNE_OUT(false),
    TRAP(false),
    BENEFICIALS(false),
    OTHER_SPRAY(true),
    OTHER(false),
}

data class Treatment(
    val id: String,
    val problemId: String,
    val method: TreatmentMethod,
    /** The product the user used, in their words. */
    val productText: String,
    val appliedOn: LocalDate,
    val followUpOn: LocalDate?,
    val notes: String = "",
)

/** A treatment whose follow-up has come, on a problem still open. */
data class FollowUp(val problem: PlantProblem, val treatment: Treatment)

object ProblemTasks {
    /**
     * Pollenplot's choice of how long to wait before looking again at a treated problem. No source given here sets a
     * number, and the user can change it on each treatment.
     */
    const val FOLLOW_UP_DAYS = 7L

    /** The follow-ups due: the latest treatment of each open problem, once its follow-up day has come. */
    fun due(problems: List<PlantProblem>, treatments: List<Treatment>, today: LocalDate): List<FollowUp> {
        val latest = treatments.groupBy { it.problemId }.mapValues { (_, list) -> list.maxBy { it.appliedOn } }
        return problems.filter { it.open }.mapNotNull { problem ->
            latest[problem.id]?.takeIf { it.followUpOn != null && !it.followUpOn.isAfter(today) }?.let { FollowUp(problem, it) }
        }
    }
}

/** A plant as the spray check sees it: its flowering months (written for the south) and the days it was seen in flower. */
data class FlowerPlant(val title: String, val flowering: MonthSet?, val seenInFlower: List<LocalDate>)

/**
 * Which plants are likely in flower now, before a spray: a plant counts when this month is one of its flowering months
 * in the garden's hemisphere, or it was seen in flower in the last [SEEN_DAYS] days. BeeAware advises not to spray
 * flowers that bees visit except in the evening, with the spray dry before bees touch it (Managing the risk of honey
 * bee poisoning). [SEEN_DAYS] is Pollenplot's choice.
 */
object SprayCheck {
    const val SEEN_DAYS = 30L

    fun inFlower(plants: List<FlowerPlant>, today: LocalDate, hemisphere: Hemisphere): List<String> = plants.filter { plant ->
        val months = plant.flowering?.fromSouthern(hemisphere)
        (months != null && today.month in months) || plant.seenInFlower.any { !it.isAfter(today) && !it.isBefore(today.minusDays(SEEN_DAYS)) }
    }.map { it.title }.distinct()
}
