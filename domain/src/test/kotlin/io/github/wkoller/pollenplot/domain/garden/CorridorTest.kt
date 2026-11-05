package io.github.wkoller.pollenplot.domain.garden

import io.github.wkoller.pollenplot.domain.plants.GrowthForm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CorridorTest {
    private val front = Zone("front", "Front", ZoneKind.GARDEN_BED, ZoneRect(0, 0, 300, 200), mulched = true)
    private val back = Zone("back", "Back", ZoneKind.GARDEN_BED, ZoneRect(600, 600, 200, 200), rocksAndLogs = true, denseShrubs = true)
    private val side = Zone("side", "Side", ZoneKind.POND, ZoneRect(300, 0, 100, 100))
    private val bath = WaterFeature("bath", "back", WaterKind.BIRD_BATH, cleanedOn = LocalDate.of(2026, 10, 1))

    @Test
    fun `growth forms make the three layers`() {
        assertEquals(HabitatLayer.CANOPY, Corridor.layer(GrowthForm.TREE))
        assertEquals(HabitatLayer.MIDSTOREY, Corridor.layer(GrowthForm.SHRUB))
        assertEquals(HabitatLayer.GROUNDCOVER, Corridor.layer(GrowthForm.STRAPPY))
        assertNull("A mown lawn is not a layer", Corridor.layer(GrowthForm.LAWN))
        assertNull(Corridor.layer(null))
        assertEquals(setOf(HabitatLayer.CANOPY, HabitatLayer.GROUNDCOVER), Corridor.layers(listOf(GrowthForm.TREE, GrowthForm.FERN, GrowthForm.VEGETABLE)))
    }

    @Test
    fun `every layer earns the layered habitat badge`() {
        val tree = PlantFacts(null, emptyList(), GrowthForm.TREE)
        val shrub = PlantFacts(null, emptyList(), GrowthForm.SHRUB)
        val grass = PlantFacts(null, emptyList(), GrowthForm.GRASS)
        assertEquals(listOf(ZoneBadge.LAYERED_HABITAT), ZoneBadges.of(listOf(tree, shrub, grass)))
        assertEquals(emptyList<ZoneBadge>(), ZoneBadges.of(listOf(tree, grass)))
    }

    @Test
    fun `the nearest water is measured between zones`() {
        assertEquals("Zones that touch are no distance apart", 0, Corridor.distanceCm(front.rect, side.rect))
        assertEquals(300, Corridor.distanceCm(side.rect, ZoneRect(300, 400, 100, 100)))
        // From the front's corner at (300, 200) to the back's at (600, 600): 300 across and 400 down.
        assertEquals(500, Corridor.distanceCm(front.rect, back.rect))

        val habitat = Corridor.habitat(front, listOf(GrowthForm.SHRUB), listOf(front, back, side), listOf(bath))
        assertEquals(500, habitat.nearestWaterCm)
        assertEquals("back", habitat.nearestWaterZoneId)
        assertEquals(setOf(GroundFeature.LEAF_LITTER), habitat.features)
        assertEquals(listOf(HabitatLayer.CANOPY, HabitatLayer.GROUNDCOVER), habitat.missingLayers)
        assertEquals(2, habitat.present)

        val wet = Corridor.habitat(back, emptyList(), listOf(front, back, side), listOf(bath))
        assertEquals(0, wet.nearestWaterCm)
        assertNull(wet.nearestWaterZoneId)
        assertEquals(3, wet.present)
        assertEquals(7, ZoneHabitat.POSSIBLE)

        assertNull("No water in the yard", Corridor.habitat(front, emptyList(), listOf(front, back), emptyList()).nearestWaterCm)
    }

    @Test
    fun `the yard is summed up from its zones`() {
        val zones = listOf(front, back)
        val habitats = listOf(
            Corridor.habitat(front, listOf(GrowthForm.TREE, GrowthForm.SHRUB, GrowthForm.GROUNDCOVER), zones, listOf(bath)),
            Corridor.habitat(back, listOf(GrowthForm.SHRUB), zones, listOf(bath)),
        )
        val summary = Corridor.summary(habitats)
        assertEquals(HabitatLayer.entries.toSet(), summary.layers)
        assertEquals(GroundFeature.entries.toSet(), summary.features)
        assertEquals(1, summary.waterFeatures)
        assertEquals(1, summary.layeredZones)
        assertTrue(summary.zones == 2)
    }
}
