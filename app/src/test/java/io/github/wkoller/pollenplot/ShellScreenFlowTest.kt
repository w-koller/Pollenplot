package io.github.wkoller.pollenplot

import android.graphics.Bitmap
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.wkoller.pollenplot.data.prefs.Settings
import io.github.wkoller.pollenplot.data.prefs.ThemeChoice
import io.github.wkoller.pollenplot.ui.nav.AppRoot
import io.github.wkoller.pollenplot.ui.theme.PollenplotTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Drives the app's frame on the JVM and saves a picture of each step to build/screenshots, so the layout can be
 * reviewed without a phone or emulator.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ShellScreenFlowTest {
    @get:Rule
    val compose = createComposeRule()

    private val app: PollenplotApp get() = ApplicationProvider.getApplicationContext()

    @Test
    fun tabsAndAddMenu() {
        show()
        shot("shell_plot")
        tab("Plot").assertIsSelected()

        compose.onNodeWithContentDescription("Open the add menu").performClick()
        compose.onNodeWithText("Identify with the camera").assertIsDisplayed()
        compose.onNodeWithText("Check a weed").assertIsDisplayed()
        shot("shell_plot_add_menu")
        compose.onNodeWithText("Check a weed").performClick()
        compose.onNodeWithText("Pollenplot needs the camera to identify plants.").assertIsDisplayed()
        compose.onNodeWithText("Check a weed").assertIsDisplayed()
        shot("shell_check_weed")
        compose.onNodeWithContentDescription("Back").performClick()

        tab("Rhythms").performClick()
        compose.onNodeWithText("Pollen Rhythms needs plants in your plot.").assertIsDisplayed()
        shot("shell_rhythms")

        tab("Wildlife").performClick()
        compose.onNodeWithText("No wildlife has been recorded yet.").assertIsDisplayed()
        shot("shell_wildlife")

        tab("Bees").performClick()
        compose.onNodeWithText("You have no honey bee hives yet.").assertIsDisplayed()
        shot("shell_bees_honey")
        compose.onNodeWithText("Native bees").performClick()
        compose.onNodeWithText("You have no native bee hives yet.").assertIsDisplayed()
        shot("shell_bees_native")
        compose.onNodeWithText("Bee hotels").performClick()
        compose.onNodeWithText("You have no bee hotels yet.").assertIsDisplayed()
        shot("shell_bees_hotels")

        tab("Water").performClick()
        compose.onNodeWithText("No watering lines have been set up yet.").assertIsDisplayed()
        shot("shell_water")
    }

    @Test
    fun settingsAboutAndLicences() {
        show()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Use colours from your wallpaper").assertIsDisplayed()
        shot("settings")
        compose.onNodeWithText("Models").performClick()
        compose.onNodeWithText("Download").assertIsDisplayed()
        shot("models")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("About Pollenplot").performClick()
        compose.onNodeWithText("Ecosystem Management for Your Backyard").assertIsDisplayed()
        shot("about")
        compose.onNodeWithText("Licences").performClick()
        shot("licences")
        compose.onNodeWithText("Android Jetpack and Jetpack Compose").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Apache License", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        shot("licence_text")
    }

    @Test
    fun darkTheme() {
        show(Settings(theme = ThemeChoice.DARK))
        shot("shell_plot_dark")
        tab("Bees").performClick()
        shot("shell_bees_dark")
    }

    @Test
    @Config(qualifiers = "w900dp-h600dp-xhdpi")
    fun wideScreenUsesARail() {
        show()
        tab("Plot").assertIsSelected()
        shot("shell_wide_plot")
        tab("Water").performClick()
        tab("Water").assertIsSelected()
        shot("shell_wide_water")
    }

    private fun show(settings: Settings = Settings()) {
        compose.setContent {
            PollenplotTheme(settings) {
                Surface { AppRoot(app.container) }
            }
        }
    }

    /** A navigation tab, told apart from a screen title with the same words by its role. */
    private fun tab(label: String) =
        compose.onNode(hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab), useUnmergedTree = false)

    private fun shot(name: String) {
        compose.waitForIdle()
        val dir = File(System.getProperty("screenshots.dir") ?: "build/screenshots").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
