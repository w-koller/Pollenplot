package io.github.wkoller.pollenplot.data

import io.github.wkoller.pollenplot.domain.garden.Corridor
import io.github.wkoller.pollenplot.domain.garden.CorridorSummary
import io.github.wkoller.pollenplot.domain.garden.ZoneHabitat
import io.github.wkoller.pollenplot.domain.rhythms.RhythmPlant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * A zone as wildlife sees it: its layers, ground features and water, the nearest water elsewhere, the bees kept in it,
 * and its plants as Pollen Rhythms sees them.
 */
data class ZoneHabitatView(
    val habitat: ZoneHabitat,
    val nearestWaterZone: String?,
    val hives: List<HiveView>,
    val nativeHives: List<NativeHiveView>,
    val hotels: List<HotelView>,
    val food: List<RhythmPlant>,
    /** Honey bees are kept here and the yard has no water set out, which the bee Code of Practice asks for. */
    val beesNeedWater: Boolean,
) {
    val beeHomes: Int get() = hives.size + nativeHives.size + hotels.size
}

/** Each zone's habitat and bees, and the yard as a corridor for wildlife. */
class HabitatViews(views: GardenViews, habitat: HabitatRepository, bees: BeeViews, rhythms: RhythmsViews) {
    private val homes = combine(bees.hives, bees.nativeHives, bees.hotels) { hives, natives, hotels -> Triple(hives, natives, hotels) }

    val zones: Flow<Map<String, ZoneHabitatView>> = combine(views.zones, habitat.water, homes, rhythms.state) { zones, water, (hives, natives, hotels), state ->
        val all = zones.map { it.zone }
        val names = all.associate { it.id to it.name }
        zones.associate { view ->
            val zone = view.zone
            val here = Corridor.habitat(zone, view.plants.map { it.species?.form }, all, water)
            val plantIds = view.plants.map { it.plant.id }.toSet()
            val honey = hives.filter { it.hive.zoneId == zone.id }
            zone.id to ZoneHabitatView(
                habitat = here,
                nearestWaterZone = here.nearestWaterZoneId?.let(names::get),
                hives = honey,
                nativeHives = natives.filter { it.hive.zoneId == zone.id },
                hotels = hotels.filter { it.hotel.zoneId == zone.id },
                food = state.plants.filter { it.id in plantIds },
                beesNeedWater = honey.isNotEmpty() && water.isEmpty(),
            )
        }
    }

    fun zone(id: String): Flow<ZoneHabitatView?> = zones.map { it[id] }

    val summary: Flow<CorridorSummary> = zones.map { all -> Corridor.summary(all.values.map { it.habitat }) }
}
