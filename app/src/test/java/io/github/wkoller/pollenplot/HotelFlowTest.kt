package io.github.wkoller.pollenplot

import android.graphics.Bitmap
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import io.github.wkoller.pollenplot.data.prefs.Settings
import io.github.wkoller.pollenplot.data.prefs.ThemeChoice
import io.github.wkoller.pollenplot.domain.hotels.Seal
import io.github.wkoller.pollenplot.domain.plants.AuState
import io.github.wkoller.pollenplot.ui.nav.AppRoot
import io.github.wkoller.pollenplot.ui.theme.PollenplotTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** The bee hotel section of the Bees tab, a hotel's page and the hotel guide. Saves pictures. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = WildlifeTestApp::class, qualifiers = "en-rAU-w411dp-h2400dp-xhdpi")
class HotelFlowTest {
    @Suppress("DEPRECATION")
    @get:Rule
    val compose = createComposeRule()

    private val app: WildlifeTestApp get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        runBlocking {
            app.container.knowledge.await()
            app.container.settingsStore.update { it.copy(gardenState = AuState.QLD) }
            DemoGarden.seed(app.container.garden)
        }
    }

    @Test
    fun aHotelFromItsFirstLook() {
        show()
        openHotels()
        waitFor(hasText("You have no bee hotels yet."))
        compose.onNodeWithText("Leaf discs").assertExists()
        compose.onNodeWithText("Most likely a leafcutter bee.").assertExists()
        compose.onNodeWithText("Add a hotel").performClick()

        compose.onNodeWithText("Name").performTextInput("Fence hotel")
        compose.onNodeWithText("Hive Corner").performClick()
        compose.onNodeWithText("Hole width in millimetres").performScrollTo().performTextInput("8")
        compose.onNodeWithText("Hole depth in millimetres").performScrollTo().performTextInput("120")
        compose.onNodeWithText("Number of holes").performScrollTo().performTextInput("24")
        compose.onNodeWithText("Add this section").performScrollTo().performClick()
        waitFor(hasText("Drilled timber · 8 mm wide · 120 mm deep · 24 holes"))
        compose.onNodeWithText("Save").performScrollTo().performClick()

        waitFor(hasText("24 holes · Hive Corner"))
        compose.onNodeWithText("Drilled timber · 8 mm wide · 120 mm deep · 24 holes").assertExists()
        // Never cleaned, and it is spring.
        compose.onNodeWithText("Spring is the time to clean out old nest material", substring = true).assertExists()

        compose.onNodeWithText("Record what you see").performClick()
        waitFor(hasText("Holes sealed with each"))
        val more = compose.onAllNodesWithContentDescription("One more")
        more[Seal.LEAF.ordinal].performClick()
        more[Seal.LEAF.ordinal].performClick()
        more[Seal.LEAF.ordinal].performClick()
        more[Seal.CLAY.ordinal].performClick()
        compose.onNodeWithText("Ants").performScrollTo().performClick()
        compose.onNodeWithText("Save").performScrollTo().performClick()
        waitFor(hasText("3 sealed with leaf discs"))
        compose.onNodeWithText("Most likely a potter wasp, which catches caterpillars for its young.").assertExists()
        assertEquals(mapOf(Seal.LEAF to 3, Seal.CLAY to 1), runBlocking { app.container.hotels.observations.first().single().sealed })

        compose.onNodeWithText("Record upkeep").performClick()
        waitFor(hasText("What you did"))
        compose.onNodeWithText("New paper liners").performClick()
        compose.onNodeWithText("Save").performScrollTo().performClick()
        waitFor(hasText("New paper liners"))
        compose.onAllNodesWithText("Spring is the time to clean out old nest material", substring = true).assertCountEquals(0)
        shot("hotel_page")

        compose.onNodeWithContentDescription("Back").performClick()
        waitFor(hasText("Bee hotels"))
        compose.onNodeWithText("Fence hotel").assertExists()
        shot("bees_hotels")
    }

    @Test
    fun theGuideSaysWhoUsesAHotel() {
        show(Settings(theme = ThemeChoice.DARK))
        openHotels()
        waitFor(hasText("Who uses a bee hotel"))
        compose.onNodeWithText("Who uses a bee hotel").performClick()
        waitFor(hasText("Wasps"))
        compose.onNodeWithText("belongs to a potter wasp", substring = true).assertExists()
        shot("guide_hotels_dark")
    }

    private fun openHotels() {
        compose.onNodeWithText("Bees").performClick()
        waitFor(hasText("Bee hotels"))
        compose.onNodeWithText("Bee hotels").performClick()
    }

    private fun show(settings: Settings = Settings()) {
        compose.setContent {
            PollenplotTheme(settings) {
                Surface { AppRoot(app.container) }
            }
        }
    }

    private fun waitFor(matcher: SemanticsMatcher) {
        compose.waitUntil(WAIT_MS) { compose.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        val dir = File(System.getProperty("screenshots.dir") ?: "build/screenshots").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
