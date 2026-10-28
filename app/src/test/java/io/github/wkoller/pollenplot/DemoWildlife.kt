package io.github.wkoller.pollenplot

import io.github.wkoller.pollenplot.data.GardenRepository
import io.github.wkoller.pollenplot.domain.wildlife.Behaviour
import io.github.wkoller.pollenplot.domain.wildlife.LifeGroup
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * A year of wildlife in [DemoGarden]: birds, a bee on the lavender, a frog in the native strip, fire ants and aphids in
 * the veg patch, a redback and a skink Pollenplot doesn't know, and a noisy miner from the year before.
 */
object DemoWildlife {
    val zone: ZoneId = ZoneId.of("Australia/Brisbane")

    /** 8 am on 28 October 2026 in Brisbane. */
    val clock: Clock = Clock.fixed(LocalDate.of(2026, 10, 28).atTime(8, 0).atZone(zone).toInstant(), zone)

    suspend fun seed(garden: GardenRepository, ids: DemoGarden.Ids) {
        val plants = garden.plantList.first().associateBy { it.speciesName }
        suspend fun seen(name: String?, label: String, group: LifeGroup, date: LocalDate, zoneId: String?, plant: String? = null, behaviour: Behaviour? = null, count: Int = 1) {
            // Midday, so the day is the same in every time zone the tests might run in.
            val at = date.atTime(LocalTime.NOON).atZone(zone).toInstant()
            garden.recordSighting(name, label, group, zoneId, plant?.let { plants.getValue(it).id }, behaviour, count, at, notes = "")
        }
        seen("Manorina melanocephala", "Noisy miner", LifeGroup.BIRD, LocalDate.of(2026, 10, 12), ids.hive, "Salvia rosmarinus", Behaviour.FEEDING)
        seen("Manorina melanocephala", "Noisy miner", LifeGroup.BIRD, LocalDate.of(2026, 8, 3), ids.hive, behaviour = Behaviour.CALLING)
        seen("Manorina melanocephala", "Noisy miner", LifeGroup.BIRD, LocalDate.of(2025, 11, 20), ids.front)
        seen("Trichoglossus moluccanus", "Rainbow lorikeet", LifeGroup.BIRD, LocalDate.of(2026, 10, 25), ids.front, "Grevillea 'Robyn Gordon'", Behaviour.FEEDING, count = 4)
        seen("Amegilla cingulata", "Blue-banded bee", LifeGroup.INSECT, LocalDate.of(2026, 10, 26), ids.hive, "Lavandula dentata", Behaviour.POLLINATING, count = 2)
        seen("Litoria fallax", "Eastern dwarf tree frog", LifeGroup.FROG, LocalDate.of(2026, 9, 15), ids.front, behaviour = Behaviour.CALLING)
        seen("Solenopsis invicta", "Red imported fire ant", LifeGroup.INSECT, LocalDate.of(2026, 10, 20), ids.veg, count = 30)
        seen("Myzus persicae", "Green peach aphid", LifeGroup.INSECT, LocalDate.of(2026, 10, 22), ids.veg, "Solanum lycopersicum", Behaviour.FEEDING, count = 40)
        seen("Latrodectus hasselti", "Redback spider", LifeGroup.SPIDER, LocalDate.of(2026, 7, 2), ids.citrus, behaviour = Behaviour.RESTING)
        seen(null, "Brown skink", LifeGroup.REPTILE, LocalDate.of(2026, 3, 5), ids.veg, behaviour = Behaviour.BASKING)
        seen("Dacelo novaeguineae", "Laughing kookaburra", LifeGroup.BIRD, LocalDate.of(2026, 1, 14), null, behaviour = Behaviour.CALLING)
    }
}
