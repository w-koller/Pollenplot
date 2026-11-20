package io.github.wkoller.pollenplot

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import io.github.wkoller.pollenplot.ui.theme.PollenColors
import io.github.wkoller.pollenplot.ui.theme.PollenDarkColors
import io.github.wkoller.pollenplot.ui.theme.PollenLightColors
import io.github.wkoller.pollenplot.ui.theme.PollenplotDark
import io.github.wkoller.pollenplot.ui.theme.PollenplotLight
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pollenplot's own colours meet WCAG 2.2 AA in the light and in the dark: 4.5:1 for words on every colour they are set
 * on, and 3:1 for icons, outlines and marks that carry meaning (success criteria 1.4.3 and 1.4.11). The wallpaper's
 * colours come from Android, which builds them to the same ratios.
 */
class ThemeContrastTest {
    @Test
    fun lightThemeMeetsAa() = check("light", PollenplotLight, PollenLightColors)

    @Test
    fun darkThemeMeetsAa() = check("dark", PollenplotDark, PollenDarkColors)

    private fun check(name: String, scheme: ColorScheme, pollen: PollenColors) {
        val failures = mutableListOf<String>()
        fun need(label: String, fore: Color, back: Color, minimum: Double) {
            val ratio = contrast(fore, back)
            if (ratio < minimum) failures += "$name $label: %.2f:1, needs %.1f:1".format(ratio, minimum)
        }
        with(scheme) {
            // Words on each colour Material pairs them with.
            need("onPrimary on primary", onPrimary, primary, TEXT)
            need("onPrimaryContainer on primaryContainer", onPrimaryContainer, primaryContainer, TEXT)
            need("onSecondary on secondary", onSecondary, secondary, TEXT)
            need("onSecondaryContainer on secondaryContainer", onSecondaryContainer, secondaryContainer, TEXT)
            need("onTertiary on tertiary", onTertiary, tertiary, TEXT)
            need("onTertiaryContainer on tertiaryContainer", onTertiaryContainer, tertiaryContainer, TEXT)
            need("onError on error", onError, error, TEXT)
            need("onErrorContainer on errorContainer", onErrorContainer, errorContainer, TEXT)
            need("inverseOnSurface on inverseSurface", inverseOnSurface, inverseSurface, TEXT)
            need("inversePrimary on inverseSurface", inversePrimary, inverseSurface, TEXT)
            val surfaces = mapOf(
                "background" to background,
                "surface" to surface,
                "surfaceContainerLowest" to surfaceContainerLowest,
                "surfaceContainerLow" to surfaceContainerLow,
                "surfaceContainer" to surfaceContainer,
                "surfaceContainerHigh" to surfaceContainerHigh,
                "surfaceContainerHighest" to surfaceContainerHighest,
            )
            surfaces.forEach { (surfaceName, colour) ->
                need("onSurface on $surfaceName", onSurface, colour, TEXT)
                need("onSurfaceVariant on $surfaceName", onSurfaceVariant, colour, TEXT)
                // Headings, links and text buttons are set in primary on the page and on cards.
                need("primary on $surfaceName", primary, colour, TEXT)
                need("error on $surfaceName", error, colour, TEXT)
                need("outline on $surfaceName", outline, colour, NON_TEXT)
                need("bloom on $surfaceName", pollen.bloom.colour, colour, NON_TEXT)
                need("water on $surfaceName", pollen.water.colour, colour, NON_TEXT)
            }
            // Links on a notice card, and the flower mark on a zone's tile.
            need("primary on secondaryContainer", primary, secondaryContainer, TEXT)
            need("bloom on primaryContainer", pollen.bloom.colour, primaryContainer, NON_TEXT)
        }
        with(pollen) {
            need("onWater on water", water.onColour, water.colour, TEXT)
            need("onWaterContainer on waterContainer", water.onContainer, water.container, TEXT)
            need("onBloom on bloom", bloom.onColour, bloom.colour, TEXT)
            need("onBloomContainer on bloomContainer", bloom.onContainer, bloom.container, TEXT)
            need("onScrim on scrim", onScrim, scrim, TEXT)
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance().toDouble()
        val lb = b.luminance().toDouble()
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private companion object {
        const val TEXT = 4.5
        const val NON_TEXT = 3.0
    }
}
