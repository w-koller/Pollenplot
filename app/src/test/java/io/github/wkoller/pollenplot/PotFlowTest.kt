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
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import io.github.wkoller.pollenplot.data.TodayItem
import io.github.wkoller.pollenplot.data.prefs.Settings
import io.github.wkoller.pollenplot.data.prefs.ThemeChoice
import io.github.wkoller.pollenplot.domain.garden.PlantEventKind
import io.github.wkoller.pollenplot.domain.garden.PlantPot
import io.github.wkoller.pollenplot.domain.garden.PotMaterial
import io.github.wkoller.pollenplot.domain.garden.PotMix
import io.github.wkoller.pollenplot.domain.garden.PotTaskKind
import io.github.wkoller.pollenplot.domain.plants.AuState
import io.github.wkoller.pollenplot.ui.plants.PlantDetailScreen
import io.github.wkoller.pollenplot.ui.plants.PlantEventScreen
import io.github.wkoller.pollenplot.ui.plants.PotEditScreen
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

/**
 * A plant's pot: its details, the care recorded for it, and the flushes and repotting that fall due, on the plant's
 * page, the Today card and the morning notification. The clock is fixed at 28 October 2026.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = WildlifeTestApp::class, qualifiers = "en-rAU-w411dp-h891dp-xxhdpi")
class PotFlowTest {
    // The classic rule runs coroutines at once, so flows from the database reach the screen while the test waits.
    @Suppress("DEPRECATION")
    @get:Rule
    val compose = createComposeRule()

    private val app: WildlifeTestApp get() = ApplicationProvider.getApplicationContext()
    private val pots get() = app.container.pots
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
    fun aPotIsAddedAndItsRepottingRecorded() {
        val ids = seed()
        show(ids.lemon)
        waitFor(hasText("Light"))
        compose.scrollTo(hasText("Add pot details"))
        compose.onNodeWithText("Add pot details").performClick()

        waitFor(hasText("Material"))
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("40")
        compose.onNodeWithText("Terracotta").performScrollTo().performClick()
        compose.onNodeWithText("Citrus potting mix").performScrollTo().performClick()
        shot("pot_edit")
        compose.onNodeWithText("Save").performScrollTo().performClick()
        waitFor(hasText("40 cm across · Terracotta"))
        compose.onNodeWithText("Please record when this plant was last potted up or repotted", substring = true).assertExists()
        shot("pot_card_new")

        compose.onNodeWithText("Record care").performClick()
        waitFor(hasText("What you did"))
        shot("pot_record")
        compose.onNodeWithText("Save").performScrollTo().performClick()
        waitFor(hasText("Repotted on 28 October 2026"))
        compose.onNodeWithText("Next flush around 28 February 2027.").assertExists()
        compose.onNodeWithText("Look at repotting around 28 October 2028.").assertExists()
        shot("pot_card")
        assertEquals(PotMaterial.TERRACOTTA, runBlocking { pots.pot(ids.lemon) }?.material)
        assertEquals(400, runBlocking { pots.pot(ids.lemon) }?.diameterMm)
    }

    @Test
    fun duePotsReachTodayAndTheMorningNotificationOnce() {
        val ids = seed()
        runBlocking {
            pots.savePot(PlantPot(pots.newId(), ids.lemon, 400, null, PotMaterial.GLAZED, PotMix.CITRUS))
            pots.addEvent(ids.lemon, PlantEventKind.REPOTTED, today.minusYears(3))
        }
        val due = runBlocking { app.container.potViews.due.first { it.isNotEmpty() } }
        assertEquals(setOf(PotTaskKind.FLUSH, PotTaskKind.REPOT), due.map { it.task.kind }.toSet())
        val items = runBlocking { app.container.today.state.first { state -> state.items.any { it is TodayItem.PotCare } } }.items
        assertEquals(2, items.count { it is TodayItem.PotCare })
        assertTrue("A zone holding a potted plant counts as pots before a hot day", runBlocking { app.container.potViews.pottedZones.first() }.contains(ids.citrus))

        shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runBlocking { app.container.potCare.run() }
        assertEquals(1, shadowOf(manager).allNotifications.size)
        assertEquals(due.map { it.key }.toSet(), runBlocking { app.container.settingsStore.current() }.potTasksTold)
        // The next morning the same jobs are not told about again.
        manager.cancelAll()
        runBlocking { app.container.potCare.run() }
        assertEquals(0, shadowOf(manager).allNotifications.size)

        // A flush ends that job; a done job is forgotten, so a later one is told about.
        runBlocking { pots.addEvent(ids.lemon, PlantEventKind.FLUSHED, today) }
        runBlocking { app.container.potViews.due.first { list -> list.none { it.task.kind == PotTaskKind.FLUSH } } }
        runBlocking { app.container.potCare.run() }
        assertEquals(1, runBlocking { app.container.settingsStore.current() }.potTasksTold.size)
    }

    @Test
    fun aDuePotInTheDark() {
        val ids = seed()
        runBlocking {
            pots.savePot(PlantPot(pots.newId(), ids.lemon, 400, 30_000, PotMaterial.GLAZED, PotMix.CITRUS, "Searles"))
            pots.addEvent(ids.lemon, PlantEventKind.POTTED_UP, today.minusMonths(5), "Into the glazed pot by the door.")
        }
        show(ids.lemon, Settings(theme = ThemeChoice.DARK))
        waitFor(hasText("Flush due"))
        compose.onAllNodesWithText("Potted up on", substring = true).onFirst().assertExists()
        shot("pot_card_dark")
    }

    /** A plant's page, which opens the pot's editor and the care form in its place. */
    private fun show(plantId: String, settings: Settings = Settings(gardenState = AuState.QLD)) {
        compose.setContent {
            PollenplotTheme(settings) {
                Surface {
                    var screen by remember { mutableStateOf("plant") }
                    when (screen) {
                        "pot" -> PotEditScreen(plantId, pots) { screen = "plant" }
                        "care" -> PlantEventScreen(plantId, pots, today) { screen = "plant" }
                        else -> PlantDetailScreen(
                            views = app.container.views,
                            garden = app.container.garden,
                            knowledge = app.container.knowledge,
                            plantId = plantId,
                            onBack = {},
                            onEditFacts = {},
                            today = today,
                            pots = app.container.potViews,
                            onEditPot = { screen = "pot" },
                            onRecordCare = { screen = "care" },
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
