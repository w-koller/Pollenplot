package io.github.wkoller.pollenplot.ui.assistant

import io.github.wkoller.pollenplot.ui.theme.PollenTheme
import androidx.compose.ui.graphics.Color
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.ai.AssistantReply
import io.github.wkoller.pollenplot.domain.assistant.GardenPulse
import io.github.wkoller.pollenplot.domain.assistant.MidSentence
import io.github.wkoller.pollenplot.domain.assistant.PartOfDay
import io.github.wkoller.pollenplot.domain.assistant.Starter
import io.github.wkoller.pollenplot.domain.assistant.StarterKind
import io.github.wkoller.pollenplot.domain.rhythms.BeeGroup
import io.github.wkoller.pollenplot.domain.season.Season
import io.github.wkoller.pollenplot.ui.UiFormat
import io.github.wkoller.pollenplot.ui.components.PollenLoadingIndicator
import io.github.wkoller.pollenplot.ui.components.PollenShapes
import io.github.wkoller.pollenplot.ui.listWords
import io.github.wkoller.pollenplot.ui.theme.IconSize
import io.github.wkoller.pollenplot.ui.theme.Spacing
import java.time.format.TextStyle

/** The question a starter or follow-up asks, in the user's own voice. */
@Composable
fun starterText(starter: Starter): String {
    val names = starter.names
    fun name(i: Int) = names.getOrElse(i) { "" }
    val locale = LocalConfiguration.current.locales[0]
    return when (starter.kind) {
        StarterKind.HOT_DAY -> stringResource(R.string.ask_start_hot_day, starter.date?.dayOfWeek?.getDisplayName(TextStyle.FULL, locale).orEmpty())
        StarterKind.FOLLOW_UP -> stringResource(R.string.ask_start_follow_up, MidSentence.name(name(0)), MidSentence.name(name(1)))
        StarterKind.VISITOR_ON_PLANT -> stringResource(R.string.ask_start_visitor_on_plant, name(0), MidSentence.name(name(1)))
        StarterKind.VISITOR -> stringResource(R.string.ask_start_visitor, name(0))
        StarterKind.FUNGUS -> stringResource(R.string.ask_start_fungus, name(0))
        StarterKind.IN_FLOWER_ZONE -> stringResource(R.string.ask_start_in_flower_zone, name(0))
        StarterKind.IN_FLOWER -> stringResource(R.string.ask_start_in_flower)
        StarterKind.GAP -> stringResource(R.string.ask_start_gap, bees(starter.group), starter.month?.getDisplayName(TextStyle.FULL, locale).orEmpty())
        StarterKind.UNFED -> stringResource(R.string.ask_start_unfed, bees(starter.group))
        StarterKind.SEASON_BEES -> stringResource(R.string.ask_start_season_bees, season(starter.season))
        StarterKind.SEASON_BIRDS -> stringResource(R.string.ask_start_season_birds, season(starter.season))
        StarterKind.SEASON_JOBS -> stringResource(R.string.ask_start_season_jobs, season(starter.season))
        StarterKind.FROGS -> stringResource(R.string.ask_start_frogs)
        StarterKind.PLANT_FLOWERING -> stringResource(R.string.ask_start_plant_flowering, MidSentence.name(name(0)))
        StarterKind.PLANT_BEES -> stringResource(R.string.ask_start_plant_bees, MidSentence.name(name(0)))
        StarterKind.PLANT_PRUNING -> stringResource(R.string.ask_start_plant_pruning, MidSentence.name(name(0)))
        StarterKind.PLANT_SPOT -> stringResource(R.string.ask_start_plant_spot, MidSentence.name(name(0)))
        StarterKind.ZONE_BEES -> stringResource(R.string.ask_start_zone_bees, name(0))
        StarterKind.ANIMAL_NEEDS -> stringResource(R.string.ask_start_animal_needs, MidSentence.name(name(0)))
        StarterKind.ANIMAL_FOOD -> stringResource(R.string.ask_start_animal_food, MidSentence.name(name(0)))
        StarterKind.ANIMAL_PLANTS -> stringResource(R.string.ask_start_animal_plants, MidSentence.name(name(0)))
    }
}

@Composable
private fun bees(group: BeeGroup?): String = stringResource(
    when (group) {
        BeeGroup.HONEY -> R.string.ask_group_honey
        BeeGroup.STINGLESS -> R.string.ask_group_stingless
        else -> R.string.ask_group_solitary
    },
)

@Composable
private fun season(season: Season?): String = stringResource(
    when (season) {
        Season.SUMMER -> R.string.harvest_summer
        Season.AUTUMN -> R.string.harvest_autumn
        Season.WINTER -> R.string.harvest_winter
        else -> R.string.harvest_spring
    },
)

@DrawableRes
private fun Starter.icon(): Int = when (kind) {
    StarterKind.HOT_DAY -> R.drawable.ic_sunny
    StarterKind.FOLLOW_UP -> R.drawable.ic_bug_report
    StarterKind.VISITOR_ON_PLANT, StarterKind.VISITOR, StarterKind.ANIMAL_NEEDS, StarterKind.ANIMAL_FOOD, StarterKind.ANIMAL_PLANTS -> R.drawable.ic_butterfly
    StarterKind.FUNGUS -> R.drawable.ic_mushroom
    StarterKind.IN_FLOWER_ZONE, StarterKind.IN_FLOWER, StarterKind.PLANT_FLOWERING -> R.drawable.ic_local_florist
    StarterKind.GAP, StarterKind.UNFED, StarterKind.PLANT_PRUNING -> R.drawable.ic_calendar_month
    StarterKind.SEASON_BEES, StarterKind.PLANT_BEES, StarterKind.ZONE_BEES -> R.drawable.ic_native_bee
    StarterKind.SEASON_BIRDS -> R.drawable.ic_raven
    StarterKind.SEASON_JOBS, StarterKind.PLANT_SPOT -> R.drawable.ic_potted_plant
    StarterKind.FROGS -> R.drawable.ic_frog
}

/**
 * The colours of a question's icon, by what it is about, as the rest of the app gives them: the bees' wattle gold,
 * flowers' bloom, animals' banksia rust, and leaf green for the garden's plants and jobs.
 */
@Composable
private fun Starter.tone(): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    val bloom = PollenTheme.colors.bloom
    return when (kind) {
        StarterKind.HOT_DAY, StarterKind.GAP, StarterKind.UNFED, StarterKind.SEASON_BEES, StarterKind.PLANT_BEES, StarterKind.ZONE_BEES ->
            scheme.secondaryContainer to scheme.onSecondaryContainer
        StarterKind.IN_FLOWER_ZONE, StarterKind.IN_FLOWER, StarterKind.PLANT_FLOWERING -> bloom.container to bloom.onContainer
        StarterKind.VISITOR_ON_PLANT, StarterKind.VISITOR, StarterKind.FUNGUS, StarterKind.SEASON_BIRDS, StarterKind.FROGS,
        StarterKind.ANIMAL_NEEDS, StarterKind.ANIMAL_FOOD, StarterKind.ANIMAL_PLANTS,
        -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        StarterKind.FOLLOW_UP, StarterKind.SEASON_JOBS, StarterKind.PLANT_PRUNING, StarterKind.PLANT_SPOT -> scheme.primaryContainer to scheme.onPrimaryContainer
    }
}

/**
 * The greeting at the top of Ask your garden, with what the garden is doing now: how many plants are in flower and how
 * many new visitors there have been.
 */
@Composable
fun Greeting(pulse: GardenPulse) {
    val hello = stringResource(
        when (pulse.partOfDay) {
            PartOfDay.MORNING -> R.string.ask_greeting_morning
            PartOfDay.AFTERNOON -> R.string.ask_greeting_afternoon
            PartOfDay.EVENING -> R.string.ask_greeting_evening
        },
    )
    val news = listOfNotNull(
        pulse.inFlower.size.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.ask_pulse_flowering, it, it) },
        pulse.visitors.size.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.ask_pulse_visitors, it, it) },
    )
    val sentence = if (news.isEmpty()) null else stringResource(R.string.ask_pulse, UiFormat.joinAnd(news, listWords()))
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.l), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(56.dp).clip(PollenShapes.flower()).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_local_florist), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(hello, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            sentence?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        }
    }
}

/**
 * The questions to start with, each a card that asks it when pressed, and More ideas when there are more than fit.
 */
@Composable
fun Starters(starters: List<Starter>, hasMore: Boolean, onAsk: (Starter) -> Unit, onMore: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(stringResource(R.string.ask_try), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        starters.forEach { starter -> StarterCard(starter) { onAsk(starter) } }
        if (hasMore) {
            TextButton(onClick = onMore) {
                Icon(painterResource(R.drawable.ic_refresh), contentDescription = null, modifier = Modifier.size(IconSize.small))
                Text(stringResource(R.string.ask_more_ideas), modifier = Modifier.padding(start = Spacing.s))
            }
        }
    }
}

@Composable
private fun StarterCard(starter: Starter, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(Spacing.m), horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
            val (container, content) = starter.tone()
            Box(Modifier.size(40.dp).clip(PollenShapes.cookie()).background(container), contentAlignment = Alignment.Center) {
                Icon(painterResource(starter.icon()), contentDescription = null, tint = content, modifier = Modifier.size(IconSize.small))
            }
            Text(starterText(starter), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        }
    }
}

/** What to ask next after an answer, as chips that ask it when pressed. */
@Composable
fun FollowUps(followUps: List<Starter>, onAsk: (Starter) -> Unit) {
    if (followUps.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(stringResource(R.string.ask_next), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            followUps.forEach { starter ->
                SuggestionChip(
                    onClick = { onAsk(starter) },
                    label = { Text(starterText(starter)) },
                    icon = { Icon(painterResource(starter.icon()), contentDescription = null, modifier = Modifier.size(IconSize.small)) },
                )
            }
        }
    }
}

/**
 * What the assistant is doing before its first words: reading the garden's records, counted, or [about], the words
 * for the one plant or animal the question is about. It is read out when it appears.
 */
@Composable
fun ReadingLine(reply: AssistantReply.Reading, about: String?) {
    val counts = listOfNotNull(
        reply.plants.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.ask_reading_plants, it, it) },
        reply.zones.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.ask_reading_zones, it, it) },
        reply.sightings.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.ask_reading_sightings, it, it) },
    )
    val text = when {
        about != null -> about
        counts.isNotEmpty() -> stringResource(R.string.ask_reading_garden, UiFormat.joinAnd(counts, listWords()))
        else -> stringResource(R.string.ask_reading_summary)
    }
    Row(
        Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PollenLoadingIndicator(Modifier.size(32.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
