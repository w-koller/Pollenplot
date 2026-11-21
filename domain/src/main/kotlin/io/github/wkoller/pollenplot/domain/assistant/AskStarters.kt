package io.github.wkoller.pollenplot.domain.assistant

import io.github.wkoller.pollenplot.domain.rhythms.BeeGroup
import io.github.wkoller.pollenplot.domain.season.Season
import io.github.wkoller.pollenplot.domain.wildlife.LifeGroup
import java.time.LocalDate
import java.time.Month

/** What a starter or follow-up asks, each with its own question in the app's strings. */
enum class StarterKind {
    // About the whole garden, from what it is doing now.
    HOT_DAY,
    FOLLOW_UP,
    VISITOR_ON_PLANT,
    VISITOR,
    FUNGUS,
    IN_FLOWER_ZONE,
    IN_FLOWER,
    GAP,
    UNFED,

    // About the whole garden, whatever it holds, for the season.
    SEASON_BEES,
    SEASON_BIRDS,
    SEASON_JOBS,
    FROGS,

    // About one plant or one kind of animal, and the follow-ups after an answer that drew on it.
    PLANT_FLOWERING,
    PLANT_BEES,
    PLANT_PRUNING,
    PLANT_SPOT,
    ZONE_BEES,
    ANIMAL_NEEDS,
    ANIMAL_FOOD,
    ANIMAL_PLANTS,
}

/**
 * A question offered to the user: its [kind], the [names] its words use in order, and the [about] records of the
 * garden it is about, which the model is given first so it can answer. [group], [month], [date] and [season] fill the
 * questions that name them.
 */
data class Starter(
    val kind: StarterKind,
    val names: List<String> = emptyList(),
    val about: List<String> = emptyList(),
    val group: BeeGroup? = null,
    val month: Month? = null,
    val date: LocalDate? = null,
    val season: Season? = null,
)

/**
 * The questions the Ask screen offers before anything is asked. For the whole garden they come from what it is doing
 * now, the most pressing first: a hot day ahead for the stingless bees and a problem due a second look, then a new
 * visitor, what is in flower and a month the bees go without. Questions for the season follow, so an empty garden
 * still has something to ask. None of them is a question the app answers itself, and the app drops any that a
 * user's own name turns into one.
 */
object AskStarters {
    const val SHOWN = 3

    /** Every question for the garden, in the order they are offered. */
    fun forGarden(pulse: GardenPulse): List<Starter> = buildList {
        pulse.hotDays.firstOrNull()?.let { add(Starter(StarterKind.HOT_DAY, date = it.date)) }
        pulse.followUps.firstOrNull { it.plantName != null }?.let { add(Starter(StarterKind.FOLLOW_UP, listOf(it.problem, it.plantName!!), listOfNotNull(it.plantId))) }
        pulse.visitors.firstOrNull()?.let { visitor ->
            val about = listOfNotNull(visitor.sightingId, visitor.plantId)
            when {
                visitor.group == LifeGroup.FUNGUS -> add(Starter(StarterKind.FUNGUS, listOf(visitor.name), about))
                visitor.plantName != null -> add(Starter(StarterKind.VISITOR_ON_PLANT, listOf(visitor.name, visitor.plantName), about))
                else -> add(Starter(StarterKind.VISITOR, listOf(visitor.name), about))
            }
        }
        inFlower(pulse.inFlower)?.let(::add)
        pulse.gaps.firstOrNull()?.let { gap ->
            add(if (gap.month == null) Starter(StarterKind.UNFED, group = gap.group) else Starter(StarterKind.GAP, group = gap.group, month = gap.month))
        }
        add(Starter(StarterKind.SEASON_BEES, season = pulse.season))
        add(Starter(StarterKind.SEASON_BIRDS, season = pulse.season))
        add(Starter(StarterKind.SEASON_JOBS, season = pulse.season))
        add(Starter(StarterKind.FROGS))
    }

    /** The questions about one plant in the garden, called [name]. */
    fun forPlant(name: String): List<Starter> = listOf(StarterKind.PLANT_FLOWERING, StarterKind.PLANT_BEES, StarterKind.PLANT_PRUNING, StarterKind.PLANT_SPOT).map { Starter(it, listOf(name)) }

    /** The questions about one kind of animal, called [name]. */
    fun forAnimal(name: String): List<Starter> = listOf(StarterKind.ANIMAL_NEEDS, StarterKind.ANIMAL_FOOD, StarterKind.ANIMAL_PLANTS).map { Starter(it, listOf(name)) }

    /**
     * The [SHOWN] questions on page [offset] of [all], going round to the start again, so "More ideas" always shows
     * something different while there is more.
     */
    fun page(all: List<Starter>, offset: Int): List<Starter> {
        if (all.size <= SHOWN) return all
        val start = Math.floorMod(offset * SHOWN, all.size)
        return (0 until SHOWN).map { all[(start + it) % all.size] }
    }

    /** Whether there are more questions than one page shows. */
    fun hasMore(all: List<Starter>): Boolean = all.size > SHOWN

    /** What is in flower, asked about the zone with the most of it, pinned to those plants. */
    private fun inFlower(plants: List<PulsePlant>): Starter? {
        if (plants.isEmpty()) return null
        val zone = plants.mapNotNull { it.zoneName }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        return if (zone != null) {
            Starter(StarterKind.IN_FLOWER_ZONE, listOf(zone), plants.filter { it.zoneName == zone }.take(PINNED).map { it.id })
        } else {
            Starter(StarterKind.IN_FLOWER, about = plants.take(PINNED).map { it.id })
        }
    }

    private const val PINNED = 4
}

/**
 * The questions offered after an answer about the garden: for each record the answer drew on, the next thing worth
 * asking about it, pinned to that record. A plant gets when it flowers and which bees visit it, a zone what to plant
 * there for bees, and a sighting what that animal needs.
 */
object AskFollowUps {
    const val SHOWN = 3

    fun after(cited: List<CitedRecord>): List<Starter> {
        val byRecord = cited.distinctBy { it.id }.map { record ->
            val pin = listOf(record.id)
            when (record.kind) {
                RecordKind.PLANT -> listOf(Starter(StarterKind.PLANT_BEES, listOf(record.title), pin), Starter(StarterKind.PLANT_FLOWERING, listOf(record.title), pin))
                RecordKind.ZONE -> listOf(Starter(StarterKind.ZONE_BEES, listOf(record.title), pin))
                RecordKind.SIGHTING -> listOf(Starter(StarterKind.ANIMAL_NEEDS, listOf(record.title), pin))
            }
        }
        // One question for each record first, so the follow-ups cover what the answer was about.
        val rounds = (0 until (byRecord.maxOfOrNull { it.size } ?: 0)).flatMap { round -> byRecord.mapNotNull { it.getOrNull(round) } }
        return rounds.take(SHOWN)
    }
}
