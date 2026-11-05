package io.github.wkoller.pollenplot

import android.graphics.Bitmap
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
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
import io.github.wkoller.pollenplot.data.prefs.ThemeChoice
import io.github.wkoller.pollenplot.domain.id.LabelScore
import io.github.wkoller.pollenplot.domain.plants.AuState
import io.github.wkoller.pollenplot.domain.plants.CareField
import io.github.wkoller.pollenplot.domain.plants.CareResolver
import io.github.wkoller.pollenplot.domain.plants.FactLevel
import io.github.wkoller.pollenplot.domain.plants.WaterNeed
import io.github.wkoller.pollenplot.ui.nav.AppRoot
import io.github.wkoller.pollenplot.ui.plants.EditFactsScreen
import io.github.wkoller.pollenplot.ui.plants.PlantDetailScreen
import io.github.wkoller.pollenplot.ui.plants.PlantPhotoScreen
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
import java.time.Duration
import java.time.Instant

/**
 * A plant's own page: a find added to any plant from Review joins its photo history, the months it was seen in flower
 * sit under the months expected, and the user's corrections to its facts apply to every plant of the species.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class PlantPageFlowTest {
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
                    TestPhotos.region(bottlebrush, flowering = true, LabelScore("Callistemon citrinus", 7_400), LabelScore("Melaleuca viminalis", 1_200)),
                    TestPhotos.region(rosemary, flowering = false, LabelScore("Rosmarinus officinalis", 4_300)),
                )
            } else {
                emptyList()
            }
        }
    }

    private fun seed(): DemoGarden.Ids = runBlocking {
        app.container.knowledge.await()
        app.container.settingsStore.update { it.copy(gardenState = AuState.QLD, setupDone = true) }
        DemoGarden.seed(garden)
    }

    @Test
    fun aFindAddedToAnyPlantJoinsItsHistory() {
        val ids = seed()
        runBlocking {
            val photo = app.gallery.add(1, Instant.now().minus(Duration.ofDays(2)), bedPhoto)
            app.container.scanner.scanLibraryPhoto(photo)
        }
        compose.setContent {
            PollenplotTheme(Settings(gardenState = AuState.QLD)) { Surface { AppRoot(app.container, open = MainActivity.OPEN_REVIEW) } }
        }
        waitFor(hasText("Taken on", substring = true))

        // The bottlebrush is not in the garden, but the photo may show a plant that is, under another name.
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Add to another plant"))
        compose.onAllNodesWithText("Add to another plant").onFirst().performClick()
        waitFor(hasText("Add to a plant"))
        shot("plant_chooser")
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Mum's lemon"))
        compose.onNodeWithText("Mum's lemon").performClick()
        waitFor(hasText("Open the plant"))
        val finds = runBlocking { garden.findsOf(ids.lemon).first() }
        assertEquals("The find joins the lemon", 1, finds.size)

        compose.onNodeWithText("Open the plant").performClick()
        waitFor(hasText("Lemon"))
        // The page and the row of photos both scroll; the page is the first.
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Seen in flower", substring = true))
        compose.onNodeWithText("Seen in flower in ", substring = true).assertExists()
        // Scrolled to Edit facts, just below the history, so the whole card is on the screen.
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Edit facts"))
        shot("plant_history")
        // The photo may sit under the top bar once scrolled to, so its click is sent as an action rather than a tap.
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Photo from ", substring = true))
        compose.onNodeWithText("Photo from ", substring = true).performSemanticsAction(SemanticsActions.OnClick)
        waitFor(hasText("What the models suggested"))
        compose.onNodeWithText("Taken on ", substring = true).assertExists()
        shot("plant_photo")
    }

    @Test
    fun aCorrectionAppliesToEveryPlantOfTheSpecies() {
        val ids = seed()
        show(ids.lemon)
        openEditFacts()
        waitFor(hasText("Water need"))
        shot("edit_facts")

        // The knowledge base has the lemon's water from the genus Citrus.
        compose.onNodeWithText("High").performScrollTo().performClick()
        waitFor(hasText("Corrected by you"))
        compose.onNodeWithText("Save").performScrollTo().performClick()
        compose.waitUntil(WAIT_MS) { runBlocking { kbWater() } == WaterNeed.HIGH }
        waitFor(hasText("Water need: High"))
        // Both of the water card's lines come from the corrected need.
        compose.onAllNodesWithText("Your correction").onFirst().assertExists()
        shot("plant_corrected")

        // Going back to the knowledge base clears the correction.
        openEditFacts()
        waitFor(hasText("Use the knowledge base"))
        compose.onNodeWithText("Use the knowledge base").performScrollTo().performClick()
        compose.onNodeWithText("Save").performScrollTo().performClick()
        compose.waitUntil(WAIT_MS) { runBlocking { kbWater() } == WaterNeed.MODERATE }
        waitFor(hasText("Water need: Moderate"))
    }

    @Test
    fun editFactsInTheDark() {
        val ids = seed()
        runBlocking {
            val kb = app.container.knowledge.await()
            app.container.corrections.set("Grevillea 'Robyn Gordon'", CareField.FROST, CareResolver.resolve(kb, kb.species("Grevillea 'Robyn Gordon'")!!).care.copy(frost = 3))
        }
        show(ids.grevillea, Settings(theme = ThemeChoice.DARK))
        openEditFacts()
        waitFor(hasText("Corrected by you"))
        shot("edit_facts_dark")
        val resolved = runBlocking { app.container.knowledge.knowledge.first { it != null }!! }.let { CareResolver.resolve(it, it.species("Grevillea 'Robyn Gordon'")!!) }
        assertEquals(FactLevel.USER, resolved.levelOf(CareField.FROST))
        assertTrue("A correction is in the exported linked data", runBlocking { app.container.exportJsonLd() }.contains("CareCorrection"))
    }

    /** Scrolls the plant's page down to Edit facts, once the page has its facts, and opens it. */
    private fun openEditFacts() {
        waitFor(hasText("Light"))
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Edit facts"))
        compose.onNodeWithText("Edit facts").performClick()
    }

    private suspend fun kbWater(): WaterNeed? {
        val kb = app.container.knowledge.knowledge.first { it != null }!!
        return CareResolver.resolve(kb, kb.species("Citrus × limon")!!).care.water
    }

    /** A plant's page, which opens Edit facts and a photo in its place. */
    private fun show(plantId: String, settings: Settings = Settings(gardenState = AuState.QLD)) {
        compose.setContent {
            PollenplotTheme(settings) {
                Surface {
                    var editing by remember { mutableStateOf(false) }
                    var photo by remember { mutableStateOf<Pair<String, String>?>(null) }
                    val open = photo
                    when {
                        editing -> EditFactsScreen(plantId, app.container.views, app.container.knowledge, app.container.corrections) { editing = false }
                        open != null -> PlantPhotoScreen(garden, app.container.knowledge, open.first, open.second) { photo = null }
                        else -> PlantDetailScreen(
                            views = app.container.views,
                            garden = garden,
                            knowledge = app.container.knowledge,
                            plantId = plantId,
                            onBack = {},
                            onEditFacts = { editing = true },
                            onOpenPhoto = { p, d -> photo = p to d },
                        )
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
