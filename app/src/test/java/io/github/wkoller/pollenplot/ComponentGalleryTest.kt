package io.github.wkoller.pollenplot

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.wkoller.pollenplot.ui.preview.AvatarsPreview
import io.github.wkoller.pollenplot.ui.preview.BadgesPreview
import io.github.wkoller.pollenplot.ui.preview.ChoicesPreview
import io.github.wkoller.pollenplot.ui.preview.EmptyStatePreview
import io.github.wkoller.pollenplot.ui.preview.FactCardPreview
import io.github.wkoller.pollenplot.ui.preview.NoticePreview
import io.github.wkoller.pollenplot.ui.preview.SegmentedPreview
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Draws the shared components' previews, as Android Studio would, in the light, in the dark and with text at twice its
 * size, and saves each as `components_*.png` among the screenshots. The loading indicator, which never stops moving, and
 * the scaffolds, which fill the window, are left to the screen tests.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "en-rAU-w411dp-h2400dp-xhdpi")
class ComponentGalleryTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun light() = gallery("components")

    @Test
    @Config(qualifiers = "en-rAU-w411dp-h2400dp-night-xhdpi")
    fun dark() = gallery("components_dark")

    @Test
    @Config(qualifiers = "en-rAU-w411dp-h4000dp-xhdpi", fontScale = 2.0f)
    fun largeText() = gallery("components_large")

    private fun gallery(name: String) {
        compose.setContent {
            Column {
                EmptyStatePreview()
                FactCardPreview()
                BadgesPreview()
                AvatarsPreview()
                ChoicesPreview()
                NoticePreview()
                SegmentedPreview()
            }
        }
        val context = ApplicationProvider.getApplicationContext<Application>()
        compose.onNodeWithText(context.getString(R.string.plot_empty_title)).assertExists()
        compose.waitForIdle()
        val dir = File(System.getProperty("screenshots.dir") ?: "build/screenshots").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
