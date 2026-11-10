package io.github.wkoller.pollenplot

import android.graphics.Bitmap
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToString
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.imageDecoderEnabled
import io.github.wkoller.pollenplot.data.PhotoAnalysis
import io.github.wkoller.pollenplot.data.prefs.Settings
import io.github.wkoller.pollenplot.data.prefs.ThemeChoice
import io.github.wkoller.pollenplot.domain.garden.GardenPlant
import io.github.wkoller.pollenplot.domain.id.BoxBp
import io.github.wkoller.pollenplot.domain.id.DetectionStatus
import io.github.wkoller.pollenplot.domain.plants.AuState
import io.github.wkoller.pollenplot.domain.id.IdSource
import io.github.wkoller.pollenplot.domain.id.LabelScore
import io.github.wkoller.pollenplot.domain.id.PhotoScope
import io.github.wkoller.pollenplot.domain.wildlife.Behaviour
import io.github.wkoller.pollenplot.domain.wildlife.LifeGroup
import io.github.wkoller.pollenplot.ml.FastPlantIdentifier
import io.github.wkoller.pollenplot.ml.FoundRegion
import io.github.wkoller.pollenplot.ui.identify.IdentifyModel
import io.github.wkoller.pollenplot.ui.identify.IdentifyScreen
import io.github.wkoller.pollenplot.ui.identify.IdentifyStage
import io.github.wkoller.pollenplot.ui.nav.AppRoot
import io.github.wkoller.pollenplot.ui.theme.PollenplotTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Identifies a painted garden photo with a stand-in for the on-device models, then reviews each plant found. The
 * camera itself needs a phone, so these tests start from the photo.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class IdentifyFlowTest {
    // The classic rule runs coroutines at once, so flows from the database reach the screen while the test waits.
    @Suppress("DEPRECATION")
    @get:Rule
    val compose = createComposeRule()

    private val app: PollenplotApp get() = ApplicationProvider.getApplicationContext()
    private val garden get() = app.container.garden

    @Before
    fun picturesLoadAtOnce() {
        // Cut-outs load on the test's thread, so each screenshot shows them. Robolectric has no ImageDecoder, so
        // pictures are decoded with BitmapFactory as on older phones.
        @OptIn(DelicateCoilApi::class)
        SingletonImageLoader.setUnsafe(
            ImageLoader.Builder(app).coroutineContext(Dispatchers.Unconfined).imageDecoderEnabled(false).build(),
        )
    }

    @Test
    @Config(qualifiers = "w411dp-h3200dp-xhdpi")
    fun reviewEachPlantInAPhoto() {
        val ids = seed()
        show(analysed(TestPhotos.threePlants()))
        waitFor(hasText("Pollenplot found 3 plants in this photo."))
        shot("identify_results_full")

        // A bottlebrush the garden does not have yet goes into the zone chosen for it, with its flowers recorded.
        compose.onAllNodesWithText("Front Native Strip")[0].performClick()
        compose.onAllNodesWithText("Add to garden")[0].performClick()
        waitFor(hasText("Added to Front Native Strip."))
        val crimson = plants().single { it.speciesName == "Melaleuca citrina" }
        assertEquals(ids.front, crimson.zoneId)
        assertNotNull("The cut-out becomes the plant's picture", crimson.thumbnail)
        assertEquals(1, runBlocking { garden.floweringOf(crimson.id) }.size)

        // Rosemary already grows in Hive Corner, so the photo is added to that plant instead of adding another.
        compose.onNodeWithText("You already have Rosemary in Hive Corner.").assertIsDisplayed()
        compose.onNodeWithText("Add the photo to it").performClick()
        waitFor(hasText("Added to Hive Corner."))
        assertEquals("No second rosemary", 1, plants().count { it.speciesName == "Salvia rosmarinus" })

        // The third is marked as not a plant, put back, and named from the knowledge base instead.
        compose.onNodeWithText("Not a plant").performClick()
        waitFor(hasText("This part of the photo will not be added to your garden."))
        shot("identify_results_decided")
        compose.onNodeWithText("Undo").performClick()
        waitFor(hasText("It is another plant"))
        compose.onNodeWithText("It is another plant").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("snake vine")
        waitFor(hasText("Hibbertia scandens"))
        shot("identify_choose_another")
        compose.onNodeWithText("Hibbertia scandens").performClick()
        waitFor(hasText("Your choice"))
        compose.onNodeWithText("Add to garden").performClick()
        compose.waitUntil(WAIT_MS) { compose.onAllNodesWithText("Added to Front Native Strip.").fetchSemanticsNodes().size == 2 }
        assertEquals("The zone chosen for the first plant is offered for the others", ids.front, plants().single { it.speciesName == "Hibbertia scandens" }.zoneId)
        shot("identify_results_done")

        val photoId = photoIdOf(crimson)
        assertTrue("Every plant in the photo was reviewed", runBlocking { garden.detectionsInPhoto(photoId).first() }.all { it.status == DetectionStatus.CONFIRMED })
    }

    @Test
    fun aPlantPollenplotDoesNotKnowIsAddedByTheNameTyped() {
        seed()
        show(analysed(TestPhotos.threePlants(), PhotoScope.ONE))
        waitFor(hasText("It is another plant"))
        compose.onNodeWithText("It is another plant").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Nan's lavender")
        waitFor(hasText("Use “Nan's lavender”"))
        shot("identify_typed_name")
        compose.onNodeWithText("Use “Nan's lavender”").performClick()
        waitFor(hasText("Your choice"))
        compose.onAllNodesWithText("Nan's lavender").assertCountEquals(2)
        compose.onNodeWithText("Add to garden").performScrollTo().performClick()
        waitFor(hasText("Added to", substring = true))
        val added = plants().single { it.customName == "Nan's lavender" }
        assertNull("A typed name is not a species", added.speciesName)
        // The same suggestion is corrected the next time it comes up.
        val top = runBlocking { garden.findsOf(added.id).first().single().candidates.first() }
        assertEquals("Nan's lavender", runBlocking { garden.idHistory() }.apply(listOf(top)).first().label)
    }

    @Test
    @Config(qualifiers = "w411dp-h3200dp-xhdpi")
    fun aPhotoOfOnePlantSeenAsSeveralIsMadeOneCard() {
        seed()
        show(analysed(TestPhotos.threePlants()))
        waitFor(hasText("Pollenplot found 3 plants in this photo."))
        compose.onNodeWithText("This photo shows one plant").performScrollTo().performClick()
        compose.waitUntil(WAIT_MS) { compose.onAllNodesWithText("Plant 1 of", substring = true).fetchSemanticsNodes().isEmpty() }
        compose.onAllNodesWithText("Add to garden").assertCountEquals(1)
        shot("identify_made_one")
        assertEquals("No card is numbered as one of several", 0, compose.onAllNodesWithText("Plant 1 of", substring = true).fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithText("This photo shows one plant").fetchSemanticsNodes().size)
        val find = runBlocking { garden.detectionsInPhoto(photoIds().single()).first().single() }
        assertEquals("The surest find is kept, with the others' suggestions after its own", "Callistemon citrinus", find.candidates.first().label)
        assertTrue(find.candidates.any { it.label == "Rosmarinus officinalis" })
    }

    @Test
    @Config(qualifiers = "w411dp-h2200dp-xhdpi")
    fun aFindLookedAtAsAPlantCanBeAnAnimal() {
        seed()
        val miner = mapOf(IdSource.AIY_BIRDS to listOf(LabelScore("Manorina melanocephala", 7_200)))
        show(analysed(TestPhotos.threePlants(), PhotoScope.ONE, animalFor = { photo, group -> listOf(FastPlantIdentifier.region(photo, FastPlantIdentifier.CENTRE, miner, group)) }))
        waitFor(hasText("It is an animal"))
        compose.onNodeWithText("It is an animal").performScrollTo().performClick()
        compose.pressWhenShown(hasText("Bird"))
        waitFor(hasText("Noisy miner"))
        shot("identify_plant_to_bird")
        compose.onNodeWithText("Add sighting").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("It is a plant").assertExists()
        val find = runBlocking { garden.detectionsInPhoto(photoIds().single()).first().single() }
        assertEquals(LifeGroup.BIRD, find.group)
        assertEquals("Manorina melanocephala", find.candidates.first().speciesName)
    }

    @Test
    fun resultsOnAPhone() {
        seed()
        show(analysed(TestPhotos.threePlants()))
        waitFor(hasText("Pollenplot found 3 plants in this photo."))
        shot("identify_results")
    }

    @Test
    fun resultsInTheDark() {
        seed()
        show(analysed(TestPhotos.threePlants()), Settings(theme = ThemeChoice.DARK, gardenState = AuState.QLD))
        waitFor(hasText("Pollenplot found 3 plants in this photo."))
        shot("identify_results_dark")
    }

    @Test
    fun aPhotoOfOnePlantGivesOneCard() {
        seed()
        show(analysed(TestPhotos.threePlants(), PhotoScope.ONE))
        waitFor(hasText("Add to garden"))
        compose.onNodeWithText("Pollenplot found 1 plant in this photo.").assertIsDisplayed()
        assertEquals("No card is numbered as one of several", 0, compose.onAllNodesWithText("Plant 1 of", substring = true).fetchSemanticsNodes().size)
    }

    @Test
    fun oneBushSeenInSeveralTilesIsOnePlant() {
        seed()
        val bush = TestPhotos.bottlebrush
        val tiles = listOf(BoxBp(0, 3_000, 2_000, 6_000), BoxBp(2_000, 6_000, 4_000, 9_000), bush.box).mapIndexed { i, box ->
            TestPhotos.region(TestPhotos.Painted(box, bush.leaf, bush.flower), flowering = i == 1, LabelScore("Callistemon citrinus", 7_000 - i * 1_000))
        }
        val model = analysed(tiles)
        show(model)
        waitFor(hasText("Pollenplot found 1 plant in this photo."))
        val photoId = (model.stage.value as IdentifyStage.Results).photoId
        val found = runBlocking { garden.detectionsInPhoto(photoId).first() }
        assertEquals(1, found.size)
        assertTrue("Flowers seen on any tile are kept", found.single().flowering)
    }

    @Test
    fun aPhotoWithNoPlantIsNotKept() {
        seed()
        val model = analysed(emptyList())
        assertEquals(IdentifyStage.NoneFound, model.stage.value)
        assertEquals("Nothing was saved", 0, File(app.filesDir, "photos").listFiles()?.size ?: 0)
        show(model)
        compose.onNodeWithText("No plant was found in this photo.").assertIsDisplayed()
        shot("identify_none")
        compose.onNodeWithText("Take another photo").performClick()
        compose.onNodeWithText("Pollenplot needs the camera to identify plants.").assertIsDisplayed()
        compose.onNodeWithText("Allow the camera").assertIsDisplayed()
        shot("identify_permission")
    }

    @Test
    fun theAddMenuOpensIdentify() {
        seed()
        compose.setContent {
            PollenplotTheme(Settings(gardenState = AuState.QLD)) { Surface { AppRoot(app.container) } }
        }
        // The Plot opens once the app knows the garden needs no setup.
        waitFor(hasContentDescription("Open the add menu"))
        compose.addFromMenu("Identify with the camera")
        compose.onNodeWithText("Pollenplot needs the camera to identify plants.").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w411dp-h2200dp-xhdpi")
    fun aBirdBecomesASightingOnThePlantItWasOn() {
        val ids = seed()
        val plantsBefore = plants().size
        val model = analysedAnimal(LifeGroup.BIRD, IdSource.AIY_BIRDS to listOf(LabelScore("Manorina melanocephala", 6_500), LabelScore("Anthochaera carunculata", 1_200)))
        show(model)
        waitFor(hasText("Noisy miner"))
        compose.onNodeWithText("Red wattlebird").assertExists()
        shot("identify_bird")
        compose.onNodeWithText("Hive Corner").performClick()
        compose.onNodeWithText("Rosemary").performClick()
        compose.onNodeWithText("Feeding").performClick()
        compose.onNodeWithText("Add sighting").performClick()
        waitFor(hasText("Added to your sightings in Hive Corner."))
        compose.onNodeWithText("Noisy miner").assertExists()
        shot("identify_bird_added")
        val sighting = runBlocking { garden.sightingList.first { it.isNotEmpty() }.single() }
        assertEquals("Manorina melanocephala", sighting.speciesName)
        assertEquals(LifeGroup.BIRD, sighting.group)
        assertEquals(Behaviour.FEEDING, sighting.behaviour)
        assertEquals(ids.hive, sighting.zoneId)
        assertEquals(plants().single { it.speciesName == "Salvia rosmarinus" }.id, sighting.plantId)
        assertEquals("No plant was added", plantsBefore, plants().size)
    }

    @Test
    @Config(qualifiers = "w411dp-h2200dp-xhdpi")
    fun aVenomousSpiderSaysWhatToDo() {
        seed()
        val model = analysedAnimal(LifeGroup.SPIDER, IdSource.BIOCAP to listOf(LabelScore("Latrodectus hasselti", 8_000)))
        show(model)
        waitFor(hasText("Redback spider"))
        compose.onNodeWithText("Venomous").assertExists()
        compose.onNodeWithText("13 11 26", substring = true).assertExists()
        shot("identify_redback")
    }

    @Test
    fun anAnimalTheKnowledgeBaseLacksIsNamedByItsCommonName() {
        seed()
        val model = analysedAnimal(LifeGroup.SPIDER, IdSource.BIOCAP to listOf(LabelScore("Mopsus mormon", 7_000, common = "Green jumping spider")))
        show(model)
        waitFor(hasText("Green jumping spider"))
        // Under the card's title and under the suggestion.
        compose.onAllNodesWithText("Mopsus mormon").assertCountEquals(2)
        shot("identify_common_name")
        compose.onNodeWithText("Add sighting").performScrollTo().performClick()
        waitFor(hasText("Added to your sightings."))
        val sighting = runBlocking { garden.sightingList.first { it.isNotEmpty() }.single() }
        assertEquals("The scientific name is kept for the records", "Mopsus mormon", sighting.speciesName)
        assertEquals("Green jumping spider", sighting.label)
    }

    /** Runs a painted photo through the app's analysis as a photo of [group], with [labels] as what the models said. */
    private fun analysedAnimal(group: LifeGroup, vararg labels: Pair<IdSource, List<LabelScore>>): IdentifyModel {
        val identifier = ScriptedIdentifier(animalFor = { photo, asked -> listOf(FastPlantIdentifier.region(photo, FastPlantIdentifier.CENTRE, labels.toMap(), asked)) })
        val container = app.container
        val analysis = PhotoAnalysis({ identifier }, container.photos, garden, container.knowledge, container.clock)
        val model = IdentifyModel(analysis, { identifier }, garden, container.knowledge, container.settingsStore, SavedStateHandle())
        model.choose(group)
        runBlocking {
            container.knowledge.awaitFauna()
            model.analyse(TestPhotos.gardenPhoto())
        }
        assertEquals(listOf(group), identifier.asked)
        assertTrue("The photo was analysed, not ${model.stage.value}", model.stage.value is IdentifyStage.Results)
        return model
    }

    private fun seed(): DemoGarden.Ids = runBlocking {
        app.container.knowledge.await()
        app.container.settingsStore.update { it.copy(gardenState = AuState.QLD) }
        DemoGarden.seed(garden)
    }

    private fun plants(): List<GardenPlant> = runBlocking { garden.plantList.first() }

    private fun photoIds(): List<String> = File(app.filesDir, "photos").listFiles()!!.map { it.nameWithoutExtension }

    private fun photoIdOf(plant: GardenPlant): String = runBlocking {
        val thumbnail = plant.thumbnail!!
        val photos = File(app.filesDir, "photos").listFiles()!!.map { it.nameWithoutExtension }
        photos.single { id -> garden.detectionsInPhoto(id).first().any { it.thumbnail == thumbnail } }
    }

    /** Runs a painted garden photo through the app's analysis, with [regions] as what the models found. */
    /** The model after a photo showing [regions], taken with the Garden bed chip unless [scope] says one plant. */
    private fun analysed(
        regions: List<FoundRegion>,
        scope: PhotoScope = PhotoScope.SEVERAL,
        animalFor: (Bitmap, LifeGroup) -> List<FoundRegion> = { _, _ -> emptyList() },
    ): IdentifyModel {
        val identifier = ScriptedIdentifier(animalFor = animalFor) { regions }
        val container = app.container
        val analysis = PhotoAnalysis({ identifier }, container.photos, garden, container.knowledge, container.clock)
        val model = IdentifyModel(analysis, { identifier }, garden, container.knowledge, container.settingsStore, SavedStateHandle())
        model.choose(LifeGroup.PLANT, scope)
        runBlocking { model.analyse(TestPhotos.gardenPhoto()) }
        assertEquals(listOf(scope), identifier.scopes.toList())
        if (regions.isNotEmpty()) assertTrue("The photo was analysed, not ${model.stage.value}", model.stage.value is IdentifyStage.Results)
        return model
    }

    private fun show(model: IdentifyModel, settings: Settings = Settings(gardenState = AuState.QLD)) {
        compose.setContent {
            PollenplotTheme(settings) {
                Surface {
                    IdentifyScreen(
                        model = model,
                        views = app.container.views,
                        knowledge = app.container.knowledge,
                        settings = app.container.settingsStore,
                        onBack = {},
                        onOpenPlant = {},
                        onAddByName = {},
                    )
                }
            }
        }
    }

    private fun waitFor(matcher: SemanticsMatcher) {
        try {
            compose.waitUntil(WAIT_MS) { compose.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: ComposeTimeoutException) {
            // Shows what the screen held instead, which is most of what a failure here needs.
            println(compose.onRoot(useUnmergedTree = true).printToString())
            throw e
        }
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        val dir = File(System.getProperty("screenshots.dir") ?: "build/screenshots").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
