package io.github.wkoller.pollenplot.ui.wildlife

import io.github.wkoller.pollenplot.ui.components.ManyChipGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.data.PlantView
import io.github.wkoller.pollenplot.data.ZoneView
import io.github.wkoller.pollenplot.domain.wildlife.Behaviour
import io.github.wkoller.pollenplot.ui.plants.ZoneChooser

/**
 * Where an animal was seen, the plants it was on and what it was doing, as chips. Several plants and several things
 * it was doing can be chosen; None and Not sure stand for choosing nothing. The plants offered are those growing in
 * the chosen zone, or every growing plant when no zone is chosen, and the plants already chosen.
 */
@Composable
fun SightingFields(
    zones: List<ZoneView>,
    plants: List<PlantView>,
    zoneId: String?,
    plantIds: Set<String>,
    behaviours: Set<Behaviour>,
    onZone: (String?) -> Unit,
    onPlants: (Set<String>) -> Unit,
    onBehaviours: (Set<Behaviour>) -> Unit,
) {
    val nearbyPlants = plants.filter { (it.isGrowing && (zoneId == null || zoneId in it.plant.zoneIds)) || it.plant.id in plantIds }
    ZoneChooser(zones, zoneId, onChoose = onZone)
    if (nearbyPlants.isNotEmpty()) {
        val titles = nearbyPlants.associate { it.plant.id to it.title }
        ManyChipGroup(
            title = stringResource(R.string.review_on_plant),
            options = nearbyPlants.map { it.plant.id },
            selected = plantIds,
            label = { id -> titles[id].orEmpty() },
            none = stringResource(R.string.review_on_no_plant),
            onChange = onPlants,
        )
    }
    ManyChipGroup(
        title = stringResource(R.string.review_behaviour),
        options = Behaviour.entries,
        selected = behaviours,
        label = { stringResource(it.label) },
        none = stringResource(R.string.behaviour_unknown),
        onChange = onBehaviours,
    )
}
