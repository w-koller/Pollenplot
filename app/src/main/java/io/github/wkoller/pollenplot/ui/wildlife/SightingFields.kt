package io.github.wkoller.pollenplot.ui.wildlife

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.data.PlantView
import io.github.wkoller.pollenplot.data.ZoneView
import io.github.wkoller.pollenplot.domain.wildlife.Behaviour
import io.github.wkoller.pollenplot.ui.plants.ZoneChooser
import io.github.wkoller.pollenplot.ui.plot.ChipGroup

/**
 * Where an animal was seen, the plant it was on and what it was doing, as chips. The plants offered are those growing
 * in the chosen zone, or every growing plant when no zone is chosen, and the plant already chosen.
 */
@Composable
fun SightingFields(
    zones: List<ZoneView>,
    plants: List<PlantView>,
    zoneId: String?,
    plantId: String?,
    behaviour: Behaviour?,
    onZone: (String?) -> Unit,
    onPlant: (String?) -> Unit,
    onBehaviour: (Behaviour?) -> Unit,
) {
    val nearbyPlants = plants.filter { (it.isGrowing && (zoneId == null || it.plant.zoneId == zoneId)) || it.plant.id == plantId }
    ZoneChooser(zones, zoneId, onZone)
    if (nearbyPlants.isNotEmpty()) {
        val titles = nearbyPlants.associate { it.plant.id to it.title }
        ChipGroup(
            title = stringResource(R.string.review_on_plant),
            options = listOf<String?>(null) + nearbyPlants.map { it.plant.id },
            selected = plantId,
            label = { id -> id?.let(titles::get) ?: stringResource(R.string.review_on_no_plant) },
            onChoose = onPlant,
        )
    }
    ChipGroup(
        title = stringResource(R.string.review_behaviour),
        options = listOf<Behaviour?>(null) + Behaviour.entries,
        selected = behaviour,
        label = { stringResource(it.label) },
        onChoose = onBehaviour,
    )
}
