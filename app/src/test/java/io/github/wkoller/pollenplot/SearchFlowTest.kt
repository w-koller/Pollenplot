package io.github.wkoller.pollenplot

import android.graphics.Bitmap
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import io.github.wkoller.pollenplot.data.prefs.Settings
import io.github.wkoller.pollenplot.domain.bees.honey.HiveType
import io.github.wkoller.pollenplot.domain.bees.honey.Inspection
import io.github.wkoller.pollenplot.domain.bees.honey.QueenCells
import io.github.wkoller.pollenplot.domain.plants.AuState
import io.github.wkoller.pollenplot.ui.nav.AppRoot
import io.github.wkoller.pollenplot.ui.search.SearchScreen
import io.github.wkoller.pollenplot.ui.theme.PollenplotTheme
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
import java.time.LocalDate

/**
 * Search everywhere: the notes of a hive's inspections and a plant's harvests are found under the hive and the plant,
 * the results narrow to one kind of record or one zone, and every tab's top bar opens search.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = WildlifeTestApp::class, qualifiers = "en-rAU-w411dp-h891dp-xxhdpi")
class SearchFlowTest {
    // The classic rule runs coroutines at once, so flows from the database reach the screen while the test waits.
    @Suppress("DEPRECATION")
    @get:Rule
    val compose = createComposeRule()

    private val app: WildlifeTestApp get() = ApplicationProvider.getApplicationContext()
    private val today: LocalDate get() = LocalDate.now(app.container.clock)

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
    }

    private fun seed(): DemoGarden.Ids = runBlocking {
        app.container.knowledge.await()
        app.container.settingsStore.update { it.copy(gardenState = AuState.QLD, setupDone = true) }
        DemoGarden.seed(app.container.garden)
    }

    @Test
    fun theNotesOfRecordsAreFoundUnderTheirHiveAndPlant() {
        val ids = seed()
        val bees = app.container.bees
        val hive = runBlocking { bees.addHive("Hive one", HiveType.LANGSTROTH_8, ids.hive, null, null, null) }
        val inspection = Inspection(bees.newRecordId(), hive.id, today, true, true, null, null, null, null, QueenCells.NONE, emptySet(), "", "Chalkbrood on two frames")
        runBlocking { bees.saveInspection(inspection) }
        assertTrue(runBlocking { app.container.garden.search("chalkbrood") }.any { it.recordId == hive.id && it.kind == "HIVE" })

        runBlocking { app.container.harvests.add(ids.lemon, today, 300, null, 5, "Tangy and full of juice") }
        assertTrue(runBlocking { app.container.garden.search("tangy") }.any { it.recordId == ids.lemon && it.kind == "PLANT" })

        // Deleting the inspection takes its notes out of the hive's entry.
        runBlocking { bees.deleteInspection(inspection.id) }
        assertTrue(runBlocking { app.container.garden.search("chalkbrood") }.isEmpty())
    }

    @Test
    fun resultsNarrowToAKindOrAZone() {
        seed()
        compose.setContent {
            PollenplotTheme(Settings(gardenState = AuState.QLD)) {
                Surface {
                    SearchScreen(
                        garden = app.container.garden,
                        views = app.container.views,
                        knowledge = app.container.knowledge,
                        meaning = app.container.meaning,
                        onBack = {},
                        onOpenZone = {},
                        onOpenPlant = {},
                        onAddSpecies = {},
                        places = app.container.searchPlaces.zoneOf,
                    )
                }
            }
        }
        waitFor(hasSetTextAction())
        compose.onNode(hasSetTextAction()).performTextInput("citrus")
        waitFor(hasText("Mum's lemon"))
        waitFor(hasText("Kind of record"))
        assertTrue("The zone is a result and a zone chip", compose.onAllNodes(hasText("Back Citrus Patch")).fetchSemanticsNodes().size >= 2)
        shot("search_filters")

        compose.onNodeWithText("Zones").performClick()
        compose.waitUntil(WAIT_MS) { compose.onAllNodes(hasText("Mum's lemon")).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Zones").performClick()
        waitFor(hasText("Mum's lemon"))
    }

    @Test
    fun theBeesTabOpensSearch() {
        seed()
        compose.setContent { PollenplotTheme(Settings()) { Surface { AppRoot(app.container) } } }
        waitFor(hasText("Today"))
        compose.pressWhenShown(hasText("Bees") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
        compose.pressWhenShown(hasContentDescription("Search your garden"))
        waitFor(hasSetTextAction())
        compose.onNode(hasSetTextAction()).performTextInput("lemon")
        waitFor(hasText("Mum's lemon"))
        assertEquals(1, compose.onAllNodes(hasText("Mum's lemon")).fetchSemanticsNodes().size)
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
