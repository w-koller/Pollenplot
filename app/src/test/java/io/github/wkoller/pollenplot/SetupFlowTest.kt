package io.github.wkoller.pollenplot

import android.Manifest
import android.graphics.Bitmap
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import io.github.wkoller.pollenplot.data.prefs.Settings
import io.github.wkoller.pollenplot.domain.garden.ZoneKind
import io.github.wkoller.pollenplot.domain.place.GardenPlace
import io.github.wkoller.pollenplot.domain.plants.AuState
import io.github.wkoller.pollenplot.ui.nav.AppRoot
import io.github.wkoller.pollenplot.ui.theme.PollenplotTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** The setup shown when Pollenplot is first opened, with recorded answers from the place services. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = WeedTestApp::class, qualifiers = "en-rAU-w411dp-h891dp-xxhdpi")
class SetupFlowTest {
    // The classic rule runs coroutines at once, so flows from the database reach the screen while the test waits.
    @Suppress("DEPRECATION")
    @get:Rule
    val compose = createComposeRule()

    private val app: WeedTestApp get() = ApplicationProvider.getApplicationContext()
    private val settings get() = app.container.settingsStore

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        runBlocking { app.container.knowledge.await() }
    }

    @Test
    fun aNewGardenIsSetUpStepByStep() {
        assertTrue("A garden with no place and no zones opens on the setup", runBlocking { app.container.setupNeeded() })
        show(setup = true)
        waitFor(hasText("Welcome to Pollenplot"))
        shot("setup_welcome")
        compose.onNodeWithText("Start").performClick()

        waitFor(hasText("Where is your garden?"))
        compose.onNode(hasSetTextAction()).performTextInput("4068")
        compose.onNodeWithContentDescription("Search").performClick()
        waitFor(hasText("Taringa"))
        compose.onNodeWithText("Taringa").performClick()
        waitFor(hasText("Brisbane City council area", substring = true))
        shot("setup_location")
        assertEquals(AuState.QLD, runBlocking { settings.current() }.gardenState)
        compose.onNodeWithText("Next").performClick()

        waitFor(hasText("Identification models"))
        shot("setup_models")
        compose.onNodeWithText("Skip").performClick()

        waitFor(hasText("Your garden's zones"))
        compose.onNodeWithText("Garden bed").performClick()
        compose.onNodeWithText("Veg patch").performClick()
        waitFor(hasText("Add 2 zones"))
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextClearance()
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Front bed")
        shot("setup_zones")
        compose.onNodeWithText("Add 2 zones").performClick()

        waitFor(hasText("Notifications and photos"))
        shot("setup_permissions")
        compose.onNodeWithText("Finish").performClick()

        waitFor(hasText("Front bed"))
        val zones = runBlocking { app.container.garden.zoneList.first() }
        assertEquals(listOf("Front bed" to ZoneKind.GARDEN_BED, "Veg patch" to ZoneKind.VEG_PATCH), zones.map { it.name to it.kind })
        assertTrue(runBlocking { settings.current() }.setupDone)
    }

    @Test
    fun theWholeSetupCanBeSkipped() {
        show(setup = true)
        waitFor(hasText("Skip setup"))
        compose.onNodeWithText("Skip setup").performClick()
        // The Plot, which has the Settings button.
        waitFor(hasContentDescription("Settings"))
        assertTrue(runBlocking { settings.current() }.setupDone)
        assertFalse(runBlocking { app.container.setupNeeded() })
    }

    @Test
    fun aGardenWithRecordsSkipsTheSetupForGood() {
        runBlocking {
            app.container.garden.addZone("Back lawn", ZoneKind.LAWN)
            assertFalse(app.container.setupNeeded())
            assertTrue("Marked done, so emptying the garden later does not bring it back", settings.current().setupDone)
        }
    }

    @Test
    fun aGardenWithOnlyAPlaceSkipsTheSetup() {
        runBlocking {
            settings.update { it.copy(gardenPlace = GardenPlace.at(-27.49, 152.98, "Taringa", "BRISBANE CITY"), gardenState = AuState.QLD) }
            assertFalse(app.container.setupNeeded())
        }
    }

    @Test
    fun notificationsAndScanningAreAskedFor() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.READ_MEDIA_IMAGES)
        show()
        compose.onNodeWithContentDescription("Settings").performClick()
        waitFor(hasText("Set up again"))
        compose.onNodeWithText("Set up again").performClick()
        waitFor(hasText("Welcome to Pollenplot"))
        compose.onNodeWithText("Start").performClick()
        repeat(3) {
            waitFor(hasText("Next"))
            compose.onNodeWithText("Next").performClick()
        }
        waitFor(hasText("Notifications are allowed."))
        compose.onNodeWithText("Turn on photo scanning").performClick()
        waitFor(hasText("Photo scanning is on."))
        assertTrue(runBlocking { settings.current() }.scanLibrary)
        compose.onNodeWithText("Finish").performClick()
        // Opened from Settings, the setup goes back there.
        waitFor(hasText("Set up again"))
    }

    private fun show(setup: Boolean = false) {
        compose.setContent {
            PollenplotTheme(Settings()) { Surface { AppRoot(app.container, setup = setup) } }
        }
    }

    private fun waitFor(matcher: SemanticsMatcher) {
        try {
            compose.waitUntil(WAIT_MS) {
                val scrollers = compose.onAllNodes(hasScrollToNodeAction())
                (0 until scrollers.fetchSemanticsNodes().size).forEach { runCatching { scrollers[it].performScrollToNode(matcher) } }
                compose.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (e: ComposeTimeoutException) {
            println(compose.onRoot(useUnmergedTree = true).printToString())
            throw e
        }
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        val dir = File(System.getProperty("screenshots.dir") ?: "build/screenshots").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private companion object {
        const val WAIT_MS = 30_000L
    }
}
