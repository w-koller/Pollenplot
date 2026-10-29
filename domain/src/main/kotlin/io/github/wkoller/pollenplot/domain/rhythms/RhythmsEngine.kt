package io.github.wkoller.pollenplot.domain.rhythms

import io.github.wkoller.pollenplot.domain.plants.AuState
import io.github.wkoller.pollenplot.domain.plants.Forage
import io.github.wkoller.pollenplot.domain.plants.GrowthForm
import io.github.wkoller.pollenplot.domain.plants.Species
import io.github.wkoller.pollenplot.domain.plants.Sun
import io.github.wkoller.pollenplot.domain.season.MonthSet
import java.time.Month

/** The three groups of bees the calendar keeps apart. Their forage is scored separately and never pooled. */
enum class BeeGroup { HONEY, STINGLESS, SOLITARY }

/** How much a month offers a group, from nothing, a gap, to plenty. */
enum class ForageLevel { NONE, LITTLE, SOME, PLENTY }

/**
 * A plant in the garden as the calendar sees it: when it flowers, as the knowledge base expects for the garden's
 * hemisphere and as photos have shown, what it offers each group of bees, and how many there are.
 */
data class RhythmPlant(
    val id: String,
    val title: String,
    val speciesName: String?,
    val form: GrowthForm?,
    val quantity: Int,
    val flowering: MonthSet,
    val peak: MonthSet,
    val seenFlowering: MonthSet,
    val forage: Forage?,
) {
    /** The months it flowers in, expected or seen. */
    val allFlowering: MonthSet get() = flowering + seenFlowering
}

/** One group's year: its points and level for each month, January first. */
data class Lane(val group: BeeGroup, val points: List<Int>, val levels: List<ForageLevel>) {
    fun level(month: Month): ForageLevel = levels[month.ordinal]

    /** The months with nothing for the group, when something in the garden feeds it at some time of year. */
    val gaps: List<Month> get() = if (points.all { it == 0 }) emptyList() else Month.entries.filter { levels[it.ordinal] == ForageLevel.NONE }

    /** Whether anything in the garden feeds the group at all. */
    val fed: Boolean get() = points.any { it > 0 }
}

/** A plant that would fill a group's gap, with the gap months it flowers in and a zone whose sun suits it. */
data class Suggestion(val species: Species, val fills: List<Month>, val nativeHere: Boolean, val zoneName: String?)

/**
 * Pollen Rhythms' calendar: what the garden's flowers offer each group of bees through the year, Pollenplot's own
 * measure, explained on the screen.
 *
 * - Honey bees forage over kilometres, so mass-flowering trees and shrubs count most. Each flowering plant adds its
 *   honey bee score, doubled for a tree or shrub, times the number of plants, up to three.
 * - Stingless and solitary bees forage close to home, so variety counts most. Each kind of flowering plant adds its
 *   score for the group once; a resin source adds one for stingless bees, and flowers that need buzz pollination add
 *   one for solitary bees.
 *
 * A month with no points is a gap, 1 to 3 points is a little, 4 to 7 is some, and 8 or more is plenty.
 */
object RhythmsEngine {
    const val SOME_FROM = 4
    const val PLENTY_FROM = 8
    private const val MOST_COUNTED = 3

    fun lanes(plants: List<RhythmPlant>): List<Lane> = BeeGroup.entries.map { group ->
        val points = Month.entries.map { month -> plants.filter { month in it.allFlowering }.sumOf { points(it, group) } }
        Lane(group, points, points.map(::level))
    }

    fun level(points: Int): ForageLevel = when {
        points <= 0 -> ForageLevel.NONE
        points < SOME_FROM -> ForageLevel.LITTLE
        points < PLENTY_FROM -> ForageLevel.SOME
        else -> ForageLevel.PLENTY
    }

    /** What a plant adds to a group's month while it flowers. */
    fun points(plant: RhythmPlant, group: BeeGroup): Int {
        val forage = plant.forage ?: return 0
        return when (group) {
            BeeGroup.HONEY -> {
                val score = forage.honeyBest
                if (score == 0) 0 else score * (if (plant.form == GrowthForm.TREE || plant.form == GrowthForm.SHRUB) 2 else 1) * plant.quantity.coerceAtMost(MOST_COUNTED)
            }
            BeeGroup.STINGLESS -> {
                val score = maxOf(forage.stinglessNectar, forage.stinglessPollen)
                if (score == 0 && forage.stinglessResin == 0) 0 else score + (if (forage.stinglessResin > 0) 1 else 0)
            }
            BeeGroup.SOLITARY -> {
                val score = maxOf(forage.solitaryNectar, forage.solitaryPollen)
                if (score == 0) 0 else score + (if (forage.buzz) 1 else 0)
            }
        }
    }

    /** A species' score for a group, from 0 to 3, as the knowledge base gives it. */
    fun score(forage: Forage?, group: BeeGroup): Int = when {
        forage == null -> 0
        group == BeeGroup.HONEY -> forage.honeyBest
        group == BeeGroup.STINGLESS -> maxOf(forage.stinglessNectar, forage.stinglessPollen, forage.stinglessResin)
        else -> maxOf(forage.solitaryNectar, forage.solitaryPollen)
    }

    /**
     * Plants that would fill [gaps] for [group]: species not in the garden that flower in a gap and score at least 2
     * for the group, leaving out weeds. Natives of the garden's state come first, then those filling the most gap
     * months, then those a zone's sun suits.
     */
    fun suggestions(
        group: BeeGroup,
        gaps: List<Month>,
        candidates: List<CandidatePlant>,
        inGarden: Set<String>,
        state: AuState?,
        zones: List<Pair<String, Sun?>>,
        limit: Int = 3,
    ): List<Suggestion> {
        if (gaps.isEmpty()) return emptyList()
        return candidates.asSequence()
            .filter { it.species.name !in inGarden && !it.weed && score(it.forage, group) >= 2 }
            .mapNotNull { candidate ->
                val fills = gaps.filter { it in candidate.flowering }
                if (fills.isEmpty()) return@mapNotNull null
                val zone = zones.firstOrNull { (_, sun) -> sun != null && candidate.sun?.contains(sun) == true }?.first
                Suggestion(candidate.species, fills, state != null && state in candidate.species.nativeStates, zone)
            }
            .sortedWith(compareByDescending<Suggestion> { it.nativeHere }.thenByDescending { it.fills.size }.thenByDescending { it.zoneName != null }.thenBy { it.species.displayName })
            .take(limit)
            .toList()
    }
}

/** A species the calendar could suggest, with its flowering for the garden's hemisphere and whether it is a weed there. */
data class CandidatePlant(val species: Species, val flowering: MonthSet, val forage: Forage?, val sun: Set<Sun>?, val weed: Boolean)
