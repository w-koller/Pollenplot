package io.github.wkoller.pollenplot.ui.wildlife

import io.github.wkoller.pollenplot.ui.components.listPadding
import io.github.wkoller.pollenplot.ui.components.SectionHeading
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import io.github.wkoller.pollenplot.ui.components.BadgeRow
import io.github.wkoller.pollenplot.ui.components.HeroPictureSize
import io.github.wkoller.pollenplot.ui.components.RecordHero
import io.github.wkoller.pollenplot.ui.components.heroGone
import io.github.wkoller.pollenplot.ui.theme.BotanicalSpan
import io.github.wkoller.pollenplot.ui.theme.Spacing
import io.github.wkoller.pollenplot.ui.theme.botanical
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
    onAsk: (() -> Unit)? = null,
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
        val ids = mine.flatMap { it.sighting.plantIds }.groupingBy { it }.eachCount()
        plants.filter { it.plant.id in ids }.sortedByDescending { ids[it.plant.id] }
    }

    val list = rememberLazyListState()
    DetailScaffold(
        title = title,
        onBack = onBack,
        titleShown = heroGone(list),
        actions = {
            onAsk?.let { ask -> IconButton(onClick = ask) { Icon(painterResource(R.drawable.ic_forum), contentDescription = stringResource(R.string.ask_open)) } }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = listPadding(padding)) {
            item(key = "header") {
                val group = animal?.group ?: mine.firstOrNull()?.sighting?.group
                val roles = animal?.roles.orEmpty().sortedBy { it.ordinal }
                RecordHero(
                    title = title,
                    scientific = animal?.name?.takeIf { it != title },
                    details = listOfNotNull(
                        listOfNotNull(group?.let { stringResource(it.label) }, animal?.family?.let { stringResource(R.string.animal_family, it) }).joinToString(" · ").ifBlank { null },
                    ),
                    tags = if (roles.isEmpty()) null else { { BadgeRow(centred = true) { roles.forEach { role -> Badge(role.icon, stringResource(role.label), role.tone) } } } },
                ) {
                    PictureAvatar(
                        group?.icon ?: R.drawable.ic_eco,
                        HeroPictureSize,
                        thumbnail = mine.firstNotNullOfOrNull { it.sighting.thumbnail },
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
            if (animal != null) {
                item(key = "safety") { SafetyNotice(animal, locality.state, Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s)) }
                item(key = "about") {
                    Column(Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                        Text(origin(animal), style = MaterialTheme.typography.bodyLarge)
                        Text(animal.description, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            } else {
                item(key = "unknown") {
                    Text(stringResource(R.string.animal_not_in_list), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s))
                }
            }

            if (mine.isNotEmpty()) {
                item(key = "yard") {
                    Column {
                        SectionHeading(stringResource(R.string.animal_in_yard))
                        Column(Modifier.padding(horizontal = Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
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
                        animal.plantNote?.let { Text(it, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = Spacing.l)) }
                    }
                }
                items(animal.plants, key = { "uses-$it" }) { name ->
                    val species = kb?.species(name)
                    val growing = plants.any { it.isGrowing && it.species != null && uses(name, it) }
                    val growingText = stringResource(R.string.animal_uses_growing)
                    ListItem(
                        supportingContent = if (species != null || growing) {
                            {
                                // The scientific name in the botanical italic, and whether it grows here in plain words.
                                Text(
                                    buildAnnotatedString {
                                        species?.name?.let { withStyle(BotanicalSpan) { append(it) } }
                                        if (growing) {
                                            if (species != null) append(" · ")
                                            append(growingText)
                                        }
                                    },
                                )
                            }
                        } else {
                            null
                        },
                    ) {
                        // A genus or family the animal uses is a scientific name too.
                        Text(species?.displayName ?: name, style = if (species == null) LocalTextStyle.current.botanical() else LocalTextStyle.current)
                    }
                }
            }
            animal?.habitat?.let { habitat ->
                item(key = "needs") {
                    Column {
                        SectionHeading(stringResource(R.string.animal_needs))
                        Text(habitat, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = Spacing.l))
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
