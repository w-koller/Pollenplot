package io.github.wkoller.pollenplot.ui.components

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.wkoller.pollenplot.domain.compost.CompostPhase
import kotlin.math.PI
import kotlin.math.sin

/**
 * A compost bin drawn doing what its batch is doing: browns and greens settling in layer by layer while it fills,
 * warmth rising while it heats, slower wisps as it cools, the layers darkening into soil as it cures, and a seedling
 * once it may be ready. It moves in a slow loop, and stays still when the phone's animations are turned off. The
 * picture is a companion to the words, which always name the phase; [description] is what a screen reader says.
 */
@Composable
fun CompostAnimation(phase: CompostPhase, description: String, modifier: Modifier = Modifier, size: Dp = 160.dp) {
    val context = LocalContext.current
    val still = remember(context) {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }.getOrDefault(1f) == 0f
    }
    val period = when (phase) {
        CompostPhase.HOT -> 1_800
        CompostPhase.COOLING, CompostPhase.WARMING -> 3_200
        else -> 4_000
    }
    // With animations off nothing runs at all, which also keeps screen tests able to settle.
    val t = if (still) 0.6f else loop(period)
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val outline = MaterialTheme.colorScheme.onSurfaceVariant
    val paints = CompostPaints(dark)
    Canvas(modifier.size(size).semantics { contentDescription = description }) {
        val binLeft = this.size.width * 0.18f
        val binRight = this.size.width * 0.82f
        val binTop = this.size.height * 0.30f
        val binBottom = this.size.height * 0.92f
        val inside = Size(binRight - binLeft, binBottom - binTop)
        val radius = CornerRadius(10.dp.toPx())
        clipRect(binLeft, binTop, binRight, binBottom) {
            when (phase) {
                CompostPhase.FILLING -> drawFilling(binLeft, binTop, inside, t, paints)
                CompostPhase.WARMING, CompostPhase.HOT, CompostPhase.COOLING -> drawLayers(binLeft, binBottom, inside, 1f, paints, darkening = if (phase == CompostPhase.COOLING) 0.35f else 0f)
                CompostPhase.CURING -> drawLayers(binLeft, binBottom, inside, 1f, paints, darkening = 0.55f + 0.1f * sin(t * 2 * PI).toFloat())
                CompostPhase.READY -> drawSoil(binLeft, binBottom, inside, paints)
                CompostPhase.EMPTIED -> Unit
            }
        }
        when (phase) {
            CompostPhase.WARMING -> drawHeat(binLeft, binTop, inside.width, t, paints.warm, count = 2, rise = 0.5f)
            CompostPhase.HOT -> drawHeat(binLeft, binTop, inside.width, t, paints.hot, count = 3, rise = 1f)
            CompostPhase.COOLING -> drawHeat(binLeft, binTop, inside.width, t, paints.warm.copy(alpha = 0.6f), count = 1, rise = 0.6f)
            CompostPhase.READY -> drawSprout(binLeft + inside.width / 2, binTop + inside.height * 0.12f, t, paints.leaf)
            else -> Unit
        }
        // The bin over what is in it: an open box with slats, so the heap shows through.
        drawRoundRect(outline, Offset(binLeft, binTop), inside, radius, style = Stroke(width = 3.dp.toPx()))
        val slat = Stroke(width = 1.dp.toPx())
        for (i in 1..2) {
            val x = binLeft + inside.width * i / 3f
            drawLine(outline.copy(alpha = 0.35f), Offset(x, binTop + 4.dp.toPx()), Offset(x, binBottom - 4.dp.toPx()), strokeWidth = slat.width)
        }
    }
}

@Composable
private fun loop(period: Int): Float {
    val transition = rememberInfiniteTransition(label = "compost")
    val moving by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(period, easing = LinearEasing), RepeatMode.Restart),
        label = "loop",
    )
    return moving
}

private class CompostPaints(dark: Boolean) {
    val browns = if (dark) listOf(Color(0xFFA1887F), Color(0xFF8D6E63)) else listOf(Color(0xFF8D6E63), Color(0xFFA1887F))
    val greens = if (dark) listOf(Color(0xFF9CCC65), Color(0xFF7CB342)) else listOf(Color(0xFF7CB342), Color(0xFF689F38))
    val soil = if (dark) Color(0xFF5D4037) else Color(0xFF3E2723)
    val warm = Color(0xFFFFB74D)
    val hot = Color(0xFFFF7043)
    val leaf = if (dark) Color(0xFFAED581) else Color(0xFF558B2F)
}

private const val LAYERS = 6

/** Layers from the bottom up, browns and greens in turn, the top one dropping in as the loop runs. */
private fun DrawScope.drawFilling(left: Float, top: Float, inside: Size, t: Float, paints: CompostPaints) {
    val layer = inside.height / (LAYERS + 2)
    val full = 4
    val bottom = top + inside.height
    for (i in 0 until full) drawLayer(left, bottom - layer * (i + 1), inside.width, layer, colourOf(i, paints), 0f)
    // The next layer falls from above the bin and settles on the others.
    val settled = bottom - layer * (full + 1)
    val y = top - layer + (settled - top + layer) * easeOut(t)
    drawLayer(left, y, inside.width, layer, colourOf(full, paints), 0f)
}

private fun DrawScope.drawLayers(left: Float, bottom: Float, inside: Size, fill: Float, paints: CompostPaints, darkening: Float) {
    val layer = inside.height / (LAYERS + 2)
    for (i in 0 until LAYERS) drawLayer(left, bottom - layer * (i + 1) * fill, inside.width, layer, colourOf(i, paints), darkening, paints.soil)
}

private fun DrawScope.drawLayer(left: Float, top: Float, width: Float, height: Float, colour: Color, darkening: Float, soil: Color = Color.Black) {
    val shade = lerp(colour, soil, darkening.coerceIn(0f, 1f))
    // A gently wavy top edge, as a heap's layers are never flat.
    val path = Path().apply {
        moveTo(left, top + height * 0.2f)
        val steps = 8
        for (s in 1..steps) {
            val x = left + width * s / steps
            val y = top + height * (0.2f + 0.15f * sin(s * 1.7).toFloat())
            lineTo(x, y)
        }
        lineTo(left + width, top + height * 1.05f)
        lineTo(left, top + height * 1.05f)
        close()
    }
    drawPath(path, shade)
}

private fun DrawScope.drawSoil(left: Float, bottom: Float, inside: Size, paints: CompostPaints) {
    val top = bottom - inside.height * 0.78f
    drawRect(Brush.verticalGradient(listOf(lerp(paints.soil, Color.White, 0.12f), paints.soil), startY = top, endY = bottom), Offset(left, top), Size(inside.width, bottom - top))
    // Crumbs on the surface, so it reads as loose soil.
    val crumb = lerp(paints.soil, Color.White, 0.25f)
    for (i in 0 until 9) {
        val x = left + inside.width * (0.08f + 0.1f * i)
        val y = top + 4.dp.toPx() + (i % 3) * 3.dp.toPx()
        drawCircle(crumb, 2.dp.toPx(), Offset(x, y))
    }
}

/** Wavy lines of warmth rising from the heap, [count] of them, faster and taller when hotter. */
private fun DrawScope.drawHeat(left: Float, top: Float, width: Float, t: Float, colour: Color, count: Int, rise: Float) {
    val height = size.height * 0.26f * rise
    for (i in 0 until count) {
        val phase = (t + i.toFloat() / count) % 1f
        val x = left + width * (i + 1) / (count + 1)
        val base = top - 4.dp.toPx() - height * phase * 0.4f
        val alpha = (1f - phase).coerceIn(0f, 1f)
        val path = Path().apply {
            moveTo(x, base)
            val steps = 12
            for (s in 1..steps) {
                val y = base - height * s / steps
                val dx = 6.dp.toPx() * sin((s / steps.toFloat() + phase) * 2 * PI).toFloat()
                lineTo(x + dx, y)
            }
        }
        drawPath(path, colour.copy(alpha = colour.alpha * alpha), style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** A seedling growing from the finished compost and swaying a little. */
private fun DrawScope.drawSprout(x: Float, groundY: Float, t: Float, leaf: Color) {
    val grow = 0.85f + 0.15f * sin(t * 2 * PI).toFloat()
    val stem = size.height * 0.2f * grow
    val sway = 3.dp.toPx() * sin(t * 2 * PI).toFloat()
    val tip = Offset(x + sway, groundY - stem)
    drawLine(leaf, Offset(x, groundY), tip, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
    val leafSize = Size(14.dp.toPx() * grow, 7.dp.toPx() * grow)
    drawOval(leaf, Offset(tip.x - leafSize.width, tip.y - leafSize.height / 2), leafSize)
    drawOval(leaf, Offset(tip.x, tip.y - leafSize.height), leafSize)
}

private fun colourOf(index: Int, paints: CompostPaints): Color =
    if (index % 2 == 0) paints.browns[(index / 2) % 2] else paints.greens[(index / 2) % 2]

private fun easeOut(t: Float): Float = 1f - (1f - t) * (1f - t) * (1f - t)

private fun lerp(a: Color, b: Color, f: Float) = Color(
    red = a.red + (b.red - a.red) * f,
    green = a.green + (b.green - a.green) * f,
    blue = a.blue + (b.blue - a.blue) * f,
    alpha = a.alpha + (b.alpha - a.alpha) * f,
)
