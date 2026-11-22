package io.github.wkoller.pollenplot

import java.time.Month
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import io.github.wkoller.pollenplot.ui.assistant.starterText
import io.github.wkoller.pollenplot.domain.wildlife.LifeGroup
import io.github.wkoller.pollenplot.domain.season.Season
import io.github.wkoller.pollenplot.domain.rhythms.BeeGroup
import io.github.wkoller.pollenplot.domain.assistant.StarterKind
import io.github.wkoller.pollenplot.domain.assistant.Starter
import io.github.wkoller.pollenplot.domain.assistant.QuestionKind
import io.github.wkoller.pollenplot.domain.assistant.PulseVisitor
import io.github.wkoller.pollenplot.domain.assistant.PulsePlant
import io.github.wkoller.pollenplot.domain.assistant.PartOfDay
import io.github.wkoller.pollenplot.domain.assistant.GardenPulse
import io.github.wkoller.pollenplot.domain.assistant.GardenAssistantGuard
import io.github.wkoller.pollenplot.data.prefs.ThemeChoice
import io.github.wkoller.pollenplot.ai.AssistantReply
import android.graphics.Bitmap
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
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
        val name = "Petalostigma triloculare"
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

    @Test
    fun startersAskWhatTheGardenIsDoingAndFollowUpsAskWhatIsNext() {
        val (rosemary, miner) = rosemaryAndMiner()
        app.assistant.answer = "Noisy miners feed on the rosemary [1]."
        app.assistant.cited = listOf(CitedRecord(rosemary, RecordKind.PLANT, "Rosemary", "Plant: Rosemary"))
        app.assistant.reading = AssistantReply.Reading(plants = 1, sightings = 1)
        val hold = CompletableDeferred<Unit>()
        app.assistant.hold = hold
        show(app.assistant, AskSubject.Garden, pulse = { pulse(rosemary, miner) })
        waitFor(hasText("Good morning."))
        compose.onNodeWithText("1 plant is in flower and 1 new visitor was seen in the last month.").assertExists()
        compose.onNodeWithText("What brings the noisy miner to my rosemary?").assertExists()
        compose.onNodeWithText("What is flowering for the bees in the Hive Corner?").assertExists()
        compose.onNodeWithText("What could I plant this spring to bring in more bees?").assertExists()
        shot("ask_starters")

        // More ideas shows the next three, and comes round to the first again.
        compose.onNodeWithText("More ideas").performClick()
        waitFor(hasText("Which plants would bring birds to my garden this spring?"))
        compose.onNodeWithText("How can I make my garden a better home for frogs?").assertExists()
        compose.onNodeWithText("What brings the noisy miner to my rosemary?").assertDoesNotExist()
        compose.onNodeWithText("More ideas").performClick()
        waitFor(hasText("What brings the noisy miner to my rosemary?"))

        // A press asks at once, about the sighting and the plant, and says what it is reading.
        compose.onNodeWithText("What brings the noisy miner to my rosemary?").performClick()
        waitFor(hasText("Reading about 1 plant and 1 sighting in your garden."))
        assertEquals("What brings the noisy miner to my rosemary?", app.assistant.asked.single().first)
        assertEquals(listOf(miner, rosemary), app.assistant.about.single())
        shot("ask_reading")
        app.assistant.hold = null
        hold.complete(Unit)

        waitFor(hasText("Ask next"))
        compose.onNodeWithText("Which bees visit the rosemary?").assertExists()
        compose.onNodeWithText("When does the rosemary flower?").assertExists()
        compose.onNodeWithText("Try asking").assertDoesNotExist()
        shot("ask_follow_ups")
        compose.onNodeWithText("Which bees visit the rosemary?").performClick()
        compose.waitUntil(WAIT_MS) { app.assistant.asked.size == 2 }
        assertEquals("Which bees visit the rosemary?", app.assistant.asked.last().first)
        assertEquals(listOf(rosemary), app.assistant.about.last())
    }

    @Test
    fun startersInTheDark() {
        val (rosemary, miner) = rosemaryAndMiner()
        show(app.assistant, AskSubject.Garden, pulse = { pulse(rosemary, miner) }, settings = Settings(theme = ThemeChoice.DARK))
        waitFor(hasText("Try asking"))
        shot("ask_starters_dark")
    }

    @Test
    @Config(fontScale = 2.0f, qualifiers = "en-rAU-w411dp-h2400dp-xhdpi")
    fun startersAtTwiceTheSize() {
        val (rosemary, miner) = rosemaryAndMiner()
        show(app.assistant, AskSubject.Garden, pulse = { pulse(rosemary, miner) })
        waitFor(hasText("Try asking"))
        shot("large_ask_starters")
    }

    @Test
    fun aPlantsStartersAreAboutIt() {
        val (rosemary, _) = rosemaryAndMiner()
        show(app.assistant, AskSubject.Plant(rosemary))
        waitFor(hasText("Which bees visit the rosemary?"))
        compose.onNodeWithText("When does the rosemary flower?").assertExists()
        compose.onNodeWithText("Good morning.").assertDoesNotExist()
    }

    @Test
    fun whatTheGardenIsDoingIsReadFromItsRecords() {
        val (rosemary, _) = rosemaryAndMiner()
        val pulse = runBlocking { container.askViews.current() }
        assertEquals(LocalDate.of(2026, 10, 28), pulse.today)
        assertEquals(Season.SPRING, pulse.season)
        assertEquals(PartOfDay.MORNING, pulse.partOfDay)
        val plants = runBlocking { container.garden.plantList.first() }.map { it.id }.toSet()
        assertTrue("Only the garden's own plants are in flower: ${pulse.inFlower}", pulse.inFlower.all { it.id in plants })
        assertTrue("Solitary bees are always counted: ${pulse.gaps}", pulse.gaps.none { it.group == BeeGroup.STINGLESS })
        // A question offered about a record is given that record first, whatever its words match.
        val records = runBlocking { real.records("What is happening?", listOf(rosemary)) }
        assertEquals(rosemary, records.first().id)
    }

    @Test
    fun noOfferedQuestionIsOneTheAppAnswersItself() {
        seed()
        val kb = runBlocking { container.knowledge.awaitFauna() }
        val plants = runBlocking { container.garden.plantList.first() }.mapNotNull { it.customName ?: it.speciesName }
        val zones = runBlocking { container.garden.zoneList.first() }.map { it.name }
        val animals = kb.all.map { it.displayName }
        val problems = container.catalogue.problems.map { it.name }
        // Each question with every name it could hold, and the name, so a name the guard catches on its own is allowed.
        val starters = buildList {
            animals.forEach { animal ->
                add(Starter(StarterKind.VISITOR, listOf(animal)) to animal)
                add(Starter(StarterKind.VISITOR_ON_PLANT, listOf(animal, plants.first())) to animal)
                add(Starter(StarterKind.FUNGUS, listOf(animal)) to animal)
                listOf(StarterKind.ANIMAL_NEEDS, StarterKind.ANIMAL_FOOD, StarterKind.ANIMAL_PLANTS).forEach { add(Starter(it, listOf(animal)) to animal) }
            }
            problems.forEach { problem -> plants.forEach { plant -> add(Starter(StarterKind.FOLLOW_UP, listOf(problem, plant)) to problem) } }
            plants.forEach { plant ->
                listOf(StarterKind.PLANT_FLOWERING, StarterKind.PLANT_BEES, StarterKind.PLANT_PRUNING, StarterKind.PLANT_SPOT).forEach { add(Starter(it, listOf(plant)) to plant) }
            }
            zones.forEach { zone ->
                add(Starter(StarterKind.IN_FLOWER_ZONE, listOf(zone)) to zone)
                add(Starter(StarterKind.ZONE_BEES, listOf(zone)) to zone)
            }
            BeeGroup.entries.forEach { group ->
                add(Starter(StarterKind.UNFED, group = group) to "")
                Month.entries.forEach { add(Starter(StarterKind.GAP, group = group, month = it) to "") }
            }
            Season.entries.forEach { season ->
                listOf(StarterKind.SEASON_BEES, StarterKind.SEASON_BIRDS, StarterKind.SEASON_JOBS).forEach { add(Starter(it, season = season) to "") }
            }
            add(Starter(StarterKind.HOT_DAY, date = LocalDate.of(2026, 10, 30)) to "")
            add(Starter(StarterKind.IN_FLOWER) to "")
            add(Starter(StarterKind.FROGS) to "")
        }
        assertTrue("Every kind is checked", StarterKind.entries.all { kind -> starters.any { it.first.kind == kind } })
        val texts = mutableListOf<String>()
        compose.setContent { starters.forEach { (starter, _) -> texts += starterText(starter) } }
        compose.waitForIdle()
        val caught = texts.zip(starters).filter { (text, pair) ->
            GardenAssistantGuard.classify(text) != QuestionKind.GENERAL && GardenAssistantGuard.classify(pair.second) == QuestionKind.GENERAL
        }
        assertTrue("Questions the guard would answer itself: ${caught.map { it.first }}", caught.isEmpty())
    }

    private fun rosemaryAndMiner(): Pair<String, String> {
        seed()
        val rosemary = runBlocking { container.garden.plantList.first().single { it.speciesName == "Salvia rosmarinus" } }.id
        val miner = runBlocking { container.views.sightings.first() }.first { it.sighting.label == "Noisy miner" && rosemary in it.sighting.plantIds }.sighting.id
        return rosemary to miner
    }

    private fun pulse(rosemary: String, miner: String) = GardenPulse(
        LocalDate.of(2026, 10, 28),
        Season.SPRING,
        PartOfDay.MORNING,
        inFlower = listOf(PulsePlant(rosemary, "Rosemary", "Hive Corner")),
        visitors = listOf(PulseVisitor("noisy miner", LifeGroup.BIRD, miner, rosemary, "Rosemary")),
    )

    private fun seed(): DemoGarden.Ids = runBlocking {
        container.knowledge.await()
        container.knowledge.awaitFauna()
        container.settingsStore.update { it.copy(gardenState = AuState.QLD) }
        DemoGarden.seed(container.garden).also { DemoWildlife.seed(container.garden, it) }
    }

    private fun show(
        assistant: Assistant,
        subject: AskSubject,
        onOpenRecord: (CitedRecord) -> Unit = {},
        pulse: (suspend () -> GardenPulse)? = null,
        settings: Settings = Settings(),
    ) {
        val model = AskModel(assistant, subject)
        compose.setContent {
            PollenplotTheme(settings) {
                Surface {
                    AskScreen(model, assistant, container.views, container.knowledge, onBack = {}, onOpenModels = {}, onOpenRecord = onOpenRecord, pulse = pulse)
                }
            }
        }
    }

    private fun ask(question: String) {
        compose.onNode(hasSetTextAction()).performTextInput(question)
        compose.onNodeWithText("Ask").performClick()
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
