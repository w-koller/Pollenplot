package io.github.wkoller.pollenplot

import androidx.compose.material3.Surface
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import io.github.wkoller.pollenplot.data.prefs.Settings
import io.github.wkoller.pollenplot.domain.place.GardenPlace
import io.github.wkoller.pollenplot.domain.wildlife.Behaviour
import io.github.wkoller.pollenplot.domain.wildlife.LifeGroup
import io.github.wkoller.pollenplot.ui.nav.AppRoot
import io.github.wkoller.pollenplot.ui.theme.PollenplotTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Exporting the garden's records, on the fixed spring morning of 28 October 2026 in Brisbane. */
@RunWith(AndroidJUnit4::class)
@Config(application = WildlifeTestApp::class, qualifiers = "en-rAU")
class ExportTest {
    @Suppress("DEPRECATION")
    @get:Rule
    val compose = createComposeRule()

    private val app: WildlifeTestApp get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        runBlocking {
            app.container.knowledge.awaitFauna()
            app.container.settingsStore.update { it.copy(gardenPlace = GardenPlace.at(-27.4987, 152.9934, "Taringa")) }
        }
    }

    @Test
    fun `sightings become a Darwin Core table with the garden's rounded place`() = runBlocking {
        app.container.garden.recordSighting("Dacelo novaeguineae", "Laughing kookaburra", LifeGroup.BIRD, null, null, Behaviour.CALLING, 2, DemoWildlife.clock.instant(), "")
        val lines = app.container.exportDarwinCore("Queensland").trimEnd().split("\r\n")
        assertEquals(2, lines.size)
        assertTrue(lines[1], lines[1].contains(",2026-10-28T08:00:00+10:00,Dacelo novaeguineae,Laughing kookaburra,2,calling,present,Australia,AU,Queensland,-27.50,152.99,1000,"))
    }

    @Test
    fun `the export is reached from Settings`() {
        compose.setContent { PollenplotTheme(Settings()) { Surface { AppRoot(app.container) } } }
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.scrollTo(hasText("Export data"))
        compose.onNodeWithText("Export data").performClick()
        compose.waitUntil(WAIT_MS) { compose.onAllNodes(hasText("Garden report")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Save as a PDF").assertExists()
        compose.onNodeWithText("Save as a spreadsheet").assertExists()
        compose.onNodeWithText("Last 3 months").assertExists()
        compose.scrollTo(hasText("Sightings as Darwin Core"))
        compose.scrollTo(hasText("All records as JSON-LD"))
    }
}
