package io.github.wkoller.pollenplot.ui.preview

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.data.TodayItem
import io.github.wkoller.pollenplot.data.TodayState
import io.github.wkoller.pollenplot.domain.bees.honey.HiveTaskKind
import io.github.wkoller.pollenplot.ui.plot.TodayCard
import io.github.wkoller.pollenplot.ui.rhythms.FeedCard
import io.github.wkoller.pollenplot.ui.rhythms.FeedKind
import io.github.wkoller.pollenplot.ui.theme.Spacing

// Previews of the main tabs' pieces that draw from plain state, with jobs and names like the demo garden's. Whole tabs
// read the garden's records, so the screen tests draw them.

/** A day with a job of every colour: an overdue hive check, watering, a feed, a prune, a pest to look at again and a hot day. */
private val sampleDay = TodayState(
    items = listOf(
        TodayItem.HiveCheck("Hive 1", HiveTaskKind.INSPECT, overdue = true, hiveId = "hive"),
        TodayItem.HiveCheck("Hive 1", HiveTaskKind.MITE_CHECK, overdue = true, hiveId = "hive"),
        TodayItem.Watering("Veg Patch", minutes = null, litres = 21, zoneId = "veg"),
        TodayItem.Feed("citrus", "Back Citrus Patch", listOf("Mum's lemon", "Mandarin")),
        TodayItem.Prune("fuchsia", "Native fuchsia"),
        TodayItem.ProblemFollowUp("aphids", "Aphids", "Mum's lemon"),
        TodayItem.HotDay(today = false, maxTenthsC = 370),
    ),
    reviews = 2,
    weeds = 0,
)

@PollenPreviews
@Composable
internal fun TodayCardPreview() = PreviewTheme {
    TodayCard(
        sampleDay,
        Modifier.padding(Spacing.l),
        onWater = {},
        onBees = {},
        onHeat = {},
        onReview = {},
        onWeeds = {},
        onWatered = {},
    )
}

@PollenPreviews
@Composable
internal fun RhythmsCardsPreview() = PreviewTheme {
    Column(Modifier.padding(vertical = Spacing.s)) {
        FeedCard(FeedKind.HONEY, R.drawable.ic_hive, stringResource(R.string.rhythms_nectar), stringResource(R.string.rhythms_nectar_body, "French lavender"))
        FeedCard(FeedKind.STINGLESS, R.drawable.ic_emoji_nature, stringResource(R.string.rhythms_stingless), stringResource(R.string.rhythms_stingless_body, "Grevillea Robyn Gordon"))
        FeedCard(FeedKind.VISITS, R.drawable.ic_raven, stringResource(R.string.rhythms_visits), stringResource(R.string.rhythms_visit, "Blue-banded bee", "Mum's lemon"))
        FeedCard(
            FeedKind.TIMING,
            R.drawable.ic_calendar_month,
            stringResource(R.string.rhythms_timing),
            stringResource(R.string.rhythms_earlier, "Grevillea Robyn Gordon", pluralStringResource(R.plurals.rhythms_days, 9, 9)),
        )
    }
}
