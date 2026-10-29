package io.github.wkoller.pollenplot

import android.graphics.Bitmap
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import io.github.wkoller.pollenplot.data.prefs.Settings
import io.github.wkoller.pollenplot.data.prefs.ThemeChoice
import io.github.wkoller.pollenplot.domain.bees.stingless.Facing
import io.github.wkoller.pollenplot.domain.bees.stingless.NativeLogKind
import io.github.wkoller.pollenplot.domain.plants.AuState
import io.github.wkoller.pollenplot.domain.wildlife.LifeGroup
import io.github.wkoller.pollenplot.ui.nav.AppRoot
import io.github.wkoller.pollenplot.ui.theme.PollenplotTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** The native bee section of the Bees tab, a stingless bee hive's page and the native bee guide. Saves pictures. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = WildlifeTestApp::class, qualifiers = "en-rAU-w411dp-h2400dp-xhdpi")
class NativeFlowTest {
    @Suppress("DEPRECATION")
    @get:Rule
    val compose = createComposeRule()

    private val app: WildlifeTestApp get() = ApplicationProvider.getApplicationContext()
    private val natives get() = app.container.nativeBees

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        runBlocking {
            app.container.knowledge.await()
            app.container.knowledge.awaitFauna()
            app.container.settingsStore.update { it.copy(gardenState = AuState.QLD) }
            DemoGarden.seed(app.container.garden)
        }
    }

    @Test
    fun aHiveFromItsFirstCount() {
        show()
        openNativeBees()
        waitFor(hasText("You have no native bee hives yet."))
        compose.onNodeWithText("Add a hive").performClick()

        compose.onNodeWithText("Name").performTextInput("Sugarbag 1")
        compose.onNodeWithText("Hive Corner").performClick()
        compose.onNodeWithText("North-east").performScrollTo().performClick()
        compose.onNodeWithText("Save").performScrollTo().performClick()

        waitFor(hasText("Tetragonula carbonaria · OATH box · Hive Corner"))
        compose.onNodeWithText("Entrance faces · North-east").assertExists()
        compose.onNodeWithText("Not weighed yet").assertExists()

        compose.onNodeWithText("Count the bees coming home").performClick()
        waitFor(hasText("Bees coming home in a minute"))
        compose.onNodeWithText("Bees coming home in a minute").performTextInput("42")
        compose.onNodeWithText("Save").performScrollTo().performClick()
        waitFor(hasText("42 bees coming home a minute on 28 October 2026"))

        compose.onNodeWithText("Record a weight").performClick()
        waitFor(hasText("Weight in kilograms"))
        compose.onNodeWithText("Weight in kilograms").performTextInput("7.2")
        compose.onNodeWithText("Save").performScrollTo().performClick()
        waitFor(hasText("Weighed 7.2 kg on 28 October 2026"))
        val hive = runBlocking { natives.hives.first().single() }
        assertEquals(Facing.NE, hive.facing)
        assertEquals(listOf(NativeLogKind.WEIGHT, NativeLogKind.ACTIVITY), runBlocking { natives.logs.first() }.map { it.kind })
        shot("native_hive")

        compose.onNodeWithContentDescription("Back").performClick()
        waitFor(hasText("Native bee hives"))
        compose.onNodeWithText("Sugarbag 1").assertExists()
        compose.onNodeWithText("Warn me before a hot day").assertExists()
        shot("bees_native")
    }

    @Test
    fun nativeBeesSeenThisYear() {
        runBlocking {
            app.container.garden.recordSighting("Amegilla cingulata", "Blue-banded bee", LifeGroup.INSECT, null, null, null, 1, DemoWildlife.clock.instant(), "")
            app.container.garden.recordSighting("Apis mellifera", "European honey bee", LifeGroup.INSECT, null, null, null, 1, DemoWildlife.clock.instant(), "")
        }
        show()
        openNativeBees()
        waitFor(hasText("Blue-banded bee"))
        compose.onNodeWithText("Seen once this year", substring = true).assertExists()
        // The honey bee was brought to Australia, so it is not one of the native bees.
        compose.onAllNodesWithText("European honey bee").assertCountEquals(0)
        compose.onNodeWithText("Blue-banded bee").performClick()
        waitFor(hasText("Amegilla cingulata", substring = true))
    }

    @Test
    fun theGuideSaysHowToCoolAHive() {
        show(Settings(theme = ThemeChoice.DARK))
        openNativeBees()
        waitFor(hasText("Looking after a stingless bee hive"))
        compose.onNodeWithText("Looking after a stingless bee hive").performClick()
        waitFor(hasText("Hot days"))
        compose.onNodeWithText("Drape a wet towel over the hive", substring = true).assertExists()
        shot("guide_native_dark")
    }

    @Test
    fun aHeatWarningOpensTheGuide() {
        compose.setContent {
            PollenplotTheme(Settings()) {
                Surface { AppRoot(app.container, open = MainActivity.OPEN_GUIDE + "native/hive-care", onOpened = {}) }
            }
        }
        waitFor(hasText("Looking after a stingless bee hive"))
    }

    private fun openNativeBees() {
        compose.onNodeWithText("Bees").performClick()
        waitFor(hasText("Native bees"))
        compose.onNodeWithText("Native bees").performClick()
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
