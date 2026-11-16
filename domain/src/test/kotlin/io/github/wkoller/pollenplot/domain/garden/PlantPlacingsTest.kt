package io.github.wkoller.pollenplot.domain.garden

import org.junit.Test
import org.junit.Assert.assertEquals

class PlantPlacingsTest {
    @Test
    fun onePlantGrowsInEveryZoneChosen() {
        assertEquals(listOf(listOf("a", "b")), GardenPlant.placings(listOf("a", "b", "a"), each = false))
    }

    @Test
    fun aPlantInEachZoneIsARecordForEach() {
        assertEquals(listOf(listOf("a"), listOf("b")), GardenPlant.placings(listOf("a", "b"), each = true))
    }

    @Test
    fun oneZoneOrNoneIsOneRecordEitherWay() {
        assertEquals(listOf(listOf("a")), GardenPlant.placings(listOf("a"), each = true))
        assertEquals(listOf(emptyList<String>()), GardenPlant.placings(emptyList(), each = true))
    }

    @Test
    fun thePlantCountsItsNumberInEachZone() {
        assertEquals(6, GardenPlant("p", "Salvia rosmarinus", null, listOf("a", "b"), quantity = 3).total)
        assertEquals(3, GardenPlant("p", "Salvia rosmarinus", null, quantity = 3).total)
    }
}
