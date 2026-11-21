package io.github.wkoller.pollenplot.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.wkoller.pollenplot.ui.theme.Spacing
import io.github.wkoller.pollenplot.ui.theme.animationsOn
import io.github.wkoller.pollenplot.ui.theme.botanical
import kotlinx.coroutines.launch

/**
 * The top of a record's page, set out like a plate in a field guide: its [picture] large on a soft sun shape, its name
 * in the display face, its [scientific] name in italic, a line or two of [details] about what it is and where, and its
 * [tags], all centred. The sun grows into place once when the page opens, and is simply there with the phone's
 * animations off. The page's top bar shows the name only once this has scrolled away (see [DetailScaffold]).
 */
@Composable
fun RecordHero(
    title: String,
    modifier: Modifier = Modifier,
    /** Another name under the title, such as a plant's common name under the name the user gave it. */
    subtitle: String? = null,
    scientific: String? = null,
    details: List<String> = emptyList(),
    backdrop: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    tags: (@Composable () -> Unit)? = null,
    picture: @Composable () -> Unit,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.s),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        HeroBackdrop(backdrop, picture)
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.s).semantics { heading() },
        )
        subtitle?.let { Text(it, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center) }
        scientific?.let {
            Text(it, style = MaterialTheme.typography.titleMedium.botanical(), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        details.forEach { line ->
            Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        tags?.let { Box(Modifier.padding(top = Spacing.s)) { it() } }
    }
}

/** The hero's picture on a sun that grows and turns into place. */
@Composable
private fun HeroBackdrop(colour: Color, picture: @Composable () -> Unit) {
    val animate = animationsOn()
    val grow = remember { Animatable(if (animate) 0.6f else 1f) }
    val turn = remember { Animatable(if (animate) -30f else 0f) }
    val spatial = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(Unit) {
        launch { grow.animateTo(1f, spatial) }
        launch { turn.animateTo(0f, spatial) }
    }
    Box(Modifier.size(HERO_BACKDROP), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(HERO_BACKDROP)
                .graphicsLayer {
                    rotationZ = turn.value
                    scaleX = grow.value
                    scaleY = grow.value
                }
                .clip(PollenShapes.sunny())
                .background(colour),
        )
        picture()
    }
}

/** How large a hero's picture is drawn, inside its sun. */
val HeroPictureSize: Dp = 112.dp

private val HERO_BACKDROP = 152.dp

/** Whether a list's first item, the page's hero, has scrolled out of sight, so the top bar should name the page. */
@Composable
fun heroGone(list: LazyListState): () -> Boolean {
    val gone = remember(list) { derivedStateOf { list.firstVisibleItemIndex > 0 } }
    return { gone.value }
}

/** Whether a scrolling page's hero, [heroHeight] tall at its top, has scrolled out of sight. */
@Composable
fun heroGone(scroll: ScrollState, heroHeight: Dp = HERO_HEIGHT): () -> Boolean {
    val limit = with(LocalDensity.current) { heroHeight.roundToPx() }
    val gone = remember(scroll, limit) { derivedStateOf { scroll.value > limit } }
    return { gone.value }
}

/** About how tall a hero with a name and a line under it is, for pages that scroll without a list. */
private val HERO_HEIGHT = 240.dp
