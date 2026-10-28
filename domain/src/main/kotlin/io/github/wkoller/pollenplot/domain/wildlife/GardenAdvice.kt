package io.github.wkoller.pollenplot.domain.wildlife

/** A garden pest seen in the yard, with the animals in the knowledge base that eat it. */
data class PestAdvice(val pest: SeenSpecies, val animal: Animal, val predators: List<Animal>)

/** What a year's sightings suggest doing for the yard: help with its pests, and habitat for the groups not seen. */
object GardenAdvice {
    /** The groups a backyard can easily do more for, so not seeing them is worth an idea. */
    val HABITAT_GROUPS = listOf(LifeGroup.BIRD, LifeGroup.INSECT, LifeGroup.FROG, LifeGroup.REPTILE)

    /** The garden pests seen in [year], the most often seen first, each with what eats it. */
    fun pests(year: Biodiversity, fauna: FaunaBase): List<PestAdvice> = year.species
        .mapNotNull { seen ->
            val animal = seen.animal?.takeIf { FaunaRole.PLANT_PEST in it.roles } ?: return@mapNotNull null
            PestAdvice(seen, animal, fauna.predatorsOf(animal))
        }
        .sortedByDescending { it.pest.sightings.size }

    /** The habitat groups with no sightings in [year]. */
    fun missing(year: Biodiversity): List<LifeGroup> = HABITAT_GROUPS.filter { it !in year.byGroup }
}
