package io.github.wkoller.pollenplot.domain.wildlife

import io.github.wkoller.pollenplot.domain.plants.AuState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.Month
import java.time.ZoneId

class BiodiversityTest {
    private val brisbane = ZoneId.of("Australia/Brisbane")

    private fun animal(name: String, common: String, group: LifeGroup, native: Boolean = true, hazard: Hazard? = null, rules: List<AnimalRule> = emptyList()) = Animal(
        name = name, common = listOf(common), group = group, family = "Family", nativeStates = if (native) setOf(AuState.QLD) else emptySet(),
        roles = emptySet(), plants = emptyList(), plantNote = null, habitat = null, hazard = hazard, hazardNote = hazard?.let { "Please keep away." },
        rules = rules, description = "An animal.", synonyms = emptyList(),
    )

    private val fireAntRule = AnimalRule(setOf(AuState.QLD), "Please report it.", report = true, source = "https://www.fireants.org.au/")
    private val toadRule = AnimalRule(setOf(AuState.QLD), "Please do not move it.", report = false, source = "https://www.business.qld.gov.au/")
    private val fauna = FaunaBase(
        listOf(
            animal("Manorina melanocephala", "Noisy miner", LifeGroup.BIRD),
            animal("Rhinella marina", "Cane toad", LifeGroup.FROG, native = false, rules = listOf(toadRule)),
            animal("Litoria fallax", "Eastern sedge frog", LifeGroup.FROG),
            animal("Solenopsis invicta", "Red imported fire ant", LifeGroup.INSECT, native = false, hazard = Hazard.STINGS, rules = listOf(fireAntRule)),
            animal("Latrodectus hasselti", "Redback spider", LifeGroup.SPIDER, hazard = Hazard.VENOMOUS),
        ),
    )

    private var next = 0

    private fun seen(name: String, year: Int, month: Int, day: Int = 10, group: LifeGroup = LifeGroup.BIRD, speciesName: String? = name, plantId: String? = null, thumbnail: String? = null) = Sighting(
        id = "s${next++}", speciesName = speciesName, label = name, group = group, zoneId = null, plantId = plantId, behaviour = null, count = 1,
        photoId = null, thumbnail = thumbnail, seenAt = LocalDateTime.of(year, month, day, 7, 30).atZone(brisbane).toInstant(), notes = "",
    )

    @Test
    fun `counts each kind once and how many are native`() {
        val sightings = listOf(
            seen("Manorina melanocephala", 2026, 3),
            seen("Manorina melanocephala", 2026, 9, plantId = "rosemary", thumbnail = "t/miner.webp"),
            seen("Manorina melanocephala", 2026, 10, plantId = "rosemary"),
            seen("Litoria fallax", 2026, 1, group = LifeGroup.FROG),
            seen("Rhinella marina", 2026, 2, group = LifeGroup.FROG),
            seen("Manorina melanocephala", 2025, 6),
        )
        val year = Biodiversity.of(sightings, fauna, 2026, brisbane)
        assertEquals(3, year.species.size)
        assertEquals("The sedge frog and the miner are native, the cane toad is not", 2, year.nativeCount)
        assertEquals(listOf(LifeGroup.BIRD, LifeGroup.FROG), year.byGroup.keys.toList())
        val miner = year.species.first()
        assertEquals(3, miner.sightings.size)
        assertEquals("Its latest cut-out", "t/miner.webp", miner.thumbnail)
        assertEquals(listOf("rosemary"), miner.plantIds)
        assertFalse("The miner was first seen in 2025", miner.isNew)
        assertEquals(2, year.newCount)
        assertEquals(listOf(2026, 2025), Biodiversity.years(sightings, brisbane))
    }

    @Test
    fun `a name the knowledge base does not have is still one kind`() {
        val sightings = listOf(
            seen("Blue-banded bee", 2026, 11, group = LifeGroup.INSECT, speciesName = null),
            seen("blue-banded  Bee", 2026, 12, group = LifeGroup.INSECT, speciesName = null),
        )
        val year = Biodiversity.of(sightings, fauna, 2026, brisbane)
        assertEquals(1, year.species.size)
        assertEquals("The latest name it was recorded under", "blue-banded  Bee", year.species.single().title)
        assertEquals("Not in the knowledge base, so not counted as native", 0, year.nativeCount)
    }

    @Test
    fun `months use the garden's time zone`() {
        // 11 pm on 31 December in UTC is already New Year's Day in Brisbane.
        val newYear = seen("Manorina melanocephala", 2026, 1).copy(seenAt = LocalDateTime.of(2025, 12, 31, 23, 0).atZone(ZoneId.of("UTC")).toInstant())
        val year = Biodiversity.of(listOf(newYear, seen("Litoria fallax", 2026, 1, group = LifeGroup.FROG)), fauna, 2026, brisbane)
        assertEquals(MonthSeen(Month.JANUARY, species = 2, sightings = 2), year.months.first())
        assertEquals(12, year.months.size)
        assertTrue(year.months.drop(1).all { it.sightings == 0 })
    }

    @Test
    fun `animals to report come before venomous ones`() {
        val sightings = listOf(
            seen("Latrodectus hasselti", 2026, 10, group = LifeGroup.SPIDER),
            seen("Rhinella marina", 2026, 9, group = LifeGroup.FROG),
            seen("Solenopsis invicta", 2026, 8, group = LifeGroup.INSECT),
            seen("Manorina melanocephala", 2026, 10),
        )
        val year = Biodiversity.of(sightings, fauna, 2026, brisbane)
        val notices = year.notices(AuState.QLD)
        assertEquals(listOf("Solenopsis invicta", "Rhinella marina", "Latrodectus hasselti"), notices.map { it.species.key })
        assertTrue(notices.first().mustReport)
        assertTrue(notices.last().venomous)
        assertEquals("Outside Queensland only the redback's venom matters", listOf("Latrodectus hasselti"), year.notices(AuState.WA).map { it.species.key })
    }
}
