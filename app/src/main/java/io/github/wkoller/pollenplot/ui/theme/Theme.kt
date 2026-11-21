package io.github.wkoller.pollenplot.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.github.wkoller.pollenplot.data.prefs.Settings
import io.github.wkoller.pollenplot.data.prefs.ThemeChoice

/**
 * Material 3 Expressive in Pollenplot's Field Guide style: its colours unless the user chose the wallpaper's, Fraunces
 * headings, soft corners and expressive springs. Pollenplot's own colours, such as water and flowering, and whether
 * animations are on, are given beside Material's through [PollenTheme].
 */
@Composable
fun PollenplotTheme(settings: Settings, content: @Composable () -> Unit) {
    val dark = when (settings.theme) {
        ThemeChoice.SYSTEM -> isSystemInDarkTheme()
        ThemeChoice.LIGHT -> false
        ThemeChoice.DARK -> true
    }
    val context = LocalContext.current
    val colors = when {
        settings.wallpaperColours -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> PollenplotDark
        else -> PollenplotLight
    }
    val animations = remember(context) { animationsOn(context) }
    CompositionLocalProvider(
        LocalPollenColors provides if (dark) PollenDarkColors else PollenLightColors,
        LocalAnimationsOn provides animations,
    ) {
        MaterialExpressiveTheme(
            colorScheme = colors,
            motionScheme = MotionScheme.expressive(),
            shapes = PollenShapeScale,
            typography = PollenTypography,
            content = content,
        )
    }
}

/** Pollenplot's tokens beside Material's: `PollenTheme.colors.water`, as `MaterialTheme.colorScheme.primary`. */
object PollenTheme {
    val colors: PollenColors
        @Composable @ReadOnlyComposable get() = LocalPollenColors.current
}
