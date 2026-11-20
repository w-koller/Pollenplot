package io.github.wkoller.pollenplot.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.wkoller.pollenplot.ui.theme.IconSize
import io.github.wkoller.pollenplot.ui.theme.Spacing
import io.github.wkoller.pollenplot.ui.theme.animationsOn
import kotlinx.coroutines.launch

/**
 * What a screen shows before it has anything in it: a small picture, a heading and a sentence or two. The picture is the
 * screen's icon on a cookie, in front of a sun in wattle gold with a grain of pollen beside it, and it grows into place
 * once when the screen opens. With the phone's animations off it is simply there.
 */
@Composable
fun EmptyState(@DrawableRes icon: Int, title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        // Kept to a readable line length on wide screens.
        modifier.fillMaxWidth().wrapContentWidth().widthIn(max = Spacing.readableWidth).padding(horizontal = Spacing.xl, vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        EmptyVignette(icon)
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** The empty state's picture: the icon on a cookie, a sun behind it and a grain of pollen. */
@Composable
private fun EmptyVignette(@DrawableRes icon: Int) {
    val animate = animationsOn()
    val grow = remember { Animatable(if (animate) 0.7f else 1f) }
    val turn = remember { Animatable(if (animate) -40f else 0f) }
    val spatial = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(Unit) {
        launch { grow.animateTo(1f, spatial) }
        launch { turn.animateTo(0f, spatial) }
    }
    val colours = MaterialTheme.colorScheme
    Box(Modifier.size(VIGNETTE), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(VIGNETTE)
                .graphicsLayer {
                    rotationZ = turn.value
                    scaleX = grow.value
                    scaleY = grow.value
                }
                .clip(PollenShapes.sunny())
                .background(colours.secondaryContainer),
        )
        Box(
            Modifier
                .size(COOKIE)
                .graphicsLayer {
                    scaleX = grow.value
                    scaleY = grow.value
                }
                .clip(PollenShapes.cookie())
                .background(colours.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = colours.onPrimaryContainer, modifier = Modifier.size(IconSize.large))
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = -Spacing.s, y = Spacing.m)
                .size(POLLEN)
                .graphicsLayer {
                    scaleX = grow.value
                    scaleY = grow.value
                }
                .clip(CircleShape)
                .background(colours.tertiary),
        )
    }
}

private val VIGNETTE = 136.dp
private val COOKIE = 104.dp
private val POLLEN = 18.dp
