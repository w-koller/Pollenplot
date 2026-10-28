package io.github.wkoller.pollenplot

import android.graphics.Bitmap
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
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
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import io.github.wkoller.pollenplot.data.prefs.Settings
import io.github.wkoller.pollenplot.data.prefs.ThemeChoice
import io.github.wkoller.pollenplot.domain.garden.ZoneBadge
import io.github.wkoller.pollenplot.domain.plants.AuState
import io.github.wkoller.pollenplot.domain.wildlife.Behaviour
import io.github.wkoller.pollenplot.domain.wildlife.Sighting
import io.github.wkoller.pollenplot.ui.nav.AppRoot
import io.github.wkoller.pollenplot.ui.plot.ZoneDetailScreen
import io.github.wkoller.pollenplot.ui.theme.PollenplotTheme
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

/** The Wildlife tab, an animal's page and a sighting's, on a fixed day in Brisbane. Saves a picture of each step. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = WildlifeTestApp::class, qualifiers = "en-rAU-w411dp-h891dp-xxhdpi")
class WildlifeFlowTest {
    // The classic rule runs coroutines at once, so flows from the database reach the screen while the test waits.
    @Suppress("DEPRECATION")
    @get:Rule
    val compose = createComposeRule()

    private val app: WildlifeTestApp get() = ApplicationProvider.getApplicationContext()
    private val garden get() = app.container.garden

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
    }

    @Test
    @Config(qualifiers = "en-rAU-w411dp-h2600dp-xhdpi")
    fun theYearsWildlifeWithWhatToReport() {
        seed()
        show()
        compose.onNodeWithText("Wildlife").performClick()
        waitFor(hasText("8 species seen in your yard this year"))
        compose.onNodeWithText("6 of them are native to Australia.").assertExists()
        compose.onNodeWithText("7 were seen in your yard for the first time.").assertExists()
        compose.onNodeWithText("Animals to know about").assertExists()
        compose.onNodeWithText("13 22 68", substring = true).assertExists()
        compose.onNodeWithText("Birds · 3 species").assertExists()
        compose.onNodeWithText("Reptiles · 1 species").assertExists()
        // The skink isn't in the animal list, so Pollenplot says nothing about where it is native.
        compose.onNodeWithText("Brown skink").assertExists()
        shot("wildlife_tab")
    }

    @Test
    fun anEmptyYardSaysHowToStart() {
        show()
        compose.onNodeWithText("Wildlife").performClick()
        waitFor(hasText("No wildlife has been recorded yet."))
        compose.onNodeWithText("Add a sighting by name").assertExists()
        shot("wildlife_empty")
    }

    @Test
    @Config(qualifiers = "en-rAU-w411dp-h1600dp-xhdpi")
    fun anAnimalsPageThenChangeAndDeleteASighting() {
        seed()
        show()
        compose.onNodeWithText("Wildlife").performClick()
        waitFor(hasText("Noisy miner"))
        compose.onAllNodesWithText("Noisy miner").onFirst().performClick()
        waitFor(hasText("Seen 3 times, most recently on 12 October 2026."))
        compose.onNodeWithText("First seen on 20 November 2025.").assertExists()
        compose.onNodeWithText("Plants it was seen on").assertExists()
        shot("wildlife_animal")

        compose.onNodeWithText("12 October 2026").performClick()
        waitFor(hasText("Seen on 12 October 2026", substring = true))
        shot("wildlife_sighting")
        compose.onNodeWithText("Resting").performClick()
        compose.onNodeWithText("Save").performScrollTo().performClick()
        waitFor(hasText("Seen 3 times, most recently on 12 October 2026."))
        assertEquals(Behaviour.RESTING, sightings().first { it.seenAt.atZone(DemoWildlife.zone).dayOfMonth == 12 }.behaviour)

        compose.onNodeWithText("3 August 2026").performClick()
        waitFor(hasText("Seen on 3 August 2026", substring = true))
        compose.onNodeWithContentDescription("Delete sighting").performClick()
        compose.onNodeWithText("Delete this sighting?").assertExists()
        compose.onNodeWithText("Delete").performClick()
        waitFor(hasText("Seen 2 times, most recently on 12 October 2026."))
        assertEquals(9, sightings().size)
    }

    @Test
    fun addASightingByName() {
        val ids = seed()
        show()
        compose.onNodeWithText("Wildlife").performClick()
        waitFor(hasText("8 species seen in your yard this year"))
        compose.onNodeWithContentDescription("Open the add menu").performClick()
        compose.onNodeWithText("Add a sighting by name").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("tongue")
        waitFor(hasText("Eastern blue-tongued lizard"))
        compose.onNodeWithText("Eastern blue-tongued lizard").performClick()
        waitFor(hasText("Add sighting"))
        compose.onNodeWithText("Veg Patch").performClick()
        compose.onNodeWithText("Basking").performClick()
        shot("wildlife_add_sighting")
        compose.onNodeWithText("Add sighting").performScrollTo().performClick()
        waitFor(hasText("9 species seen in your yard this year"))
        val added = sightings().single { it.speciesName == "Tiliqua scincoides" }
        assertEquals(ids.veg, added.zoneId)
        assertEquals(Behaviour.BASKING, added.behaviour)
        assertEquals("Added without a photo, on the day it was added", DemoWildlife.clock.instant(), added.seenAt)
    }

    @Test
    @Config(qualifiers = "en-rAU-w411dp-h1400dp-xhdpi")
    fun frogsSeenInAZoneShowOnItsPage() {
        val ids = seed()
        val front = runBlocking { app.container.views.zone(ids.front).first { it != null && it.sightings.isNotEmpty() }!! }
        assertTrue(ZoneBadge.FROGS_SEEN in front.badges)
        compose.setContent {
            PollenplotTheme(Settings()) {
                Surface { ZoneDetailScreen(app.container.views, ids.front, onBack = {}, onEdit = {}, onOpenPlant = {}, onAddPlant = {}) }
            }
        }
        waitFor(hasText("Wildlife seen here"))
        compose.onNodeWithText("Frogs seen").assertExists()
        compose.onNodeWithText("Eastern dwarf tree frog").assertExists()
        shot("zone_wildlife")
    }

    @Test
    @Config(qualifiers = "en-rAU-w360dp-h1600dp-xxhdpi", fontScale = 1.3f)
    fun theTabInTheDarkWithLargeText() {
        seed()
        show(Settings(theme = ThemeChoice.DARK))
        compose.onNodeWithText("Wildlife").performClick()
        waitFor(hasText("8 species seen in your yard this year"))
        shot("wildlife_tab_dark_large")
    }

    private fun seed(): DemoGarden.Ids = runBlocking {
        app.container.knowledge.await()
        app.container.knowledge.awaitFauna()
        app.container.settingsStore.update { it.copy(gardenState = AuState.QLD) }
        DemoGarden.seed(garden).also { DemoWildlife.seed(garden, it) }
    }

    private fun sightings(): List<Sighting> = runBlocking { garden.sightingList.first() }

    private fun show(settings: Settings = Settings()) {
        compose.setContent {
            PollenplotTheme(settings) {
                Surface { AppRoot(app.container) }
            }
        }
    }

    private fun waitFor(matcher: SemanticsMatcher) {
        compose.waitUntil(10_000) { compose.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        val dir = File(System.getProperty("screenshots.dir") ?: "build/screenshots").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
