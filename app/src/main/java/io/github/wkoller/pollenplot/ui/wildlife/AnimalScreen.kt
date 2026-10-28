package io.github.wkoller.pollenplot.ui.wildlife

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.data.GardenViews
import io.github.wkoller.pollenplot.data.Locality
import io.github.wkoller.pollenplot.data.PlantView
import io.github.wkoller.pollenplot.data.kb.KnowledgeRepository
import io.github.wkoller.pollenplot.domain.plants.AuState
import io.github.wkoller.pollenplot.domain.plants.KnowledgeBase
import io.github.wkoller.pollenplot.domain.wildlife.Animal
import io.github.wkoller.pollenplot.domain.wildlife.Biodiversity
import io.github.wkoller.pollenplot.ui.UiFormat
import io.github.wkoller.pollenplot.ui.components.Badge
import io.github.wkoller.pollenplot.ui.components.DetailScaffold
import io.github.wkoller.pollenplot.ui.components.PictureAvatar
import io.github.wkoller.pollenplot.ui.listWords
import io.github.wkoller.pollenplot.ui.plants.PlantRow
import io.github.wkoller.pollenplot.ui.plants.label
import io.github.wkoller.pollenplot.ui.settings.SectionHeading

/**
 * One kind of animal seen in the yard: what Pollenplot knows about it, what to know before going near it, the plants
 * it was seen on and the ones it uses, and every sighting of it. [key] is its name in the knowledge base, or, for an
 * animal not in it, the name it was recorded under.
 */
@Composable
fun AnimalScreen(
    views: GardenViews,
    knowledge: KnowledgeRepository,
    key: String,
    onBack: () -> Unit,
    onOpenSighting: (String) -> Unit,
    onOpenPlant: (String) -> Unit,
) {
    val sightings by views.sightings.collectAsStateWithLifecycle(initialValue = emptyList())
    val plants by views.plants.collectAsStateWithLifecycle(initialValue = emptyList())
    val fauna by knowledge.fauna.collectAsStateWithLifecycle()
    val kb by knowledge.knowledge.collectAsStateWithLifecycle()
    val locality by views.locality.collectAsStateWithLifecycle(initialValue = Locality.NONE)
    val animal = fauna?.animal(key)
    val mine = remember(sightings, fauna, key) { sightings.filter { Biodiversity.keyOf(it.sighting, fauna) == key } }
    val title = animal?.displayName ?: mine.firstOrNull()?.sighting?.label ?: key
    val seenOn = remember(mine, plants) {
        val ids = mine.mapNotNull { it.sighting.plantId }.groupingBy { it }.eachCount()
        plants.filter { it.plant.id in ids }.sortedByDescending { ids[it.plant.id] }
    }

    DetailScaffold(title = title, onBack = onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item(key = "header") {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    val group = animal?.group ?: mine.firstOrNull()?.sighting?.group
                    PictureAvatar(
                        group?.icon ?: R.drawable.ic_eco,
                        96.dp,
                        thumbnail = mine.firstNotNullOfOrNull { it.sighting.thumbnail },
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(title, style = MaterialTheme.typography.headlineSmall)
                        animal?.name?.takeIf { it != title }?.let { Text(it, style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic) }
                        val facts = listOfNotNull(group?.let { stringResource(it.label) }, animal?.family?.let { stringResource(R.string.animal_family, it) })
                        if (facts.isNotEmpty()) {
                            Text(facts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (animal != null) {
                item(key = "safety") { SafetyNotice(animal, locality.state, Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
                item(key = "about") {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(origin(animal), style = MaterialTheme.typography.bodyLarge)
                        Text(animal.description, style = MaterialTheme.typography.bodyLarge)
                        if (animal.roles.isNotEmpty()) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                animal.roles.sortedBy { it.ordinal }.forEach { role -> Badge(role.icon, stringResource(role.label), role.tone) }
                            }
                        }
                    }
                }
            } else {
                item(key = "unknown") {
                    Text(stringResource(R.string.animal_not_in_list), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
            }

            if (mine.isNotEmpty()) {
                item(key = "yard") {
                    Column {
                        SectionHeading(stringResource(R.string.animal_in_yard))
                        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(pluralStringResource(R.plurals.animal_seen_summary, mine.size, mine.size, UiFormat.longDate(mine.first().sighting.seenAt)), style = MaterialTheme.typography.bodyLarge)
                            if (mine.size > 1) Text(stringResource(R.string.animal_first_seen, UiFormat.longDate(mine.last().sighting.seenAt)), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
            if (seenOn.isNotEmpty()) {
                item(key = "seen-on") { SectionHeading(stringResource(R.string.animal_seen_on)) }
                items(seenOn, key = { "seen-on-" + it.plant.id }) { view -> PlantRow(view, onClick = { onOpenPlant(view.plant.id) }) }
            }
            if (animal != null && animal.plants.isNotEmpty()) {
                item(key = "uses") {
                    Column {
                        SectionHeading(stringResource(R.string.animal_uses))
                        animal.plantNote?.let { Text(it, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp)) }
                    }
                }
                items(animal.plants, key = { "uses-$it" }) { name ->
                    val species = kb?.species(name)
                    val growing = plants.any { it.isGrowing && it.species != null && uses(name, it) }
                    ListItem(
                        supportingContent = if (species != null || growing) {
                            {
                                Text(
                                    listOfNotNull(species?.name, if (growing) stringResource(R.string.animal_uses_growing) else null).joinToString(" · "),
                                    fontStyle = if (species != null && !growing) FontStyle.Italic else FontStyle.Normal,
                                )
                            }
                        } else {
                            null
                        },
                    ) { Text(species?.displayName ?: name, fontStyle = if (species == null) FontStyle.Italic else FontStyle.Normal) }
                }
            }
            animal?.habitat?.let { habitat ->
                item(key = "needs") {
                    Column {
                        SectionHeading(stringResource(R.string.animal_needs))
                        Text(habitat, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
            if (mine.isNotEmpty()) {
                item(key = "sightings") { SectionHeading(stringResource(R.string.animal_sightings)) }
                items(mine, key = { "sighting-" + it.sighting.id }) { view -> SightingRow(view, onClick = { onOpenSighting(view.sighting.id) }, showName = false) }
            }
        }
    }
}

/** Where the animal is native, or that it was brought to Australia. */
@Composable
private fun origin(animal: Animal): String = when {
    !animal.isNative -> stringResource(R.string.animal_introduced)
    animal.nativeStates.containsAll(AuState.entries) -> stringResource(R.string.animal_native_everywhere)
    else -> stringResource(R.string.animal_native_to, UiFormat.joinAnd(animal.nativeStates.sortedBy { it.ordinal }.map { stringResource(it.label) }, listWords()))
}

/** Whether [plant] is the species, genus or family an animal is said to use. */
private fun uses(name: String, plant: PlantView): Boolean {
    val species = plant.species ?: return false
    val wanted = KnowledgeBase.normalise(name)
    return KnowledgeBase.normalise(species.name) == wanted || KnowledgeBase.normalise(species.genus) == wanted || KnowledgeBase.normalise(species.family) == wanted
}
