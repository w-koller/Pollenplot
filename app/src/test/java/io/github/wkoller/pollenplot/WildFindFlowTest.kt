package io.github.wkoller.pollenplot

import android.graphics.Bitmap
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.imageDecoderEnabled
import io.github.wkoller.pollenplot.data.PhotoAnalysis
import io.github.wkoller.pollenplot.data.prefs.Settings
import io.github.wkoller.pollenplot.data.prefs.ThemeChoice
import io.github.wkoller.pollenplot.domain.garden.WishStatus
import io.github.wkoller.pollenplot.domain.id.DetectionStatus
import io.github.wkoller.pollenplot.domain.id.LabelScore
import io.github.wkoller.pollenplot.domain.id.PhotoScope
import io.github.wkoller.pollenplot.domain.place.GardenPlace
import io.github.wkoller.pollenplot.domain.plants.AuState
import io.github.wkoller.pollenplot.domain.wildlife.LifeGroup
import io.github.wkoller.pollenplot.ui.identify.IdentifyModel
import io.github.wkoller.pollenplot.ui.identify.IdentifyScreen
import io.github.wkoller.pollenplot.ui.identify.IdentifyStage
import io.github.wkoller.pollenplot.ui.nav.AppRoot
import io.github.wkoller.pollenplot.ui.theme.PollenplotTheme
import kotlinx.coroutines.Dispatchers
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
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Plants identified away from home: saved to the wishlist or let go, never waiting in the Review queue, with weeds
 * showing native alternatives, and a wish planted in the garden. The garden is in Taringa, with weed lists recorded
 * from the real services.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = WeedTestApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class WildFindFlowTest {
    // The classic rule runs coroutines at once, so flows from the database reach the screen while the test waits.
    @Suppress("DEPRECATION")
    @get:Rule
    val compose = createComposeRule()

    private val app: WeedTestApp get() = ApplicationProvider.getApplicationContext()
    private val garden get() = app.container.garden
    private val wishes get() = app.container.wishes

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        @OptIn(DelicateCoilApi::class)
        SingletonImageLoader.setUnsafe(
            ImageLoader.Builder(app).coroutineContext(Dispatchers.Unconfined).imageDecoderEnabled(false).build(),
        )
    }

    @Test
    fun aFindAwayFromHomeIsSavedAndNeverWaitsForReview() {
        located()
        val model = found(LabelScore("Callistemon citrinus", 7_400))
        assertEquals("A find away from home never waits in the Review queue", 0, runBlocking { garden.pendingReviewCount.first() })
        show(model)
        waitFor(hasText("Native to Queensland."))
        compose.onNodeWithText("Zone it might go in").assertExists()
        shot("field_native")

        compose.onNodeWithText("Save to wishlist").performScrollTo().performClick()
        waitFor(hasText("Saved to your wishlist."))
        shot("field_saved")
        val wish = runBlocking { wishes.wishes.first { it.isNotEmpty() }.single() }
        assertEquals("Melaleuca citrina", wish.speciesName)
        assertEquals(WishStatus.WANTED, wish.status)
        assertTrue("The wish keeps the find's cut-out", wish.thumbnail != null && File(app.filesDir, wish.thumbnail!!).isFile)

        // Taking another photo keeps one with something saved from it.
        val photoId = (model.stage.value as IdentifyStage.Results).photoId
        model.takeAnother()
        assertEquals(DetectionStatus.SAVED, runBlocking { garden.detectionsInPhoto(photoId).first() }.single().status)
        assertEquals(0, runBlocking { garden.pendingReviewCount.first() })
        assertTrue("A wish is found by search", runBlocking { garden.search("crimson") }.any { it.recordId == wish.id })
    }

    @Test
    fun aFindLetGoIsDeletedOnLeaving() {
        located()
        val model = found(LabelScore("Callistemon citrinus", 7_400))
        show(model)
        waitFor(hasText("Not this one"))
        val photoId = (model.stage.value as IdentifyStage.Results).photoId
        val photoFile = File(app.filesDir, runBlocking { garden.photo(photoId) }!!.path)
        assertTrue(photoFile.isFile)
        compose.onNodeWithText("Not this one").performScrollTo().performClick()
        waitFor(hasText("This find will not be saved, and the photo is deleted when you leave."))

        model.takeAnother()
        // The photo is let go outside the screen's scope, which may already be gone, and its files after its record.
        compose.waitUntil(WAIT_MS) { runBlocking { garden.photo(photoId) } == null && !photoFile.exists() }
        assertNull("The photo's record is gone", runBlocking { garden.photo(photoId) })
        assertFalse("The photo's file is gone", photoFile.exists())
    }

    @Test
    @Config(qualifiers = "w411dp-h2000dp-xhdpi")
    fun aWeedAsksBeforeSavingAndShowsNativeAlternatives() {
        located()
        // Lantana is a weed in Brisbane; the Northern Territory's booklet is the one that names an alternative to it.
        runBlocking { app.container.settingsStore.update { it.copy(gardenState = AuState.NT) } }
        val model = found(LabelScore("Lantana camara", 8_000))
        show(model)
        waitFor(hasText("Native alternatives"))
        compose.onNodeWithText("Source: Grow Me Instead: Northern Territory").assertExists()
        shot("field_weed_alternatives")

        compose.onNodeWithText("Add to wishlist").performScrollTo().performClick()
        waitFor(hasText("On your wishlist"))
        assertEquals(listOf("Eremophila polyclada"), runBlocking { wishes.wishes.first { it.isNotEmpty() } }.map { it.speciesName })

        compose.onNodeWithText("Save to wishlist").performScrollTo().performClick()
        waitFor(hasText("Save a weed?"))
        compose.onNodeWithText("Cancel").performClick()
        assertEquals("Cancelling saves nothing", 1, runBlocking { wishes.wishes.first() }.size)
    }

    @Test
    fun aWishIsPlantedFromTheWishlist() {
        located()
        val ids = runBlocking { DemoGarden.seed(garden) }
        runBlocking { wishes.add("Hibbertia scandens", targetZoneId = ids.front) }
        showApp()
        compose.pressWhenShown(hasContentDescription("Wishlist"))
        waitFor(hasText("Snake vine"))
        shot("wishlist")
        compose.onNodeWithText("Snake vine").performClick()
        waitFor(hasText("Plant it"))
        shot("wish")
        compose.onNodeWithText("Plant it").performScrollTo().performClick()
        waitFor(hasText("Add to garden"))
        shot("wish_plant_sheet")
        compose.onNodeWithText("Add to garden").performClick()
        waitFor(hasText("In Front Native Strip"))
        compose.waitUntil(WAIT_MS) { runBlocking { wishes.wishes.first() }.single().status == WishStatus.PLANTED }
        val plant = runBlocking { garden.plantList.first() }.single { it.speciesName == "Hibbertia scandens" }
        assertEquals("The plant goes in the wish's zone", ids.front, plant.zoneId)
    }

    @Test
    fun theWishlistInTheDark() {
        located()
        runBlocking {
            DemoGarden.seed(garden)
            wishes.add("Westringia fruticosa")
        }
        showApp(Settings(theme = ThemeChoice.DARK))
        compose.pressWhenShown(hasContentDescription("Wishlist"))
        waitFor(hasText("Coastal rosemary"))
        shot("wishlist_dark")
    }

    @Test
    fun anEmptyWishlistOffersToFindAPlant() {
        located()
        showApp()
        compose.pressWhenShown(hasContentDescription("Wishlist"))
        waitFor(hasText("Your wishlist is empty."))
        shot("wishlist_empty")
    }

    /** Puts the garden in Taringa, with its weed lists downloaded from the recorded answers. */
    private fun located() = runBlocking {
        app.container.knowledge.await()
        app.container.settingsStore.update {
            it.copy(gardenPlace = GardenPlace.at(-27.49, 152.98, "Taringa", "BRISBANE CITY"), gardenState = AuState.QLD, setupDone = true)
        }
        assertTrue(app.container.weedSync.update().complete)
    }

    /** A photo taken away from home of one plant, which the models think is [labels]. */
    private fun found(vararg labels: LabelScore): IdentifyModel {
        val identifier = ScriptedIdentifier { listOf(TestPhotos.region(TestPhotos.bottlebrush, false, *labels)) }
        val container = app.container
        val analysis = PhotoAnalysis({ identifier }, container.photos, garden, container.knowledge, container.clock)
        val model = IdentifyModel(
            analysis, { identifier }, garden, container.knowledge, container.settingsStore, SavedStateHandle(),
            field = true, wishes = wishes, discardFieldPhoto = container::discardFieldPhoto,
        )
        model.choose(LifeGroup.PLANT, PhotoScope.ONE)
        runBlocking { model.analyse(TestPhotos.gardenPhoto()) }
        assertTrue("The photo was analysed, not ${model.stage.value}", model.stage.value is IdentifyStage.Results)
        return model
    }

    private fun show(model: IdentifyModel) {
        compose.setContent {
            PollenplotTheme(Settings()) {
                Surface {
                    IdentifyScreen(
                        model = model,
                        views = app.container.views,
                        knowledge = app.container.knowledge,
                        settings = app.container.settingsStore,
                        onBack = {},
                        onOpenPlant = {},
                        onAddByName = {},
                        help = app.container.weedHelp,
                    )
                }
            }
        }
    }

    private fun showApp(settings: Settings = Settings()) {
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
