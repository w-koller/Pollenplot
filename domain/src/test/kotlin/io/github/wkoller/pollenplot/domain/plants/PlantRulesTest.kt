package io.github.wkoller.pollenplot.domain.plants

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlantRulesTest {
    private val proteaceae = GroupProfile("Proteaceae", null, null, CareProfile(fertiliser = Fertiliser.LOW_PHOSPHORUS, fertiliserNote = "No phosphorus.", description = "Proteas."))
    private val solanaceae = GroupProfile("Solanaceae", null, null, CareProfile(toxicPets = Toxicity.MILD, toxicNote = "Leaves are toxic.", description = "Nightshades."))
    private val grevillea = GroupProfile(
        "Grevillea", "Proteaceae", GrowthForm.SHRUB,
        CareProfile(sun = setOf(Sun.FULL_SUN), water = WaterNeed.LOW, drought = 2, wildlife = Wildlife(2, 1, false, 3, 1, 0), description = "Grevilleas."),
    )
    private val robusta = Species(
        "Grevillea robusta", "Grevillea", "Proteaceae", listOf("Silky oak"), GrowthForm.TREE, setOf(AuState.QLD, AuState.NSW), false, emptyList(),
        CareProfile(water = WaterNeed.VERY_LOW, description = "A tall tree."),
    )
    private val tomato = Species(
        "Solanum lycopersicum", "Solanum", "Solanaceae", listOf("Tomato"), GrowthForm.VEGETABLE, emptySet(), false, emptyList(),
        CareProfile(
            water = WaterNeed.HIGH, edible = "fruit", description = "Tomato.",
            forage = Forage(0, 1, 0, 1, 0, 0, 3, buzz = true),
        ),
    )
    private val basil = Species(
        "Ocimum basilicum", "Ocimum", "Lamiaceae", listOf("Basil"), GrowthForm.HERB, emptySet(), false, emptyList(),
        CareProfile(description = "Basil."),
    )
    private val kb = KnowledgeBase(
        1, listOf(robusta, tomato, basil), listOf(grevillea), listOf(proteaceae, solanaceae),
        listOf(
            CompanionPair("Solanum lycopersicum", "Ocimum basilicum", CompanionRelation.GOOD, "Basil helps.", Evidence.TRADITIONAL),
            CompanionPair("Grevillea", "Brassica", CompanionRelation.AVOID, "Fertiliser clash.", Evidence.SOME_EVIDENCE),
        ),
    )

    @Test
    fun `species facts win, then the genus, then the family`() {
        val resolved = CareResolver.resolve(kb, robusta)
        assertEquals(WaterNeed.VERY_LOW, resolved.care.water)
        assertEquals(FactLevel.SPECIES, resolved.levelOf(CareField.WATER))
        assertEquals(setOf(Sun.FULL_SUN), resolved.care.sun)
        assertEquals(FactLevel.GENUS, resolved.levelOf(CareField.SUN))
        assertEquals(Fertiliser.LOW_PHOSPHORUS, resolved.care.fertiliser)
        assertEquals("No phosphorus.", resolved.care.fertiliserNote)
        assertEquals(FactLevel.FAMILY, resolved.levelOf(CareField.FERTILISER))
        assertEquals("A tall tree.", resolved.care.description)
        assertNull(resolved.levelOf(CareField.FROST))
        assertTrue(resolved.usesFallback)
    }

    @Test
    fun `a plant known only by its genus gets the genus and family facts`() {
        val resolved = CareResolver.resolveGenus(kb, "Grevillea")!!
        assertEquals(WaterNeed.LOW, resolved.care.water)
        assertEquals(FactLevel.GENUS, resolved.levelOf(CareField.WATER))
        assertEquals(FactLevel.FAMILY, resolved.levelOf(CareField.FERTILISER))
        assertNull(CareResolver.resolveGenus(kb, "Nonexistent"))
    }

    @Test
    fun `eco roles follow the plant's facts and put safety first`() {
        val tomatoRoles = EcoRoles.of(tomato, CareResolver.resolve(kb, tomato).care, AuState.QLD)
        assertEquals(listOf(EcoRole.TOXIC_TO_PETS, EcoRole.NATIVE_BEE_FAVOURITE, EcoRole.EDIBLE, EcoRole.HIGH_THIRST), tomatoRoles)

        val oak = CareResolver.resolve(kb, robusta).care
        assertEquals(listOf(EcoRole.BIRD_FEEDER, EcoRole.LOCAL_NATIVE), EcoRoles.of(robusta, oak, AuState.QLD).filter { it in setOf(EcoRole.BIRD_FEEDER, EcoRole.LOCAL_NATIVE) })
        assertFalse(EcoRole.LOCAL_NATIVE in EcoRoles.of(robusta, oak, AuState.WA))
        assertTrue(EcoRole.LOCAL_NATIVE in EcoRoles.of(robusta, oak, state = null))
        assertFalse("Very low water without drought tolerance is not drought hardy", EcoRole.DROUGHT_HARDY in EcoRoles.of(robusta, oak.copy(drought = 1), AuState.QLD))
        assertTrue(EcoRole.DROUGHT_HARDY in EcoRoles.of(robusta, oak.copy(drought = 3), AuState.QLD))

        val flagged = EcoRoles.of(robusta, oak, AuState.QLD, weedRisk = true, overwatering = true)
        assertEquals(EcoRole.WEED_RISK, flagged[0])
        assertEquals(EcoRole.OVERWATERING_RISK, flagged[1])
    }

    @Test
    fun `companions name the matching plants in the garden`() {
        val forTomato = Companions.forSpecies(kb, tomato, listOf(tomato, basil))
        assertEquals(1, forTomato.size)
        assertEquals("Ocimum basilicum", forTomato[0].otherName)
        assertEquals(listOf(basil), forTomato[0].inGarden)

        val forBasil = Companions.forSpecies(kb, basil, emptyList())
        assertEquals("Solanum lycopersicum", forBasil[0].otherName)
        assertTrue(forBasil[0].inGarden.isEmpty())

        val forOak = Companions.forSpecies(kb, robusta, listOf(robusta))
        assertEquals(CompanionRelation.AVOID, forOak.single().pair.relation)
        assertTrue("A genus-level pairing covers each species in the genus", Companions.covers("Grevillea", robusta))
        assertTrue(Companions.covers("Proteaceae", robusta))
    }
}
