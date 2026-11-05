package io.github.wkoller.pollenplot

import android.graphics.Bitmap
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import io.github.wkoller.pollenplot.data.prefs.Settings
import io.github.wkoller.pollenplot.data.prefs.ThemeChoice
import io.github.wkoller.pollenplot.domain.bees.honey.HiveType
import io.github.wkoller.pollenplot.domain.garden.GroundFeature
import io.github.wkoller.pollenplot.domain.garden.HabitatLayer
import io.github.wkoller.pollenplot.domain.garden.WaterKind
import io.github.wkoller.pollenplot.domain.garden.ZoneBadge
import io.github.wkoller.pollenplot.domain.plants.AuState
import io.github.wkoller.pollenplot.ui.plot.CorridorCard
import io.github.wkoller.pollenplot.ui.plot.WaterFeatureScreen
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
import java.time.LocalDate

/**
 * The yard as a corridor for wildlife: a zone's layers and ground features, water added from its page and cleaned, the
 * nearest water from another zone, the bees kept in a zone, the Layered habitat badge and the Wildlife tab's summary.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = WildlifeTestApp::class, qualifiers = "en-rAU-w411dp-h891dp-xxhdpi")
class HabitatFlowTest {
    // The classic rule runs coroutines at once, so flows from the database reach the screen while the test waits.
    @Suppress("DEPRECATION")
    @get:Rule
    val compose = createComposeRule()

    private val app: WildlifeTestApp get() = ApplicationProvider.getApplicationContext()
    private val habitat get() = app.container.habitat
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
    fun waterAddedToAZoneReachesItsNeighbours() {
        val ids = seed()
        runBlocking { app.container.bees.addHive("Hive one", HiveType.LANGSTROTH_8, ids.hive, null, null, null) }
        show(ids.front)
        waitFor(hasText("Lomandra", substring = true))
        compose.scrollTo(hasText("Add water for wildlife"))
        compose.onNodeWithText("No water for wildlife has been recorded in your yard yet.").assertExists()
        compose.onNode(hasContentDescription("Canopy, Missing")).assertExists()
        compose.onNode(hasContentDescription("Mulch or leaf litter, Present")).assertExists()
        shot("habitat_zone")

        compose.onNodeWithText("Add water for wildlife").performClick()
        waitFor(hasText("Kind of water"))
        compose.onNodeWithText("Water for bees").performClick()
        compose.onNodeWithText("wet sand or gravel", substring = true).assertExists()
        shot("water_feature")
        compose.onNodeWithText("Save").performScrollTo().performClick()
        waitFor(hasText("This zone has water for wildlife."))
        compose.scrollTo(hasText("Cleaned today"))
        compose.onNodeWithText("Cleaned today").performClick()
        waitFor(hasText("Cleaned on 28 October 2026"))

        // The hive corner is 1.5 m below the front strip, and its bees now have water in the yard.
        val hive = runBlocking { app.container.habitatViews.zone(ids.hive).first { it?.habitat?.nearestWaterCm != null } }!!
        assertEquals(150, hive.habitat.nearestWaterCm)
        assertEquals("Front Native Strip", hive.nearestWaterZone)
        assertEquals(listOf("Hive one"), hive.hives.map { it.hive.name })
        assertTrue("Water anywhere in the yard answers the Code", !hive.beesNeedWater)

        val feature = runBlocking { habitat.water.first { it.isNotEmpty() } }.single()
        assertEquals(WaterKind.BEE_WATER, feature.kind)
        assertEquals(today, feature.cleanedOn)
        assertTrue(runBlocking { app.container.garden.search("water for bees") }.any { it.recordId == feature.id && it.kind == "WATER_FEATURE" })
        assertTrue(runBlocking { app.container.exportJsonLd() }.contains("WaterFeature"))

        // Removing the zone takes its water with it, out of search too.
        runBlocking { app.container.garden.deleteZone(ids.front) }
        runBlocking { habitat.water.first { it.isEmpty() } }
        assertTrue(runBlocking { app.container.garden.search("water for bees") }.none { it.recordId == feature.id })
    }

    @Test
    fun aHiveCornerListsItsBeesAndAsksForWater() {
        val ids = seed()
        runBlocking { app.container.bees.addHive("Hive one", HiveType.LANGSTROTH_8, ids.hive, null, null, null) }
        show(ids.hive)
        waitFor(hasText("Lantana", substring = true))
        compose.scrollTo(hasText("Hive one"))
        compose.onNodeWithText("Honey bee hive").assertExists()
        compose.onNodeWithText("Your bees need water they can reach", substring = true).assertExists()
        compose.scrollTo(hasText("Bee food here"))
        compose.onNodeWithText("Honey bees find nectar all year, and pollen all year.").assertExists()
        shot("habitat_hive_corner")
    }

    @Test
    fun everyLayerAndTheCorridorSummary() {
        val ids = seed()
        runBlocking {
            app.container.garden.addPlant("Backhousia citriodora", null, ids.front)
            val front = app.container.garden.zone(ids.front)!!
            app.container.garden.updateZone(front.copy(rocksAndLogs = true, denseShrubs = true))
            habitat.addWater(ids.citrus, WaterKind.BIRD_BATH, "Under the lemon")
        }
        val front = runBlocking { app.container.views.zone(ids.front).first { it?.badges?.contains(ZoneBadge.LAYERED_HABITAT) == true } }!!
        assertEquals(ZoneBadge.LAYERED_HABITAT, front.badges.first { it == ZoneBadge.LAYERED_HABITAT })
        val summary = runBlocking { app.container.habitatViews.summary.first { it.waterFeatures == 1 && it.layeredZones == 1 } }
        assertEquals(HabitatLayer.entries.toSet(), summary.layers)
        assertEquals(GroundFeature.entries.toSet(), summary.features)
        assertEquals(4, summary.zones)

        compose.setContent {
            PollenplotTheme(Settings(theme = ThemeChoice.DARK)) { Surface { CorridorCard(summary) } }
        }
        waitFor(hasText("1 of 4 zones has all three layers."))
        compose.onNodeWithText("Your yard has 1 water feature.").assertExists()
        compose.onNode(hasContentDescription("Understorey and groundcover, Present")).assertExists()
        shot("corridor_dark")
    }

    /** A zone's page, which opens the water form in its place. */
    private fun show(zoneId: String) {
        compose.setContent {
            PollenplotTheme(Settings(gardenState = AuState.QLD)) {
                Surface {
                    var screen by remember { mutableStateOf("zone") }
                    when (screen) {
                        "water" -> WaterFeatureScreen(habitat, zoneId, null, today) { screen = "zone" }
                        else -> ZoneDetailScreen(
                            views = app.container.views,
                            zoneId = zoneId,
                            onBack = {},
                            onEdit = {},
                            onOpenPlant = {},
                            onAddPlant = {},
                            habitat = app.container.habitatViews,
                            onAddWater = { screen = "water" },
                            onWaterCleaned = { id -> runBlocking { habitat.cleaned(id, today) } },
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
