package io.github.wkoller.pollenplot

import android.graphics.Bitmap
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import io.github.wkoller.pollenplot.ai.AskSubject
import io.github.wkoller.pollenplot.ai.Assistant
import io.github.wkoller.pollenplot.ai.GardenAssistant
import io.github.wkoller.pollenplot.data.prefs.Settings
import io.github.wkoller.pollenplot.domain.assistant.CitedRecord
import io.github.wkoller.pollenplot.domain.assistant.DraftedCare
import io.github.wkoller.pollenplot.domain.assistant.RecordKind
import io.github.wkoller.pollenplot.domain.plants.AuState
import io.github.wkoller.pollenplot.domain.plants.Sun
import io.github.wkoller.pollenplot.domain.plants.WaterNeed
import io.github.wkoller.pollenplot.ui.assistant.AskModel
import io.github.wkoller.pollenplot.ui.assistant.AskScreen
import io.github.wkoller.pollenplot.ui.plants.PlantDetailScreen
import io.github.wkoller.pollenplot.ui.theme.PollenplotTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The garden assistant. The app's own answers and the search for a question's records run for real; the model's
 * answers come from a stand-in, as Gemma 4 cannot run on the JVM.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = AskTestApp::class, qualifiers = "en-rAU-w411dp-h1200dp-xhdpi")
class AskFlowTest {
    @Suppress("DEPRECATION")
    @get:Rule
    val compose = createComposeRule()

    private val app: AskTestApp get() = ApplicationProvider.getApplicationContext()
    private val container get() = app.container

    /** The real assistant, with Gemma 4 not on the phone, as on these tests' machine. */
    private val real: GardenAssistant by lazy {
        GardenAssistant(app, container.gemma, container.views, container.garden, container.knowledge, container.meaning, container.settingsStore)
    }

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
    }

    @Test
    fun dangerousQuestionsGetPollenplotsOwnAnswer() {
        show(real, AskSubject.Garden)
        ask("My dog ate some snail pellets")
        waitFor(hasText("13 11 26", substring = true))
        compose.onNodeWithText("Pollenplot's own answer").assertExists()
        shot("ask_poisoning")
        ask("Can I eat the berries?")
        waitFor(hasText("Please never eat a plant", substring = true))
    }

    @Test
    fun injuredWildlifeGoesToTheGardensStateRescueService() {
        runBlocking { container.settingsStore.update { it.copy(gardenState = AuState.QLD) } }
        show(real, AskSubject.Garden)
        ask("I found an injured possum")
        waitFor(hasText("Please call RSPCA Queensland on 1300 264 625 for advice about the animal."))
        compose.onNodeWithText("warm, dark and quiet", substring = true).assertExists()
        shot("ask_injured_wildlife")
    }

    @Test
    fun withoutGemmaTheScreenSaysWhereToGetIt() {
        show(real, AskSubject.Garden)
        compose.onNodeWithText("Gemma 4 isn't on this phone yet.").assertExists()
        ask("When does the grevillea flower?")
        waitFor(hasText("Open Settings › Models"))
        shot("ask_not_ready")
    }

    @Test
    fun anAnswerLinksTheRecordsItUsed() {
        val ids = seed()
        val rosemary = runBlocking { container.garden.plantList.first().single { it.speciesName == "Salvia rosmarinus" } }
        app.assistant.answer = "Noisy miners have been seen feeding on the rosemary [1]."
        app.assistant.cited = listOf(CitedRecord(rosemary.id, RecordKind.PLANT, "Rosemary", "Plant: Rosemary"))
        val opened = mutableListOf<CitedRecord>()
        show(app.assistant, AskSubject.Garden, onOpenRecord = { opened += it })
        ask("What feeds on the rosemary?")
        waitFor(hasText("Written by Gemma 4. Please check anything important."))
        compose.onNodeWithText("Rosemary").performClick()
        assertEquals(listOf(rosemary.id), opened.map { it.id })
        shot("ask_garden_answer")
        assertTrue(ids.hive.isNotEmpty())
    }

    @Test
    fun aGardenQuestionFindsTheRecordsItIsAbout() {
        seed()
        val records = runBlocking { real.records("What feeds on the rosemary?") }
        assertTrue("The rosemary itself, found by its name: $records", records.any { it.kind == RecordKind.PLANT && it.title == "Rosemary" })
        assertTrue("The noisy miner seen on it, found through its sighting: $records", records.any { it.kind == RecordKind.SIGHTING && "Rosemary" in it.text })
    }

    @Test
    fun aWeedsOfficialStatusGoesWithTheQuestion() {
        val ids = seed()
        val lantana = runBlocking { container.garden.plantList.first().single { it.speciesName == "Lantana camara" } }
        app.assistant.answer = "Lantana is a weed where you live."
        show(app.assistant, AskSubject.Plant(lantana.id))
        waitFor(hasText("Please ask anything about Lantana", substring = true))
        ask("Should I keep it?")
        waitFor(hasText("Lantana is a weed where you live."))
        assertEquals("Should I keep it?", app.assistant.asked.single().first)
        assertTrue(ids.front.isNotEmpty())
    }

    @Test
    @Config(qualifiers = "en-rAU-w411dp-h1600dp-xhdpi")
    fun gemmaDraftsTheCareOfAPlantTheKnowledgeBaseLacks() {
        val ids = seed()
        val name = "Petalostigma pubescens"
        assertNull("The knowledge base must not have it", runBlocking { container.knowledge.await().species(name) })
        val plant = runBlocking { container.garden.addPlant(name, null, ids.front) }
        app.assistant.draft = DraftedCare(sun = listOf(Sun.FULL_SUN), water = WaterNeed.LOW, drought = 3, heightMm = listOf(3_000, 8_000), note = "It needs little care once established.")
        compose.setContent {
            PollenplotTheme(Settings()) {
                Surface { PlantDetailScreen(container.views, container.garden, container.knowledge, plant.id, onBack = {}, assistant = app.assistant) }
            }
        }
        waitFor(hasText("Draft its care"))
        compose.onNodeWithText("Draft its care").performScrollTo().performClick()
        waitFor(hasText("Care drafted by Gemma 4"))
        compose.onNodeWithText("Written by Gemma 4. Please check.").assertExists()
        compose.onNodeWithText("Height: 3 to 8 m").assertExists()
        assertEquals(listOf(name), app.assistant.drafted)
        shot("plant_care_draft")
        compose.onNodeWithText("Keep it").performScrollTo().performClick()
        waitFor(hasText("You kept this draft", substring = true))
        compose.onNodeWithText("Remove").performScrollTo().performClick()
        waitFor(hasText("Draft its care"))
    }

    private fun seed(): DemoGarden.Ids = runBlocking {
        container.knowledge.await()
        container.knowledge.awaitFauna()
        container.settingsStore.update { it.copy(gardenState = AuState.QLD) }
        DemoGarden.seed(container.garden).also { DemoWildlife.seed(container.garden, it) }
    }

    private fun show(assistant: Assistant, subject: AskSubject, onOpenRecord: (CitedRecord) -> Unit = {}) {
        val model = AskModel(assistant, subject)
        compose.setContent {
            PollenplotTheme(Settings()) {
                Surface {
                    AskScreen(model, assistant, container.views, container.knowledge, onBack = {}, onOpenModels = {}, onOpenRecord = onOpenRecord)
                }
            }
        }
    }

    private fun ask(question: String) {
        compose.onNode(hasSetTextAction()).performTextInput(question)
        compose.onNodeWithText("Ask").performClick()
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
