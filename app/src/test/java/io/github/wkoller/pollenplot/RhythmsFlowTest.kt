package io.github.wkoller.pollenplot

import android.graphics.Bitmap
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import io.github.wkoller.pollenplot.data.prefs.Settings
import io.github.wkoller.pollenplot.data.prefs.ThemeChoice
import io.github.wkoller.pollenplot.domain.garden.ZoneKind
import io.github.wkoller.pollenplot.domain.plants.AuState
import io.github.wkoller.pollenplot.domain.rhythms.BeeGroup
import io.github.wkoller.pollenplot.domain.rhythms.ForageLevel
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
import java.time.Month

/** Pollen Rhythms for the demo yard on the fixed spring morning of 28 October 2026 in Brisbane. Saves pictures. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = WildlifeTestApp::class, qualifiers = "en-rAU-w411dp-h3600dp-xhdpi")
class RhythmsFlowTest {
    @Suppress("DEPRECATION")
    @get:Rule
    val compose = createComposeRule()

    private val app: WildlifeTestApp get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        runBlocking {
            app.container.knowledge.await()
            app.container.settingsStore.update { it.copy(gardenState = AuState.QLD) }
        }
    }

    @Test
    fun theYearForTheDemoYardsBees() {
        runBlocking { DemoGarden.seed(app.container.garden) }
        val state = runBlocking { app.container.rhythms.state.first { it.plants.isNotEmpty() } }
        val honey = state.lanes.first { it.group == BeeGroup.HONEY }
        assertTrue("Spring in a yard of citrus, tea tree and grevillea is good for honey bees", honey.level(Month.OCTOBER) >= ForageLevel.SOME)
        assertTrue(state.nowFlowering.isNotEmpty())

        show()
        compose.onNodeWithText("Rhythms").performClick()
        waitFor(hasText("The year for your bees"))
        // Each lane is read as a sentence, since its levels are drawn.
        compose.onNode(hasContentDescription("Honey bees.", substring = true)).assertExists()
        compose.onNodeWithText("Flowering now").assertExists()
        compose.onNodeWithText("Flowering through the year").assertExists()
        shot("rhythms")
    }

    @Test
    fun aGapAndThePlantsThatWouldFillIt() {
        runBlocking {
            val zone = app.container.garden.addZone("Back Citrus Patch", ZoneKind.ORCHARD)
            app.container.garden.addPlant("Citrus × limon", null, zone.id)
        }
        val state = runBlocking { app.container.rhythms.state.first { it.plants.isNotEmpty() } }
        val honey = state.lanes.first { it.group == BeeGroup.HONEY }
        assertTrue("A lemon alone leaves months with nothing", honey.gaps.isNotEmpty())
        val suggested = state.suggestions.getValue(BeeGroup.HONEY)
        assertTrue(suggested.isNotEmpty())
        assertTrue("Each fills a gap", suggested.all { it.fills.all { month -> month in honey.gaps } })

        show()
        compose.onNodeWithText("Rhythms").performClick()
        waitFor(hasText("Gaps for honey bees"))
        // A plant can fill gaps for more than one group, and is then suggested for each.
        compose.onAllNodesWithText(suggested.first().species.displayName).onFirst().assertExists()
        shot("rhythms_gaps")
    }

    @Test
    fun theYearInTheDark() {
        runBlocking { DemoGarden.seed(app.container.garden) }
        show(Settings(theme = ThemeChoice.DARK))
        compose.onNodeWithText("Rhythms").performClick()
        waitFor(hasText("The year for your bees"))
        shot("rhythms_dark")
    }

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
