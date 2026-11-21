package io.github.wkoller.pollenplot.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.domain.plants.EcoRole
import io.github.wkoller.pollenplot.ui.plants.BadgeTone
import io.github.wkoller.pollenplot.ui.plants.icon
import io.github.wkoller.pollenplot.ui.plants.label
import io.github.wkoller.pollenplot.ui.plants.tone
import io.github.wkoller.pollenplot.ui.theme.IconSize
import io.github.wkoller.pollenplot.ui.theme.PollenTheme
import io.github.wkoller.pollenplot.ui.theme.Spacing

data class ToneColours(val container: Color, val content: Color)

@Composable
fun toneColours(tone: BadgeTone): ToneColours {
    val scheme = MaterialTheme.colorScheme
    // Water keeps its own blue-green, whatever colours the theme takes from the wallpaper.
    val water = PollenTheme.colors.water
    return when (tone) {
        BadgeTone.WARNING -> ToneColours(scheme.errorContainer, scheme.onErrorContainer)
        BadgeTone.WILDLIFE -> ToneColours(scheme.secondaryContainer, scheme.onSecondaryContainer)
        BadgeTone.PLANT -> ToneColours(scheme.primaryContainer, scheme.onPrimaryContainer)
        BadgeTone.WATER -> ToneColours(water.container, water.onContainer)
        BadgeTone.ANIMAL -> ToneColours(scheme.tertiaryContainer, scheme.onTertiaryContainer)
    }
}

/** A badge: an icon and its words on a coloured pill. */
@Composable
fun Badge(@DrawableRes icon: Int, text: String, tone: BadgeTone, modifier: Modifier = Modifier) {
    val colours = toneColours(tone)
    Surface(shape = CircleShape, color = colours.container, contentColor = colours.content, modifier = modifier) {
        Row(
            Modifier.padding(horizontal = Spacing.m, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(IconSize.small))
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun RoleBadge(role: EcoRole, modifier: Modifier = Modifier) = Badge(role.icon, stringResource(role.label), role.tone, modifier)

/** Every badge for a plant, wrapping onto new lines, or centred under a [RecordHero] when [centred]. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoleBadges(roles: List<EcoRole>, modifier: Modifier = Modifier, centred: Boolean = false) {
    BadgeRow(modifier, centred) { roles.forEach { RoleBadge(it) } }
}

/** Badges wrapping onto new lines, from the start or centred. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BadgeRow(modifier: Modifier = Modifier, centred: Boolean = false, content: @Composable () -> Unit) {
    FlowRow(
        modifier,
        horizontalArrangement = if (centred) Arrangement.spacedBy(Spacing.s, Alignment.CenterHorizontally) else Arrangement.spacedBy(Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) { content() }
}

/** One badge for [IconBadges]: its icon, its words and its tone. */
data class BadgeItem(@DrawableRes val icon: Int, val label: String, val tone: BadgeTone)

/**
 * The first few badges as icons only, for thumbnails, tiles and list rows. The words are read out by TalkBack, and the
 * full badges are on the record's own page.
 */
@Composable
fun IconBadges(items: List<BadgeItem>, modifier: Modifier = Modifier, max: Int = 3, size: Dp = 26.dp) {
    if (items.isEmpty()) return
    val shown = items.take(max)
    val more = items.size - shown.size
    val moreText = if (more > 0) pluralStringResource(R.plurals.role_more, more, more) else null
    Row(
        modifier.semantics(mergeDescendants = true) { contentDescription = items.joinToString(", ") { it.label } },
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        shown.forEach { item ->
            val colours = toneColours(item.tone)
            Surface(shape = CircleShape, color = colours.container, contentColor = colours.content, modifier = Modifier.size(size)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(painterResource(item.icon), contentDescription = null, modifier = Modifier.size(size * 0.62f))
                }
            }
        }
        if (moreText != null) Text(moreText, style = MaterialTheme.typography.labelMedium)
    }
}

/** A plant's first few eco-role badges as icons. */
@Composable
fun RoleIcons(roles: List<EcoRole>, modifier: Modifier = Modifier, max: Int = 3, size: Dp = 26.dp) =
    IconBadges(roles.map { BadgeItem(it.icon, stringResource(it.label), it.tone) }, modifier, max, size)
