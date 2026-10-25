package io.github.wkoller.pollenplot.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import io.github.wkoller.pollenplot.data.prefs.Settings
import io.github.wkoller.pollenplot.data.prefs.ThemeChoice

/** Material 3 Expressive, with Pollenplot's colours unless the user chose the wallpaper's colours. */
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
    MaterialExpressiveTheme(
        colorScheme = colors,
        motionScheme = MotionScheme.expressive(),
        shapes = Shapes(),
        typography = Typography(),
        content = content,
    )
}
