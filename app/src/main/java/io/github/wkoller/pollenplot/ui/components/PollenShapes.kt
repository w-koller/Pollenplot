package io.github.wkoller.pollenplot.ui.components

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toPath
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon

/**
 * The Material 3 Expressive shapes Pollenplot uses, each with a meaning: a clover for a plant in leaf, a flower for a
 * plant in flower, a cookie for an empty page, and the sunny and soft-burst shapes behind it. MaterialShapes still needs
 * an opt-in, so screens reach the shapes through here and only this file changes if the API does.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
object PollenShapes {
    @Composable
    fun cookie(): Shape = MaterialShapes.Cookie9Sided.toShape()

    @Composable
    fun flower(): Shape = MaterialShapes.Flower.toShape()

    @Composable
    fun clover(): Shape = MaterialShapes.Clover4Leaf.toShape()

    @Composable
    fun sunny(): Shape = MaterialShapes.Sunny.toShape()

    @Composable
    fun softBurst(): Shape = MaterialShapes.SoftBurst.toShape()

    /** The shapes the loading indicator turns through: a flower, a clover, the sun and a cookie. */
    val loading: List<RoundedPolygon>
        get() = listOf(MaterialShapes.Flower, MaterialShapes.Clover4Leaf, MaterialShapes.Sunny, MaterialShapes.Cookie9Sided)

    /** A plant's shape from in leaf (0) to in flower (1). */
    val leafToFlower: Morph by lazy { Morph(MaterialShapes.Clover4Leaf, MaterialShapes.Flower) }
}

/** A shape part way through [morph], at [progress] from 0 (its start) to 1 (its end), scaled to what it clips. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
class MorphShape(private val morph: Morph, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        // MaterialShapes are drawn in a unit square, so the path is scaled up to the size it clips.
        val path = morph.toPath(progress, Path())
        path.transform(Matrix().apply { scale(size.width, size.height) })
        return Outline.Generic(path)
    }

    override fun equals(other: Any?) = other is MorphShape && other.morph === morph && other.progress == progress

    override fun hashCode() = 31 * morph.hashCode() + progress.hashCode()
}
