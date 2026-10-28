package io.github.wkoller.pollenplot.ui.wildlife

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.data.SightingView
import io.github.wkoller.pollenplot.domain.wildlife.LifeGroup
import io.github.wkoller.pollenplot.domain.wildlife.MonthSeen
import io.github.wkoller.pollenplot.domain.wildlife.SeenSpecies
import io.github.wkoller.pollenplot.ui.UiFormat
import io.github.wkoller.pollenplot.ui.components.PictureAvatar
import java.time.Month
import java.time.format.TextStyle

/** An animal's cut-out from a photo, or its group's icon. */
@Composable
fun AnimalAvatar(group: LifeGroup, thumbnail: String?, modifier: Modifier = Modifier) = PictureAvatar(
    group.icon,
    48.dp,
    modifier,
    thumbnail = thumbnail,
    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
)

/** One kind of animal seen in the yard: its name, how often it was seen, and whether it is native. */
@Composable
fun SeenSpeciesRow(seen: SeenSpecies, onClick: () -> Unit) {
    val details = listOfNotNull(
        pluralStringResource(R.plurals.wildlife_seen_times, seen.sightings.size, seen.sightings.size),
        seen.animal?.let { stringResource(if (it.isNative) R.string.review_native else R.string.review_introduced) },
        if (seen.isNew) stringResource(R.string.wildlife_new_badge) else null,
    )
    ListItem(
        onClick = onClick,
        leadingContent = { AnimalAvatar(seen.group, seen.thumbnail) },
        supportingContent = {
            Column {
                seen.animal?.name?.takeIf { it != seen.title }?.let {
                    Text(it, fontStyle = FontStyle.Italic, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(details.joinToString(" · "), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        },
        trailingContent = { Text(UiFormat.shortDate(seen.latest.seenAt), style = MaterialTheme.typography.labelMedium) },
    ) {
        Text(seen.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** One sighting: what was seen, what it was doing and where, and the day. A zone's own page leaves out [showZone]. */
@Composable
fun SightingRow(view: SightingView, onClick: () -> Unit, showName: Boolean = true, showZone: Boolean = true) {
    val sighting = view.sighting
    val details = listOfNotNull(
        sighting.behaviour?.let { stringResource(it.label) },
        sightingPlace(view, showZone),
        if (sighting.count > 1) pluralStringResource(R.plurals.sighting_count, sighting.count, sighting.count) else null,
    )
    ListItem(
        onClick = onClick,
        leadingContent = { AnimalAvatar(view.animal?.group ?: sighting.group, sighting.thumbnail) },
        supportingContent = if (details.isEmpty()) null else { { Text(details.joinToString(" · "), maxLines = 2, overflow = TextOverflow.Ellipsis) } },
        trailingContent = { Text(UiFormat.shortDate(sighting.seenAt), style = MaterialTheme.typography.labelMedium) },
    ) {
        Text(if (showName) view.title else UiFormat.longDate(sighting.seenAt), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Where a sighting was: the plant it was on and its zone, as far as they are known. */
@Composable
fun sightingPlace(view: SightingView, showZone: Boolean = true): String? {
    val plant = view.plantTitle
    val zone = view.zoneName?.takeIf { showZone }
    return when {
        plant != null && zone != null -> stringResource(R.string.sighting_on_plant_in_zone, plant, zone)
        plant != null -> stringResource(R.string.sighting_on_plant, plant)
        zone != null -> stringResource(R.string.sighting_in_zone, zone)
        else -> null
    }
}

/**
 * The year as twelve bars, one a month, as tall as the number of kinds seen that month, with [current] marked. A
 * screen reader hears each month's number instead.
 */
@Composable
fun MonthStrip(months: List<MonthSeen>, current: Month?, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val most = months.maxOf { it.species }.coerceAtLeast(1)
    val spoken = mutableListOf<String>()
    for (month in months) spoken += pluralStringResource(R.plurals.wildlife_month_species, month.species, month.month.getDisplayName(TextStyle.FULL, locale), month.species)
    val description = spoken.joinToString(", ")
    Row(
        modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        months.forEach { month ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val colour = if (month.species > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant
                Box(contentAlignment = Alignment.BottomCenter, modifier = Modifier.height(BAR_HEIGHT.dp)) {
                    val height = if (month.species == 0) 4 else (BAR_HEIGHT * month.species / most).coerceAtLeast(8)
                    Box(Modifier.width(16.dp).height(height.dp).clip(RoundedCornerShape(8.dp)).background(colour))
                }
                Text(
                    month.month.getDisplayName(TextStyle.NARROW, locale),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (month.month == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = if (month.month == current) Modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(6.dp)).padding(horizontal = 4.dp) else Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

private const val BAR_HEIGHT = 56
