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
import androidx.compose.runtime.Composable
import io.github.wkoller.pollenplot.ui.preview.FactCardPreview
import io.github.wkoller.pollenplot.ui.preview.FactGroupPreview
import io.github.wkoller.pollenplot.ui.preview.GroupedRecordsPreview
import io.github.wkoller.pollenplot.ui.preview.HiveHeroPreview
import io.github.wkoller.pollenplot.ui.preview.RecordHeroPreview
import io.github.wkoller.pollenplot.ui.preview.NoticePreview
import io.github.wkoller.pollenplot.ui.preview.RhythmsCardsPreview
import io.github.wkoller.pollenplot.ui.preview.SegmentedPreview
import io.github.wkoller.pollenplot.ui.preview.TodayCardPreview
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Draws the shared components' previews, as Android Studio would, in the light, in the dark and with text at twice its
 * size, and saves each as `components_*.png` among the screenshots, and the record pages' pieces as `records_*.png`.
 * The loading indicator, which never stops moving, the record menu, which opens over a page, and the scaffolds, which
 * fill the window, are left to the screen tests.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "en-rAU-w411dp-h3600dp-xhdpi")
class ComponentGalleryTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun light() = gallery("components") { Components() }

    @Test
    @Config(qualifiers = "en-rAU-w411dp-h3600dp-night-xhdpi")
    fun dark() = gallery("components_dark") { Components() }

    @Test
    @Config(qualifiers = "en-rAU-w411dp-h6400dp-xhdpi", fontScale = 2.0f)
    fun largeText() = gallery("components_large") { Components() }

    @Test
    @Config(qualifiers = "en-rAU-w411dp-h2400dp-xhdpi")
    fun records() = gallery("records", R.string.honey_inspections) { Records() }

    @Test
    @Config(qualifiers = "en-rAU-w411dp-h2400dp-night-xhdpi")
    fun recordsDark() = gallery("records_dark", R.string.honey_inspections) { Records() }

    @Test
    @Config(qualifiers = "en-rAU-w411dp-h4400dp-xhdpi", fontScale = 2.0f)
    fun recordsLargeText() = gallery("records_large", R.string.honey_inspections) { Records() }

    @Composable
    private fun Components() = Column {
        EmptyStatePreview()
        FactCardPreview()
        BadgesPreview()
        AvatarsPreview()
        ChoicesPreview()
        NoticePreview()
        SegmentedPreview()
        TodayCardPreview()
        RhythmsCardsPreview()
    }

    /** The record pages' shared pieces: two heroes, a group of fact cards and a grouped list of records. */
    @Composable
    private fun Records() = Column {
        RecordHeroPreview()
        HiveHeroPreview()
        FactGroupPreview()
        GroupedRecordsPreview()
    }

    /** Draws [content], waits for the words [shows] to be there, and saves the picture as [name]. */
    private fun gallery(name: String, shows: Int = R.string.plot_empty_title, content: @Composable () -> Unit) {
        compose.setContent { content() }
        val context = ApplicationProvider.getApplicationContext<Application>()
        compose.onNodeWithText(context.getString(shows)).assertExists()
        compose.waitForIdle()
        val dir = File(System.getProperty("screenshots.dir") ?: "build/screenshots").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
