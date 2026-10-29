package io.github.wkoller.pollenplot

import android.graphics.Bitmap
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.wkoller.pollenplot.data.prefs.Settings
import io.github.wkoller.pollenplot.domain.plants.AuState
import io.github.wkoller.pollenplot.ui.nav.AppRoot
import io.github.wkoller.pollenplot.ui.theme.PollenplotTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Drives the Plot, zones, plants and search with a demo yard, saving a picture of each step to build/screenshots. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class PlotScreenFlowTest {
    // The classic rule runs coroutines at once, so flows from the database reach the screen while the test waits.
    @Suppress("DEPRECATION")
    @get:Rule
    val compose = createComposeRule()

    private val app: PollenplotApp get() = ApplicationProvider.getApplicationContext()

    @Test
    fun canvasListZoneAndPlant() {
        seed()
        show()
        waitFor(hasContentDescription("Front Native Strip", substring = true))
        shot("plot_canvas")

        compose.onNodeWithContentDescription("Show as a list").performClick()
        waitFor(hasText("Back Citrus Patch"))
        compose.onNodeWithText("Back Citrus Patch").assertIsDisplayed()
        shot("plot_list")

        compose.onNodeWithText("Hive Corner").performClick()
        waitFor(hasText("Pollinator hotspot"))
        shot("zone_hive_corner")

        compose.onNodeWithText("Rosemary").performClick()
        waitFor(hasText("Salvia rosmarinus"))
        shot("plant_rosemary")
    }

    @Test
    @Config(qualifiers = "w411dp-h2600dp-xhdpi")
    fun plantPagesInFull() {
        seed()
        show()
        waitFor(hasContentDescription("Show as a list"))
        compose.onNodeWithContentDescription("Show as a list").performClick()
        waitFor(hasText("Front Native Strip"))
        compose.onNodeWithText("Front Native Strip").performClick()
        // The zone's plants come from the database, which can take a moment on a busy machine.
        waitFor(hasText("Grevillea Robyn Gordon"))
        compose.onNodeWithText("Grevillea Robyn Gordon").performClick()
        waitFor(hasText("Low-phosphorus native food only"))
        shot("plant_grevillea_full")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        // Back on the Plot's list, which takes a moment to come back on a busy machine.
        waitFor(hasText("Back Citrus Patch"))
        compose.onNodeWithText("Back Citrus Patch").performClick()
        waitFor(hasText("Mixed thirst"))
        shot("zone_citrus_mixed_thirst")
        compose.onNodeWithText("Mum's lemon").performClick()
        waitFor(hasText("Citrus food"))
        shot("plant_lemon_full")
    }

    @Test
    fun addAPlantByName() {
        seed()
        show()
        compose.onNodeWithContentDescription("Open the add menu").performClick()
        compose.onNodeWithText("Add a plant by name").performClick()
        waitFor(hasSetTextAction())
        compose.onNode(hasSetTextAction()).performTextInput("bottle")
        waitFor(hasText("Weeping bottlebrush"))
        shot("add_plant_search")
        compose.onNodeWithText("Weeping bottlebrush").performClick()
        compose.onNodeWithText("Front Native Strip").performClick()
        shot("add_plant_sheet")
        compose.onNodeWithText("Add to garden").performClick()
        waitFor(hasText("In Front Native Strip"))
        shot("add_plant_done")
    }

    @Test
    fun searchTheGarden() {
        seed()
        show()
        compose.onNodeWithContentDescription("Search your garden").performClick()
        // On a busy machine the search screen can take a moment to open.
        waitFor(hasSetTextAction())
        compose.onNode(hasSetTextAction()).performTextInput("lemon")
        waitFor(hasText("Mum's lemon"))
        compose.onNodeWithText("Plants you could add").assertExists()
        shot("search_lemon")
        compose.onNodeWithText("Mum's lemon").performClick()
        waitFor(hasText("Citrus food"))
    }

    @Test
    fun editTheLayout() {
        val ids = seed()
        show()
        waitFor(hasContentDescription("Hive Corner", substring = true))
        compose.onNodeWithContentDescription("Edit layout").performClick()
        compose.onNodeWithContentDescription("Hive Corner", substring = true).performClick()
        shot("plot_edit_selected")
        compose.onNodeWithContentDescription("Move and resize").performClick()
        compose.onNodeWithText("Wider").performClick()
        compose.onNodeWithContentDescription("Move down").performClick()
        waitFor(hasText("Width: 4.5 m"))
        shot("plot_adjust_sheet")
        compose.onAllNodesWithText("Done").onFirst().performClick()
        val rect = runBlocking { app.container.garden.zone(ids.hive)!!.rect }
        assertEquals(450, rect.widthCm)
        assertEquals(500, rect.yCm)
    }

    private fun seed(): DemoGarden.Ids = runBlocking {
        app.container.knowledge.await()
        app.container.settingsStore.update { it.copy(gardenState = AuState.QLD) }
        DemoGarden.seed(app.container.garden)
    }

    private fun show(settings: Settings = Settings(gardenState = AuState.QLD)) {
        compose.setContent {
            PollenplotTheme(settings) {
                Surface { AppRoot(app.container) }
            }
        }
    }

    private fun waitFor(matcher: androidx.compose.ui.test.SemanticsMatcher) {
        compose.waitUntil(WAIT_MS) { compose.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        val dir = File(System.getProperty("screenshots.dir") ?: "build/screenshots").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
