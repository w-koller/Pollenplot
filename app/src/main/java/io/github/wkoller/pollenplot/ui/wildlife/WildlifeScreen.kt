package io.github.wkoller.pollenplot.ui.wildlife

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.data.GardenViews
import io.github.wkoller.pollenplot.data.Locality
import io.github.wkoller.pollenplot.data.kb.KnowledgeRepository
import io.github.wkoller.pollenplot.domain.wildlife.Biodiversity
import io.github.wkoller.pollenplot.ui.components.AddMenu
import io.github.wkoller.pollenplot.ui.components.Badge
import io.github.wkoller.pollenplot.ui.components.EmptyState
import io.github.wkoller.pollenplot.ui.components.TabScaffold
import io.github.wkoller.pollenplot.ui.nav.AddAction
import io.github.wkoller.pollenplot.ui.plants.BadgeTone
import io.github.wkoller.pollenplot.ui.review.readable
import io.github.wkoller.pollenplot.ui.settings.SectionHeading
import java.time.Clock
import java.time.LocalDate
import java.time.Month

/**
 * The animals and fungi seen in the yard, a year at a time: how many kinds and how many of them are native, when in
 * the year they were seen, what to know about any that are venomous or that the law asks about, each group's kinds,
 * and the year's sightings, the latest first.
 */
@Composable
fun WildlifeScreen(
    views: GardenViews,
    knowledge: KnowledgeRepository,
    clock: Clock,
    onOpenSettings: () -> Unit,
    onAdd: (AddAction) -> Unit,
    onOpenAnimal: (String) -> Unit,
    onOpenSighting: (String) -> Unit,
) {
    val sightings by views.sightings.collectAsStateWithLifecycle(initialValue = null)
    val fauna by knowledge.fauna.collectAsStateWithLifecycle()
    val locality by views.locality.collectAsStateWithLifecycle(initialValue = Locality.NONE)
    val zone = clock.zone
    val today = remember(clock) { LocalDate.now(clock) }
    var year by rememberSaveable { mutableIntStateOf(today.year) }
    var showAll by rememberSaveable { mutableStateOf(false) }
    val loaded = sightings
    val records = remember(loaded) { loaded?.map { it.sighting }.orEmpty() }
    val yearsSeen = remember(records, zone) { Biodiversity.years(records, zone) }
    val summary = remember(records, fauna, year, zone) { Biodiversity.of(records, fauna, year, zone) }

    TabScaffold(
        title = stringResource(R.string.wildlife_title),
        subtitle = stringResource(R.string.wildlife_subtitle),
        onOpenSettings = onOpenSettings,
        floatingActionButton = { if (loaded != null) AddMenu(AddAction.WILDLIFE, onAdd) },
    ) { padding ->
        when {
            loaded == null -> Unit
            loaded.isEmpty() -> LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
                item {
                    EmptyState(
                        icon = R.drawable.ic_raven,
                        title = stringResource(R.string.wildlife_empty_title),
                        body = stringResource(R.string.wildlife_empty_body),
                    )
                }
                item {
                    FlowRow(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(onClick = { onAdd(AddAction.IDENTIFY) }) {
                            Icon(painterResource(R.drawable.ic_photo_camera), contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                            Text(stringResource(R.string.add_identify_camera))
                        }
                        OutlinedButton(onClick = { onAdd(AddAction.SIGHTING) }) { Text(stringResource(R.string.add_sighting_by_name)) }
                    }
                }
            }
            else -> {
                val inYear = loaded.filter { it.sighting.seenAt.atZone(zone).year == year }
                val shown = if (showAll) inYear else inYear.take(RECENT)
                val notices = summary.notices(locality.state)
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + FAB_ROOM.dp),
                ) {
                    val years = (yearsSeen + today.year).distinct().sortedDescending()
                    if (years.size > 1) {
                        item(key = "years") {
                            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                years.forEach { y ->
                                    FilterChip(selected = y == year, onClick = { year = y; showAll = false }, label = { Text(y.toString()) })
                                }
                            }
                        }
                    }
                    item(key = "summary") { Summary(summary, isThisYear = year == today.year, firstYear = yearsSeen.lastOrNull(), current = today.month.takeIf { year == today.year }) }
                    if (notices.isNotEmpty()) {
                        item(key = "notices") { SectionHeading(stringResource(R.string.wildlife_notices)) }
                        items(notices, key = { "notice-" + it.species.key }) { notice ->
                            Column {
                                SeenSpeciesRow(notice.species, onClick = { onOpenAnimal(notice.species.key) })
                                notice.species.animal?.let { SafetyNotice(it, locality.state, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) }
                            }
                        }
                    }
                    summary.byGroup.forEach { (group, kinds) ->
                        item(key = "group-$group") { SectionHeading(pluralStringResource(R.plurals.wildlife_group_heading, kinds.size, stringResource(group.plural), kinds.size)) }
                        items(kinds, key = { "kind-" + it.key }) { seen -> SeenSpeciesRow(seen, onClick = { onOpenAnimal(seen.key) }) }
                    }
                    if (inYear.isNotEmpty()) {
                        item(key = "recent") { SectionHeading(stringResource(R.string.wildlife_recent)) }
                        items(shown, key = { "sighting-" + it.sighting.id }) { view -> SightingRow(view, onClick = { onOpenSighting(view.sighting.id) }) }
                        if (!showAll && inYear.size > RECENT) {
                            item(key = "all") {
                                TextButton(onClick = { showAll = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
                                    Text(pluralStringResource(R.plurals.wildlife_show_all, inYear.size, inYear.size))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** How many kinds were seen in the year, how many are native or new, each group's number, and the months. */
@Composable
private fun Summary(summary: Biodiversity, isThisYear: Boolean, firstYear: Int?, current: Month?) {
    val count = summary.species.size
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.readable().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val title = when {
                count == 0 -> stringResource(R.string.wildlife_none_in_year, summary.year)
                isThisYear -> pluralStringResource(R.plurals.wildlife_species_this_year, count, count)
                else -> pluralStringResource(R.plurals.wildlife_species_in_year, count, count, summary.year)
            }
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            if (count > 0) {
                when {
                    summary.nativeCount > 0 -> Text(pluralStringResource(R.plurals.wildlife_native, summary.nativeCount, summary.nativeCount), style = MaterialTheme.typography.bodyLarge)
                    summary.species.none { it.animal != null } -> Text(stringResource(R.string.wildlife_native_unknown), style = MaterialTheme.typography.bodyLarge)
                }
                // In the first year of records everything is new, so this only says something in later years.
                if (summary.newCount > 0 && firstYear != null && summary.year > firstYear) {
                    Text(pluralStringResource(R.plurals.wildlife_new, summary.newCount, summary.newCount), style = MaterialTheme.typography.bodyLarge)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    summary.byGroup.forEach { (group, kinds) ->
                        Badge(group.icon, stringResource(R.string.wildlife_group_chip, stringResource(group.plural), kinds.size), BadgeTone.WILDLIFE)
                    }
                }
                Text(stringResource(R.string.wildlife_by_month), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                MonthStrip(summary.months, current)
            }
        }
    }
}

/** Sightings shown before Show all. */
private const val RECENT = 10

/** Room under the list for the add button, so it never covers the last row. */
private const val FAB_ROOM = 96
