package io.github.wkoller.pollenplot.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import io.github.wkoller.pollenplot.R

// Field Guide sets its headings in Fraunces, a soft old-style serif by Undercase Type (SIL Open Font License), and the
// names of plants and animals in its italic, as a field guide does. Everything people read at length, and every label,
// stays in the phone's own Roboto, so reading and large text behave as they do across Android.
//
// The fonts are bundled in res/font rather than downloaded, because downloadable fonts come through Google Play
// services, which Pollenplot does not use. Fraunces is a variable font: each family below sets its optical size for the
// sizes it is used at, a little softness, and no "wonky" letters.

/** How soft Fraunces's serifs are, from 0 (sharp) to 100 (round). */
private const val SOFTNESS = 50f

private fun fraunces(weight: FontWeight, italic: Boolean, opticalSize: Float) = Font(
    if (italic) R.font.fraunces_italic else R.font.fraunces,
    weight = weight,
    style = if (italic) FontStyle.Italic else FontStyle.Normal,
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight.weight),
        FontVariation.Setting("opsz", opticalSize),
        FontVariation.Setting("SOFT", SOFTNESS),
        FontVariation.Setting("WONK", 0f),
    ),
)

private fun frauncesFamily(opticalSize: Float) = FontFamily(
    fraunces(FontWeight.Normal, italic = false, opticalSize),
    fraunces(FontWeight.Medium, italic = false, opticalSize),
    fraunces(FontWeight.SemiBold, italic = false, opticalSize),
    fraunces(FontWeight.Normal, italic = true, opticalSize),
    fraunces(FontWeight.Medium, italic = true, opticalSize),
)

/** Fraunces for display sizes, 36 sp and up. */
val FrauncesDisplay = frauncesFamily(opticalSize = 72f)

/** Fraunces for headlines, 24 to 32 sp. */
val FrauncesHeadline = frauncesFamily(opticalSize = 28f)

/** Fraunces for names set beside body text, 12 to 16 sp. */
val FrauncesText = frauncesFamily(opticalSize = 14f)

private val base = Typography()

private fun TextStyle.display(weight: FontWeight) = copy(fontFamily = FrauncesDisplay, fontWeight = weight, lineBreak = LineBreak.Heading)

private fun TextStyle.headline(weight: FontWeight) = copy(fontFamily = FrauncesHeadline, fontWeight = weight, lineBreak = LineBreak.Heading)

/**
 * Material's type scale with Fraunces for display and headline sizes. The emphasised styles, for the one thing on a
 * screen that matters most, are a step heavier. Titles, body text and labels are Material's own.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal val PollenTypography = Typography(
    displayLarge = base.displayLarge.display(FontWeight.Normal),
    displayMedium = base.displayMedium.display(FontWeight.Normal),
    displaySmall = base.displaySmall.display(FontWeight.Normal),
    headlineLarge = base.headlineLarge.headline(FontWeight.Medium),
    headlineMedium = base.headlineMedium.headline(FontWeight.Medium),
    headlineSmall = base.headlineSmall.headline(FontWeight.Medium),
    titleLarge = base.titleLarge,
    titleMedium = base.titleMedium,
    titleSmall = base.titleSmall,
    bodyLarge = base.bodyLarge,
    bodyMedium = base.bodyMedium,
    bodySmall = base.bodySmall,
    labelLarge = base.labelLarge,
    labelMedium = base.labelMedium,
    labelSmall = base.labelSmall,
    displayLargeEmphasized = base.displayLargeEmphasized.display(FontWeight.Medium),
    displayMediumEmphasized = base.displayMediumEmphasized.display(FontWeight.Medium),
    displaySmallEmphasized = base.displaySmallEmphasized.display(FontWeight.Medium),
    headlineLargeEmphasized = base.headlineLargeEmphasized.headline(FontWeight.SemiBold),
    headlineMediumEmphasized = base.headlineMediumEmphasized.headline(FontWeight.SemiBold),
    headlineSmallEmphasized = base.headlineSmallEmphasized.headline(FontWeight.SemiBold),
    titleLargeEmphasized = base.titleLargeEmphasized,
    titleMediumEmphasized = base.titleMediumEmphasized,
    titleSmallEmphasized = base.titleSmallEmphasized,
    bodyLargeEmphasized = base.bodyLargeEmphasized,
    bodyMediumEmphasized = base.bodyMediumEmphasized,
    bodySmallEmphasized = base.bodySmallEmphasized,
    labelLargeEmphasized = base.labelLargeEmphasized,
    labelMediumEmphasized = base.labelMediumEmphasized,
    labelSmallEmphasized = base.labelSmallEmphasized,
)

/** This style for the scientific name of a plant or an animal: Fraunces italic, at the same size. */
fun TextStyle.botanical(): TextStyle = copy(fontFamily = FrauncesText, fontStyle = FontStyle.Italic)
