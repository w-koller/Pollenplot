package io.github.wkoller.pollenplot

import android.graphics.Bitmap
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import io.github.wkoller.pollenplot.data.prefs.Settings
import io.github.wkoller.pollenplot.domain.bees.honey.HiveType
import io.github.wkoller.pollenplot.domain.bees.stingless.NativeBox
import io.github.wkoller.pollenplot.domain.bees.stingless.NativeOrigin
import io.github.wkoller.pollenplot.domain.bees.stingless.StinglessSpecies
import io.github.wkoller.pollenplot.domain.hotels.BeeHotel
import io.github.wkoller.pollenplot.domain.hotels.HotelMaterial
import io.github.wkoller.pollenplot.domain.hotels.HotelSection
import io.github.wkoller.pollenplot.domain.irrigation.WaterMethod
import io.github.wkoller.pollenplot.domain.irrigation.WateringLine
import io.github.wkoller.pollenplot.domain.place.GardenPlace
import io.github.wkoller.pollenplot.domain.plants.AuState
import io.github.wkoller.pollenplot.ui.nav.AppRoot
import io.github.wkoller.pollenplot.ui.theme.PollenplotTheme
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

/**
 * The main screens of the demo yard, with something on each: every control a screen reader reaches says what it is,
 * and with text at twice its size the screens are saved as pictures to look at. Google's Accessibility Test
 * Framework sees nothing of Compose under Robolectric, so the labels are checked from the semantics tree here.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = WaterTestApp::class, qualifiers = "en-rAU-w360dp-h800dp-xxhdpi")
class AccessibilityFlowTest {
    @Suppress("DEPRECATION")
    @get:Rule
    val compose = createComposeRule()

    private val app: WaterTestApp get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        runBlocking {
            val container = app.container
            container.knowledge.await()
            container.knowledge.awaitFauna()
            val place = GardenPlace.at(-27.4987, 152.9934, "Taringa")
            container.settingsStore.update { it.copy(gardenState = AuState.QLD, gardenPlace = place) }
            val ids = DemoGarden.seed(container.garden)
            DemoWildlife.seed(container.garden, ids)
            container.bees.addHive("Hive 1", HiveType.LANGSTROTH_8, ids.hive, null, 2025, LocalDate.of(2026, 9, 1))
            container.nativeBees.addHive("Sugarbag", StinglessSpecies.entries.first(), NativeBox.OATH, NativeOrigin.BOUGHT, ids.hive, null, null)
            container.hotels.saveHotel(BeeHotel(container.hotels.newRecordId(), "Fence hotel", ids.hive, null, null, null, listOf(HotelSection(HotelMaterial.BAMBOO, null, null, 12))))
            container.water.saveLine(WateringLine(container.water.newId(), "Citrus drip", WaterMethod.entries.first(), setOf(ids.citrus), 8_000))
            container.water.refreshWeather(place)
        }
    }

    @Test
    fun everyControlSaysWhatItIs() = walk(::assertLabelled)

    @Test
    @Config(fontScale = 2.0f)
    fun theScreensWithTextAtTwiceItsSize() = walk { screen -> shot("large_$screen") }

    /** Opens the main screens in turn and hands each one to [visit] by name once it shows. */
    private fun walk(visit: (String) -> Unit) {
        compose.setContent { PollenplotTheme(Settings()) { Surface { AppRoot(app.container) } } }
        waitFor(hasText("Today"))
        visit("plot")
        compose.onNodeWithContentDescription("Show as a list").performClick()
        reach(hasText("Back Citrus Patch"))
        visit("plot_list")
        compose.onNodeWithText("Back Citrus Patch").performClick()
        reach(hasText("Mum's lemon"))
        visit("zone")
        compose.onNodeWithText("Mum's lemon").performClick()
        reach(hasText("Bee forage"))
        visit("plant")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        tab("Rhythms").performClick()
        reach(hasText("The year for your bees"))
        visit("rhythms")
        reach(hasContentDescription("Solitary bees", substring = true))
        visit("rhythms_lanes")
        tab("Wildlife").performClick()
        reach(hasText("species seen in your yard this year", substring = true))
        visit("wildlife")
        tab("Bees").performClick()
        reach(hasText("Hive 1"))
        visit("bees_honey")
        compose.onNodeWithText("Hive 1").performClick()
        reach(hasText("Inspections"))
        visit("hive")
        compose.onNodeWithContentDescription("Back").performClick()
        reach(hasText("What every beekeeper must do"))
        compose.onNodeWithText("What every beekeeper must do").performClick()
        reach(hasText("Sources"))
        visit("guide")
        compose.onNodeWithContentDescription("Back").performClick()
        reach(hasText("Native bees") and hasClickAction())
        compose.onNode(hasText("Native bees") and hasClickAction()).performClick()
        reach(hasText("Sugarbag"))
        visit("bees_native")
        compose.onNodeWithText("Sugarbag").performClick()
        reach(hasText("Records"))
        visit("native_hive")
        compose.onNodeWithContentDescription("Back").performClick()
        reach(hasText("Bee hotels") and hasClickAction())
        compose.onNode(hasText("Bee hotels") and hasClickAction()).performClick()
        reach(hasText("Fence hotel"))
        visit("bees_hotels")
        compose.onNodeWithText("Fence hotel").performClick()
        reach(hasText("Upkeep"))
        visit("hotel")
        compose.onNodeWithContentDescription("Back").performClick()
        tab("Water").performClick()
        reach(hasText("Citrus drip"))
        visit("water")
        compose.onNodeWithContentDescription("Settings").performClick()
        reach(hasText("Use colours from your wallpaper"))
        visit("settings")
        reach(hasText("Backup and restore"))
        compose.onNodeWithText("Backup and restore").performClick()
        reach(hasText("Choose a backup file"))
        visit("backup")
        compose.onNodeWithContentDescription("Back").performClick()
        reach(hasText("Export data"))
        compose.onNodeWithText("Export data").performClick()
        reach(hasText("Sightings as Darwin Core"))
        visit("export")
        compose.onNodeWithContentDescription("Back").performClick()
        reach(hasText("Set up again"))
        compose.onNodeWithText("Set up again").performClick()
        reach(hasText("Welcome to Pollenplot"))
        visit("setup_welcome")
        compose.onNodeWithText("Start").performClick()
        reach(hasText("Where is your garden?"))
        visit("setup_location")
        compose.onNodeWithText("Next").performClick()
        reach(hasText("Identification models"))
        visit("setup_models")
        compose.onNodeWithText("Next").performClick()
        reach(hasText("Your garden's zones"))
        visit("setup_zones")
        compose.onNodeWithText("Next").performClick()
        reach(hasText("Notifications and photos"))
        visit("setup_permissions")
    }

    /**
     * Waits for [matcher] to show, scrolling it fully into view, so a click lands on it rather than on a bar it is
     * partly under. Something outside the scrolling content only has to be there.
     */
    private fun reach(matcher: SemanticsMatcher) {
        compose.waitUntil(WAIT_MS) {
            val scrollers = compose.onAllNodes(hasScrollToNodeAction())
            val scrolled = (0 until scrollers.fetchSemanticsNodes().size).any { runCatching { scrollers[it].performScrollToNode(matcher) }.isSuccess }
            scrolled || compose.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Fails when a control that can be pressed has no words for a screen reader to say. */
    private fun assertLabelled(screen: String) {
        compose.waitForIdle()
        val unlabelled = mutableListOf<String>()
        fun check(node: SemanticsNode) {
            val config = node.config
            if (SemanticsActions.OnClick in config) {
                val words = listOfNotNull(
                    config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" "),
                    config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text },
                    config.getOrNull(SemanticsProperties.EditableText)?.text,
                ).joinToString(" ").trim()
                if (words.isEmpty()) unlabelled += "${config.getOrNull(SemanticsProperties.Role) ?: "Control"} at ${node.boundsInRoot}"
            }
            node.children.forEach(::check)
        }
        compose.onAllNodes(isRoot()).fetchSemanticsNodes().forEach(::check)
        assertTrue("On $screen, these say nothing to a screen reader: $unlabelled", unlabelled.isEmpty())
    }

    private fun tab(label: String) =
        compose.onNode(hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))

    private fun waitFor(matcher: SemanticsMatcher) {
        compose.waitUntil(WAIT_MS) { compose.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        val bitmap: Bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(System.getProperty("screenshots.dir") ?: "build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
