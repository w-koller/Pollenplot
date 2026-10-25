package io.github.wkoller.pollenplot.ui.components

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape

/**
 * The Material 3 Expressive shapes Pollenplot uses. MaterialShapes still needs an opt-in, so screens reach the shapes
 * through here and only this file changes if the API does.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
object PollenShapes {
    @Composable
    fun cookie(): Shape = MaterialShapes.Cookie9Sided.toShape()

    @Composable
    fun flower(): Shape = MaterialShapes.Flower.toShape()

    @Composable
    fun clover(): Shape = MaterialShapes.Clover4Leaf.toShape()
}
