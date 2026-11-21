package io.github.wkoller.pollenplot.ui.plot

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.data.TodayItem
import io.github.wkoller.pollenplot.data.TodayState
import io.github.wkoller.pollenplot.domain.bees.honey.HiveTaskKind
import io.github.wkoller.pollenplot.domain.garden.PotTaskKind
import io.github.wkoller.pollenplot.ui.UiFormat
import io.github.wkoller.pollenplot.ui.bees.degrees
import io.github.wkoller.pollenplot.ui.components.ToneColours
import io.github.wkoller.pollenplot.ui.components.largeText
import io.github.wkoller.pollenplot.ui.listWords
import io.github.wkoller.pollenplot.ui.theme.IconSize
import io.github.wkoller.pollenplot.ui.theme.PollenTheme
import io.github.wkoller.pollenplot.ui.theme.Spacing

/**
 * What the garden needs today, at the top of the Plot: watering due, hive checks due or overdue, a hot day for
 * stingless bees, hotels to clean out in spring, pots to flush or look at repotting, finds to review and weeds. Each line
 * opens where it is done: the hive, the hotel, the zone's feed, the plant's pruning. A watering and a pot's flush, which
 * need nothing more said, also have a button that records them on the spot.
 */
@Composable
fun TodayCard(
    state: TodayState,
    modifier: Modifier = Modifier,
    /** The most height the card may take, beyond which its lines scroll inside it. */
    maxHeight: Dp = Dp.Unspecified,
    onWater: () -> Unit,
    onBees: () -> Unit,
    onHeat: () -> Unit,
    onReview: () -> Unit,
    onWeeds: () -> Unit,
    onPlant: (String) -> Unit = {},
    onZone: (String) -> Unit = {},
    onFeed: () -> Unit = {},
    onProblem: (String) -> Unit = {},
    onHive: (String) -> Unit = { onBees() },
    onHotel: (String) -> Unit = { onBees() },
    onFeedZone: (String) -> Unit = onZone,
    onPrune: (String) -> Unit = onPlant,
    onCompost: (String) -> Unit = {},
    /** Records a compost bin as turned today; without it the line only opens the bin. */
    onTurned: ((TodayItem.Compost) -> Unit)? = null,
    /** Records a watering that was due as done today; without it the line only opens the Water tab. */
    onWatered: ((TodayItem.Watering) -> Unit)? = null,
    /** Records a pot's flush as done today; without it the line only opens the plant. */
    onFlushed: ((TodayItem.PotCare) -> Unit)? = null,
) {
    if (state.isEmpty) return
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth().heightIn(max = maxHeight),
    ) {
        // The card closes up smoothly when a job is done and its line leaves.
        Column(
            Modifier.animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())
                .then(if (maxHeight.isSpecified) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(vertical = Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            Text(
                stringResource(R.string.today_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.xs).semantics { heading() },
            )
            // A hive's checks share one line, so a new hive does not fill the card.
            val checks = state.items.filterIsInstance<TodayItem.HiveCheck>().groupBy { it.hive }
            for ((hive, due) in checks) {
                val words = listWords()
                val kinds = mutableListOf<String>()
                for (check in due) kinds += stringResource(check.kind.noun)
                val overdue = due.any { it.overdue }
                Line(
                    if (overdue) R.drawable.ic_warning else R.drawable.ic_hive,
                    if (overdue) JobKind.URGENT else JobKind.BEES,
                    stringResource(R.string.today_hive_due, hive, UiFormat.joinAnd(kinds, words)) + if (overdue) " · " + stringResource(R.string.today_overdue) else "",
                    onClick = { due.firstNotNullOfOrNull { it.hiveId }?.let(onHive) ?: onBees() },
                )
            }
            for (item in state.items) {
                when (item) {
                    is TodayItem.Watering -> Line(
                        R.drawable.ic_water_drop,
                        JobKind.WATER,
                        item.minutes?.let { pluralStringResource(R.plurals.today_water_minutes, it, item.name, it) }
                            ?: stringResource(R.string.today_water_litres, item.name, item.litres.toString()),
                        onClick = onWater,
                        done = onWatered?.let { record -> DoneButton(stringResource(R.string.today_watered), stringResource(R.string.today_watered_for, item.name)) { record(item) } },
                    )
                    is TodayItem.HiveCheck -> Unit
                    is TodayItem.HotDay -> Line(
                        R.drawable.ic_warning,
                        JobKind.URGENT,
                        stringResource(if (item.today) R.string.notify_heat_today else R.string.notify_heat_tomorrow, degrees(item.maxTenthsC)),
                        onClick = onHeat,
                    )
                    is TodayItem.CleanHotel -> Line(R.drawable.ic_cottage, JobKind.BEES, stringResource(R.string.today_clean_hotel, item.hotel), onClick = { item.hotelId?.let(onHotel) ?: onBees() })
                    is TodayItem.PotCare -> Line(
                        R.drawable.ic_potted_plant,
                        JobKind.PLANT,
                        stringResource(if (item.kind == PotTaskKind.FLUSH) R.string.today_pot_flush else R.string.today_pot_repot, item.plant),
                        onClick = { onPlant(item.plantId) },
                        done = onFlushed?.takeIf { item.kind == PotTaskKind.FLUSH }?.let { record ->
                            DoneButton(stringResource(R.string.today_flushed), stringResource(R.string.today_flushed_for, item.plant)) { record(item) }
                        },
                    )
                    is TodayItem.ProblemFollowUp -> Line(
                        R.drawable.ic_bug_report,
                        JobKind.ANIMAL,
                        item.plant?.let { stringResource(R.string.today_follow_up, item.problem, it) } ?: stringResource(R.string.today_follow_up_no_plant, item.problem),
                        onClick = { onProblem(item.problemId) },
                    )
                    is TodayItem.Prune -> Line(R.drawable.ic_content_cut, JobKind.PLANT, stringResource(R.string.today_prune, item.plant), onClick = { onPrune(item.plantId) })
                    is TodayItem.Compost -> Line(
                        R.drawable.ic_compost,
                        JobKind.PLANT,
                        stringResource(if (item.turn) R.string.today_compost_turn else R.string.today_compost_check, item.bin),
                        onClick = { onCompost(item.binId) },
                        done = onTurned?.takeIf { item.turn }?.let { record ->
                            DoneButton(stringResource(R.string.today_compost_turned), stringResource(R.string.today_compost_turned_for, item.bin)) { record(item) }
                        },
                    )
                    is TodayItem.Feed -> Line(
                        R.drawable.ic_compost,
                        JobKind.PLANT,
                        stringResource(R.string.today_feed, item.zone, UiFormat.joinAnd(item.plants, listWords())),
                        onClick = { onFeedZone(item.zoneId) },
                    )
                    is TodayItem.Conditioner -> Line(
                        R.drawable.ic_compost,
                        JobKind.PLANT,
                        stringResource(if (item.today) R.string.today_conditioner_today else R.string.today_conditioner, degrees(item.maxTenthsC)),
                        onClick = onFeed,
                    )
                    is TodayItem.PotsBeforeHeat -> Line(
                        R.drawable.ic_potted_plant,
                        JobKind.URGENT,
                        stringResource(R.string.today_pots_heat, UiFormat.joinAnd(item.zones, listWords()), degrees(item.maxTenthsC)),
                        onClick = onWater,
                    )
                }
            }
            if (state.reviews > 0) Line(R.drawable.ic_rate_review, JobKind.OTHER, pluralStringResource(R.plurals.review_waiting, state.reviews, state.reviews), onClick = onReview)
            if (state.weeds > 0) Line(R.drawable.ic_grass, JobKind.OTHER, pluralStringResource(R.plurals.plot_weeds_found, state.weeds, state.weeds), onClick = onWeeds)
        }
    }
}

/**
 * What a job is about, which colours the dot behind its icon: plants green, bees wattle gold, animals and their
 * problems banksia rust, water blue-green. An urgent job, such as an overdue check or a hot day, stands on its own tile
 * in the error colours. The icon and the words always say the same, so the colour is never the only sign.
 */
private enum class JobKind { PLANT, BEES, ANIMAL, WATER, URGENT, OTHER }

@Composable
private fun JobKind.colours(): ToneColours {
    val scheme = MaterialTheme.colorScheme
    return when (this) {
        JobKind.PLANT -> ToneColours(scheme.primaryContainer, scheme.onPrimaryContainer)
        JobKind.BEES -> ToneColours(scheme.secondaryContainer, scheme.onSecondaryContainer)
        JobKind.ANIMAL -> ToneColours(scheme.tertiaryContainer, scheme.onTertiaryContainer)
        JobKind.WATER -> PollenTheme.colors.water.let { ToneColours(it.container, it.onContainer) }
        JobKind.URGENT -> ToneColours(scheme.error, scheme.onError)
        JobKind.OTHER -> ToneColours(scheme.surfaceContainerHighest, scheme.onSurfaceVariant)
    }
}

/** A check as the words after "due for", such as "an inspection". */
private val HiveTaskKind.noun: Int
    get() = when (this) {
        HiveTaskKind.INSPECT -> R.string.today_kind_inspect
        HiveTaskKind.BROOD_CHECK -> R.string.today_kind_brood
        HiveTaskKind.MITE_CHECK -> R.string.today_kind_mites
    }

/**
 * What the app does for the Today card's lines beyond opening a tab: the page of a hive or hotel, a zone's feed, a
 * plant's pruning, and the two jobs recorded on the spot. A screen shown without them falls back to the tabs.
 */
class TodayActions(
    val onHive: ((String) -> Unit)? = null,
    val onHotel: ((String) -> Unit)? = null,
    val onFeedZone: ((String) -> Unit)? = null,
    val onPrune: ((String) -> Unit)? = null,
    val onCompost: ((String) -> Unit)? = null,
    val onTurned: ((TodayItem.Compost) -> Unit)? = null,
    val onWatered: ((TodayItem.Watering) -> Unit)? = null,
    val onFlushed: ((TodayItem.PotCare) -> Unit)? = null,
)

/** The button on a line that records its job as done: its [label], what a screen reader says for it, and the press. */
private class DoneButton(val label: String, val description: String, val onClick: () -> Unit)

/**
 * One thing to do, as a button at least 48 dp tall: its icon on a dot in its [kind]'s colour, and its words. An urgent
 * job stands on its own tile in the error colours. A job that can be recorded on the spot has a second button, [done],
 * beside the line, or under it with large text.
 */
@Composable
private fun Line(icon: Int, kind: JobKind, text: String, onClick: () -> Unit, done: DoneButton? = null) {
    val haptics = LocalHapticFeedback.current
    val dot = kind.colours()
    val urgent = kind == JobKind.URGENT
    val words: @Composable (Modifier) -> Unit = { modifier ->
        Row(
            modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = Spacing.m, vertical = Spacing.s),
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(DOT).clip(CircleShape).background(dot.container), contentAlignment = Alignment.Center) {
                Icon(painterResource(icon), contentDescription = null, tint = dot.content, modifier = Modifier.size(IconSize.small))
            }
            Text(text, style = MaterialTheme.typography.bodyLarge)
        }
    }
    val button: @Composable (Modifier) -> Unit = { modifier ->
        if (done != null) {
            OutlinedButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    done.onClick()
                },
                modifier = modifier.semantics { contentDescription = done.description },
            ) { Text(done.label) }
        }
    }
    val tile = if (urgent) {
        Modifier.padding(horizontal = Spacing.s).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.errorContainer)
    } else {
        Modifier.padding(horizontal = Spacing.xs)
    }
    CompositionLocalProvider(LocalContentColor provides if (urgent) MaterialTheme.colorScheme.onErrorContainer else LocalContentColor.current) {
        when {
            done == null -> words(Modifier.fillMaxWidth().then(tile))
            largeText() -> Column(Modifier.fillMaxWidth().then(tile)) {
                words(Modifier.fillMaxWidth())
                button(Modifier.padding(start = DOT + Spacing.xl, bottom = Spacing.xs))
            }
            else -> Row(Modifier.fillMaxWidth().then(tile), verticalAlignment = Alignment.CenterVertically) {
                words(Modifier.weight(1f))
                button(Modifier.padding(end = Spacing.m))
            }
        }
    }
}

/** The dot behind a job's icon. */
private val DOT = 32.dp
