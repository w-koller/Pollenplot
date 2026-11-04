package io.github.wkoller.pollenplot.ui.wishlist

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.data.WishView
import io.github.wkoller.pollenplot.data.WishViews
import io.github.wkoller.pollenplot.domain.garden.WishStatus
import io.github.wkoller.pollenplot.ui.components.Badge
import io.github.wkoller.pollenplot.ui.components.DetailScaffold
import io.github.wkoller.pollenplot.ui.components.EmptyState
import io.github.wkoller.pollenplot.ui.components.PollenLoadingIndicator
import io.github.wkoller.pollenplot.ui.components.RoleIcons
import io.github.wkoller.pollenplot.ui.plants.BadgeTone
import io.github.wkoller.pollenplot.ui.review.Cutout
import io.github.wkoller.pollenplot.ui.review.readable
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * The wishlist: plants the user would like to grow, wanted ones first, each with its picture, status, badges, where it
 * was seen, its price and the zone it might go in. A button finds a plant with the camera away from home.
 */
@Composable
fun WishlistScreen(views: WishViews, onBack: () -> Unit, onOpenWish: (String) -> Unit, onFindPlant: () -> Unit) {
    val list by views.list.collectAsStateWithLifecycle(initialValue = null)
    DetailScaffold(
        title = stringResource(R.string.wishlist_title),
        onBack = onBack,
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onFindPlant) {
                Icon(painterResource(R.drawable.ic_travel_explore), contentDescription = null)
                Text(stringResource(R.string.wishlist_find), modifier = Modifier.padding(start = 12.dp))
            }
        },
    ) { padding ->
        val wishes = list
        when {
            wishes == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { PollenLoadingIndicator() }
            wishes.isEmpty() -> Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally) {
                EmptyState(R.drawable.ic_bookmark, stringResource(R.string.wishlist_empty_title), stringResource(R.string.wishlist_empty_body))
                Button(onClick = onFindPlant) { Text(stringResource(R.string.wishlist_find)) }
            }
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                // Room below the last row for the button.
                contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 96.dp),
            ) {
                items(wishes, key = { it.wish.id }) { view -> WishRow(view, Modifier.readable()) { onOpenWish(view.wish.id) } }
            }
        }
    }
}

@Composable
private fun WishRow(view: WishView, modifier: Modifier, onClick: () -> Unit) {
    val wish = view.wish
    ListItem(
        onClick = onClick,
        modifier = modifier,
        leadingContent = { WishPicture(wish.thumbnail) },
        overlineContent = { WishStatusLabel(wish.status) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                view.scientificName?.let { Text(it, fontStyle = FontStyle.Italic) }
                val details = listOfNotNull(
                    wish.placeName.ifBlank { null }?.let { stringResource(R.string.wishlist_seen_at, it) },
                    wish.priceCents?.let(::price),
                    view.zoneName?.let { stringResource(R.string.wishlist_for_zone, it) },
                )
                if (details.isNotEmpty()) Text(details.joinToString(" · "))
                if (view.weed.isWeed) {
                    Badge(R.drawable.ic_warning, stringResource(R.string.wishlist_weed_here), BadgeTone.WARNING)
                }
                if (view.roles.isNotEmpty()) RoleIcons(view.roles, max = 4, size = 22.dp)
            }
        },
    ) { Text(view.title) }
}

@get:StringRes
val WishStatus.label: Int
    get() = when (this) {
        WishStatus.WANTED -> R.string.wish_status_wanted
        WishStatus.PLANTED -> R.string.wish_status_planted
        WishStatus.DROPPED -> R.string.wish_status_dropped
    }

@get:DrawableRes
private val WishStatus.icon: Int
    get() = when (this) {
        WishStatus.WANTED -> R.drawable.ic_bookmark
        WishStatus.PLANTED -> R.drawable.ic_check_circle
        WishStatus.DROPPED -> R.drawable.ic_close
    }

/** A wish's status, as an icon and a word. */
@Composable
internal fun WishStatusLabel(status: WishStatus) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(status.icon), contentDescription = null, modifier = Modifier.size(16.dp))
        Text(stringResource(status.label))
    }
}

/** The find's cut-out, or a plant icon for a wish added by name. */
@Composable
internal fun WishPicture(thumbnail: String?) {
    if (thumbnail != null) {
        Cutout(thumbnail, 64.dp)
    } else {
        Box(
            Modifier.size(64.dp).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_potted_plant), contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

/** A price in Australian dollars, such as "$14.95". */
internal fun price(cents: Long): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-AU")).apply { currency = Currency.getInstance("AUD") }.format(BigDecimal.valueOf(cents, 2))
