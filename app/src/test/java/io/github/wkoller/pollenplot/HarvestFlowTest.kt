package io.github.wkoller.pollenplot

import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import io.github.wkoller.pollenplot.data.TodayItem
import io.github.wkoller.pollenplot.data.prefs.Settings
import io.github.wkoller.pollenplot.data.prefs.ThemeChoice
import io.github.wkoller.pollenplot.domain.garden.PlantEventKind
import io.github.wkoller.pollenplot.domain.plants.AuState
import io.github.wkoller.pollenplot.domain.plants.CareField
import io.github.wkoller.pollenplot.domain.plants.CareProfile
import io.github.wkoller.pollenplot.domain.season.MonthSet
import io.github.wkoller.pollenplot.report.ReportPart
import io.github.wkoller.pollenplot.ui.plants.HarvestScreen
import io.github.wkoller.pollenplot.ui.plants.PlantDetailScreen
import io.github.wkoller.pollenplot.ui.plants.PlantEventScreen
import io.github.wkoller.pollenplot.ui.theme.PollenplotTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.Month

/**
 * Harvests and pruning: a harvest recorded from the lemon's page and added up for the season, the native fuchsia due a
 * prune in the month after its flowering, on the Today card and in the morning notification, until a prune is
 * recorded, and the user's own pruning months. The clock is fixed at 28 October 2026.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = WildlifeTestApp::class, qualifiers = "en-rAU-w411dp-h891dp-xxhdpi")
class HarvestFlowTest {
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

    private fun plantNamed(name: String): String = runBlocking { app.container.views.plants.first { list -> list.any { it.title == name } } }.first { it.title == name }.plant.id

    @Test
    fun aHarvestIsRecordedAndAddedUpForTheSeason() {
        val ids = seed()
        show(ids.lemon)
        waitFor(hasText("Citrus × limon"))
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Record a harvest"))
        compose.onNodeWithText("No harvests recorded yet.").assertExists()
        compose.onNodeWithText("Record a harvest").performClick()
        waitFor(hasText("Weight in kilograms (optional)"))
        val fields = compose.onAllNodes(hasSetTextAction())
        fields[0].performTextInput("0.4")
        fields[1].performTextInput("3")
        compose.onNodeWithText("4 out of 5").performClick()
        fields[2].performTextInput("Very juicy")
        shot("harvest_form")
        compose.onNodeWithText("Save").performScrollTo().performClick()
        waitFor(hasText("Citrus × limon"))
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("In spring 2026 you recorded 1 harvest, which gave 400 g and 3 pieces."))
        compose.onNodeWithText("You picked from it in October.").assertExists()
        compose.onNodeWithText("400 g · 3 pieces · Rated 4 out of 5 · Very juicy").assertExists()
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Please prune it after the harvest", substring = true))
        shot("plant_harvests")

        val harvest = runBlocking { app.container.harvests.harvests.first { it.isNotEmpty() } }.single()
        assertEquals(400, harvest.weightG)
        assertEquals(3, harvest.count)
        assertEquals(4, harvest.rating)

        val report = runBlocking { app.container.reports.build(setOf(ReportPart.HARVESTS), today.minusMonths(1), today) }
        val section = report.sections.single()
        assertEquals("Harvests", section.title)
        assertEquals(2, section.tables.size)
        assertTrue(runBlocking { app.container.exportJsonLd() }.contains("PlantHarvest"))
    }

    @Test
    fun aNativeFuchsiaIsDueAPruneAfterFloweringUntilOneIsRecorded() {
        seed()
        val correa = plantNamed("Native fuchsia")
        val due = runBlocking { app.container.today.state.first { state -> state.items.any { it is TodayItem.Prune } } }
            .items.filterIsInstance<TodayItem.Prune>().single()
        assertEquals(correa, due.plantId)

        shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runBlocking { app.container.pruneCheck.run() }
        assertEquals(1, shadowOf(manager).allNotifications.size)
        manager.cancelAll()
        runBlocking { app.container.pruneCheck.run() }
        assertEquals("Told once a season", 0, shadowOf(manager).allNotifications.size)

        show(correa)
        waitFor(hasText("Correa reflexa"))
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Record pruning"))
        compose.onNodeWithText("Due for a prune").assertExists()
        compose.onNodeWithText("Prune it in October.").assertExists()
        shot("plant_pruning")
        compose.onNodeWithText("Record pruning").performClick()
        waitFor(hasText("Tip-pruned"))
        compose.onNodeWithText("Tip-pruned").performClick()
        compose.onNodeWithText("Save").performScrollTo().performClick()
        // The prune is stored first, then the page shows it once the database's flow reaches it.
        val event = runBlocking { app.container.pots.events.first { it.isNotEmpty() } }.single()
        assertEquals(PlantEventKind.TIP_PRUNED, event.kind)
        runBlocking { app.container.harvestViews.due.first { it.isEmpty() } }
        compose.scrollTo(hasText("Last pruned on 28 October 2026."))
    }

    @Test
    fun yourOwnPruningMonthsComeFirst() {
        val ids = seed()
        runBlocking { app.container.corrections.set("Citrus × limon", CareField.PRUNING_MONTHS, CareProfile(pruningMonths = MonthSet.of(Month.OCTOBER))) }
        val due = runBlocking { app.container.harvestViews.due.first { list -> list.any { it.plantId == ids.lemon } } }
        assertTrue(due.any { it.title == "Mum's lemon" })
        compose.setContent {
            PollenplotTheme(Settings(theme = ThemeChoice.DARK, gardenState = AuState.QLD)) {
                Surface {
                    PlantDetailScreen(
                        views = app.container.views,
                        garden = app.container.garden,
                        knowledge = app.container.knowledge,
                        plantId = ids.lemon,
                        onBack = {},
                        today = today,
                        crops = app.container.harvestViews,
                    )
                }
            }
        }
        waitFor(hasText("Citrus × limon"))
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("These are your own months, set in Edit facts."))
        shot("plant_pruning_dark")
    }

    /** A plant's page, which opens the harvest and pruning forms in its place. */
    private fun show(plantId: String) {
        compose.setContent {
            PollenplotTheme(Settings(gardenState = AuState.QLD)) {
                Surface {
                    var screen by remember { mutableStateOf("plant") }
                    when (screen) {
                        "harvest" -> HarvestScreen(plantId, null, app.container.harvests, today) { screen = "plant" }
                        "prune" -> PlantEventScreen(plantId, app.container.pots, today, PlantEventKind.PRUNING) { screen = "plant" }
                        else -> PlantDetailScreen(
                            views = app.container.views,
                            garden = app.container.garden,
                            knowledge = app.container.knowledge,
                            plantId = plantId,
                            onBack = {},
                            today = today,
                            pots = app.container.potViews,
                            crops = app.container.harvestViews,
                            onRecordHarvest = { screen = "harvest" },
                            onRecordPruning = { screen = "prune" },
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
