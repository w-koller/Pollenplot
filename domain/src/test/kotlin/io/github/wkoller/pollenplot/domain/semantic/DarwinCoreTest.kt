package io.github.wkoller.pollenplot.domain.semantic

import io.github.wkoller.pollenplot.domain.place.GardenPlace
import io.github.wkoller.pollenplot.domain.plants.AuState
import io.github.wkoller.pollenplot.domain.wildlife.Animal
import io.github.wkoller.pollenplot.domain.wildlife.Behaviour
import io.github.wkoller.pollenplot.domain.wildlife.LifeGroup
import io.github.wkoller.pollenplot.domain.wildlife.Sighting
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class DarwinCoreTest {
    private val brisbane = ZoneId.of("Australia/Brisbane")
    private val kookaburra = Animal(
        name = "Dacelo novaeguineae", common = listOf("Laughing kookaburra"), group = LifeGroup.BIRD, family = "Alcedinidae", nativeStates = setOf(AuState.QLD),
        roles = emptySet(), plants = emptyList(), plantNote = null, habitat = null, hazard = null, hazardNote = null, rules = emptyList(), description = "A bird.",
        synonyms = emptyList(),
    )

    private fun sighting(id: String, at: LocalDateTime, notes: String, photo: String? = null) = Sighting(
        id = id, speciesName = "Dacelo novaeguineae", label = "Laughing kookaburra", group = LifeGroup.BIRD, zoneId = null, plantId = null,
        behaviour = Behaviour.CALLING, count = 2, photoId = photo, thumbnail = null, seenAt = at.atZone(brisbane).toInstant(), notes = notes,
    )

    @Test
    fun `sightings become a Darwin Core table with the rounded place`() {
        val csv = DarwinCore.csv(
            listOf(
                sighting("b", LocalDateTime.of(2026, 10, 28, 7, 30), "On the fence, \"laughing\", at dawn", photo = "p1") to kookaburra,
                sighting("a", LocalDateTime.of(2026, 10, 1, 6, 15), "") to kookaburra,
            ),
            GardenPlace.at(-27.4987, 152.9934), "Queensland", brisbane,
        )
        val lines = csv.trimEnd().split("\r\n")
        assertEquals(DarwinCore.COLUMNS.joinToString(","), lines[0])
        assertEquals(
            "The earliest first",
            "urn:uuid:a,HumanObservation,2026-10-01T06:15:00+10:00,Dacelo novaeguineae,Laughing kookaburra,2,calling,present,Australia,AU,Queensland,-27.50,152.99,1000,",
            lines[1],
        )
        assertEquals(
            "urn:uuid:b,MachineObservation,2026-10-28T07:30:00+10:00,Dacelo novaeguineae,Laughing kookaburra,2,calling,present,Australia,AU,Queensland,-27.50,152.99,1000,\"On the fence, \"\"laughing\"\", at dawn\"",
            lines[2],
        )
    }
}
