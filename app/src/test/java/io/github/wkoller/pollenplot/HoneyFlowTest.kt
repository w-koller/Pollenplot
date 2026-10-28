package io.github.wkoller.pollenplot

import android.graphics.Bitmap
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
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
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import io.github.wkoller.pollenplot.data.prefs.Settings
import io.github.wkoller.pollenplot.data.prefs.ThemeChoice
import io.github.wkoller.pollenplot.domain.bees.honey.BroodPattern
import io.github.wkoller.pollenplot.domain.bees.honey.Harvest
import io.github.wkoller.pollenplot.domain.bees.honey.HiveSign
import io.github.wkoller.pollenplot.domain.bees.honey.HiveStatus
import io.github.wkoller.pollenplot.domain.bees.honey.HiveType
import io.github.wkoller.pollenplot.domain.bees.honey.Inspection
import io.github.wkoller.pollenplot.domain.bees.honey.QueenCells
import io.github.wkoller.pollenplot.domain.bees.honey.Temperament
import io.github.wkoller.pollenplot.domain.bees.honey.Treatment
import io.github.wkoller.pollenplot.domain.bees.honey.VarroaMethod
import io.github.wkoller.pollenplot.domain.plants.AuState
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
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/** Honey bee hives on the Bees tab, on the fixed spring morning the wildlife tests use. Saves a picture of each step. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = WildlifeTestApp::class, qualifiers = "en-rAU-w411dp-h1600dp-xhdpi")
class HoneyFlowTest {
    @Suppress("DEPRECATION")
    @get:Rule
    val compose = createComposeRule()

    private val app: WildlifeTestApp get() = ApplicationProvider.getApplicationContext()
    private val bees get() = app.container.bees

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        runBlocking {
            app.container.knowledge.await()
            app.container.settingsStore.update { it.copy(gardenState = AuState.QLD) }
            DemoGarden.seed(app.container.garden)
        }
    }

    @Test
    fun aHiveFromItsFirstInspection() {
        show()
        compose.onNodeWithText("Bees").performClick()
        waitFor(hasText("You have no honey bee hives yet."))
        compose.onNodeWithText("Open the registration page").assertExists()
        compose.onAllNodesWithText("Add a hive").onFirst().performClick()

        compose.onNodeWithText("Name").performTextInput("Hive 1")
        compose.onNodeWithText("Hive Corner").performClick()
        compose.onNodeWithText("Year the queen was born or introduced").performTextReplacement("2025")
        compose.onNodeWithText("Save").performScrollTo().performClick()

        waitFor(hasText("Queen from 2025, marked blue"))
        compose.onNodeWithText("Langstroth, 8 frames · Hive Corner").assertExists()
        // Never inspected, so everything is due today.
        compose.onAllNodesWithText("Due by 28 October 2026").onFirst().assertExists()
        shot("hive_new")

        compose.onNodeWithText("Record an inspection").performClick()
        waitFor(hasText("Queen seen"))
        compose.onNodeWithText("Queen seen").performClick()
        compose.onNodeWithText("Solid").performClick()
        compose.onNodeWithText("Sunken or perforated cappings").performScrollTo().performClick()
        waitFor(hasText("notifiable in most of Australia", substring = true))
        shot("hive_inspection_foulbrood")
        compose.onNodeWithText("Save").performScrollTo().performClick()

        waitFor(hasText("Due by 11 November 2026"))
        val inspection = runBlocking { bees.inspections.first().single() }
        assertTrue(inspection.queenSeen)
        assertEquals(setOf(HiveSign.SUNKEN_CAPPINGS), inspection.signs)
        assertEquals(LocalDate.of(2026, 10, 28), inspection.date)

        compose.onNodeWithText("Record a mite check").performClick()
        waitFor(hasText("Mites counted"))
        compose.onNodeWithText("Mites counted").performTextInput("3")
        compose.onNodeWithText("Save").performScrollTo().performClick()
        // Three mites in the usual 300 bees is one in a hundred.
        waitFor(hasText("Alcohol wash · 1 mite per 100 bees"))
        val check = runBlocking { bees.checks.first().single() }
        assertEquals(VarroaMethod.ALCOHOL_WASH, check.method)
        assertEquals(10, check.mitesPer100BeesTenths)
        // Checked this spring, so the next mite check is due by the end of summer.
        compose.onNodeWithText("Due by 28 February 2027").assertExists()
        compose.onNodeWithText("Due by 28 January 2027").assertExists()
        shot("hive_page")

        compose.onNodeWithContentDescription("Back").performClick()
        waitFor(hasText("Hives"))
        compose.onNodeWithText("Hive 1").assertExists()
        compose.onNodeWithText("Hive 1 · Inspection").assertExists()
        compose.onNodeWithText("Last inspected on 28 October 2026", substring = true).assertExists()
        shot("bees_honey")

        // Where Varroa is established, mites are checked monthly instead.
        compose.onNodeWithText("Varroa mite is established where my hives are").performClick()
        waitFor(hasText("Due by 27 November 2026"))
        assertTrue(runBlocking { app.container.settingsStore.settings.first().varroaEstablished })
    }

    @Test
    fun aHiveFoundInSearchOpensItsPage() {
        runBlocking { bees.addHive("Sugarloaf", HiveType.FLOW, null, null, 2026, null) }
        show()
        compose.onNodeWithContentDescription("Search your garden").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("sugarloaf")
        waitFor(hasText("Sugarloaf"))
        compose.onAllNodesWithText("Sugarloaf").onFirst().performClick()
        waitFor(hasText("Queen from 2026, marked white"))
        compose.onNodeWithText("Flow hive").assertExists()

        // Its details open filled in, and a change shows on its page.
        compose.onNodeWithContentDescription("Edit hive").performClick()
        waitFor(hasText("Queenless"))
        compose.onNodeWithText("Queenless").performClick()
        compose.onNodeWithText("Save").performScrollTo().performClick()
        // The editor shows the status too, so this waits for it to close.
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Save").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Queenless").assertExists()
        compose.onNodeWithText("Queen from 2026, marked white").assertExists()
        assertEquals(HiveStatus.QUEENLESS, runBlocking { bees.hives.first().single().status })
    }

    @Test
    fun theHivePageInTheDark() {
        val hive = runBlocking { bees.addHive("Hive 2", HiveType.LANGSTROTH_10, null, null, 2024, LocalDate.of(2026, 9, 1)) }
        runBlocking {
            // Last looked at in July, in winter, so the inspection and the brood check are overdue.
            bees.saveInspection(Inspection(bees.newRecordId(), hive.id, LocalDate.of(2026, 7, 20), true, true, BroodPattern.PATCHY, Temperament.CALM, 6, 4, QueenCells.NONE, setOf(HiveSign.SMALL_HIVE_BEETLE), "Added a beetle trap", ""))
        }
        runBlocking {
            bees.saveTreatment(Treatment(bees.newRecordId(), hive.id, "Apivar", "Amitraz", LocalDate.of(2026, 10, 1), null, supersRemoved = true))
            bees.saveHarvest(Harvest(bees.newRecordId(), hive.id, LocalDate.of(2026, 9, 20), 1, 2_500, ""))
        }
        show(Settings(theme = ThemeChoice.DARK))
        compose.onNodeWithText("Bees").performClick()
        waitFor(hasText("Hive 2"))
        compose.onNodeWithText("Hive 2").performClick()
        waitFor(hasText("Overdue. It was due by 3 August 2026."))
        compose.onNodeWithText("Overdue. It was due by 20 October 2026.").assertExists()
        compose.onNodeWithText("Queen seen · Patchy · Small hive beetle · Added a beetle trap").assertExists()
        compose.onNodeWithText("Apivar · Amitraz · Still in the hive").assertExists()
        compose.onNodeWithText("2.5 kg from 1 frame").assertExists()
        shot("hive_page_dark")

        compose.onNodeWithText("Mark as ended today").performClick()
        waitFor(hasText("Apivar · Amitraz · Ended on 28 October 2026"))
        assertEquals(LocalDate.of(2026, 10, 28), runBlocking { bees.treatments.first().single().ended })
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
