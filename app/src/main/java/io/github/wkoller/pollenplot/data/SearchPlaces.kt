package io.github.wkoller.pollenplot.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Which zone each record in the search index belongs to, for search's zone filter. Records without a zone are left out. */
class SearchPlaces(views: GardenViews, bees: BeeViews, wishes: WishRepository, problems: ProblemRepository, habitat: HabitatRepository) {
    private val homes: Flow<Map<String, String>> = combine(bees.hives, bees.nativeHives, bees.hotels) { hives, natives, hotels ->
        hives.mapNotNull { view -> view.hive.zoneId?.let { view.hive.id to it } }.toMap() +
            natives.mapNotNull { view -> view.hive.zoneId?.let { view.hive.id to it } } +
            hotels.mapNotNull { view -> view.hotel.zoneId?.let { view.hotel.id to it } }
    }

    private val garden: Flow<Map<String, String>> = combine(views.zones, views.plants, views.sightings) { zones, plants, sightings ->
        val plantZones = plants.mapNotNull { view -> view.plant.zoneId?.let { view.plant.id to it } }.toMap()
        zones.associate { it.zone.id to it.zone.id } + plantZones +
            sightings.mapNotNull { view -> (view.sighting.zoneId ?: view.sighting.plantId?.let(plantZones::get))?.let { view.sighting.id to it } }
    }

    val zoneOf: Flow<Map<String, String>> = combine(garden, homes, wishes.wishes, problems.problems, habitat.water) { garden, homes, wishes, problems, water ->
        garden + homes +
            wishes.mapNotNull { wish -> wish.targetZoneId?.let { wish.id to it } } +
            problems.mapNotNull { problem -> garden[problem.plantId]?.let { problem.id to it } } +
            water.map { it.id to it.zoneId }
    }
}
