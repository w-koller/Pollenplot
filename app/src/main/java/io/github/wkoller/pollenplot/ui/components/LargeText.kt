package io.github.wkoller.pollenplot.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalDensity

/** The font scale from which a label moves above or below what it names, rather than sitting beside it. */
private const val LARGE_TEXT_SCALE = 1.3f

/**
 * Whether the phone's text is set large enough that a label beside a picture, a row of badges or a calendar strip
 * would be squeezed into a few letters. Layouts that put the two side by side stack them instead.
 */
@Composable
@ReadOnlyComposable
fun largeText(): Boolean = LocalDensity.current.fontScale >= LARGE_TEXT_SCALE
