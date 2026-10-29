package io.github.wkoller.pollenplot

import android.graphics.Bitmap
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import io.github.wkoller.pollenplot.data.TodayItem
import io.github.wkoller.pollenplot.data.prefs.Settings
import io.github.wkoller.pollenplot.domain.bees.honey.HiveType
import io.github.wkoller.pollenplot.domain.place.GardenPlace
import io.github.wkoller.pollenplot.domain.plants.AuState
import io.github.wkoller.pollenplot.domain.plants.Soil
import io.github.wkoller.pollenplot.ui.nav.AppRoot
import io.github.wkoller.pollenplot.ui.theme.PollenplotTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/** The Today card on the Plot, with recorded weather, on the fixed spring morning of 28 October 2026. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = WaterTestApp::class, qualifiers = "en-rAU-w411dp-h891dp-xxhdpi")
class TodayFlowTest {
    @Suppress("DEPRECATION")
    @get:Rule
    val compose = createComposeRule()

    private val app: WaterTestApp get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        runBlocking {
            app.container.knowledge.await()
            val place = GardenPlace.at(-27.4987, 152.9934, "Taringa")
            app.container.settingsStore.update { it.copy(gardenState = AuState.QLD, gardenPlace = place) }
            DemoGarden.seed(app.container.garden)
            // Tomatoes in sand hold little water, and a hive added today has had no inspection.
            val veg = app.container.garden.zoneList.first().single { it.name == "Veg Patch" }
            app.container.garden.updateZone(veg.copy(soil = Soil.SAND))
            app.container.bees.addHive("Hive 1", HiveType.LANGSTROTH_8, null, null, 2025, LocalDate.of(2026, 10, 28))
            app.container.water.refreshWeather(place)
        }
    }

    @Test
    fun todaysJobsFromAcrossTheGarden() {
        val state = runBlocking { app.container.today.state.first { it.items.isNotEmpty() } }
        assertTrue(state.items.any { it is TodayItem.Watering && it.name == "Veg Patch" })
        assertTrue(state.items.any { it is TodayItem.HiveCheck && it.hive == "Hive 1" })

        compose.setContent { PollenplotTheme(Settings()) { Surface { AppRoot(app.container) } } }
        waitFor(hasText("Today"))
        compose.onNodeWithText("Hive 1 is due for an inspection, a brood check and a mite check").assertExists()
        shot("plot_today")
        compose.onNodeWithText("Please water Veg Patch", substring = true).performClick()
        waitFor(hasText("Watered by hand"))
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
