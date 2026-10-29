package io.github.wkoller.pollenplot.ui.plot

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.data.TodayItem
import io.github.wkoller.pollenplot.data.TodayState
import io.github.wkoller.pollenplot.domain.bees.honey.HiveTaskKind
import io.github.wkoller.pollenplot.ui.UiFormat
import io.github.wkoller.pollenplot.ui.bees.degrees
import io.github.wkoller.pollenplot.ui.listWords

/**
 * What the garden needs today, at the top of the Plot: watering due, hive checks due or overdue, a hot day for
 * stingless bees, hotels to clean out in spring, finds to review and weeds. Each line opens where it is done.
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
) {
    if (state.isEmpty) return
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier.fillMaxWidth().heightIn(max = maxHeight),
    ) {
        Column(Modifier.then(if (maxHeight.isSpecified) Modifier.verticalScroll(rememberScrollState()) else Modifier).padding(vertical = 8.dp)) {
            Text(
                stringResource(R.string.today_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).semantics { heading() },
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
                    stringResource(R.string.today_hive_due, hive, UiFormat.joinAnd(kinds, words)) + if (overdue) " · " + stringResource(R.string.today_overdue) else "",
                    onClick = onBees,
                )
            }
            for (item in state.items) {
                when (item) {
                    is TodayItem.Watering -> Line(
                        R.drawable.ic_water_drop,
                        item.minutes?.let { pluralStringResource(R.plurals.today_water_minutes, it, item.name, it) }
                            ?: stringResource(R.string.today_water_litres, item.name, item.litres.toString()),
                        onClick = onWater,
                    )
                    is TodayItem.HiveCheck -> Unit
                    is TodayItem.HotDay -> Line(
                        R.drawable.ic_warning,
                        stringResource(if (item.today) R.string.notify_heat_today else R.string.notify_heat_tomorrow, degrees(item.maxTenthsC)),
                        onClick = onHeat,
                    )
                    is TodayItem.CleanHotel -> Line(R.drawable.ic_cottage, stringResource(R.string.today_clean_hotel, item.hotel), onClick = onBees)
                    is TodayItem.PotsBeforeHeat -> Line(
                        R.drawable.ic_potted_plant,
                        stringResource(R.string.today_pots_heat, UiFormat.joinAnd(item.zones, listWords()), degrees(item.maxTenthsC)),
                        onClick = onWater,
                    )
                }
            }
            if (state.reviews > 0) Line(R.drawable.ic_rate_review, pluralStringResource(R.plurals.review_waiting, state.reviews, state.reviews), onClick = onReview)
            if (state.weeds > 0) Line(R.drawable.ic_grass, pluralStringResource(R.plurals.plot_weeds_found, state.weeds, state.weeds), onClick = onWeeds)
        }
    }
}

/** A check as the words after "due for", such as "an inspection". */
private val HiveTaskKind.noun: Int
    get() = when (this) {
        HiveTaskKind.INSPECT -> R.string.today_kind_inspect
        HiveTaskKind.BROOD_CHECK -> R.string.today_kind_brood
        HiveTaskKind.MITE_CHECK -> R.string.today_kind_mites
    }

/** One thing to do, with its icon, as a button at least 48 dp tall. */
@Composable
private fun Line(icon: Int, text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
