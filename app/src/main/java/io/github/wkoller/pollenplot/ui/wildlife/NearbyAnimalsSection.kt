package io.github.wkoller.pollenplot.ui.wildlife

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.domain.plants.KnowledgeBase
import io.github.wkoller.pollenplot.domain.wildlife.FaunaBase
import io.github.wkoller.pollenplot.domain.wildlife.LifeGroup
import io.github.wkoller.pollenplot.domain.wildlife.NearbyAnimals
import io.github.wkoller.pollenplot.domain.wildlife.RecordedAnimal
import io.github.wkoller.pollenplot.ui.settings.SectionHeading
import java.text.NumberFormat

/**
 * The animals most recorded near the garden on the Atlas of Living Australia, a group at a time, with those seen in
 * the yard marked. [seenKeys] are the kinds seen, as [io.github.wkoller.pollenplot.domain.wildlife.Biodiversity.keyOf]
 * names them. Without a garden location, it asks for one.
 */
internal fun LazyListScope.nearbyAnimals(
    nearby: NearbyAnimals?,
    hasPlace: Boolean,
    fauna: FaunaBase?,
    seenKeys: Set<String>,
    group: LifeGroup?,
    onGroup: (LifeGroup) -> Unit,
    showAll: Boolean,
    onShowAll: () -> Unit,
    onOpenAnimal: (String) -> Unit,
    onSetLocation: () -> Unit,
) {
    item(key = "nearby") { SectionHeading(stringResource(R.string.wildlife_nearby)) }
    if (!hasPlace) {
        item(key = "nearby-place") {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.wildlife_nearby_no_location), style = MaterialTheme.typography.bodyLarge)
                OutlinedButton(onClick = onSetLocation) {
                    Icon(painterResource(R.drawable.ic_location_on), contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text(stringResource(R.string.weeds_set_location))
                }
            }
        }
        return
    }
    if (nearby == null || nearby.animals.isEmpty()) {
        item(key = "nearby-waiting") {
            Text(stringResource(R.string.wildlife_nearby_waiting), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp))
        }
        return
    }
    val chosen = group?.takeIf { it in nearby.groups } ?: nearby.groups.first()
    val animals = nearby.inGroup(chosen)
    item(key = "nearby-intro") {
        Column {
            Text(
                stringResource(R.string.wildlife_nearby_body, nearby.radiusKm, nearby.sinceYear),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                nearby.groups.forEach { g ->
                    FilterChip(
                        selected = g == chosen,
                        onClick = { onGroup(g) },
                        label = { Text(stringResource(g.plural)) },
                        leadingIcon = { Icon(painterResource(g.icon), contentDescription = null, modifier = Modifier.padding(start = 4.dp)) },
                    )
                }
            }
        }
    }
    items(if (showAll) animals else animals.take(NEARBY_SHOWN), key = { "nearby-" + it.group + it.name }) { animal ->
        val known = fauna?.animal(animal.name)
        val key = known?.name ?: KnowledgeBase.normalise(animal.name)
        val seen = key in seenKeys
        NearbyAnimalRow(animal, known?.displayName, seen, onOpen = if (known != null || seen) { { onOpenAnimal(key) } } else null)
    }
    if (!showAll && animals.size > NEARBY_SHOWN) {
        item(key = "nearby-all") {
            TextButton(onClick = onShowAll, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text(pluralStringResource(R.plurals.wildlife_nearby_show_all, animals.size, animals.size))
            }
        }
    }
}

/** One animal recorded near the garden: its name, how many records there are, and whether it has been seen in the yard. */
@Composable
private fun NearbyAnimalRow(animal: RecordedAnimal, knownName: String?, seen: Boolean, onOpen: (() -> Unit)?) {
    val title = knownName ?: animal.common ?: animal.name
    val number = NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0]).format(animal.records)
    val details = listOfNotNull(
        pluralStringResource(R.plurals.wildlife_nearby_records, animal.records, number),
        if (seen) stringResource(R.string.wildlife_seen_in_yard) else null,
    ).joinToString(" · ")
    val supporting: @Composable () -> Unit = {
        Column {
            if (title != animal.name) Text(animal.name, fontStyle = FontStyle.Italic, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(details, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    if (onOpen != null) {
        ListItem(onClick = onOpen, leadingContent = { AnimalAvatar(animal.group, null) }, supportingContent = supporting) { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    } else {
        ListItem(leadingContent = { AnimalAvatar(animal.group, null) }, supportingContent = supporting) { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

/** The animals of a group shown before Show all. */
private const val NEARBY_SHOWN = 8
