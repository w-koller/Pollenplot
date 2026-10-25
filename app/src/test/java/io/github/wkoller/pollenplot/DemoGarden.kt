package io.github.wkoller.pollenplot

import io.github.wkoller.pollenplot.data.GardenRepository
import io.github.wkoller.pollenplot.domain.garden.ZoneKind
import io.github.wkoller.pollenplot.domain.garden.ZoneRect
import io.github.wkoller.pollenplot.domain.plants.Soil
import io.github.wkoller.pollenplot.domain.plants.Sun

/** A small backyard for tests and screenshots: a native strip, a citrus patch, a hive corner and a veg patch. */
object DemoGarden {
    data class Ids(val front: String, val citrus: String, val hive: String, val veg: String, val lemon: String, val grevillea: String)

    suspend fun seed(garden: GardenRepository): Ids {
        val front = garden.addZone("Front Native Strip", ZoneKind.GARDEN_BED)
        val citrus = garden.addZone("Back Citrus Patch", ZoneKind.ORCHARD)
        val hive = garden.addZone("Hive Corner", ZoneKind.HIVE_CORNER)
        val veg = garden.addZone("Veg Patch", ZoneKind.VEG_PATCH)
        garden.updateZone(front.copy(rect = ZoneRect(0, 0, 1200, 300), sun = Sun.FULL_SUN, soil = Soil.SAND, mulched = true))
        garden.updateZone(citrus.copy(rect = ZoneRect(0, 450, 650, 550), sun = Sun.FULL_SUN, soil = Soil.LOAM))
        garden.updateZone(hive.copy(rect = ZoneRect(800, 450, 400, 300), sun = Sun.PART_SHADE))
        garden.updateZone(veg.copy(rect = ZoneRect(800, 850, 400, 350), sun = Sun.FULL_SUN, soil = Soil.LOAM, mulched = true))

        val grevillea = garden.addPlant("Grevillea 'Robyn Gordon'", null, front.id)
        listOf("Lomandra longifolia", "Westringia fruticosa", "Dianella caerulea", "Correa reflexa").forEach { garden.addPlant(it, null, front.id) }
        val lemon = garden.addPlant("Citrus × limon", "Mum's lemon", citrus.id)
        garden.addPlant("Citrus reticulata", null, citrus.id)
        garden.addPlant("Chrysocephalum apiculatum", null, citrus.id, quantity = 6)
        garden.addPlant("Leptospermum petersonii", null, hive.id)
        garden.addPlant("Salvia rosmarinus", null, hive.id)
        garden.addPlant("Lavandula dentata", null, hive.id)
        garden.addPlant("Lantana camara", null, hive.id)
        garden.addPlant("Solanum lycopersicum", null, veg.id, quantity = 4)
        garden.addPlant("Ocimum basilicum", null, veg.id)
        garden.addPlant("Tagetes patula", null, veg.id)
        return Ids(front.id, citrus.id, hive.id, veg.id, lemon.id, grevillea.id)
    }
}
