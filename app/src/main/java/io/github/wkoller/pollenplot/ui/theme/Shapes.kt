package io.github.wkoller.pollenplot.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Field Guide's corners, a little rounder than Material's so cards and sheets feel soft, like pages of a notebook:
 * small things such as tags and fields stay crisp, and cards, sheets and heroes grow rounder as they grow larger.
 * Shapes with a meaning of their own, such as a plant's avatar, are in PollenShapes.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal val PollenShapeScale = Shapes(
    // Tags, menus, text fields and snackbars.
    extraSmall = RoundedCornerShape(4.dp),
    // Chips and small buttons.
    small = RoundedCornerShape(8.dp),
    // List rows and small cards.
    medium = RoundedCornerShape(16.dp),
    // Cards, the floating action button and navigation drawers.
    large = RoundedCornerShape(24.dp),
    largeIncreased = RoundedCornerShape(28.dp),
    // Dialogs, sheets and heroes.
    extraLarge = RoundedCornerShape(32.dp),
    extraLargeIncreased = RoundedCornerShape(40.dp),
    extraExtraLarge = RoundedCornerShape(48.dp),
)
