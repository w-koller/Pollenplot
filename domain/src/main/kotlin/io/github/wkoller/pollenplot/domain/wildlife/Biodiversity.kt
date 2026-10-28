package io.github.wkoller.pollenplot.domain.wildlife

import io.github.wkoller.pollenplot.domain.plants.AuState
import io.github.wkoller.pollenplot.domain.plants.KnowledgeBase
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId

/**
 * One kind of animal or fungus seen in the yard in a year, with its sightings, the most recent first. Sightings are of
 * the same kind when they name the same animal in the knowledge base, or, for an animal it does not have, the same
 * name.
 */
data class SeenSpecies(
    val key: String,
    val animal: Animal?,
    val label: String,
    val group: LifeGroup,
    val sightings: List<Sighting>,
    /** Whether this year is the first time it was seen in the yard. */
    val isNew: Boolean,
) {
    val title: String get() = animal?.displayName ?: label

    val latest: Sighting get() = sightings.first()

    /** The latest cut-out of it, for its tile. */
    val thumbnail: String? get() = sightings.firstNotNullOfOrNull { it.thumbnail }

    /** The plants it was seen on, the most used first. */
    val plantIds: List<String>
        get() = sightings.mapNotNull { it.plantId }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
}

/** How many kinds of animal were seen in one month of the year, and how many times. */
data class MonthSeen(val month: Month, val species: Int, val sightings: Int)

/** What to know about an animal seen in the yard: what the law asks in the garden's state, or that it is venomous. */
data class AnimalNotice(val species: SeenSpecies, val rules: List<AnimalRule>, val venomous: Boolean) {
    val mustReport: Boolean get() = rules.any { it.report }
}

/** The wildlife seen in the yard in one year, for the Wildlife tab. */
class Biodiversity(val year: Int, val species: List<SeenSpecies>, val months: List<MonthSeen>) {
    val nativeCount: Int get() = species.count { it.animal?.isNative == true }

    val newCount: Int get() = species.count { it.isNew }

    /** The kinds seen in each group, the groups in the order the app lists them. */
    val byGroup: Map<LifeGroup, List<SeenSpecies>> = LifeGroup.entries.associateWith { group -> species.filter { it.group == group } }.filterValues { it.isNotEmpty() }

    /** The animals seen this year that the law in [state] asks about or that are venomous, those to report first. */
    fun notices(state: AuState?): List<AnimalNotice> = species
        .mapNotNull { seen ->
            val animal = seen.animal ?: return@mapNotNull null
            val rules = animal.rulesIn(state)
            val venomous = animal.hazard == Hazard.VENOMOUS
            if (rules.isEmpty() && !venomous) null else AnimalNotice(seen, rules, venomous)
        }
        .sortedWith(compareBy<AnimalNotice>({ !it.mustReport }, { it.rules.isEmpty() }).thenByDescending { it.species.latest.seenAt })

    companion object {
        /** The kind a sighting is of: its animal's name in the knowledge base, or the name it was recorded under. */
        fun keyOf(sighting: Sighting, fauna: FaunaBase?): String {
            val name = sighting.speciesName ?: sighting.label
            return fauna?.animal(name)?.name ?: KnowledgeBase.normalise(name)
        }

        /** The years with sightings, the latest first. */
        fun years(sightings: List<Sighting>, zone: ZoneId): List<Int> = sightings.map { it.seenAt.atZone(zone).year }.distinct().sortedDescending()

        /**
         * The kinds seen in [year] in the garden's time [zone], from [sightings] of every year, each group's most often
         * seen first.
         */
        fun of(sightings: List<Sighting>, fauna: FaunaBase?, year: Int, zone: ZoneId): Biodiversity {
            val keyed = sightings.map { sighting ->
                Keyed(keyOf(sighting, fauna), fauna?.animal(sighting.speciesName ?: sighting.label), sighting, sighting.seenAt.atZone(zone).toLocalDate())
            }
            val firstYear = keyed.groupBy { it.key }.mapValues { (_, list) -> list.minOf { it.date.year } }
            val inYear = keyed.filter { it.date.year == year }
            val species = inYear.groupBy { it.key }
                .map { (key, list) ->
                    val sorted = list.sortedByDescending { it.sighting.seenAt }
                    val animal = sorted.first().animal
                    SeenSpecies(
                        key = key,
                        animal = animal,
                        label = sorted.first().sighting.label,
                        group = animal?.group ?: sorted.first().sighting.group,
                        sightings = sorted.map { it.sighting },
                        isNew = firstYear[key] == year,
                    )
                }
                .sortedWith(compareBy<SeenSpecies> { it.group.ordinal }.thenByDescending { it.sightings.size }.thenByDescending { it.latest.seenAt }.thenBy { it.title.lowercase() })
            val months = Month.entries.map { month ->
                val inMonth = inYear.filter { it.date.month == month }
                MonthSeen(month, inMonth.map { it.key }.distinct().size, inMonth.size)
            }
            return Biodiversity(year, species, months)
        }
    }

    private class Keyed(val key: String, val animal: Animal?, val sighting: Sighting, val date: LocalDate)
}
