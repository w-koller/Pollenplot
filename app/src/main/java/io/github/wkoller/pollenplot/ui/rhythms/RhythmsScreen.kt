package io.github.wkoller.pollenplot.ui.rhythms

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.data.RhythmsState
import io.github.wkoller.pollenplot.data.RhythmsViews
import io.github.wkoller.pollenplot.domain.rhythms.BeeGroup
import io.github.wkoller.pollenplot.domain.rhythms.ForageLevel
import io.github.wkoller.pollenplot.domain.rhythms.Lane
import io.github.wkoller.pollenplot.domain.rhythms.RhythmPlant
import io.github.wkoller.pollenplot.domain.season.MonthSet
import io.github.wkoller.pollenplot.ui.UiFormat
import io.github.wkoller.pollenplot.ui.components.EmptyState
import io.github.wkoller.pollenplot.ui.components.TabScaffold
import io.github.wkoller.pollenplot.ui.components.largeText
import io.github.wkoller.pollenplot.ui.listWords
import io.github.wkoller.pollenplot.ui.settings.SectionHeading
import java.time.Month
import java.time.format.TextStyle

/**
 * Pollen Rhythms: what the garden's flowers offer honey bees, stingless bees and solitary bees through the year, the
 * gaps in each group's year with plants that would fill them, what is flowering now and next month, and each plant's
 * flowering, expected and seen in photos.
 */
@Composable
fun RhythmsScreen(rhythms: RhythmsViews, onOpenSettings: () -> Unit, onOpenPlant: (String) -> Unit, onAddSpecies: (String) -> Unit) {
    val state by rhythms.state.collectAsStateWithLifecycle(initialValue = null)
    TabScaffold(
        title = stringResource(R.string.rhythms_title),
        subtitle = stringResource(R.string.rhythms_subtitle),
        onOpenSettings = onOpenSettings,
    ) { padding ->
        val loaded = state ?: return@TabScaffold
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            if (loaded.plants.isEmpty()) {
                item {
                    EmptyState(icon = R.drawable.ic_local_florist, title = stringResource(R.string.rhythms_empty_title), body = stringResource(R.string.rhythms_empty_body))
                }
                return@LazyColumn
            }
            item(key = "intro") {
                Text(stringResource(R.string.rhythms_intro), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            feed(loaded)
            item(key = "calendar") { Calendar(loaded) }
            item(key = "how") {
                Text(
                    stringResource(R.string.rhythms_how),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            for (lane in loaded.lanes) {
                if (lane.gaps.isEmpty()) continue
                val suggestions = loaded.suggestions[lane.group].orEmpty()
                item(key = "gaps-" + lane.group) {
                    val words = listWords()
                    val group = stringResource(lane.group.label)
                    Column {
                        SectionHeading(stringResource(R.string.rhythms_gaps, group.lowercase()))
                        Text(
                            stringResource(R.string.rhythms_gap_months, UiFormat.months(MonthSet.of(lane.gaps), words, locale())),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        if (suggestions.isEmpty()) {
                            Text(stringResource(R.string.rhythms_gap_none), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
                        }
                    }
                }
                items(suggestions, key = { "suggest-" + lane.group + it.species.name }) { suggestion ->
                    val words = listWords()
                    val details = listOfNotNull(
                        stringResource(R.string.rhythms_fills, UiFormat.months(MonthSet.of(suggestion.fills), words, locale())),
                        if (suggestion.nativeHere) stringResource(R.string.rhythms_native_here) else null,
                        suggestion.zoneName?.let { stringResource(R.string.rhythms_suits, it) },
                    )
                    ListItem(
                        onClick = { onAddSpecies(suggestion.species.name) },
                        leadingContent = { Icon(painterResource(R.drawable.ic_local_florist), contentDescription = null) },
                        supportingContent = { Text(details.joinToString(" · ")) },
                    ) { Text(suggestion.species.displayName) }
                }
            }
            item(key = "now") { SectionHeading(stringResource(R.string.rhythms_now)) }
            if (loaded.nowFlowering.isEmpty()) {
                item(key = "now-none") {
                    Text(stringResource(R.string.rhythms_now_none), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
            items(loaded.nowFlowering, key = { "now-" + it.id }) { plant ->
                ListItem(
                    onClick = { onOpenPlant(plant.id) },
                    leadingContent = { Icon(painterResource(R.drawable.ic_local_florist), contentDescription = null) },
                    supportingContent = { Text(stringResource(if (loaded.month in plant.seenFlowering) R.string.rhythms_seen else R.string.rhythms_expected)) },
                ) { Text(plant.title) }
            }
            if (loaded.startingNextMonth.isNotEmpty()) {
                item(key = "next") { SectionHeading(stringResource(R.string.rhythms_next)) }
                items(loaded.startingNextMonth, key = { "next-" + it.id }) { plant ->
                    ListItem(onClick = { onOpenPlant(plant.id) }, leadingContent = { Icon(painterResource(R.drawable.ic_calendar_month), contentDescription = null) }) { Text(plant.title) }
                }
            }
            item(key = "plants") { SectionHeading(stringResource(R.string.rhythms_plants)) }
            item(key = "plants-head") { MonthHeader(loaded.month, Modifier.padding(horizontal = 16.dp)) }
            items(loaded.plants, key = { "row-" + it.id }) { plant -> PlantRow(plant, loaded.month) }
            item(key = "plants-key") { PlantKey() }
        }
    }
}

/** This month's cards: rich nectar and stingless forage for those who keep bees, pollinators seen, and timing shifts. */
private fun LazyListScope.feed(state: RhythmsState) {
    val nectar = if (state.keepsHoneyBees) state.richNectar else emptyList()
    val stingless = if (state.keepsStinglessBees) state.forStingless else emptyList()
    if (nectar.isEmpty() && stingless.isEmpty() && state.visits.isEmpty() && state.shifts.isEmpty()) return
    item(key = "feed") { SectionHeading(stringResource(R.string.rhythms_month)) }
    if (nectar.isNotEmpty()) {
        item(key = "feed-nectar") {
            val words = listWords()
            val names = nectar.map { it.title }
            FeedCard(
                R.drawable.ic_hive,
                stringResource(R.string.rhythms_nectar),
                if (names.size == 1) stringResource(R.string.rhythms_nectar_body, names[0]) else stringResource(R.string.rhythms_nectar_body_many, UiFormat.joinAnd(names, words)),
            )
        }
    }
    if (stingless.isNotEmpty()) {
        item(key = "feed-stingless") {
            val words = listWords()
            val names = stingless.map { it.title }
            FeedCard(
                R.drawable.ic_emoji_nature,
                stringResource(R.string.rhythms_stingless),
                if (names.size == 1) stringResource(R.string.rhythms_stingless_body, names[0]) else stringResource(R.string.rhythms_stingless_body_many, UiFormat.joinAnd(names, words)),
            )
        }
    }
    if (state.visits.isNotEmpty()) {
        item(key = "feed-visits") {
            val lines = mutableListOf<String>()
            for (visit in state.visits) {
                val name = visit.animal?.displayName ?: visit.sighting.label
                lines += stringResource(R.string.rhythms_visit, name, visit.plantTitle.orEmpty()) + " · " + UiFormat.shortDate(visit.sighting.seenAt)
            }
            FeedCard(R.drawable.ic_raven, stringResource(R.string.rhythms_visits), lines.joinToString("\n"))
        }
    }
    if (state.shifts.isNotEmpty()) {
        item(key = "feed-timing") {
            val lines = mutableListOf<String>()
            for (shift in state.shifts) {
                val title = state.title(shift.plantId) ?: continue
                val days = kotlin.math.abs(shift.days)
                lines += stringResource(if (shift.days < 0) R.string.rhythms_earlier else R.string.rhythms_later, title, pluralStringResource(R.plurals.rhythms_days, days, days))
            }
            if (lines.isNotEmpty()) FeedCard(R.drawable.ic_calendar_month, stringResource(R.string.rhythms_timing), lines.joinToString("\n"))
        }
    }
}

@Composable
private fun FeedCard(icon: Int, title: String, body: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(painterResource(icon), contentDescription = null)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(body, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@get:StringRes
private val BeeGroup.label: Int
    get() = when (this) {
        BeeGroup.HONEY -> R.string.bee_group_honey
        BeeGroup.STINGLESS -> R.string.bee_group_stingless
        BeeGroup.SOLITARY -> R.string.bee_group_solitary
    }

@get:StringRes
private val ForageLevel.label: Int
    get() = when (this) {
        ForageLevel.PLENTY -> R.string.forage_plenty
        ForageLevel.SOME -> R.string.forage_some
        ForageLevel.LITTLE -> R.string.forage_little
        ForageLevel.NONE -> R.string.forage_none
    }

@Composable
private fun locale() = LocalConfiguration.current.locales[0]

/** The three lanes, a month to a column, with the level shown by dots and by shade, and a key that says what each is. */
@Composable
private fun Calendar(state: RhythmsState) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.rhythms_calendar), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        MonthHeader(state.month)
        state.lanes.forEach { lane -> LaneRow(lane, state.month) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            ForageLevel.entries.reversed().forEach { level ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    LevelCell(level, current = false, modifier = Modifier.size(width = 20.dp, height = 24.dp))
                    Text(stringResource(level.label), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private val NAME_WIDTH = 96.dp

/** The width of the names beside each row of months, or none when large text puts the names above. */
@Composable
private fun nameWidth(): Dp = if (largeText()) 0.dp else NAME_WIDTH

@Composable
private fun MonthHeader(current: Month, modifier: Modifier = Modifier) {
    val locale = locale()
    Row(modifier.fillMaxWidth().clearAndSetSemantics {}) {
        Spacer(Modifier.width(nameWidth()))
        Month.entries.forEach { month ->
            Text(
                month.getDisplayName(TextStyle.NARROW, locale),
                style = MaterialTheme.typography.labelMedium,
                color = if (month == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** One group's year, read by TalkBack as a sentence, such as "Honey bees. Plenty in August to October." */
@Composable
private fun LaneRow(lane: Lane, current: Month) {
    val words = listWords()
    val locale = locale()
    val group = stringResource(lane.group.label)
    val parts = mutableListOf<String>()
    for (level in ForageLevel.entries.reversed()) {
        val months = Month.entries.filter { lane.level(it) == level }
        if (months.isNotEmpty() && lane.fed) parts += stringResource(R.string.rhythms_level_in, stringResource(level.label), UiFormat.months(MonthSet.of(months), words, locale))
    }
    val summary = stringResource(R.string.rhythms_lane_summary, group, if (lane.fed) parts.joinToString(". ") else stringResource(R.string.rhythms_lane_unfed))
    val cells: @Composable RowScope.() -> Unit = {
        Month.entries.forEach { month -> LevelCell(lane.level(month), current = month == current, modifier = Modifier.weight(1f).height(32.dp).padding(1.dp)) }
    }
    if (largeText()) {
        Column(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = summary }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(group, style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), content = cells)
        }
    } else {
        Row(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = summary }, verticalAlignment = Alignment.CenterVertically) {
            Text(group, style = MaterialTheme.typography.bodyMedium, maxLines = 2, modifier = Modifier.width(NAME_WIDTH))
            cells()
        }
    }
}

/** A month's level: no dots and a pale cell for nothing, up to three dots on a deeper shade for plenty. */
@Composable
private fun LevelCell(level: ForageLevel, current: Boolean, modifier: Modifier) {
    val colours = MaterialTheme.colorScheme
    val background = when (level) {
        ForageLevel.NONE -> colours.surfaceVariant
        ForageLevel.LITTLE -> colours.secondaryContainer
        ForageLevel.SOME -> colours.primaryContainer
        ForageLevel.PLENTY -> colours.primary
    }
    val dot = if (level == ForageLevel.PLENTY) colours.onPrimary else colours.onSurface
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier.background(background, shape).then(if (current) Modifier.border(2.dp, colours.tertiary, shape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (level == ForageLevel.NONE) {
                Box(Modifier.size(width = 8.dp, height = 2.dp).background(colours.onSurfaceVariant))
            } else {
                repeat(level.ordinal) { Box(Modifier.size(4.dp).background(dot, CircleShape)) }
            }
        }
    }
}

/** One plant's year: its expected flowering shaded, its peak deeper, and a dot where photos showed it flowering. */
@Composable
private fun PlantRow(plant: RhythmPlant, current: Month) {
    val words = listWords()
    val locale = locale()
    val expected = if (plant.flowering.isEmpty) stringResource(R.string.rhythms_not_recorded) else stringResource(R.string.rhythms_expected_in, UiFormat.months(plant.flowering, words, locale))
    val seen = if (plant.seenFlowering.isEmpty) "" else ", " + stringResource(R.string.rhythms_seen_in, UiFormat.months(plant.seenFlowering, words, locale))
    val summary = stringResource(R.string.rhythms_plant_summary, plant.title, expected + seen)
    val cells: @Composable RowScope.() -> Unit = {
        Month.entries.forEach { month -> FloweringCell(month in plant.flowering, month in plant.peak, month in plant.seenFlowering, month == current) }
    }
    val modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp).clearAndSetSemantics { contentDescription = summary }
    if (largeText()) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(plant.title, style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), content = cells)
        }
    } else {
        Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            Text(plant.title, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(NAME_WIDTH).padding(end = 4.dp))
            cells()
        }
    }
}

@Composable
private fun RowScope.FloweringCell(expected: Boolean, peak: Boolean, seen: Boolean, current: Boolean) {
    val colours = MaterialTheme.colorScheme
    val background = when {
        peak -> colours.primary
        expected -> colours.primaryContainer
        else -> Color.Transparent
    }
    val shape = RoundedCornerShape(4.dp)
    Box(
        Modifier.weight(1f).height(20.dp).padding(1.dp).background(background, shape).then(if (current) Modifier.border(1.dp, colours.tertiary, shape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (seen) Box(Modifier.size(6.dp).background(if (peak) colours.onPrimary else colours.onSurface, CircleShape))
    }
}

/** What the shades and the dot in a plant's row mean. */
@Composable
private fun PlantKey() {
    val colours = MaterialTheme.colorScheme
    FlowRow(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 20.dp, height = 16.dp).background(colours.primaryContainer, RoundedCornerShape(4.dp)))
            Text(stringResource(R.string.rhythms_expected), style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 20.dp, height = 16.dp).background(colours.primary, RoundedCornerShape(4.dp)))
            Text(stringResource(R.string.rhythms_peak), style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 20.dp, height = 16.dp), contentAlignment = Alignment.Center) { Box(Modifier.size(6.dp).background(colours.onSurface, CircleShape)) }
            Text(stringResource(R.string.rhythms_seen), style = MaterialTheme.typography.bodySmall)
        }
    }
}
