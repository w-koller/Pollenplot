package io.github.wkoller.pollenplot

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.imageDecoderEnabled
import io.github.wkoller.pollenplot.data.PhotoAnalysis
import io.github.wkoller.pollenplot.data.prefs.Settings
import io.github.wkoller.pollenplot.domain.bees.stingless.Facing
import io.github.wkoller.pollenplot.domain.bees.stingless.NativeBox
import io.github.wkoller.pollenplot.domain.bees.stingless.NativeOrigin
import io.github.wkoller.pollenplot.domain.bees.stingless.StinglessSpecies
import io.github.wkoller.pollenplot.domain.garden.ZoneKind
import io.github.wkoller.pollenplot.domain.garden.ZoneRect
import io.github.wkoller.pollenplot.domain.id.BoxBp
import io.github.wkoller.pollenplot.data.DetectionRecord
import io.github.wkoller.pollenplot.domain.id.IdSource
import io.github.wkoller.pollenplot.domain.id.LabelScore
import io.github.wkoller.pollenplot.domain.id.PhotoOrigin
import io.github.wkoller.pollenplot.domain.id.PhotoScope
import io.github.wkoller.pollenplot.domain.plants.AuState
import io.github.wkoller.pollenplot.domain.plants.Soil
import io.github.wkoller.pollenplot.domain.plants.Sun
import io.github.wkoller.pollenplot.domain.wildlife.Behaviour
import io.github.wkoller.pollenplot.domain.wildlife.LifeGroup
import io.github.wkoller.pollenplot.ml.FastPlantIdentifier
import io.github.wkoller.pollenplot.ui.components.LocalNotices
import io.github.wkoller.pollenplot.ui.components.rememberNotices
import io.github.wkoller.pollenplot.ui.identify.IdentifyModel
import io.github.wkoller.pollenplot.ui.identify.IdentifyScreen
import io.github.wkoller.pollenplot.ui.nav.AppRoot
import io.github.wkoller.pollenplot.ui.theme.PollenplotTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/**
 * The screens shown in the README, made from real photos of a garden in `src/test/resources/readme`. Each photo goes
 * through the app's own analysis, so the Plot, the plant pages and the Wildlife tab show its cut-out. The models
 * cannot run on the JVM, so a scripted identifier names each photo as the README's author named it.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = WildlifeTestApp::class, qualifiers = "en-rAU-w411dp-h891dp-xxhdpi")
class ReadmeScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val app: WildlifeTestApp get() = ApplicationProvider.getApplicationContext()
    private val garden get() = app.container.garden

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        // Cut-outs load on the test's thread, so each screenshot shows them, and are decoded with BitmapFactory.
        @OptIn(DelicateCoilApi::class)
        SingletonImageLoader.setUnsafe(
            ImageLoader.Builder(app).coroutineContext(Dispatchers.Unconfined).imageDecoderEnabled(false).build(),
        )
    }

    @Test
    fun thePlot() {
        seed()
        showApp()
        waitFor(hasContentDescription("Hive Corner", substring = true))
        shot("readme_plot")
    }

    @Test
    @Config(qualifiers = "en-rAU-w411dp-h2400dp-xhdpi")
    fun aPlantsPage() {
        seed()
        showApp()
        compose.pressWhenShown(hasText("Front Native Strip"))
        compose.pressWhenShown(hasText("Native rosella"))
        waitFor(hasText("Hibiscus heterophyllus"))
        shot("readme_plant")
    }

    @Test
    @Config(qualifiers = "en-rAU-w411dp-h2400dp-xhdpi")
    fun theWildlifeTab() {
        seed()
        showApp()
        compose.pressWhenShown(hasText("Wildlife"))
        waitFor(hasText("Galah"))
        shot("readme_wildlife")
    }

    @Test
    fun theRhythmsTab() {
        seed()
        showApp()
        compose.pressWhenShown(hasText("Rhythms"))
        // Scrolls until the heading after the plants in flower is in view, so the whole list of them shows.
        compose.scrollTo(hasText("Flowering through the year"))
        waitFor(hasText("Seen flowering in your garden"))
        shot("readme_rhythms")
    }

    @Test
    fun theNativeBees() {
        seed()
        showApp()
        compose.pressWhenShown(hasText("Bees"))
        compose.pressWhenShown(hasText("Native bees"))
        waitFor(hasText("Sugarbag bee"))
        shot("readme_bees")
    }

    @Test
    @Config(qualifiers = "en-rAU-w411dp-h2000dp-xhdpi")
    fun identifyingAPhoto() {
        ready()
        val photo = photo("white-hibiscus")
        val identifier = ScriptedIdentifier {
            listOf(
                FastPlantIdentifier.region(
                    it, BoxBp(1_500, 1_200, 8_000, 5_500),
                    mapOf(IdSource.AIY_PLANTS to listOf(LabelScore("Hibiscus heterophyllus", 7_600), LabelScore("Alyogyne huegelii", 1_100))),
                    modelFlowering = true,
                ),
            )
        }
        val container = app.container
        val analysis = PhotoAnalysis({ identifier }, container.photos, garden, container.knowledge, container.clock)
        val model = IdentifyModel(analysis, { identifier }, garden, container.knowledge, container.settingsStore, SavedStateHandle())
        model.choose(LifeGroup.PLANT, PhotoScope.ONE)
        runBlocking { model.analyse(photo) }
        compose.setContent {
            PollenplotTheme(Settings(gardenState = AuState.QLD)) {
                Surface {
                    CompositionLocalProvider(LocalNotices provides rememberNotices()) {
                        IdentifyScreen(
                            model = model, views = container.views, knowledge = container.knowledge,
                            settings = container.settingsStore, onBack = {}, onOpenPlant = {}, onAddByName = {},
                        )
                    }
                }
            }
        }
        waitFor(hasText("Native rosella"))
        shot("readme_identify")
    }

    private fun ready() = runBlocking {
        app.container.knowledge.await()
        app.container.knowledge.awaitFauna()
        app.container.settingsStore.update { it.copy(gardenState = AuState.QLD) }
    }

    /** The garden in the photos, laid out as four zones, with each plant and animal added from its photo. */
    private fun seed() {
        ready()
        runBlocking {
            val front = garden.addZone("Front Native Strip", ZoneKind.GARDEN_BED)
            val citrus = garden.addZone("Back Citrus Patch", ZoneKind.ORCHARD)
            val hive = garden.addZone("Hive Corner", ZoneKind.HIVE_CORNER)
            val veg = garden.addZone("Veg Patch", ZoneKind.VEG_PATCH)
            garden.updateZone(front.copy(rect = ZoneRect(0, 0, 1200, 350), sun = Sun.FULL_SUN, soil = Soil.SAND, mulched = true))
            garden.updateZone(citrus.copy(rect = ZoneRect(0, 500, 650, 500), sun = Sun.FULL_SUN, soil = Soil.LOAM))
            garden.updateZone(hive.copy(rect = ZoneRect(800, 500, 400, 350), sun = Sun.PART_SHADE))
            garden.updateZone(veg.copy(rect = ZoneRect(800, 1000, 400, 300), sun = Sun.FULL_SUN, soil = Soil.LOAM, mulched = true))

            fun daysAgo(n: Long) = LocalDate.of(2026, 10, 28).minusDays(n).atTime(9, 0).atZone(ZoneId.of("Australia/Brisbane")).toInstant()

            addPlant("grevillea", null, "Pink grevillea", front.id, daysAgo(3))
            addPlant("pink-spikes", "Ptilotus exaltatus", null, front.id, daysAgo(5))
            addPlant("white-hibiscus", "Hibiscus heterophyllus", null, front.id, daysAgo(2))
            addPlant("flowering-shrub", "Pavetta australiensis", null, front.id, daysAgo(9))
            val lime = addPlant("lime-tree", "Citrus × latifolia", null, citrus.id, daysAgo(1))
            addPlant("lilac-flower", null, "Plectranthus", hive.id, daysAgo(4))
            addPlant("blue-flower", "Convolvulus sabatius", null, hive.id, daysAgo(6))
            addPlant("red-lily", null, "Hippeastrum", hive.id, daysAgo(12))
            app.container.nativeBees.addHive("Sugarbag 1", StinglessSpecies.TETRAGONULA_CARBONARIA, NativeBox.OATH, NativeOrigin.BOUGHT, hive.id, Facing.NE, LocalDate.of(2025, 9, 1))
            app.container.nativeBees.addHive("Sugarbag 2", StinglessSpecies.TETRAGONULA_CARBONARIA, NativeBox.OATH, NativeOrigin.SPLIT, hive.id, Facing.NE, LocalDate.of(2026, 3, 14))
            addPlant("strawberry-flower", "Fragaria × ananassa", null, veg.id, daysAgo(7))

            addSighting("galah", LifeGroup.BIRD, IdSource.AIY_BIRDS, "Eolophus roseicapilla", "Galah", front.id, emptyList(), setOf(Behaviour.FEEDING), 2, daysAgo(1))
            addSighting("honey-bees", LifeGroup.INSECT, IdSource.AIY_INSECTS, "Apis mellifera", "European honey bee", hive.id, emptyList(), setOf(Behaviour.NESTING), 20, daysAgo(3))
            addSighting("stingless-hives", LifeGroup.INSECT, IdSource.AIY_INSECTS, "Tetragonula carbonaria", "Sugarbag bee", hive.id, emptyList(), setOf(Behaviour.NESTING), 40, daysAgo(8))
            // The sugarbag bee in the lime's flowers, as a photo of the flower it is on.
            addSighting("lime-tree", LifeGroup.INSECT, IdSource.AIY_INSECTS, "Tetragonula carbonaria", "Sugarbag bee", citrus.id, listOf(lime), setOf(Behaviour.POLLINATING), 2, daysAgo(1), BoxBp(3_400, 4_900, 5_900, 6_400))
            addSighting("tree-frog", LifeGroup.FROG, IdSource.AIY_INSECTS, "Litoria fallax", "Eastern dwarf tree frog", hive.id, emptyList(), setOf(Behaviour.RESTING), 1, daysAgo(10))
            addSighting("citrus-bug", LifeGroup.INSECT, IdSource.AIY_INSECTS, "Musgraveia sulciventris", "Bronze orange bug", citrus.id, listOf(lime), setOf(Behaviour.FEEDING), 1, daysAgo(15))
        }
    }

    private suspend fun analysed(name: String, group: LifeGroup, source: IdSource, label: String, takenAt: java.time.Instant, box: BoxBp = MOST): DetectionRecord {
        val photo = photo(name)
        val region = { bitmap: Bitmap -> listOf(FastPlantIdentifier.region(bitmap, box, mapOf(source to listOf(LabelScore(label, 7_000))), group)) }
        val identifier = ScriptedIdentifier(animalFor = { bitmap, _ -> region(bitmap) }) { region(it) }
        val container = app.container
        val analysis = PhotoAnalysis({ identifier }, container.photos, garden, container.knowledge, container.clock)
        return analysis.analyse(photo, PhotoOrigin.CAMERA, takenAt, group, PhotoScope.ONE).detections.single()
    }

    private suspend fun addPlant(photo: String, species: String?, name: String?, zoneId: String, takenAt: java.time.Instant): String {
        val find = analysed(photo, LifeGroup.PLANT, IdSource.AIY_PLANTS, species ?: name!!, takenAt)
        return garden.confirmDetection(find.id, species, name, zoneId, flowering = true).id
    }

    private suspend fun addSighting(
        photo: String, group: LifeGroup, source: IdSource, species: String, label: String, zoneId: String,
        plantIds: List<String>, behaviours: Set<Behaviour>, count: Int, takenAt: java.time.Instant, box: BoxBp = MOST,
    ) {
        val find = analysed(photo, group, source, species, takenAt, box)
        garden.confirmSighting(find.id, species, label, group, zoneId, plantIds, behaviours, count)
    }

    private fun photo(name: String): Bitmap =
        javaClass.getResourceAsStream("/readme/$name.jpg")!!.use { BitmapFactory.decodeStream(it) }!!

    private fun showApp() {
        compose.setContent {
            PollenplotTheme(Settings(gardenState = AuState.QLD)) { Surface { AppRoot(app.container) } }
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

    private companion object {
        /** Most of the frame, as the camera's square guide would hold it. */
        val MOST = BoxBp(800, 800, 9_200, 9_200)
    }
}
