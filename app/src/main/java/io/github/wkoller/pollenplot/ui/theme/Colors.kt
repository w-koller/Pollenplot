package io.github.wkoller.pollenplot.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import io.github.wkoller.pollenplot.data.prefs.PlotBackground

// Field Guide: gum-leaf green ink on warm paper, with wattle gold and banksia rust beside it. Each palette is one hue at
// one chroma, and its tones are CIE lightness (L*), which is what Material calls tone. Every role takes the tone Material
// 3 gives it, so text on each colour meets a contrast ratio of at least 4.5:1, as ThemeContrastTest checks. Status is
// never shown by colour alone.
//
// Leaf (hue 134), wattle (hue 88) and banksia rust (hue 45) are the main colours, paper (hue 88, chroma 5) the surfaces
// and paper-variant (hue 96, chroma 9) the outlines and quieter text.

internal val PollenplotLight = lightColorScheme(
    primary = Color(0xFF3D692D),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC0F0AA),
    onPrimaryContainer = Color(0xFF0F2000),
    inversePrimary = Color(0xFFA4D38F),
    secondary = Color(0xFF735C00),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDF96),
    onSecondaryContainer = Color(0xFF231B00),
    tertiary = Color(0xFF924A31),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDBCF),
    onTertiaryContainer = Color(0xFF331200),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFEF9F0),
    onBackground = Color(0xFF1E1B15),
    surface = Color(0xFFFEF9F0),
    onSurface = Color(0xFF1E1B15),
    surfaceVariant = Color(0xFFE8E2D1),
    onSurfaceVariant = Color(0xFF4B4739),
    surfaceTint = Color(0xFF3D692D),
    inverseSurface = Color(0xFF343029),
    inverseOnSurface = Color(0xFFF5F0E7),
    outline = Color(0xFF7C7768),
    outlineVariant = Color(0xFFCCC6B6),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFEF9F0),
    surfaceDim = Color(0xFFDED9D0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8F3EA),
    surfaceContainer = Color(0xFFF2EDE4),
    surfaceContainerHigh = Color(0xFFEDE8DE),
    surfaceContainerHighest = Color(0xFFE7E2D9),
)

internal val PollenplotDark = darkColorScheme(
    primary = Color(0xFFA4D38F),
    onPrimary = Color(0xFF0D3900),
    primaryContainer = Color(0xFF245016),
    onPrimaryContainer = Color(0xFFC0F0AA),
    inversePrimary = Color(0xFF3D692D),
    secondary = Color(0xFFE7C363),
    onSecondary = Color(0xFF3C2F00),
    secondaryContainer = Color(0xFF574500),
    onSecondaryContainer = Color(0xFFFFDF96),
    tertiary = Color(0xFFFFB59B),
    onTertiary = Color(0xFF5B1B04),
    tertiaryContainer = Color(0xFF76321C),
    onTertiaryContainer = Color(0xFFFFDBCF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF17130A),
    onBackground = Color(0xFFE7E2D9),
    surface = Color(0xFF17130A),
    onSurface = Color(0xFFE7E2D9),
    surfaceVariant = Color(0xFF4B4739),
    onSurfaceVariant = Color(0xFFCCC6B6),
    surfaceTint = Color(0xFFA4D38F),
    inverseSurface = Color(0xFFE7E2D9),
    inverseOnSurface = Color(0xFF343029),
    outline = Color(0xFF969181),
    outlineVariant = Color(0xFF4B4739),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF3C3932),
    surfaceDim = Color(0xFF17130A),
    surfaceContainerLowest = Color(0xFF130E02),
    surfaceContainerLow = Color(0xFF1E1B15),
    surfaceContainer = Color(0xFF221F19),
    surfaceContainerHigh = Color(0xFF2D2A23),
    surfaceContainerHighest = Color(0xFF38342D),
)

/** A colour with a meaning of its own, in the four roles Material gives a colour. */
@Immutable
data class ToneSet(val colour: Color, val onColour: Color, val container: Color, val onContainer: Color)

/**
 * How one setting of the Plot's sun or flowering layer is drawn: a colour laid over the zone, and a mark (a hatch or
 * dots) so the layer does not rest on colour alone.
 */
@Immutable
data class LayerColours(val fill: Color, val mark: Color)

/**
 * The colours Pollenplot gives a meaning beyond Material's roles. They keep their hues when the user chooses the
 * wallpaper's colours, so water stays blue-green and flowers stay pink whatever the wallpaper is.
 */
@Immutable
data class PollenColors(
    /** Water: watering lines, water features and the water badge (pond teal, hue 198). */
    val water: ToneSet,
    /** Flowering: plants in flower and the flowering layers (bloom magenta, hue 333). */
    val bloom: ToneSet,
    /** What is laid over a photo or the camera, which is dark whatever the theme. */
    val scrim: Color,
    /** Words and marks on [scrim]. */
    val onScrim: Color,
    val fullSun: LayerColours,
    val partShade: LayerColours,
    val fullShade: LayerColours,
    /** The flowering layer's grades, from none in flower to most in flower. */
    val bloomGrades: List<LayerColours>,
)

internal val PollenLightColors = PollenColors(
    water = ToneSet(Color(0xFF00696B), Color(0xFFFFFFFF), Color(0xFF98F2F2), Color(0xFF002020)),
    bloom = ToneSet(Color(0xFF9B338C), Color(0xFFFFFFFF), Color(0xFFFFD7F5), Color(0xFF3A0033)),
    scrim = Color(0xFF000000),
    onScrim = Color(0xFFFFFFFF),
    // The sun layer runs from warm to cool on one scale, and the flowering layer from pale to deep.
    fullSun = LayerColours(Color(0xFFFFB000), Color.Transparent),
    partShade = LayerColours(Color(0xFF26A69A), Color(0xFF00695C)),
    fullShade = LayerColours(Color(0xFF3949AB), Color(0xFF1A237E)),
    bloomGrades = listOf(
        LayerColours(Color(0xFFEDE0EC), Color(0xFF4A0D45)),
        LayerColours(Color(0xFFE1B4DC), Color(0xFF4A0D45)),
        LayerColours(Color(0xFFCB6FBF), Color(0xFF4A0D45)),
        LayerColours(Color(0xFF9C2A8C), Color(0xFF4A0D45)),
    ),
)

internal val PollenDarkColors = PollenColors(
    water = ToneSet(Color(0xFF7CD5D6), Color(0xFF003737), Color(0xFF004F50), Color(0xFF98F2F2)),
    bloom = ToneSet(Color(0xFFFFABEE), Color(0xFF5E0053), Color(0xFF801272), Color(0xFFFFD7F5)),
    scrim = Color(0xFF000000),
    onScrim = Color(0xFFFFFFFF),
    fullSun = LayerColours(Color(0xFFFFB300), Color.Transparent),
    partShade = LayerColours(Color(0xFF4DB6AC), Color(0xFFB2DFDB)),
    fullShade = LayerColours(Color(0xFF5C7CFA), Color(0xFFC5CAE9)),
    bloomGrades = listOf(
        LayerColours(Color(0xFF4A3B4F), Color(0xFFF8E1F4)),
        LayerColours(Color(0xFF7E4C86), Color(0xFFF8E1F4)),
        LayerColours(Color(0xFFB0549F), Color(0xFFF8E1F4)),
        LayerColours(Color(0xFFE07BC9), Color(0xFFF8E1F4)),
    ),
)

/** Pollenplot's own colours for the theme in use, given by [PollenplotTheme]. */
val LocalPollenColors = staticCompositionLocalOf { PollenLightColors }

/**
 * The colour the Plot is drawn on for a background the user chose, in the light and in the dark, or null for the
 * theme's own background. Each is pale in the light and deep in the dark, so the zones and their words stay clear.
 */
fun plotBackgroundColour(choice: PlotBackground, dark: Boolean): Color? = when (choice) {
    PlotBackground.THEME -> null
    PlotBackground.GRASS -> if (dark) Color(0xFF1F3319) else Color(0xFFD3E6B8)
    PlotBackground.SOIL -> if (dark) Color(0xFF36281D) else Color(0xFFE4D0B9)
    PlotBackground.SAND -> if (dark) Color(0xFF383220) else Color(0xFFF2E5C4)
    PlotBackground.PAVING -> if (dark) Color(0xFF2B2D2C) else Color(0xFFDDDEDB)
}
