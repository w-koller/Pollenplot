package io.github.wkoller.pollenplot.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.computeWindowSizeClass

/**
 * The window's size class from Material's breakpoints: compact under 600 dp wide (a phone held upright), medium to
 * 840 dp (a phone on its side, a small tablet or an unfolded phone) and expanded beyond (a tablet).
 */
@Composable
fun windowSizeClass(): WindowSizeClass {
    val size = LocalWindowInfo.current.containerSize
    return with(LocalDensity.current) {
        WindowSizeClass.BREAKPOINTS_V1.computeWindowSizeClass(size.width.toDp().value, size.height.toDp().value)
    }
}

/** Whether the window is medium or wider, where the tabs move to a rail at the side. */
@Composable
fun mediumWindow(): Boolean = windowSizeClass().isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

/** Whether the window is expanded, where the rail has room to show its labels beside its icons. */
@Composable
fun expandedWindow(): Boolean = windowSizeClass().isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)
