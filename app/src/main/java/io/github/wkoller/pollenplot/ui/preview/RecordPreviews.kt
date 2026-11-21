package io.github.wkoller.pollenplot.ui.preview

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.domain.plants.EcoRole
import io.github.wkoller.pollenplot.domain.plants.GrowthForm
import io.github.wkoller.pollenplot.ui.components.FactCard
import io.github.wkoller.pollenplot.ui.components.FactGroup
import io.github.wkoller.pollenplot.ui.components.GroupedItem
import io.github.wkoller.pollenplot.ui.components.HeroPictureSize
import io.github.wkoller.pollenplot.ui.components.PictureAvatar
import io.github.wkoller.pollenplot.ui.components.PlantAvatar
import io.github.wkoller.pollenplot.ui.components.RecordHero
import io.github.wkoller.pollenplot.ui.components.RoleBadges
import io.github.wkoller.pollenplot.ui.components.SectionHeading
import io.github.wkoller.pollenplot.ui.theme.Spacing

// Previews of the record pages' shared pieces, with records like the demo garden's. Whole pages read the garden's
// records, so the screen tests draw them.

/** A plant's hero while it is in flower, with its names, what it is and where, and its badges. */
@PollenPreviews
@Composable
internal fun RecordHeroPreview() = PreviewTheme {
    RecordHero(
        title = "Grevillea Robyn Gordon",
        scientific = "Grevillea 'Robyn Gordon'",
        details = listOf("Shrub · Proteaceae", "In Front Native Strip"),
        tags = { RoleBadges(listOf(EcoRole.BIRD_FEEDER, EcoRole.NATIVE_BEE_FAVOURITE, EcoRole.LOCAL_NATIVE), centred = true) },
    ) { PlantAvatar(GrowthForm.SHRUB, HeroPictureSize, inFlower = true) }
}

/** A honey bee hive's hero, in wattle gold. */
@PollenPreviews
@Composable
internal fun HiveHeroPreview() = PreviewTheme {
    RecordHero(
        title = "Hive 1",
        details = listOf("Langstroth, 8 frames · Hive Corner", "Queen from 2025, marked blue", "Active"),
    ) {
        PictureAvatar(
            R.drawable.ic_hive,
            HeroPictureSize,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/** A plant's growing conditions as one group of joined fact cards. */
@PollenPreviews
@Composable
internal fun FactGroupPreview() = PreviewTheme {
    Column(Modifier.padding(Spacing.l)) {
        FactGroup(
            stringResource(R.string.plant_group_growing),
            listOf<@Composable (Shape) -> Unit>(
                { shape -> FactCard(R.drawable.ic_sunny, stringResource(R.string.plant_light), shape = shape) { Text("Full sun") } },
                { shape -> FactCard(R.drawable.ic_water_drop, stringResource(R.string.plant_water), shape = shape) { Text("Water need: Low") } },
                { shape -> FactCard(R.drawable.ic_landscape, stringResource(R.string.plant_soil), shape = shape) { Text("Soils: Sand, Loam") } },
            ),
        )
    }
}

/** A hive's inspections as a grouped list, each with its delete button. */
@PollenPreviews
@Composable
internal fun GroupedRecordsPreview() = PreviewTheme {
    val rows = listOf(
        "28 October 2026" to "Queen seen · Solid",
        "14 October 2026" to "Eggs seen · Patchy",
        "30 September 2026" to "Queen seen · Solid · Smelly brood",
    )
    Column(Modifier.padding(bottom = Spacing.l)) {
        SectionHeading(stringResource(R.string.honey_inspections))
        rows.forEachIndexed { index, (day, details) ->
            GroupedItem(
                index = index,
                count = rows.size,
                supporting = { Text(details) },
                trailing = {
                    IconButton(onClick = {}) { Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.honey_delete_record)) }
                },
            ) { Text(day) }
        }
    }
}
