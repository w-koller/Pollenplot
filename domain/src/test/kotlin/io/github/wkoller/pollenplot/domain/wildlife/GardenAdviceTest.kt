package io.github.wkoller.pollenplot.domain.wildlife

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class GardenAdviceTest {
    private val brisbane = ZoneId.of("Australia/Brisbane")

    private fun animal(name: String, group: LifeGroup, roles: Set<FaunaRole>, eats: List<String> = emptyList()) = Animal(
        name = name, common = listOf(name), group = group, family = "Family", nativeStates = emptySet(), roles = roles,
        plants = emptyList(), plantNote = null, habitat = null, hazard = null, hazardNote = null, rules = emptyList(),
        description = "An animal.", synonyms = emptyList(), eats = eats,
    )

    private val aphid = animal("Myzus persicae", LifeGroup.INSECT, setOf(FaunaRole.PLANT_PEST))
    private val snail = animal("Cornu aspersum", LifeGroup.MOLLUSC, setOf(FaunaRole.PLANT_PEST))
    private val fauna = FaunaBase(
        listOf(
            aphid,
            snail,
            animal("Coccinella transversalis", LifeGroup.INSECT, setOf(FaunaRole.PEST_CONTROLLER), eats = listOf("Myzus persicae")),
            animal("Ischiodon scutellaris", LifeGroup.INSECT, setOf(FaunaRole.PEST_CONTROLLER, FaunaRole.POLLINATOR), eats = listOf("Myzus")),
            animal("Manorina melanocephala", LifeGroup.BIRD, emptySet()),
        ),
    )

    private var next = 0

    private fun seen(name: String, group: LifeGroup, day: Int) = Sighting(
        id = "s${next++}", speciesName = name, label = name, group = group, zoneId = null, plantId = null, behaviour = null,
        count = 1, photoId = null, thumbnail = null, seenAt = LocalDateTime.of(2026, 10, day, 9, 0).atZone(brisbane).toInstant(), notes = "",
    )

    @Test
    fun `pests come with what eats them, by name or genus`() {
        val sightings = listOf(
            seen("Cornu aspersum", LifeGroup.MOLLUSC, 1),
            seen("Myzus persicae", LifeGroup.INSECT, 2),
            seen("Myzus persicae", LifeGroup.INSECT, 3),
            seen("Manorina melanocephala", LifeGroup.BIRD, 4),
        )
        val pests = GardenAdvice.pests(Biodiversity.of(sightings, fauna, 2026, brisbane), fauna)
        assertEquals("The aphid was seen most", listOf("Myzus persicae", "Cornu aspersum"), pests.map { it.animal.name })
        assertEquals(listOf("Coccinella transversalis", "Ischiodon scutellaris"), pests.first().predators.map { it.name })
        assertTrue("Nothing in the knowledge base eats snails here", pests.last().predators.isEmpty())
    }

    @Test
    fun `groups not seen get habitat ideas`() {
        val year = Biodiversity.of(listOf(seen("Manorina melanocephala", LifeGroup.BIRD, 4)), fauna, 2026, brisbane)
        assertEquals(listOf(LifeGroup.INSECT, LifeGroup.FROG, LifeGroup.REPTILE), GardenAdvice.missing(year))
        assertEquals(GardenAdvice.HABITAT_GROUPS, GardenAdvice.missing(Biodiversity.of(emptyList(), fauna, 2026, brisbane)))
    }
}
