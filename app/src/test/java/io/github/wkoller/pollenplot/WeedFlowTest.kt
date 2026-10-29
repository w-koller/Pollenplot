package io.github.wkoller.pollenplot

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.graphics.Bitmap
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
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
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import io.github.wkoller.pollenplot.data.prefs.Settings
import io.github.wkoller.pollenplot.data.prefs.ThemeChoice
import io.github.wkoller.pollenplot.domain.place.GardenPlace
import io.github.wkoller.pollenplot.domain.plants.AuState
import io.github.wkoller.pollenplot.domain.weeds.WeedLevel
import io.github.wkoller.pollenplot.ui.nav.AppRoot
import io.github.wkoller.pollenplot.ui.theme.PollenplotTheme
import io.github.wkoller.pollenplot.ui.weeds.WeedsScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The garden's location and the Weed identifier, with web answers recorded from the real services. Saves a picture of
 * each step to build/screenshots.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = WeedTestApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class WeedFlowTest {
    // The classic rule runs coroutines at once, so flows from the database reach the screen while the test waits.
    @Suppress("DEPRECATION")
    @get:Rule
    val compose = createComposeRule()

    private val app: WeedTestApp get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
    }

    @Test
    fun searchForTheSuburbThenSeeTheWeeds() {
        seed()
        show()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Garden location").performClick()
        waitFor(hasText("No location set yet."))
        shot("location_empty")

        compose.onNode(hasSetTextAction()).performTextInput("4068")
        compose.onNodeWithContentDescription("Search").performClick()
        waitFor(hasText("Taringa"))
        shot("location_search")
        compose.onNodeWithText("Taringa").performClick()
        waitFor(hasText("Brisbane City council area", substring = true))
        shot("location_set")
        val place = runBlocking { app.container.settingsStore.current() }
        assertEquals(GardenPlace.at(-27.49061, 152.9786, "Taringa", "BRISBANE CITY"), place.gardenPlace)
        assertEquals(AuState.QLD, place.gardenState)
        assertTrue("Only the rounded point is sent", app.web.asked.any { it.endsWith("/-27.49/152.98") })

        // The update the new place starts in the background, run here so the test can wait for it.
        assertTrue(runBlocking { app.container.weedSync.update() }.complete)
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        waitFor(hasContentDescription("1 plant in your garden is a weed where you live."))
        compose.onNodeWithContentDescription("1 plant in your garden is a weed where you live.").performClick()
        waitFor(hasText("Lantana"))
        compose.onNodeWithText("Recorded near your garden").assertExists()
        shot("weeds_screen")
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Sphagneticola trilobata", substring = true))
        shot("weeds_nearby")
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Lantana"))

        compose.onAllNodesWithText("Lantana").onFirst().performClick()
        waitFor(hasText("Restricted invasive plant (category 3) in Queensland"))
        shot("plant_lantana_weed")
        compose.onNodeWithText("It is also a Weed of National Significance.").assertExists()
        compose.onNodeWithText("Brisbane City Council does not ask for a report.").assertExists()
        compose.onNodeWithText("You must not give it away, sell it or release it into the environment.").assertExists()
        compose.onNodeWithText("Recorded 280 times within 10 km", substring = true).assertExists()

        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Compare with photos"))
        compose.onNodeWithText("Compare with photos").performClick()
        waitFor(hasText("Hejor1 · NatureMapr · CC-BY 3.0 (Au)"))
        compose.onNodeWithText("Your photo").assertDoesNotExist()
        shot("compare_lantana")
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("What to look for"))
        compose.onNodeWithText("From Brisbane City Council's weed list.").assertExists()
        shot("compare_lantana_features")
        assertTrue("Only the plant's name is sent", app.web.asked.any { "taxon_name:%22Lantana%20camara%22" in it })
    }

    @Test
    fun theWeedFinderNarrowsTheCouncilsList() {
        runBlocking {
            app.container.settingsStore.update { it.copy(gardenPlace = GardenPlace.at(-27.49, 152.98, "Taringa", "BRISBANE CITY"), gardenState = AuState.QLD) }
            assertTrue(app.container.weedSync.update().complete)
            app.container.weeds.data.first { it.councilWeeds.isNotEmpty() }
        }
        show()
        // The Plot fills in from the database, which can take a moment on a busy machine.
        waitFor(hasText("Weeds near your garden"))
        compose.onNodeWithText("Weeds near your garden").performClick()
        waitFor(hasText("Recorded near your garden"))
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Weed finder"))
        compose.onNodeWithText("Weed finder").performClick()
        waitFor(hasText("13 weeds match."))
        compose.onNodeWithText("Shrub").performClick()
        compose.onNodeWithText("Yellow").performClick()
        waitFor(hasText("3 weeds match."))
        shot("weed_finder")
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Bitou bush"))
        compose.onNodeWithText("Bitou bush").performClick()
        waitFor(hasText("Chrysanthemoides monilifera ssp. Rotundata"))
        // Brisbane lists it without a category, and it is a Weed of National Significance.
        compose.onNodeWithText("Weed of National Significance").assertExists()
        shot("compare_bitou_bush")
    }

    @Test
    @Config(qualifiers = "en-rAU-w411dp-h2000dp-xhdpi")
    fun animalsRecordedNearTheGarden() {
        seed()
        runBlocking {
            app.container.settingsStore.update { it.copy(gardenPlace = GardenPlace.at(-27.49, 152.98, "Taringa", "BRISBANE CITY"), gardenState = AuState.QLD) }
            assertTrue(app.container.weedSync.update().complete)
        }
        assertTrue("Every group was asked for", app.web.asked.count { "common_name_and_lsid" in it } == 9)
        val data = runBlocking { app.container.weeds.current() }
        assertEquals("Noisy miners count towards suggestions near the garden", 51_564, data.recordsNearby("Manorina melanocephala"))
        show()
        compose.onNodeWithText("Wildlife").performClick()
        waitFor(hasText("Recorded near you"))
        compose.onNodeWithText("Noisy miner").assertExists()
        compose.onNodeWithText("Recorded 51,564 times").assertExists()
        compose.onNodeWithText("Torresian crow").assertExists()
        shot("wildlife_nearby_birds")
        compose.onNodeWithText("Frogs").performScrollTo().performClick()
        waitFor(hasText("Striped marsh frog"))
        compose.onNodeWithText("Cane toad").assertExists()
        shot("wildlife_nearby_frogs")
    }

    @Test
    @Config(qualifiers = "w360dp-h780dp-xxhdpi", fontScale = 1.3f)
    fun weedScreensInTheDarkWithLargeText() {
        seed()
        runBlocking {
            app.container.settingsStore.update { it.copy(gardenPlace = GardenPlace.at(-27.49, 152.98, "Taringa", "BRISBANE CITY"), gardenState = AuState.QLD) }
            assertTrue(app.container.weedSync.update().complete)
        }
        show(Settings(theme = ThemeChoice.DARK))
        waitFor(hasContentDescription("1 plant in your garden is a weed where you live."))
        compose.onNodeWithContentDescription("1 plant in your garden is a weed where you live.").performClick()
        waitFor(hasText("Lantana"))
        shot("weeds_dark_large")
        compose.onAllNodesWithText("Lantana").onFirst().performClick()
        waitFor(hasText("Restricted invasive plant (category 3) in Queensland"))
        shot("plant_lantana_weed_dark_large")
    }

    @Test
    fun thePhonesLocationSetsTheGarden() {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        show()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Garden location").performClick()
        compose.onNodeWithText("Use this phone's location").performClick()
        waitFor(hasText("Near Brisbane City"))
        shot("location_phone")
        val place = runBlocking { app.container.settingsStore.current().gardenPlace }
        assertNull(place?.name)
        assertEquals("BRISBANE CITY", place?.council)
    }

    @Test
    fun aPhoneOutsideAustraliaIsNotUsed() {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        app.location.point = 51.5 to -0.12
        show()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Garden location").performClick()
        compose.onNodeWithText("Use this phone's location").performClick()
        waitFor(hasText("This location is outside Australia", substring = true))
        assertNull(runBlocking { app.container.settingsStore.current().gardenPlace })
    }

    @Test
    fun withoutAConnectionTheLastListsStay() {
        val synced = runBlocking {
            app.container.settingsStore.update { it.copy(gardenPlace = GardenPlace.at(-27.49, 152.98, "Taringa", "BRISBANE CITY"), gardenState = AuState.QLD) }
            assertTrue(app.container.weedSync.update().complete)
            app.container.weeds.data.first { it.councilWeeds.isNotEmpty() && it.nearby != null }
        }
        assertEquals(WeedLevel.RESTRICTED, synced.status(listOf("Lantana camara")).level)
        assertEquals("Found under the name the Atlas accepts", "sphagneticola trilobata", synced.accepted["wedelia trilobata"])
        assertFalse(synced.lastUpdateFailed)
        // Records near the garden boost identification, under either name.
        assertEquals(280, app.container.recordsNearby("Lantana camara"))
        assertEquals(206, app.container.recordsNearby("Wedelia trilobata"))
        assertEquals(0, app.container.recordsNearby("Grevillea banksii"))

        app.web.offline = true
        val kept = runBlocking {
            assertFalse(app.container.weedSync.update().complete)
            app.container.weeds.data.first { it.lastUpdateFailed }
        }
        assertEquals(synced.councilWeeds, kept.councilWeeds)
        assertEquals(synced.updatedAt, kept.updatedAt)
        // The update the place change queued is still waiting for a connection; without it the screen offers a retry.
        WorkManager.getInstance(app).cancelAllWork().result.get()

        compose.setContent {
            PollenplotTheme(Settings(gardenState = AuState.QLD)) {
                Surface {
                    WeedsScreen(app.container.views, app.container.knowledge, app.container.settingsStore, onBack = {}, onOpenPlant = {}, onCheckWeed = {}, onSetLocation = {})
                }
            }
        }
        waitFor(hasText("The last update couldn't reach every source. Pollenplot will try again."))
        compose.onNodeWithText("Update now").assertIsDisplayed()
        shot("weeds_offline")
    }

    @Test
    fun aWeedNewlyRecordedNearbyIsNotified() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val notifications = shadowOf(app.getSystemService(NotificationManager::class.java))
        runBlocking {
            app.container.settingsStore.update { it.copy(gardenPlace = GardenPlace.at(-27.49, 152.98, "Taringa", "BRISBANE CITY"), gardenState = AuState.QLD) }
            assertTrue(app.container.weedSync.update().complete)
        }
        assertTrue("The first records for a place are not news", notifications.allNotifications.isEmpty())

        app.web.nearbyFile = "ala_nearby_later.json"
        runBlocking { assertTrue(app.container.weedSync.update().complete) }
        val posted = notifications.allNotifications.single()
        assertEquals("A weed has been recorded near your garden.", posted.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("Siam Weed. If you see it in your yard, please take a photo to check it.", posted.extras.getCharSequence(Notification.EXTRA_TEXT).toString())

        runBlocking { assertTrue(app.container.weedSync.update().complete) }
        assertEquals("A weed already known is not news again", 1, notifications.allNotifications.size)
    }

    @Test
    fun aMoveToAnotherCouncilDropsBrisbanesList() = runBlocking {
        app.container.settingsStore.update { it.copy(gardenPlace = GardenPlace.at(-27.49, 152.98, "Taringa", "BRISBANE CITY"), gardenState = AuState.QLD) }
        app.container.weedSync.update()
        app.container.weeds.data.first { it.councilWeeds.isNotEmpty() }
        app.container.settingsStore.update { it.copy(gardenPlace = GardenPlace.at(-33.87, 151.21, "Sydney", "SYDNEY"), gardenState = AuState.NSW) }
        val moved = app.container.weeds.data.first { it.state?.state == AuState.NSW }
        assertTrue(moved.councilWeeds.isEmpty())
        assertNull("Records near Taringa are not near Sydney", moved.nearby)
        assertEquals(WeedLevel.NATIONAL, moved.status(listOf("Lantana camara")).level)
    }

    private fun seed(): DemoGarden.Ids = runBlocking {
        app.container.knowledge.await()
        DemoGarden.seed(app.container.garden)
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
