package io.github.wkoller.pollenplot

import android.graphics.Bitmap
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import io.github.wkoller.pollenplot.data.prefs.Settings
import io.github.wkoller.pollenplot.data.prefs.ThemeChoice
import io.github.wkoller.pollenplot.domain.garden.PlantStatus
import io.github.wkoller.pollenplot.domain.plants.AuState
import io.github.wkoller.pollenplot.ui.nav.AppRoot
import io.github.wkoller.pollenplot.ui.theme.PollenplotTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/** The graveyard: plants that died or were removed, how they ended, their history, and bringing one back. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = WildlifeTestApp::class, qualifiers = "en-rAU-w411dp-h891dp-xxhdpi")
class GraveyardFlowTest {
    @get:Rule
    val compose = createComposeRule()

    private val app: WildlifeTestApp get() = ApplicationProvider.getApplicationContext()
    private val garden get() = app.container.garden

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
    }

    /** The demo yard, with the lemon planted in November 2024 and dead in March 2026, and the basil pulled out. */
    private fun seed(): DemoGarden.Ids = runBlocking {
        app.container.knowledge.await()
        app.container.settingsStore.update { it.copy(gardenState = AuState.QLD, setupDone = true) }
        val ids = DemoGarden.seed(garden)
        val lemon = garden.plant(ids.lemon)!!
        garden.updatePlant(lemon.copy(plantedOn = LocalDate.of(2024, 11, 2), status = PlantStatus.DIED, endedOn = LocalDate.of(2026, 3, 9)))
        garden.recordFlowering(ids.lemon, LocalDate.of(2025, 9, 20))
        val basil = garden.plantList.first().single { it.speciesName == "Ocimum basilicum" }
        garden.updatePlant(basil.copy(status = PlantStatus.REMOVED, endedOn = LocalDate.of(2026, 5, 1)))
        ids
    }

    @Test
    fun aDeadPlantIsFoundWithItsHistoryAndBroughtBack() {
        val ids = seed()
        show()
        compose.fromMore("Graveyard")
        waitFor(hasText("Mum's lemon"))
        compose.onNodeWithText("Died on 9 March 2026").assertExists()
        compose.onNodeWithText("Lived 1 year 4 months").assertExists()
        waitFor(hasText("1 day in flower"))
        compose.onNodeWithText("Removed on 1 May 2026").assertExists()
        shot("graveyard")

        // Only the plants that died.
        compose.onNodeWithText("Died").performClick()
        compose.waitUntil(WAIT_MS) { compose.onAllNodes(hasText("Removed on 1 May 2026")).fetchSemanticsNodes().isEmpty() }

        compose.onNodeWithText("Mum's lemon").performClick()
        waitFor(hasText("In the graveyard since 9 March 2026"))
        shot("graveyard_plant")
        compose.onNodeWithText("Bring it back to the garden").performClick()
        compose.waitUntil(WAIT_MS) { runBlocking { garden.plant(ids.lemon) }!!.status == PlantStatus.GROWING }
        assertNull(runBlocking { garden.plant(ids.lemon) }!!.endedOn)
        waitFor(hasText("Mum's lemon is back in your garden."))
        assertEquals(LocalDate.of(2024, 11, 2), runBlocking { garden.plant(ids.lemon) }!!.plantedOn)
    }

    @Test
    fun theGraveyardInTheDark() {
        seed()
        show(Settings(theme = ThemeChoice.DARK))
        compose.fromMore("Graveyard")
        waitFor(hasText("Mum's lemon"))
        shot("graveyard_dark")
    }

    private fun show(settings: Settings = Settings(gardenState = AuState.QLD)) {
        compose.setContent { PollenplotTheme(settings) { Surface { AppRoot(app.container) } } }
        waitFor(hasContentDescription("More"))
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
