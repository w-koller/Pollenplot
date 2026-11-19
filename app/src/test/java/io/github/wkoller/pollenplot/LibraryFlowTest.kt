package io.github.wkoller.pollenplot

import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import android.graphics.Bitmap
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.imageDecoderEnabled
import io.github.wkoller.pollenplot.data.prefs.Settings
import io.github.wkoller.pollenplot.domain.plants.AuState
import io.github.wkoller.pollenplot.ui.components.LocalNotices
import io.github.wkoller.pollenplot.ui.components.rememberNotices
import io.github.wkoller.pollenplot.ui.library.LibraryScreen
import io.github.wkoller.pollenplot.ui.nav.AppRoot
import io.github.wkoller.pollenplot.ui.theme.PollenplotTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The plant library: every plant Pollenplot knows with a photo fetched for it, the user's own library that each added
 * plant enters, several plants added to several zones at once, and a zone's three ways to add a plant.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = WeedTestApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class LibraryFlowTest {
    @Suppress("DEPRECATION")
    @get:Rule
    val compose = createComposeRule()

    private val app: WeedTestApp get() = ApplicationProvider.getApplicationContext()
    private val garden get() = app.container.garden

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        @OptIn(DelicateCoilApi::class)
        SingletonImageLoader.setUnsafe(ImageLoader.Builder(app).coroutineContext(Dispatchers.Unconfined).imageDecoderEnabled(false).build())
    }

    private fun seed(): DemoGarden.Ids = runBlocking {
        app.container.knowledge.await()
        app.container.settingsStore.update { it.copy(gardenState = AuState.QLD, setupDone = true) }
        DemoGarden.seed(garden)
    }

    @Test
    fun photosAreAskedForAndSeveralPlantsGoToTwoZones() {
        val ids = seed()
        showLibrary()
        // Nothing is fetched until the user says photos may be shown.
        waitFor(hasText("Photos of the plants"))
        assertTrue(app.web.asked.none { "taxon_name" in it })
        shot("library_photos_question")
        compose.onNodeWithText("Show photos").performClick()
        // The photo kept is one whose licence asks only for a credit, and the credit is shown with it.
        waitFor(hasText("Photo: Hejor1 · NatureMapr · CC-BY 3.0 (Au)"))
        assertTrue("Only the plant's name is sent", app.web.asked.filter { "taxon_name" in it }.none { "lat=" in it || "lon=" in it })
        shot("library_all")

        // A press on a photo opens it over the whole screen, with its larger copy and its credit.
        compose.onAllNodesWithContentDescription("Enlarge the photo of", substring = true)[0].performClick()
        waitFor(hasText("Close"))
        compose.waitUntil(WAIT_MS) { app.web.asked.any { "proxyImageThumbnailLarge" in it } }
        waitFor(hasContentDescription("A photo of", substring = true))
        shot("library_photo_viewer")
        compose.onNodeWithText("Close").performClick()
        compose.waitUntil(WAIT_MS) { compose.onAllNodesWithText("Close").fetchSemanticsNodes().isEmpty() }

        compose.onNodeWithText("Select").performClick()
        // A row scrolled to may sit partly under the top bar, so its press is sent as an action rather than a tap.
        compose.scrollTo(hasText("Hibbertia scandens"))
        compose.onNodeWithText("Hibbertia scandens").performSemanticsAction(SemanticsActions.OnClick)
        compose.scrollTo(hasText("Melaleuca viminalis"))
        compose.onNodeWithText("Melaleuca viminalis").performSemanticsAction(SemanticsActions.OnClick)
        waitFor(hasText("Add to zones (2)"))
        shot("library_selecting")
        compose.onNodeWithText("Add to zones (2)").performClick()
        waitFor(hasText("Add 2 plants"))
        compose.onNodeWithText("Front Native Strip").performClick()
        compose.onNodeWithText("Veg Patch").performClick()
        waitFor(hasText("Keeping the plant"))
        shot("library_add_many")
        compose.onNodeWithText("Add to garden").performClick()
        waitFor(hasText("Added 2 plants to Front Native Strip, Veg Patch."))
        val added = runBlocking { garden.plantList.first() }.filter { it.speciesName in setOf("Hibbertia scandens", "Melaleuca viminalis") }
        assertEquals("One plant of each, growing in both zones", listOf(listOf(ids.front, ids.veg), listOf(ids.front, ids.veg)), added.map { it.zoneIds })

        // Every plant added is in the user's library, with the plants the garden already had. The list is where it
        // was before the plants were added, so its top is scrolled to first.
        compose.scrollTo(hasText("My library"))
        compose.onNodeWithText("My library").performClick()
        compose.scrollTo(hasText("Melaleuca viminalis"))
        val mine = runBlocking { app.container.library.plants.first() }.mapNotNull { it.speciesName }
        assertTrue(mine.containsAll(listOf("Hibbertia scandens", "Melaleuca viminalis", "Citrus × limon")))
        shot("library_mine")

        // Undo takes the plants out of the garden again; they stay in the library.
        compose.onNodeWithText("Undo").performClick()
        compose.waitUntil(WAIT_MS) { runBlocking { garden.plantList.first() }.none { it.speciesName == "Hibbertia scandens" } }
        assertTrue(runBlocking { app.container.library.plants.first() }.any { it.speciesName == "Hibbertia scandens" })
    }

    @Test
    fun aZoneOffersTheLibraryAPhotoOrTheGallery() {
        val ids = seed()
        runBlocking { app.container.settingsStore.update { it.copy(libraryPhotos = false) } }
        compose.setContent { PollenplotTheme(Settings(gardenState = AuState.QLD)) { Surface { AppRoot(app.container) } } }
        waitFor(hasContentDescription("Hive Corner", substring = true))
        compose.onNodeWithContentDescription("Hive Corner", substring = true).performClick()
        compose.scrollTo(hasText("Add a plant to this zone"))
        compose.onNodeWithText("Add a plant to this zone").performClick()
        waitFor(hasText("Add a plant to Hive Corner"))
        compose.onNodeWithText("Take a photo").assertExists()
        compose.onNodeWithText("Choose photos").assertExists()
        shot("zone_add_plant_sheet")

        compose.onNodeWithText("Search the plant library").performClick()
        waitFor(hasText("Plant library"))
        compose.scrollTo(hasText("Melaleuca viminalis"))
        compose.onNodeWithText("Melaleuca viminalis").performSemanticsAction(SemanticsActions.OnClick)
        // The zone the library was opened from is already chosen.
        waitFor(hasText("Add Weeping bottlebrush"))
        compose.onNodeWithText("Add to garden").performClick()
        waitFor(hasText("Added Weeping bottlebrush to Hive Corner."))
        assertEquals(listOf(ids.hive), runBlocking { garden.plantList.first() }.single { it.speciesName == "Melaleuca viminalis" }.zoneIds)
    }

    @Test
    fun aPressShowsThePlantAndAddModeAddsAtOnce() {
        seed()
        runBlocking { app.container.settingsStore.update { it.copy(libraryPhotos = false) } }
        showLibrary()
        waitFor(hasText("Add mode"))
        compose.scrollTo(hasText("Melaleuca viminalis"))
        compose.onNodeWithText("Melaleuca viminalis").performSemanticsAction(SemanticsActions.OnClick)
        // The plant's page, with the same facts as a garden plant's and a button to add it.
        waitFor(hasText("Save to my library"))
        shot("library_plant_page")
        compose.scrollTo(hasText("Bee forage"))
        compose.scrollTo(hasText("Add to garden"))
        compose.onNodeWithText("Add to garden").performClick()
        waitFor(hasText("Add Weeping bottlebrush"))
        compose.onAllNodesWithText("Add to garden").onLast().performClick()
        waitFor(hasText("Added Weeping bottlebrush to your garden.", substring = true))
        assertTrue(runBlocking { garden.plantList.first() }.any { it.speciesName == "Melaleuca viminalis" })

        // Back on the list, add mode makes a press add the plant straight away.
        compose.onNodeWithContentDescription("Back").performClick()
        // The list is where it was, at the plant, so the switch at its top is scrolled to.
        waitFor(hasText("Melaleuca viminalis"))
        compose.scrollTo(hasText("Add mode"))
        compose.onNodeWithText("Add mode").performClick()
        compose.scrollTo(hasText("Melaleuca viminalis"))
        compose.onNodeWithText("Melaleuca viminalis").performSemanticsAction(SemanticsActions.OnClick)
        waitFor(hasText("Add Weeping bottlebrush"))
        compose.onNodeWithText("Bee forage").assertDoesNotExist()
    }

    private fun showLibrary() {
        compose.setContent {
            PollenplotTheme(Settings(gardenState = AuState.QLD)) {
                Surface {
                    CompositionLocalProvider(LocalNotices provides rememberNotices()) {
                        LibraryScreen(app.container.knowledge, app.container.views, app.container.library, app.container.libraryPhotos, app.container.settingsStore, null, onBack = {}, onOpenPlant = {})
                    }
                }
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
