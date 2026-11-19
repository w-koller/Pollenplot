package io.github.wkoller.pollenplot

import androidx.compose.ui.semantics.SemanticsProperties
import io.github.wkoller.pollenplot.data.prefs.NorthSide
import androidx.compose.ui.test.onAllNodesWithContentDescription
import org.junit.Assert.assertTrue
import androidx.compose.ui.geometry.Offset
import io.github.wkoller.pollenplot.data.prefs.PlotBackground
import io.github.wkoller.pollenplot.domain.garden.ZoneRect
import io.github.wkoller.pollenplot.domain.garden.StructureShape
import io.github.wkoller.pollenplot.domain.garden.StructureKind
import kotlinx.coroutines.flow.first
import androidx.compose.ui.test.performScrollTo
import android.graphics.Bitmap
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.wkoller.pollenplot.data.prefs.Settings
import io.github.wkoller.pollenplot.domain.plants.AuState
import io.github.wkoller.pollenplot.ui.nav.AppRoot
import io.github.wkoller.pollenplot.data.prefs.ThemeChoice
import io.github.wkoller.pollenplot.ui.theme.PollenplotTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Drives the Plot, zones, plants and search with a demo yard, saving a picture of each step to build/screenshots. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class PlotScreenFlowTest {
    // The classic rule runs coroutines at once, so flows from the database reach the screen while the test waits.
    @Suppress("DEPRECATION")
    @get:Rule
    val compose = createComposeRule()

    private val app: PollenplotApp get() = ApplicationProvider.getApplicationContext()

    @Test
    fun canvasListZoneAndPlant() {
        seed()
        show()
        waitFor(hasContentDescription("Front Native Strip", substring = true))
        shot("plot_canvas")

        compose.pressWhenShown(hasContentDescription("Show as a list"))
        waitFor(hasText("Back Citrus Patch"))
        compose.onNodeWithText("Back Citrus Patch").assertIsDisplayed()
        shot("plot_list")

        compose.onNodeWithText("Hive Corner").performClick()
        waitFor(hasText("Pollinator hotspot"))
        shot("zone_hive_corner")

        compose.onNodeWithText("Rosemary").performClick()
        waitFor(hasText("Salvia rosmarinus"))
        shot("plant_rosemary")
    }

    @Test
    @Config(qualifiers = "w411dp-h2600dp-xhdpi")
    fun plantPagesInFull() {
        seed()
        show()
        compose.pressWhenShown(hasContentDescription("Show as a list"))
        waitFor(hasText("Front Native Strip"))
        compose.onNodeWithText("Front Native Strip").performClick()
        // The zone's plants come from the database, which can take a moment on a busy machine.
        waitFor(hasText("Grevillea Robyn Gordon"))
        compose.onNodeWithText("Grevillea Robyn Gordon").performClick()
        waitFor(hasText("Low-phosphorus native food only"))
        shot("plant_grevillea_full")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        // Back on the Plot's list, which takes a moment to come back on a busy machine.
        waitFor(hasText("Back Citrus Patch"))
        compose.onNodeWithText("Back Citrus Patch").performClick()
        waitFor(hasText("Mixed thirst"))
        shot("zone_citrus_mixed_thirst")
        compose.onNodeWithText("Mum's lemon").performClick()
        waitFor(hasText("Citrus food"))
        shot("plant_lemon_full")
    }

    @Test
    fun addAPlantByName() {
        seed()
        show()
        compose.addFromMenu("Add a plant by name")
        waitFor(hasSetTextAction())
        compose.onNode(hasSetTextAction()).performTextInput("bottle")
        waitFor(hasText("Weeping bottlebrush"))
        shot("add_plant_search")
        compose.onNodeWithText("Weeping bottlebrush").performClick()
        compose.onNodeWithText("Front Native Strip").performClick()
        shot("add_plant_sheet")
        compose.onNodeWithText("Add to garden").performClick()
        waitFor(hasText("In Front Native Strip"))
        shot("add_plant_done")
    }

    @Test
    fun addAPlantToTwoZones() {
        seed()
        show()
        compose.addFromMenu("Add a plant by name")
        waitFor(hasSetTextAction())
        compose.onNode(hasSetTextAction()).performTextInput("bottle")
        waitFor(hasText("Weeping bottlebrush"))
        compose.onNodeWithText("Weeping bottlebrush").performClick()
        compose.onNodeWithText("Front Native Strip").performClick()
        compose.onNodeWithText("Veg Patch").performClick()
        // With two zones chosen, Pollenplot asks what they hold.
        waitFor(hasText("In these zones there is"))
        shot("add_plant_two_zones")
        // One plant in both zones, counted zone by zone: two in the front strip and three in the veg patch.
        compose.onNodeWithText("In each zone").performScrollTo().performClick()
        compose.onAllNodesWithContentDescription("One more")[0].performClick()
        compose.onAllNodesWithContentDescription("One more")[1].performClick()
        compose.onAllNodesWithContentDescription("One more")[1].performClick()
        shot("add_plant_counts")
        compose.onNodeWithText("Add to garden").performScrollTo().performClick()
        compose.waitUntil(WAIT_MS) { runBlocking { app.container.garden.plantList.first() }.any { it.speciesName == "Melaleuca viminalis" } }
        val added = runBlocking { app.container.garden.plantList.first() }.single { it.speciesName == "Melaleuca viminalis" }
        assertEquals(5, added.quantity)
        assertEquals(listOf(2, 3), added.zoneIds.map { added.counts[it] })
        waitFor(hasText("In Front Native Strip (2), Veg Patch (3)", substring = true))
    }

    @Test
    fun searchTheGarden() {
        seed()
        show()
        compose.pressWhenShown(hasContentDescription("Search your garden"))
        // On a busy machine the search screen can take a moment to open.
        waitFor(hasSetTextAction())
        compose.onNode(hasSetTextAction()).performTextInput("lemon")
        waitFor(hasText("Mum's lemon"))
        compose.onNodeWithText("Plants you could add").assertExists()
        shot("search_lemon")
        compose.onNodeWithText("Mum's lemon").performClick()
        // The plant's page opens with its harvests and pruning above its facts.
        waitFor(hasText("Citrus × limon"))
        compose.scrollTo(hasText("Citrus food"))
    }

    @Test
    fun editTheLayout() {
        val ids = seed()
        show()
        waitFor(hasContentDescription("Hive Corner", substring = true))
        compose.fromMore("Edit layout")
        compose.onNodeWithContentDescription("Hive Corner", substring = true).performClick()
        shot("plot_edit_selected")
        compose.onNodeWithContentDescription("Move and resize").performClick()
        compose.onAllNodesWithText("Wider")[0].performClick()
        compose.onNodeWithContentDescription("Move down").performClick()
        waitFor(hasText("Width: 4.5 m"))
        shot("plot_adjust_sheet")
        // While the sheet is open its Done is the only one, and it closes the sheet and leaves the layout being edited.
        assertEquals(1, compose.onAllNodesWithText("Done").fetchSemanticsNodes().size)
        compose.onNodeWithText("Done").performClick()
        val rect = runBlocking { app.container.garden.zone(ids.hive)!!.rect }
        assertEquals(450, rect.widthCm)
        assertEquals(500, rect.yCm)
        // What the sheet changed is said once, and Undo puts the zone back as it was.
        waitFor(hasText("Changed the size of Hive Corner."))
        compose.onNodeWithText("Undo").performClick()
        compose.waitUntil(WAIT_MS) { runBlocking { app.container.garden.zone(ids.hive)!!.rect }.let { it.widthCm == 400 && it.yCm == 450 } }
        // The sheet's second Wider grows the zone from its left side, and the right edge stays.
        compose.onNodeWithContentDescription("Move and resize").performClick()
        compose.onAllNodesWithText("Wider")[1].performClick()
        compose.waitUntil(WAIT_MS) { runBlocking { app.container.garden.zone(ids.hive)!!.rect }.let { it.xCm == 750 && it.widthCm == 450 } }
        compose.onNodeWithText("Done").performClick()

        // The canvas has no edge: a zone at the origin moves past it, with the screen reader's action as with a drag.
        compose.onNodeWithContentDescription("Front Native Strip", substring = true).performClick()
        val front = compose.onNodeWithContentDescription("Front Native Strip", substring = true).fetchSemanticsNode()
        compose.runOnIdle { front.config[SemanticsActions.CustomActions].single { it.label == "Move left" }.action() }
        compose.waitUntil(WAIT_MS) { runBlocking { app.container.garden.zone(ids.front)!!.rect.xCm } == -50 }
        compose.onNodeWithContentDescription("Show the whole plot").performClick()
        shot("plot_edit_past_origin")
    }

    @Test
    fun structuresAndTheLookOfThePlot() {
        seed()
        val (house, pool) = runBlocking {
            val garden = app.container.garden
            val house = garden.addStructure("House", StructureKind.HOUSE)
            val pool = garden.addStructure("Pool", StructureKind.POOL)
            garden.adjustStructure(house.id) { ZoneRect(1350, 0, 800, 600) }
            garden.adjustStructure(pool.id) { ZoneRect(1350, 750, 600, 350) }
            house to pool
        }
        assertEquals("A pool starts as an oval", StructureShape.OVAL, pool.shape)
        show()
        waitFor(hasContentDescription("House, House, 8 m by 6 m"))
        shot("plot_structures")

        // The ground takes a colour, and the grid is hidden while the layout is only looked at.
        compose.fromMore("Plot appearance")
        compose.pressWhenShown(hasText("Grass"))
        compose.onNodeWithText("Show grid lines").performClick()
        compose.onNodeWithText("Done").performClick()
        compose.waitUntil(WAIT_MS) { runBlocking { app.container.settingsStore.current() }.let { it.plotBackground == PlotBackground.GRASS && !it.gridInView } }
        shot("plot_grass_no_grid")

        // Editing shows the grid again; a structure is chosen and resized with the same buttons as a zone.
        compose.fromMore("Edit layout")
        compose.onNodeWithContentDescription("House, House, 8 m by 6 m").performClick()
        shot("plot_structure_selected")
        compose.onNodeWithContentDescription("Move and resize").performClick()
        compose.onAllNodesWithText("Wider")[0].performClick()
        waitFor(hasText("Width: 8.5 m"))
        compose.onNodeWithText("Done").performClick()
        assertEquals(850, runBlocking { app.container.garden.structureList.first() }.first { it.id == house.id }.rect.widthCm)
        waitFor(hasText("Changed the size of House."))
        compose.onNodeWithText("Undo").performClick()
        compose.waitUntil(WAIT_MS) { runBlocking { app.container.garden.structureList.first() }.first { it.id == house.id }.rect.widthCm == 800 }
    }

    @Test
    fun zonesAndStructuresResizeFromAnyCornerAndDragAgain() {
        val ids = seed()
        val house = runBlocking {
            val garden = app.container.garden
            val house = garden.addStructure("House", StructureKind.HOUSE)
            garden.adjustStructure(house.id) { ZoneRect(1350, 0, 800, 600) }
            house
        }
        show()
        waitFor(hasContentDescription("Hive Corner", substring = true))
        compose.fromMore("Edit layout")
        compose.onNodeWithContentDescription("Hive Corner", substring = true).performClick()
        waitFor(hasContentDescription("Resize from the top left corner"))
        assertEquals("A chosen zone has a handle on each corner", 4, compose.onAllNodes(hasContentDescription("Resize from", substring = true)).fetchSemanticsNodes().size)
        shot("plot_four_handles")

        // Dragging the top left handle up and left grows the zone that way, and its right and bottom edges stay.
        val before = runBlocking { app.container.garden.zone(ids.hive)!!.rect }
        compose.onNodeWithContentDescription("Resize from the top left corner").performTouchInput { swipe(center, center - Offset(120f, 120f), 400) }
        compose.waitUntil(WAIT_MS) { runBlocking { app.container.garden.zone(ids.hive)!!.rect } != before }
        val after = runBlocking { app.container.garden.zone(ids.hive)!!.rect }
        assertTrue("It grew to the left and up", after.xCm < before.xCm && after.yCm < before.yCm)
        assertEquals(before.rightCm, after.rightCm)
        assertEquals(before.bottomCm, after.bottomCm)

        // A structure is dragged, and dragged again: the second drag moves it too.
        fun houseRect() = runBlocking { app.container.garden.structureList.first() }.first { it.id == house.id }.rect
        val description = hasContentDescription("House, House", substring = true)
        compose.onNode(description).performTouchInput { swipe(center, center + Offset(200f, 0f), 400) }
        compose.waitUntil(WAIT_MS) { houseRect().xCm > 1350 }
        val once = houseRect()
        compose.onNode(description).performTouchInput { swipe(center, center + Offset(0f, 200f), 400) }
        compose.waitUntil(WAIT_MS) { houseRect().yCm > once.yCm }
        assertEquals("The second drag kept the first one's move", once.xCm, houseRect().xCm)
        shot("plot_structure_dragged")
    }

    @Test
    fun zonesShowFloweringSunAndNorth() {
        val ids = seed()
        runBlocking {
            // The lemon was seen in flower today, so its zone counts it as in flower now.
            app.container.garden.recordFlowering(ids.lemon, java.time.LocalDate.now())
            app.container.settingsStore.update { it.copy(plotNorth = NorthSide.RIGHT) }
        }
        show()
        waitFor(hasContentDescription("Back Citrus Patch", substring = true))
        shot("plot_flowering")
        // The sun and shade layer is turned on from the Layers button on the plot, as on a map.
        compose.pressWhenShown(hasContentDescription("Map layers"))
        compose.pressWhenShown(hasText("Show sun and shade"))
        compose.pressWhenShown(hasText("Done"))
        compose.waitUntil(WAIT_MS) { runBlocking { app.container.settingsStore.settings.first().sunOverlay } }
        val citrus = compose.onNode(hasContentDescription("Back Citrus Patch", substring = true)).fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString()
        assertTrue("The zone says how many are in flower: $citrus", "in flower" in citrus)
        assertTrue("And its sun, in words: $citrus", "Full sun" in citrus)
        waitFor(hasText("Sun and shade"))
        waitFor(hasContentDescription("North arrow. North is towards the right of the plot."))
        shot("plot_flowering_sun")
    }

    @Test
    fun zonesShowFloweringSunAndNorthInTheDark() {
        val ids = seed()
        runBlocking {
            app.container.garden.recordFlowering(ids.lemon, java.time.LocalDate.now())
            app.container.settingsStore.update { it.copy(sunOverlay = true, plotNorth = NorthSide.RIGHT) }
        }
        show(Settings(gardenState = AuState.QLD, theme = ThemeChoice.DARK))
        waitFor(hasText("Sun and shade"))
        shot("plot_flowering_sun_dark")
    }

    @Test
    fun structuresInTheDarkOnSoil() {
        seed()
        runBlocking {
            val garden = app.container.garden
            val house = garden.addStructure("House", StructureKind.HOUSE)
            val tank = garden.addStructure("Tank", StructureKind.WATER_TANK)
            garden.adjustStructure(house.id) { ZoneRect(1350, 0, 800, 600) }
            garden.adjustStructure(tank.id) { ZoneRect(1350, 750, 250, 250) }
            app.container.settingsStore.update { it.copy(plotBackground = PlotBackground.SOIL) }
        }
        show(Settings(gardenState = AuState.QLD, theme = ThemeChoice.DARK))
        waitFor(hasContentDescription("Tank, Water tank, 2.5 m by 2.5 m"))
        shot("plot_structures_dark_soil")
        // The list names each structure, for anyone who does not use the canvas.
        compose.onNodeWithContentDescription("Show as a list").performClick()
        compose.scrollTo(hasText("Structures"))
        compose.scrollTo(hasText("Water tank · 2.5 m by 2.5 m"))
    }

    @Test
    fun addAZoneFromTheMenu() {
        seed()
        show()
        // Adding a zone no longer needs the layout to be edited first. Its dialog holds a text field, which does not
        // settle under Robolectric, so the test stops at the menu.
        compose.pressWhenShown(hasContentDescription("Open the add menu"))
        waitFor(hasText("Add a zone"))
        shot("plot_add_menu_zone")
    }

    @Test
    fun aPlantOpensFromAZonesPreview() {
        seed()
        show()
        waitFor(hasContentDescription("Hive Corner", substring = true))
        compose.onNodeWithContentDescription("Hive Corner", substring = true).performSemanticsAction(SemanticsActions.OnLongClick)
        waitFor(hasText("Open zone"))
        compose.onNodeWithText("Rosemary").performClick()
        waitFor(hasText("Salvia rosmarinus"))
    }

    @Test
    fun theAssistantAndTheGuidesAreInTheMoreMenu() {
        seed()
        show()
        compose.openMore(hasText("Ask your garden"))
        // The review queue and the weeds are there even with nothing waiting.
        compose.onNodeWithText("Review").assertExists()
        compose.onNodeWithText("Weeds").assertExists()
        shot("plot_more_menu")
        compose.pressWhenShown(hasText("Guides"))
        waitFor(hasText("Honey bees"))
        shot("guides_index")
    }

    @Test
    fun holdAZoneToPreviewIt() {
        seed()
        show()
        waitFor(hasContentDescription("Hive Corner", substring = true))
        val hive = compose.onNodeWithContentDescription("Hive Corner", substring = true)
        hive.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(1_000)
        waitFor(hasContentDescription("Close the preview"))
        compose.onNodeWithText("Lemon-scented tea tree", substring = true).assertIsDisplayed()
        shot("plot_zone_preview")
        hive.performTouchInput { up() }
        assertEquals("Holding a zone does not open it", 0, compose.onAllNodesWithText("Pollinator hotspot").fetchSemanticsNodes().size)
        // The preview stays once the finger lifts, and the zone opens from it.
        compose.onNodeWithContentDescription("Close the preview").assertExists()
        compose.onNodeWithText("Open zone").performClick()
        waitFor(hasText("Pollinator hotspot"))
        compose.onNodeWithContentDescription("Back").performClick()
        waitFor(hasContentDescription("Hive Corner", substring = true))
        assertEquals(0, compose.onAllNodes(hasContentDescription("Close the preview")).fetchSemanticsNodes().size)

        // A screen reader opens the preview with the zone's action, and it stays until it is closed.
        hive.performSemanticsAction(SemanticsActions.OnLongClick)
        waitFor(hasContentDescription("Close the preview"))
        compose.onNodeWithContentDescription("Close the preview").performClick()
        compose.waitUntil(WAIT_MS) { compose.onAllNodes(hasContentDescription("Close the preview")).fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun zonePreviewInTheDarkWithLargeText() {
        seed()
        show(Settings(theme = ThemeChoice.DARK, gardenState = AuState.QLD), fontScale = 2f)
        waitFor(hasContentDescription("Hive Corner", substring = true))
        compose.onNodeWithContentDescription("Hive Corner", substring = true).performSemanticsAction(SemanticsActions.OnLongClick)
        waitFor(hasContentDescription("Close the preview"))
        shot("plot_zone_preview_dark_large")
    }

    private fun seed(): DemoGarden.Ids = runBlocking {
        app.container.knowledge.await()
        // Read here, so a guide page opens from what is kept and not from a read on another thread.
        app.container.guides.readAll()
        app.container.settingsStore.update { it.copy(gardenState = AuState.QLD) }
        DemoGarden.seed(app.container.garden)
    }

    private fun show(settings: Settings = Settings(gardenState = AuState.QLD), fontScale: Float = 1f) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                PollenplotTheme(settings) {
                    Surface { AppRoot(app.container) }
                }
            }
        }
    }

    private fun waitFor(matcher: androidx.compose.ui.test.SemanticsMatcher) {
        compose.waitUntil(WAIT_MS) { compose.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        val dir = File(System.getProperty("screenshots.dir") ?: "build/screenshots").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
