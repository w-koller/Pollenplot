package io.github.wkoller.pollenplot

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.graphics.Bitmap
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import io.github.wkoller.pollenplot.data.prefs.Settings
import io.github.wkoller.pollenplot.data.prefs.ThemeChoice
import io.github.wkoller.pollenplot.domain.irrigation.PlanReason
import io.github.wkoller.pollenplot.domain.irrigation.WaterMethod
import io.github.wkoller.pollenplot.domain.irrigation.WateringLine
import io.github.wkoller.pollenplot.domain.irrigation.WateringWindow
import io.github.wkoller.pollenplot.domain.garden.ZoneKind
import io.github.wkoller.pollenplot.domain.place.GardenPlace
import io.github.wkoller.pollenplot.domain.plants.AuState
import io.github.wkoller.pollenplot.domain.plants.Soil
import io.github.wkoller.pollenplot.ui.nav.AppRoot
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

/** The Water tab for the demo yard, with recorded weather, on the fixed spring morning of 28 October 2026. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = WaterTestApp::class, qualifiers = "en-rAU-w411dp-h3000dp-xhdpi")
class WaterFlowTest {
    @Suppress("DEPRECATION")
    @get:Rule
    val compose = createComposeRule()

    private val app: WaterTestApp get() = ApplicationProvider.getApplicationContext()
    private val water get() = app.container.water

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        runBlocking {
            app.container.knowledge.await()
            app.container.settingsStore.update { it.copy(gardenState = AuState.QLD, gardenPlace = GardenPlace.at(-27.4987, 152.9934, "Taringa")) }
            DemoGarden.seed(app.container.garden)
        }
    }

    @Test
    fun theWeatherIsKeptAndEachZoneGetsAPlan() = runBlocking {
        assertTrue(water.refreshWeather(app.container.settingsStore.current().gardenPlace!!))
        assertTrue("Only the rounded place is sent", app.web.asked.single().contains("latitude=-27.50&longitude=152.99"))
        assertEquals(14, water.weather.first().size)
        val state = app.container.waterViews.state.first { it.et0TodayUm != null }
        assertEquals(5_140, state.et0TodayUm)
        assertEquals("14.2 mm counts as 11.36 mm; the rest falls short of 2 mm", 11_360, state.rainLastWeekUm)
        assertTrue("Zones with plants and no line are watered by hand", state.byHand.map { it.zone.name }.containsAll(listOf("Back Citrus Patch", "Front Native Strip")))
        assertTrue(state.byHand.all { it.plan.reason != PlanReason.NO_WEATHER })

        // Without a connection, the weather kept is used.
        app.web.offline = true
        assertTrue(!water.refreshWeather(app.container.settingsStore.current().gardenPlace!!))
        assertEquals(14, water.weather.first().size)
    }

    @Test
    fun aMorningWithWateringDueBringsOneNotification() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        runBlocking {
            // Tomatoes in sand hold little water: three dry days after the rain use it up.
            val veg = app.container.garden.zoneList.first().single { it.name == "Veg Patch" }
            app.container.garden.updateZone(veg.copy(soil = Soil.SAND))
            app.container.waterPlan.run()
        }
        val manager = shadowOf(app.getSystemService(NotificationManager::class.java))
        val notification = manager.allNotifications.single()
        assertEquals("Veg Patch is due for watering", notification.extras.getString(Notification.EXTRA_TITLE))
        assertTrue(notification.extras.getString(Notification.EXTRA_TEXT)!!.endsWith("\nPlease water between 5:00 am and 9:00 am."))
        assertEquals("watering", notification.channelId)

        app.getSystemService(NotificationManager::class.java).cancelAll()
        runBlocking { app.container.waterPlan.run() }
        assertTrue("Once a day", manager.allNotifications.isEmpty())
    }

    @Test
    fun aLineFromTheFirstWatering() {
        show()
        compose.onNodeWithText("Water").performClick()
        waitFor(hasText("Today's reference evapotranspiration (ET₀) is 5.1 mm."))
        compose.onNodeWithText("Watered by hand").assertExists()
        compose.onNodeWithText("Weather data by Open-Meteo.com.").assertExists()
        compose.onAllNodesWithText("Add a watering line").onFirst().performScrollTo().performClick()

        compose.onNodeWithText("Name").performTextInput("Citrus line")
        compose.onNodeWithText("Back Citrus Patch").performClick()
        compose.onNodeWithText("Flow of the whole line in litres an hour").performTextInput("40")
        compose.onNodeWithText("Save").performScrollTo().performClick()

        waitFor(hasText("Drippers · Back Citrus Patch"))
        val line = runBlocking { water.lines.first().single() }
        assertEquals(WaterMethod.DRIPPERS, line.method)
        assertEquals(40_000, line.flowMlPerHour)
        compose.scrollTo(hasText("Citrus line"))
        compose.onAllNodesWithText("I watered today").onFirst().performClick()
        waitFor(hasText("Last watered on 28 October 2026"))
        assertEquals(LocalDate.of(2026, 10, 28), runBlocking { water.waterings.first().single().date })
        shot("water")
    }

    @Test
    fun theDaysAheadAndTheHoursToWaterIn() {
        val veg = runBlocking {
            // Tomatoes in sand hold little water, so they are due today and again within the forecast.
            val zone = app.container.garden.zoneList.first().single { it.name == "Veg Patch" }
            app.container.garden.updateZone(zone.copy(soil = Soil.SAND))
            water.refreshWeather(app.container.settingsStore.current().gardenPlace!!)
            app.container.waterViews.state.first { state -> state.byHand.any { it.zone.name == "Veg Patch" && it.week.isNotEmpty() } }
                .byHand.single { it.zone.name == "Veg Patch" }
        }
        assertEquals(PlanReason.DUE, veg.plan.reason)
        assertEquals(LocalDate.of(2026, 10, 28), veg.week.first().date)
        assertTrue("Sand dries again within the forecast", veg.week.size > 1)

        show()
        compose.onNodeWithText("Water").performClick()
        waitFor(hasText("Please water today between 5:00 am and 9:00 am", substring = true))
        compose.scrollTo(hasText("Coming up"))
        val next = veg.week[1]
        compose.onNodeWithText(next.date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH), substring = true).assertExists()
        compose.scrollTo(hasText("Watering times"))
        compose.onNodeWithText("Pollenplot plans watering between 5:00 am and 9:00 am", substring = true).assertExists()
        // The clock opens, and setting the time it shows keeps the hours as they are.
        compose.onNodeWithText("Start at 5:00 am").performClick()
        waitFor(hasText("Watering starts at"))
        compose.onNodeWithText("Set the time").performClick()
        assertEquals(WateringWindow.DEFAULT, runBlocking { app.container.settingsStore.current().wateringWindow })

        runBlocking { app.container.settingsStore.update { it.copy(wateringWindow = WateringWindow(18 * 60, 20 * 60)) } }
        waitFor(hasText("Pollenplot plans watering between 6:00 pm and 8:00 pm", substring = true))
        compose.scrollTo(hasText("Please water today between 6:00 pm and 8:00 pm", substring = true))
        shot("water_week")
    }

    @Test
    fun aZoneSaysWhenItIsNextWatered() {
        runBlocking {
            val zone = app.container.garden.zoneList.first().single { it.name == "Veg Patch" }
            app.container.garden.updateZone(zone.copy(soil = Soil.SAND))
            water.refreshWeather(app.container.settingsStore.current().gardenPlace!!)
        }
        show()
        // The button is there once the zones have loaded, which a busy machine takes a moment over.
        waitFor(hasContentDescription("Show as a list"))
        compose.onNodeWithContentDescription("Show as a list").performClick()
        compose.scrollTo(hasText("Veg Patch"))
        waitFor(hasText("Water today at 5:00 am, about", substring = true))
        compose.onNodeWithText("Veg Patch").performClick()
        compose.scrollTo(hasText("This zone is watered by hand."))
        compose.onAllNodesWithText("Water today at 5:00 am, about", substring = true).onFirst().assertExists()
        compose.onNodeWithText("See the watering plan").assertExists()
    }

    @Test
    fun aSprinklerFasterThanClayRunsInCycles() {
        val line = runBlocking {
            val citrus = app.container.garden.zoneList.first().single { it.name == "Back Citrus Patch" }
            app.container.garden.updateZone(citrus.copy(soil = Soil.CLAY))
            water.refreshWeather(app.container.settingsStore.current().gardenPlace!!)
            // 400 L an hour over the patch's 35.75 m² is about 11.2 mm an hour; clay takes in 1 to 5.
            WateringLine(water.newId(), "Citrus sprinkler", WaterMethod.SPRINKLER, setOf(citrus.id), 400_000).also { water.saveLine(it) }
        }
        val first = runBlocking { app.container.waterViews.state.first { state -> state.lines.any { it.runoff != null } }.lines.single() }
        assertEquals(11_188, first.runoff!!.rateUmPerHour)
        assertEquals(Soil.CLAY, first.runoff!!.soil)
        assertEquals("The cycles wait for the line's pooling time", null, first.runoff!!.cycles)

        show()
        compose.onNodeWithText("Water").performClick()
        waitFor(hasText("This line puts on about 11.2 mm an hour", substring = true))
        compose.onNodeWithText("which FAO puts at 1 to 5 mm an hour, so water may pool and run off.", substring = true).assertExists()
        compose.onNodeWithText("please time how long the line runs before water starts to pool", substring = true).assertExists()

        runBlocking { water.saveLine(line.copy(poolingMinutes = 5)) }
        val cycles = runBlocking { app.container.waterViews.state.first { state -> state.lines.single().runoff?.cycles != null } }.lines.single().runoff!!.cycles!!
        assertTrue(cycles.size > 1 && cycles.all { it <= 5 })
        waitFor(hasText("Please run it as ${cycles.size} runs of", substring = true))
    }

    @Test
    fun potsAreWateredDeeplyBeforeAHotDay() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        runBlocking {
            val pots = app.container.garden.addZone("Pots by the door", ZoneKind.POTS)
            app.container.garden.addPlant("Salvia rosmarinus", null, pots.id)
            water.refreshWeather(app.container.settingsStore.current().gardenPlace!!)
        }
        // The recorded forecast has 39.4 °C for tomorrow.
        val warning = runBlocking { app.container.waterViews.state.first { it.potsBeforeHeat != null }.potsBeforeHeat!! }
        assertEquals(394, warning.maxTenthsC)
        assertEquals(listOf("Pots by the door"), warning.zones.map { it.name })

        runBlocking { app.container.waterPlan.run() }
        val body = shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications.single().extras.getString(Notification.EXTRA_TEXT)!!
        assertTrue(body, body.contains("Pots by the door, deeply, before tomorrow's heat"))

        show()
        waitFor(hasText("Please water the pots in Pots by the door deeply today, because tomorrow is forecast to reach 39.4 °C."))
        compose.onNodeWithText("Water").performClick()
        waitFor(hasText("Tomorrow is forecast to reach 39.4 °C. Please water the pots in Pots by the door deeply this morning", substring = true))
    }

    @Test
    fun theWaterTabInTheDark() {
        show(Settings(theme = ThemeChoice.DARK))
        compose.onNodeWithText("Water").performClick()
        waitFor(hasText("Watered by hand"))
        compose.scrollTo(hasText("How the plan is worked out"))
        compose.onNodeWithText("How the plan is worked out").performClick()
        waitFor(hasText("What the soil holds"))
        shot("water_how_dark")
    }

    private fun show(settings: Settings = Settings()) {
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
