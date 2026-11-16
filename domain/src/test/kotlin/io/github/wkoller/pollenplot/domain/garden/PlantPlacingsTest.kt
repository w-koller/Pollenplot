package io.github.wkoller.pollenplot.domain.garden

import org.junit.Assert.assertEquals
import org.junit.Test

class PlantPlacingsTest {
    @Test
    fun onePlantInManyZonesCountedZoneByZone() {
        val made = GardenPlant.plantings(listOf("a", "b", "a"), each = false, total = 1, counts = mapOf("a" to 2, "b" to 3))
        assertEquals(listOf(GardenPlant.Planting(listOf("a", "b"), mapOf("a" to 2, "b" to 3), 5)), made)
    }

    @Test
    fun onePlantInManyZonesGivenATotal() {
        assertEquals(listOf(GardenPlant.Planting(listOf("a", "b"), emptyMap(), 7)), GardenPlant.plantings(listOf("a", "b"), each = false, total = 7))
    }

    @Test
    fun aPlantInEachZoneTakesThatZonesCount() {
        val made = GardenPlant.plantings(listOf("a", "b"), each = true, total = 1, counts = mapOf("a" to 4))
        assertEquals(listOf(GardenPlant.Planting(listOf("a"), emptyMap(), 4), GardenPlant.Planting(listOf("b"), emptyMap(), 1)), made)
    }

    @Test
    fun oneZoneOrNoneIsOneRecordWithItsTotal() {
        assertEquals(listOf(GardenPlant.Planting(listOf("a"), emptyMap(), 3)), GardenPlant.plantings(listOf("a"), each = true, total = 3))
        assertEquals(listOf(GardenPlant.Planting(emptyList(), emptyMap(), 1)), GardenPlant.plantings(emptyList(), each = false, total = 1))
    }

    @Test
    fun aZoneCountsItsOwnOrAnEvenShareOfTheTotal() {
        val counted = GardenPlant("p", "Salvia rosmarinus", null, listOf("a", "b"), quantity = 5, counts = mapOf("a" to 2, "b" to 3))
        assertEquals(2, counted.countIn("a"))
        assertEquals(3, counted.countIn("b"))
        val total = GardenPlant("p", "Salvia rosmarinus", null, listOf("a", "b"), quantity = 5)
        assertEquals("Five shared between two zones rounds to three", 3, total.countIn("a"))
        assertEquals("Each zone has at least one", 1, GardenPlant("p", "Salvia rosmarinus", null, listOf("a", "b", "c")).countIn("c"))
    }
}
