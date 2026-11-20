package io.github.wkoller.pollenplot.ui.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.domain.plants.EcoRole
import io.github.wkoller.pollenplot.domain.plants.FactLevel
import io.github.wkoller.pollenplot.domain.plants.GrowthForm
import io.github.wkoller.pollenplot.domain.plants.Sun
import io.github.wkoller.pollenplot.domain.plants.WaterNeed
import io.github.wkoller.pollenplot.ui.components.ActionNotice
import io.github.wkoller.pollenplot.ui.components.ChipGroup
import io.github.wkoller.pollenplot.ui.components.ConnectedChoice
import io.github.wkoller.pollenplot.ui.components.DetailScaffold
import io.github.wkoller.pollenplot.ui.components.EmptyState
import io.github.wkoller.pollenplot.ui.components.FactCard
import io.github.wkoller.pollenplot.ui.components.LinkRow
import io.github.wkoller.pollenplot.ui.components.ManyChipGroup
import io.github.wkoller.pollenplot.ui.components.PlantAvatar
import io.github.wkoller.pollenplot.ui.components.PollenLoadingIndicator
import io.github.wkoller.pollenplot.ui.components.PollenProgressBar
import io.github.wkoller.pollenplot.ui.components.RoleBadges
import io.github.wkoller.pollenplot.ui.components.RoleIcons
import io.github.wkoller.pollenplot.ui.components.SectionHeading
import io.github.wkoller.pollenplot.ui.components.SegmentedGroup
import io.github.wkoller.pollenplot.ui.components.SwitchRow
import io.github.wkoller.pollenplot.ui.components.TabScaffold
import io.github.wkoller.pollenplot.ui.components.listPadding
import io.github.wkoller.pollenplot.ui.plants.label
import io.github.wkoller.pollenplot.ui.theme.Spacing

// Previews of the shared components, each in light, dark, large text and a wide window. They use the app's own words
// from strings.xml, so a preview shows what a person will read.

@PollenPreviews
@Composable
internal fun EmptyStatePreview() = PreviewTheme {
    EmptyState(R.drawable.ic_yard, stringResource(R.string.plot_empty_title), stringResource(R.string.plot_empty_body))
}

@PollenPreviews
@Composable
internal fun FactCardPreview() = PreviewTheme {
    Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        FactCard(R.drawable.ic_sunny, stringResource(R.string.plant_light)) {
            Text(stringResource(Sun.FULL_SUN.label), style = MaterialTheme.typography.bodyLarge)
        }
        FactCard(R.drawable.ic_water_drop, stringResource(R.string.plant_water), FactLevel.GENUS) {
            Text(stringResource(R.string.plant_water_need, stringResource(WaterNeed.MODERATE.label)), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@PollenPreviews
@Composable
internal fun BadgesPreview() = PreviewTheme {
    Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
        RoleBadges(listOf(EcoRole.POLLINATOR_MAGNET, EcoRole.NATIVE_BEE_FAVOURITE, EcoRole.LOCAL_NATIVE, EcoRole.HIGH_THIRST, EcoRole.WEED_RISK))
        RoleIcons(listOf(EcoRole.POLLINATOR_MAGNET, EcoRole.BIRD_FEEDER, EcoRole.HIGH_THIRST, EcoRole.EDIBLE))
    }
}

@PollenPreviews
@Composable
internal fun AvatarsPreview() = PreviewTheme {
    Row(Modifier.padding(Spacing.l), horizontalArrangement = Arrangement.spacedBy(Spacing.l), verticalAlignment = Alignment.CenterVertically) {
        PlantAvatar(GrowthForm.SHRUB, 72.dp)
        PlantAvatar(GrowthForm.SHRUB, 72.dp, inFlower = true)
        PlantAvatar(GrowthForm.TREE, 48.dp, containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@PollenPreviews
@Composable
private fun ProgressPreview() = PreviewTheme {
    Column(Modifier.fillMaxWidth().padding(Spacing.l), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
            PollenLoadingIndicator()
            PollenLoadingIndicator(contained = true)
        }
        PollenProgressBar(0.4f, Modifier.fillMaxWidth())
    }
}

@PollenPreviews
@Composable
internal fun ChoicesPreview() = PreviewTheme {
    Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
        ConnectedChoice(Sun.entries, Sun.FULL_SUN, { stringResource(it.label) }, {})
        ChipGroup(stringResource(R.string.plant_light), Sun.entries, Sun.PART_SHADE, { stringResource(it.label) }, {})
        ManyChipGroup(stringResource(R.string.plant_light), Sun.entries, setOf(Sun.FULL_SUN), { stringResource(it.label) }, onChange = {})
    }
}

@PollenPreviews
@Composable
internal fun NoticePreview() = PreviewTheme {
    ActionNotice(stringResource(R.string.settings_reminders_off), stringResource(R.string.scan_notifications_allow)) {}
}

@PollenPreviews
@Composable
internal fun SegmentedPreview() = PreviewTheme {
    Column(Modifier.padding(vertical = Spacing.l)) {
        SectionHeading(stringResource(R.string.settings_appearance))
        SegmentedGroup {
            row { LinkRow(it, R.drawable.ic_settings, stringResource(R.string.settings_about), null) {} }
            row { SwitchRow(it, true, R.drawable.ic_palette, stringResource(R.string.settings_wallpaper_colours), stringResource(R.string.settings_wallpaper_colours_body)) {} }
        }
    }
}

@PollenPreviews
@Composable
private fun TabFramePreview() = PreviewTheme {
    TabScaffold(
        title = stringResource(R.string.plot_title),
        subtitle = stringResource(R.string.plot_subtitle),
        onOpenSettings = {},
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = {}, icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) }, text = { Text(stringResource(R.string.plot_add_zone)) })
        },
    ) { padding ->
        LazyColumn(contentPadding = listPadding(padding)) {
            item { EmptyState(R.drawable.ic_yard, stringResource(R.string.plot_empty_title), stringResource(R.string.plot_empty_body)) }
        }
    }
}

@PollenPreviews
@Composable
private fun DetailFramePreview() = PreviewTheme {
    DetailScaffold(title = stringResource(R.string.settings_title), onBack = {}) { padding ->
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding())) {
            item { SectionHeading(stringResource(R.string.settings_appearance)) }
        }
    }
}
