package io.github.wkoller.pollenplot.ui.bees

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.data.HotelView
import io.github.wkoller.pollenplot.domain.guide.Guide
import io.github.wkoller.pollenplot.domain.hotels.Seal
import io.github.wkoller.pollenplot.domain.hotels.SealGuide
import io.github.wkoller.pollenplot.domain.plants.AuState
import io.github.wkoller.pollenplot.ui.UiFormat
import io.github.wkoller.pollenplot.ui.components.EmptyState
import io.github.wkoller.pollenplot.ui.settings.SectionHeading

/** The bee hotel section of the Bees tab: the hotels, the guide to who sealed a nest, and the guide's pages. */
internal fun LazyListScope.hotelSection(
    hotels: List<HotelView>,
    state: AuState?,
    guide: List<Guide>,
    onAddHotel: () -> Unit,
    onOpenHotel: (String) -> Unit,
    onOpenGuide: (String) -> Unit,
) {
    if (hotels.isEmpty()) {
        item(key = "hotel-empty") {
            Column {
                EmptyState(icon = R.drawable.ic_cottage, title = stringResource(R.string.bees_hotels_empty_title), body = stringResource(R.string.bees_hotels_empty_body))
                Button(onClick = onAddHotel, modifier = Modifier.padding(horizontal = 16.dp)) { Text(stringResource(R.string.hotel_add)) }
            }
        }
    } else {
        item(key = "hotels") { SectionHeading(stringResource(R.string.hotels)) }
        items(hotels, key = { "hotel-" + it.hotel.id }) { view -> HotelRow(view, onClick = { onOpenHotel(view.hotel.id) }) }
        item(key = "hotel-add") {
            OutlinedButton(onClick = onAddHotel, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { Text(stringResource(R.string.hotel_add)) }
        }
    }

    item(key = "seal-guide") {
        Column {
            SectionHeading(stringResource(R.string.hotel_seal_guide))
            Text(stringResource(R.string.hotel_seal_guide_body), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
    items(Seal.entries.filter { it != Seal.OTHER }, key = { "seal-" + it.name }) { seal ->
        ListItem(supportingContent = { Text(likelyText(seal)) }) { Text(stringResource(seal.label)) }
    }

    val pages = guide.filter { it.appliesTo(state) }
    if (pages.isNotEmpty()) {
        item(key = "hotel-guide") { SectionHeading(stringResource(R.string.guide)) }
        items(pages, key = { "guide-" + it.id }) { page ->
            ListItem(
                onClick = { onOpenGuide(page.id) },
                leadingContent = { Icon(painterResource(R.drawable.ic_menu_book), contentDescription = null) },
                supportingContent = { Text(page.summary) },
            ) { Text(page.title) }
        }
    }
}

/** Who a seal says is most likely in the nest, in a sentence. */
@Composable
internal fun likelyText(seal: Seal): String {
    val occupants = SealGuide.likely(seal)
    return when (occupants.size) {
        0 -> stringResource(R.string.seal_unknown)
        1 -> stringResource(R.string.seal_likely, stringResource(occupants[0].label))
        else -> stringResource(R.string.seal_likely_two, stringResource(occupants[0].label), stringResource(occupants[1].label))
    }
}

@Composable
private fun HotelRow(view: HotelView, onClick: () -> Unit) {
    val hotel = view.hotel
    val latest = view.state.latest
    val lines = listOfNotNull(
        listOfNotNull(pluralStringResource(R.plurals.hotel_hole_count, hotel.holes, hotel.holes), view.zoneName).joinToString(" · "),
        latest?.let {
            stringResource(R.string.hotel_latest, UiFormat.longDate(it.date)) + " · " + pluralStringResource(R.plurals.hotel_sealed_total, it.sealedHoles, it.sealedHoles)
        },
    )
    ListItem(
        onClick = onClick,
        leadingContent = { Icon(painterResource(R.drawable.ic_cottage), contentDescription = null) },
        supportingContent = {
            Column {
                Text(lines.joinToString("\n"), maxLines = 3)
                if (view.cleanThisSpring) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.ic_calendar_month), contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.hotel_clean_due), color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
    ) { Text(hotel.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}
