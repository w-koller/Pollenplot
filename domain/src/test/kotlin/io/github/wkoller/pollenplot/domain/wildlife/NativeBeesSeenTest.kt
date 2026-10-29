package io.github.wkoller.pollenplot.domain.wildlife

import io.github.wkoller.pollenplot.domain.plants.AuState
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class NativeBeesSeenTest {
    private val brisbane = ZoneId.of("Australia/Brisbane")

    private fun animal(name: String, common: String, family: String, native: Boolean = true, group: LifeGroup = LifeGroup.INSECT) = Animal(
        name = name, common = listOf(common), group = group, family = family, nativeStates = if (native) setOf(AuState.QLD) else emptySet(),
        roles = emptySet(), plants = emptyList(), plantNote = null, habitat = null, hazard = null, hazardNote = null, rules = emptyList(),
        description = "An animal.", synonyms = emptyList(),
    )

    private val blueBanded = animal("Amegilla cingulata", "Blue-banded bee", "Apidae")
    private val sugarbag = animal("Tetragonula carbonaria", "Sugarbag bee", "Apidae")
    private val honeyBee = animal("Apis mellifera", "European honey bee", "Apidae", native = false)
    private val hoverfly = animal("Melangyna viridiceps", "Common hover fly", "Syrphidae")

    private var next = 0

    private fun seen(animal: Animal, year: Int, month: Int) = Sighting(
        id = "s${next++}", speciesName = animal.name, label = animal.displayName, group = animal.group, zoneId = null, plantId = null, behaviour = null,
        count = 1, photoId = null, thumbnail = null, seenAt = LocalDateTime.of(year, month, 10, 9, 0).atZone(brisbane).toInstant(), notes = "",
    ) to animal

    @Test
    fun `only native bees seen this year are counted`() {
        val sightings = listOf(
            seen(blueBanded, 2026, 10), seen(blueBanded, 2026, 11), seen(sugarbag, 2026, 9), seen(sugarbag, 2025, 12),
            seen(honeyBee, 2026, 10), seen(hoverfly, 2026, 10),
        )
        val bees = NativeBeesSeen.of(sightings, 2026, brisbane)
        assertEquals(listOf("Amegilla cingulata" to 2, "Tetragonula carbonaria" to 1), bees.map { it.key to it.sightings })
        assertEquals(sightings[1].first.seenAt, bees.first().lastSeen)
    }
}
