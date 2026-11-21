package io.github.wkoller.pollenplot.ui.plants

import io.github.wkoller.pollenplot.ui.theme.Spacing
import io.github.wkoller.pollenplot.ui.components.listPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.data.GardenRepository
import io.github.wkoller.pollenplot.data.GardenViews
import io.github.wkoller.pollenplot.data.PlantHistory
import io.github.wkoller.pollenplot.data.PlantView
import io.github.wkoller.pollenplot.domain.garden.PlantStatus
import io.github.wkoller.pollenplot.ui.UiFormat
import io.github.wkoller.pollenplot.ui.components.ChipGroup
import io.github.wkoller.pollenplot.ui.components.DetailScaffold
import io.github.wkoller.pollenplot.ui.components.PlantAvatar
import io.github.wkoller.pollenplot.ui.components.readable
import io.github.wkoller.pollenplot.ui.components.readableSides
import java.time.Period

/**
 * Every plant that died or was removed, the most recent first, each with the day it ended, where it grew, how long it
 * lived and how much history it keeps. A press opens its page, which keeps that history and can bring it back.
 */
@Composable
fun GraveyardScreen(views: GardenViews, garden: GardenRepository, onBack: () -> Unit, onOpenPlant: (String) -> Unit) {
    val plants by views.plants.collectAsStateWithLifecycle(initialValue = null)
    var shown by rememberSaveable { mutableStateOf<PlantStatus?>(null) }
    val ended = plants.orEmpty().filter { !it.isGrowing && (shown == null || it.plant.status == shown) }
        .sortedWith(compareByDescending<PlantView> { it.plant.endedOn }.thenBy { it.title.lowercase() })
    DetailScaffold(title = stringResource(R.string.graveyard_title), onBack = onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = listPadding(padding),
        ) {
            item(key = "top") {
                Column(Modifier.readable().padding(horizontal = Spacing.l, vertical = Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    Text(stringResource(R.string.graveyard_body), style = MaterialTheme.typography.bodyLarge)
                    ChipGroup(
                        title = stringResource(R.string.graveyard_show),
                        options = listOf(null, PlantStatus.DIED, PlantStatus.REMOVED),
                        selected = shown,
                        label = { it?.let { status -> stringResource(status.label) } ?: stringResource(R.string.graveyard_all) },
                        onChoose = { shown = it },
                    )
                    if (plants != null && ended.isEmpty()) Text(stringResource(R.string.graveyard_empty), style = MaterialTheme.typography.bodyLarge)
                }
            }
            items(ended, key = { it.plant.id }) { view -> GraveRow(view, garden) { onOpenPlant(view.plant.id) } }
        }
    }
}

@Composable
private fun GraveRow(view: PlantView, garden: GardenRepository, onClick: () -> Unit) {
    val history by produceState<PlantHistory?>(null, view.plant.id) { value = garden.historyOf(view.plant.id) }
    val plant = view.plant
    val lines = listOfNotNull(
        endedText(plant.status, plant.endedOn),
        view.zoneName?.let { stringResource(R.string.graveyard_grew_in, it) },
        plant.lived?.let { livedText(it) },
        history?.let { historyText(it) },
    )
    ListItem(
        onClick = onClick,
        // Greyed, with the status said in words beside the date, so it does not rest on the shade alone.
        leadingContent = { PlantAvatar(view.species?.form, 48.dp, modifier = Modifier.alpha(0.6f), thumbnail = plant.thumbnail) },
        supportingContent = {
            Column {
                view.scientificName?.let { Text(it, fontStyle = FontStyle.Italic) }
                lines.forEach { Text(it) }
            }
        },
    ) { Text(view.title) }
}

/** "Died on 3 March 2026", "Removed on …", or the status alone when the day was not kept. */
@Composable
fun endedText(status: PlantStatus, day: java.time.LocalDate?): String? = when {
    status == PlantStatus.GROWING -> null
    day == null -> stringResource(status.label)
    status == PlantStatus.DIED -> stringResource(R.string.graveyard_died_on, UiFormat.longDate(day))
    else -> stringResource(R.string.graveyard_removed_on, UiFormat.longDate(day))
}

/** "Lived 1 year 4 months", or "Lived less than a month". */
@Composable
fun livedText(period: Period): String {
    val parts = listOfNotNull(
        period.years.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.lived_years, it, it) },
        period.months.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.lived_months, it, it) },
    )
    return if (parts.isEmpty()) stringResource(R.string.graveyard_lived_short) else stringResource(R.string.graveyard_lived, parts.joinToString(" "))
}

/** What a plant's history holds, such as "4 photos · 2 harvests", leaving out what it has none of. */
@Composable
private fun historyText(history: PlantHistory): String? = listOfNotNull(
    history.photos.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.history_photos, it, it) },
    history.flowering.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.history_flowering, it, it) },
    history.harvests.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.history_harvests, it, it) },
    history.problems.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.history_problems, it, it) },
    history.care.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.history_care, it, it) },
).joinToString(" · ").ifEmpty { null }
