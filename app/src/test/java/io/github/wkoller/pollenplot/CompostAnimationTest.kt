package io.github.wkoller.pollenplot

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.wkoller.pollenplot.domain.compost.CompostPhase
import io.github.wkoller.pollenplot.ui.components.CompostAnimation
import io.github.wkoller.pollenplot.ui.components.binBox
import io.github.wkoller.pollenplot.ui.theme.LocalAnimationsOn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

/** The compost bin's picture keeps what is in the bin inside its rounded outline in every phase. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = WildlifeTestApp::class, qualifiers = "en-rAU-w411dp-h891dp-xxhdpi")
class CompostAnimationTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun theHeapStaysInsideTheBinsRoundedCorners() {
        var phase by mutableStateOf(CompostPhase.FILLING)
        compose.setContent {
            // The picture stays still with animations off, so each phase is one frame.
            CompositionLocalProvider(LocalAnimationsOn provides false) {
                Box(Modifier.background(androidx.compose.ui.graphics.Color.White)) {
                    CompostAnimation(phase, DESCRIPTION, size = 160.dp)
                }
            }
        }
        val filled = CompostPhase.entries.filter { it != CompostPhase.EMPTIED }
        for (shown in filled) {
            phase = shown
            compose.waitForIdle()
            val picture = compose.onNodeWithContentDescription(DESCRIPTION).captureToImage().asAndroidBitmap()
            val bin = binBox(Size(picture.width.toFloat(), picture.height.toFloat()), compose.density)
            val outline = bin.outline
            // The corners of the outline's rectangle lie outside its rounded corners, where the heap once showed.
            val bottomLeft = picture.at(outline.left + 1, outline.bottom - 1)
            val bottomRight = picture.at(outline.right - 2, outline.bottom - 1)
            assertEquals("Nothing past the bottom left corner while ${shown.name}", Color.WHITE, bottomLeft)
            assertEquals("Nothing past the bottom right corner while ${shown.name}", Color.WHITE, bottomRight)
            // The heap itself is drawn, in the bottom layer in the middle of the bin, below the wavy edge it shows
            // between one layer and the next.
            val middle = picture.at(outline.center.x, outline.bottom - bin.stroke - 2 * compose.density.density)
            assertNotEquals("The heap shows while ${shown.name}", Color.WHITE, middle)
        }
    }

    private fun Bitmap.at(x: Float, y: Float): Int = getPixel(x.roundToInt().coerceIn(0, width - 1), y.roundToInt().coerceIn(0, height - 1))

    private companion object {
        const val DESCRIPTION = "Compost bin"
    }
}
