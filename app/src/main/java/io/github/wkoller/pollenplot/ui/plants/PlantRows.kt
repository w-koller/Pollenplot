package io.github.wkoller.pollenplot.ui.plants

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import io.github.wkoller.pollenplot.ui.theme.BotanicalSpan
import io.github.wkoller.pollenplot.ui.theme.Spacing
import io.github.wkoller.pollenplot.ui.theme.botanical
import androidx.compose.ui.unit.dp
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.data.PlantView
import io.github.wkoller.pollenplot.data.WaterState
import io.github.wkoller.pollenplot.data.LastFed
import io.github.wkoller.pollenplot.data.ZoneView
import io.github.wkoller.pollenplot.ui.feeding.LastFedLine
import io.github.wkoller.pollenplot.domain.plants.Species
import io.github.wkoller.pollenplot.ui.UiFormat
import io.github.wkoller.pollenplot.ui.components.BadgeItem
import io.github.wkoller.pollenplot.ui.components.IconBadges
import io.github.wkoller.pollenplot.ui.components.PlantAvatar
import io.github.wkoller.pollenplot.ui.components.PollenShapes
import io.github.wkoller.pollenplot.ui.components.RoleIcons
import io.github.wkoller.pollenplot.ui.components.largeText
import io.github.wkoller.pollenplot.ui.water.NextWateringLine
import io.github.wkoller.pollenplot.ui.water.byHand

/** A plant in a list: its picture, names, zone and first badges. */
@Composable
fun PlantRow(view: PlantView, onClick: () -> Unit, showZone: Boolean = true, zoneId: String? = null) {
    // In a zone's own list, a plant growing in several zones counts the plants in that zone.
    val count = zoneId?.let(view.plant::countIn) ?: view.plant.quantity
    val rest = listOfNotNull(
        if (count > 1) pluralStringResource(R.plurals.plant_count, count, count) else null,
        if (showZone) view.zoneName?.let { stringResource(R.string.plant_zone, it) } else null,
    )
    val details = if (view.scientificName == null && rest.isEmpty()) {
        null
    } else {
        // The scientific name is set in Fraunces italic, as a field guide sets it, and the rest in the row's own type.
        buildAnnotatedString {
            view.scientificName?.let { withStyle(BotanicalSpan) { append(it) } }
            rest.forEachIndexed { index, part ->
                if (index > 0 || view.scientificName != null) append(" · ")
                append(part)
            }
        }
    }
    // With large text, badges beside the name would leave it a few letters, so they go under the details.
    val large = largeText()
    ListItem(
        onClick = onClick,
        leadingContent = { PlantAvatar(view.species?.form, 48.dp, thumbnail = view.plant.thumbnail) },
        supportingContent = if (details == null && !(large && view.roles.isNotEmpty())) {
            null
        } else {
            {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    if (details != null) Text(details, maxLines = if (large) 4 else 2, overflow = TextOverflow.Ellipsis)
                    if (large) RoleIcons(view.roles, max = 2, size = 24.dp)
                }
            }
        },
        trailingContent = if (large) null else { { RoleIcons(view.roles, max = 2, size = 24.dp) } },
    ) {
        Text(view.title, maxLines = if (large) 3 else 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A species from the knowledge base in a list, for adding it to the garden. */
@Composable
fun SpeciesRow(species: Species, roles: List<io.github.wkoller.pollenplot.domain.plants.EcoRole>, onClick: () -> Unit) {
    val large = largeText()
    ListItem(
        onClick = onClick,
        leadingContent = { PlantAvatar(species.form, 44.dp) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(species.name, style = LocalTextStyle.current.botanical(), maxLines = if (large) 3 else 1, overflow = TextOverflow.Ellipsis)
                if (large) RoleIcons(roles, max = 2, size = 24.dp)
            }
        },
        trailingContent = if (large) null else { { RoleIcons(roles, max = 2, size = 24.dp) } },
    ) {
        Text(species.displayName, maxLines = if (large) 3 else 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A zone in the list view of the Plot: its kind, size, plants and badges, its next watering and its last feed. */
@Composable
fun ZoneCard(view: ZoneView, onClick: () -> Unit, modifier: Modifier = Modifier, water: WaterState? = null, fed: LastFed? = null) {
    ElevatedCard(onClick = onClick, shape = MaterialTheme.shapes.large, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(Spacing.l), horizontalArrangement = Arrangement.spacedBy(Spacing.l), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(PollenShapes.cookie()).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(view.zone.kind.icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(view.zone.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(
                        stringResource(view.zone.kind.label),
                        stringResource(R.string.zone_area, UiFormat.squareMetres(view.zone.areaCm2)),
                        pluralStringResource(R.plurals.plant_count, view.plants.size, view.plants.size),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (view.plants.isNotEmpty() || view.badges.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                        view.plants.take(5).forEach { PlantAvatar(it.species?.form, 28.dp, thumbnail = it.plant.thumbnail, inFlower = it.plant.id in view.flowering) }
                        if (view.badges.isNotEmpty()) {
                            Box(Modifier.padding(start = Spacing.s)) {
                                IconBadges(view.badges.map { BadgeItem(it.icon, stringResource(it.label), it.tone) }, size = 24.dp)
                            }
                        }
                    }
                }
                val watering = water?.zones?.get(view.zone.id)
                watering?.next?.let { NextWateringLine(it, water.today, water.window, watering.byHand) }
                fed?.let { LastFedLine(it) }
            }
        }
    }
}
