package io.github.wkoller.pollenplot.ui.theme

import androidx.compose.ui.unit.dp

/**
 * The spaces between and around things, on a 4 dp step: [l] is a screen's side margin and a card's padding, [s] the gap
 * between things in a row, and [xs] the gap between a line and the line under it.
 */
object Spacing {
    val hairline = 1.dp
    val xxs = 2.dp
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp

    /** The room left at the end of a list so its last row clears a floating action button and its margin. */
    val fabClearance = 96.dp

    /** The widest a column of text gets on a wide screen, so lines stay readable. */
    val readableWidth = 640.dp
}

/** Icon sizes: [small] inside chips and badges, [medium] Material's standard, [large] in a picture such as an empty page. */
object IconSize {
    val small = 18.dp
    val medium = 24.dp
    val large = 48.dp
}
