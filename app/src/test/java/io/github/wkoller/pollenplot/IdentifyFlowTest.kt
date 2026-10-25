package io.github.wkoller.pollenplot

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
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
import io.github.wkoller.pollenplot.domain.id.IdSource
import io.github.wkoller.pollenplot.domain.id.LabelScore
import io.github.wkoller.pollenplot.domain.id.TileResult
import io.github.wkoller.pollenplot.domain.plants.AuState
import io.github.wkoller.pollenplot.ml.FoundRegion
import io.github.wkoller.pollenplot.ml.Identification
import io.github.wkoller.pollenplot.ml.PlantIdentifier
import io.github.wkoller.pollenplot.ui.identify.IdentifyModel
import io.github.wkoller.pollenplot.ui.identify.IdentifyScreen
import io.github.wkoller.pollenplot.ui.identify.IdentifyStage
import io.github.wkoller.pollenplot.ui.nav.AppRoot
import io.github.wkoller.pollenplot.ui.theme.PollenplotTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.random.Random

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
        show(analysed(threePlants()))
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
        waitFor(hasText("Choose another plant"))
        compose.onNodeWithText("Choose another plant").performClick()
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
    fun resultsOnAPhone() {
        seed()
        show(analysed(threePlants()))
        waitFor(hasText("Pollenplot found 3 plants in this photo."))
        shot("identify_results")
    }

    @Test
    fun resultsInTheDark() {
        seed()
        show(analysed(threePlants()), Settings(theme = ThemeChoice.DARK, gardenState = AuState.QLD))
        waitFor(hasText("Pollenplot found 3 plants in this photo."))
        shot("identify_results_dark")
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
        compose.onNodeWithContentDescription("Open the add menu").performClick()
        compose.onNodeWithText("Identify with the camera").performClick()
        compose.onNodeWithText("Pollenplot needs the camera to identify plants.").assertIsDisplayed()
    }

    private fun seed(): DemoGarden.Ids = runBlocking {
        app.container.knowledge.await()
        app.container.settingsStore.update { it.copy(gardenState = AuState.QLD) }
        DemoGarden.seed(garden)
    }

    private fun plants(): List<GardenPlant> = runBlocking { garden.plantList.first() }

    private fun photoIdOf(plant: GardenPlant): String = runBlocking {
        val thumbnail = plant.thumbnail!!
        val photos = File(app.filesDir, "photos").listFiles()!!.map { it.nameWithoutExtension }
        photos.single { id -> garden.detectionsInPhoto(id).first().any { it.thumbnail == thumbnail } }
    }

    /** Runs a painted garden photo through the app's analysis, with [regions] as what the models found. */
    private fun analysed(regions: List<FoundRegion>): IdentifyModel {
        val identifier = FakeIdentifier(regions)
        val container = app.container
        val analysis = PhotoAnalysis({ identifier }, container.photos, garden, container.knowledge, container.clock)
        val model = IdentifyModel(analysis, { identifier }, garden, container.knowledge, SavedStateHandle())
        runBlocking { model.analyse(gardenPhoto()) }
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

    private class Painted(val box: BoxBp, val leaf: Int, val flower: Int?)

    private val bottlebrush = Painted(BoxBp(0, 3_000, 4_000, 9_000), Color.rgb(64, 110, 52), Color.rgb(200, 30, 45))
    private val rosemary = Painted(BoxBp(3_500, 1_000, 6_500, 7_000), Color.rgb(88, 120, 96), Color.rgb(150, 160, 220))
    private val vine = Painted(BoxBp(6_000, 3_000, 10_000, 9_000), Color.rgb(70, 130, 40), Color.rgb(245, 200, 30))

    /** What the models "found" in the painted photo: a bottlebrush, rosemary, and a yellow flower they are unsure of. */
    private fun threePlants() = listOf(
        region(bottlebrush, flowering = true, LabelScore("Callistemon citrinus", 7_400), LabelScore("Hibiscus syriacus", 900)),
        region(rosemary, flowering = false, LabelScore("Rosmarinus officinalis", 4_300), LabelScore("Lavandula stoechas", 2_100)),
        region(vine, flowering = false, LabelScore("Hibiscus trionum", 2_600), LabelScore("Hibiscus rosa-sinensis", 1_400)),
    )

    private fun region(plant: Painted, flowering: Boolean, vararg labels: LabelScore) =
        FoundRegion(plant.box, mapOf(IdSource.AIY_PLANTS to labels.toList()), paintedPlant(plant, 512), flowering)

    /** A garden bed with the three plants in their boxes, on mulch. */
    private fun gardenPhoto(): Bitmap {
        val width = 1_600
        val height = 1_200
        val photo = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(photo)
        canvas.drawColor(Color.rgb(104, 80, 58))
        for (plant in listOf(bottlebrush, rosemary, vine)) {
            val target = Rect(
                plant.box.left * width / BoxBp.FULL,
                plant.box.top * height / BoxBp.FULL,
                plant.box.right * width / BoxBp.FULL,
                plant.box.bottom * height / BoxBp.FULL,
            )
            canvas.drawBitmap(paintedPlant(plant, 512, mulch = false), null, target, null)
        }
        return photo
    }

    /** Leaves of one colour, scattered, with round flowers of another. */
    private fun paintedPlant(plant: Painted, size: Int, mulch: Boolean = true): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (mulch) canvas.drawColor(Color.rgb(104, 80, 58))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val random = Random(plant.leaf)
        val centre = size / 2f
        repeat(90) {
            val angle = random.nextFloat() * 360f
            val reach = random.nextFloat() * size * 0.42f
            val x = centre + reach * kotlin.math.cos(Math.toRadians(angle.toDouble())).toFloat()
            val y = centre + reach * kotlin.math.sin(Math.toRadians(angle.toDouble())).toFloat()
            paint.color = shade(plant.leaf, random.nextFloat())
            canvas.save()
            canvas.rotate(angle, x, y)
            canvas.drawOval(x - size * 0.09f, y - size * 0.025f, x + size * 0.09f, y + size * 0.025f, paint)
            canvas.restore()
        }
        plant.flower?.let { colour ->
            repeat(16) {
                paint.color = shade(colour, random.nextFloat())
                canvas.drawCircle(centre + (random.nextFloat() - 0.5f) * size * 0.7f, centre + (random.nextFloat() - 0.5f) * size * 0.7f, size * 0.045f, paint)
            }
        }
        return bitmap
    }

    private fun shade(colour: Int, amount: Float): Int {
        val f = 0.75f + amount * 0.4f
        return Color.rgb((Color.red(colour) * f).toInt().coerceAtMost(255), (Color.green(colour) * f).toInt().coerceAtMost(255), (Color.blue(colour) * f).toInt().coerceAtMost(255))
    }
}

/** Generous, because Gradle may be running lint and packaging the app on the same cores while these tests run. */
private const val WAIT_MS = 30_000L

/** Stands in for the on-device models: it finds the regions it was given. */
private class FakeIdentifier(private val regions: List<FoundRegion>) : PlantIdentifier {
    override suspend fun identify(photo: Bitmap) = Identification(showsPlant = regions.isNotEmpty(), regions = regions)

    override suspend fun glance(frame: Bitmap) = TileResult(BoxBp.WHOLE, emptyList(), backgroundBp = 0)

    override fun close() = Unit
}
