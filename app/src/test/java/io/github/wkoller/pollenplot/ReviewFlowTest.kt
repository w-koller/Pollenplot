package io.github.wkoller.pollenplot

import android.Manifest
import android.graphics.Bitmap
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.imageDecoderEnabled
import io.github.wkoller.pollenplot.TestPhotos.bottlebrush
import io.github.wkoller.pollenplot.TestPhotos.rosemary
import io.github.wkoller.pollenplot.data.prefs.Settings
import io.github.wkoller.pollenplot.domain.id.LabelScore
import io.github.wkoller.pollenplot.domain.plants.AuState
import io.github.wkoller.pollenplot.scan.GalleryAccess
import io.github.wkoller.pollenplot.ui.nav.AppRoot
import io.github.wkoller.pollenplot.ui.theme.PollenplotTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration
import java.time.Instant

/** The Review queue and Settings › Photo scanning, with a painted gallery and a stand-in for the models. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class ReviewFlowTest {
    // The classic rule runs coroutines at once, so flows from the database reach the screen while the test waits.
    @Suppress("DEPRECATION")
    @get:Rule
    val compose = createComposeRule()

    private val app: TestApp get() = ApplicationProvider.getApplicationContext()
    private val garden get() = app.container.garden
    private val bedPhoto = TestPhotos.gardenPhoto(listOf(bottlebrush, rosemary))

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        @OptIn(DelicateCoilApi::class)
        SingletonImageLoader.setUnsafe(ImageLoader.Builder(app).coroutineContext(Dispatchers.Unconfined).imageDecoderEnabled(false).build())
        app.identifier.regionsFor = { photo ->
            if (photo === bedPhoto) {
                listOf(
                    TestPhotos.region(bottlebrush, flowering = true, LabelScore("Callistemon citrinus", 7_400)),
                    TestPhotos.region(rosemary, flowering = false, LabelScore("Rosmarinus officinalis", 4_300)),
                )
            } else {
                emptyList()
            }
        }
    }

    /** The demo yard, with one gallery photo of a bed scanned and waiting for review. */
    private fun seedWithScannedPhoto(): DemoGarden.Ids = runBlocking {
        app.container.knowledge.await()
        app.container.settingsStore.update { it.copy(gardenState = AuState.QLD) }
        val ids = DemoGarden.seed(garden)
        val photo = app.gallery.add(1, Instant.now().minus(Duration.ofDays(2)), bedPhoto)
        app.container.scanner.scanLibraryPhoto(photo)
        ids
    }

    private fun show(open: String? = null, settings: Settings = Settings(gardenState = AuState.QLD)) {
        compose.setContent {
            PollenplotTheme(settings) { Surface { AppRoot(app.container, open = open) } }
        }
    }

    @Test
    fun reviewFromThePlot() {
        val ids = seedWithScannedPhoto()
        show()
        waitFor(hasContentDescription("2 plants are waiting for review."))
        shot("review_badge")
        compose.onNodeWithContentDescription("2 plants are waiting for review.").performClick()
        waitFor(hasText("Taken on", substring = true))
        shot("review_queue")

        // Rosemary already grows in Hive Corner, so the photo goes to that plant.
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Add the photo to it"))
        compose.onNodeWithText("Add the photo to it").performClick()
        waitFor(hasText("Added to Hive Corner."))
        assertEquals(1, runBlocking { garden.pendingReviewCount.first() })
        assertEquals(ids.hive, runBlocking { garden.plantList.first() }.single { it.speciesName == "Salvia rosmarinus" }.zoneId)
        shot("review_queue_decided")
    }

    @Test
    fun dismissEveryPlantAtOnce() {
        seedWithScannedPhoto()
        show(open = MainActivity.OPEN_REVIEW)
        waitFor(hasText("Taken on", substring = true))
        compose.onNodeWithText("Select").performClick()
        waitFor(hasText("All plants (2)"))
        compose.onNodeWithText("All plants (2)").performClick()
        waitFor(hasText("Dismiss (2)"))
        shot("review_selecting")
        compose.onNodeWithText("Dismiss (2)").performClick()
        compose.waitUntil(WAIT_MS) { runBlocking { garden.pendingReviewCount.first() } == 0 }
        waitFor(hasText("Undo"))
    }

    @Test
    fun addSelectedFindsToOneZone() {
        val ids = seedWithScannedPhoto()
        show(open = MainActivity.OPEN_REVIEW)
        waitFor(hasText("Taken on", substring = true))
        compose.onNodeWithText("Select").performClick()
        waitFor(hasText("Select this plant"))
        compose.onAllNodesWithText("Select this plant").onFirst().performClick()
        compose.onNodeWithText("All plants (2)").performClick()
        waitFor(hasText("Add (2)"))
        compose.onNodeWithText("Add (2)").performClick()
        waitFor(hasText("Add to a zone"))
        compose.onNodeWithText("Hive Corner").performClick()
        shot("review_add_selected")
        compose.onNodeWithText("Add 2 finds").performClick()
        compose.waitUntil(WAIT_MS) { runBlocking { garden.pendingReviewCount.first() } == 0 }

        val plants = runBlocking { garden.plantList.first() }
        assertEquals("The rosemary already in Hive Corner gets the photo", 1, plants.count { it.speciesName == "Salvia rosmarinus" })
        assertEquals(ids.hive, plants.last { it.speciesName == "Melaleuca citrina" }.zoneId)
    }

    @Test
    fun aNotificationOpensTheQueue() {
        seedWithScannedPhoto()
        show(open = MainActivity.OPEN_REVIEW)
        waitFor(hasText("Taken on", substring = true))
        compose.onAllNodesWithText("Crimson bottlebrush").onFirst().assertIsDisplayed()
    }

    @Test
    fun nothingToReview() {
        runBlocking { app.container.knowledge.await() }
        show(open = MainActivity.OPEN_REVIEW)
        waitFor(hasText("Nothing to review."))
        compose.onNodeWithText("Add photos").assertIsDisplayed()
        shot("review_empty")
    }

    @Test
    fun scanSettings() {
        shadowOf(app).grantPermissions(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.POST_NOTIFICATIONS)
        app.gallery.add(1, Instant.now(), bedPhoto)
        app.gallery.add(2, Instant.now(), bedPhoto, albumId = "Garden")
        runBlocking { app.container.settingsStore.update { it.copy(scanLibrary = true, scanSince = Instant.now().toEpochMilli()) } }
        show()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("On. Pollenplot looks for plants in new photos.").assertIsDisplayed()
        compose.onNodeWithText("Photo scanning").performClick()
        waitFor(hasText("Garden"))
        shot("scan_settings")

        // Ticking a second album keeps the camera's album too.
        compose.onNodeWithText("Garden").performClick()
        compose.waitUntil(WAIT_MS) { runBlocking { app.container.settingsStore.current() }.scanAlbums == setOf(FakeGallery.CAMERA, "Garden") }
    }

    @Test
    fun scanSettingsWithOnlySomePhotos() {
        shadowOf(app).grantPermissions(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        app.gallery.access = GalleryAccess.PARTIAL
        app.gallery.add(1, Instant.now(), bedPhoto)
        runBlocking { app.container.settingsStore.update { it.copy(scanLibrary = true, scanSince = Instant.now().toEpochMilli()) } }
        show()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Photo scanning").performClick()
        waitFor(hasText("Allow all photos"))
        compose.onNodeWithText("Allow notifications").assertIsDisplayed()
        shot("scan_settings_partial")
    }

    private fun waitFor(matcher: SemanticsMatcher) {
        try {
            compose.waitUntil(WAIT_MS) { compose.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
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
