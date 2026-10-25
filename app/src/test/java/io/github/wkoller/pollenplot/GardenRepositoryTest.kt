package io.github.wkoller.pollenplot

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.wkoller.pollenplot.data.GardenRepository
import io.github.wkoller.pollenplot.data.GardenViews
import io.github.wkoller.pollenplot.data.db.PollenplotDatabase
import io.github.wkoller.pollenplot.data.kb.KnowledgeRepository
import io.github.wkoller.pollenplot.domain.garden.PlotLayout
import io.github.wkoller.pollenplot.domain.garden.ZoneBadge
import io.github.wkoller.pollenplot.domain.garden.ZoneKind
import io.github.wkoller.pollenplot.domain.garden.ZoneRect
import io.github.wkoller.pollenplot.domain.plants.AuState
import io.github.wkoller.pollenplot.domain.plants.EcoRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock

@RunWith(AndroidJUnit4::class)
class GardenRepositoryTest {
    private lateinit var db: PollenplotDatabase
    private lateinit var garden: GardenRepository
    private lateinit var knowledge: KnowledgeRepository
    private var state: AuState? = AuState.QLD

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<PollenplotApp>()
        db = PollenplotDatabase.inMemory(context)
        knowledge = KnowledgeRepository(context, CoroutineScope(Dispatchers.IO))
        garden = GardenRepository(db, Clock.systemUTC(), { knowledge.await() }, { state })
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `new zones are placed in a tidy grid`() = runBlocking {
        val first = garden.addZone("Front Native Strip", ZoneKind.GARDEN_BED)
        val second = garden.addZone("Hive Corner", ZoneKind.HIVE_CORNER)
        assertEquals(ZoneRect(0, 0, PlotLayout.DEFAULT_WIDTH_CM, PlotLayout.DEFAULT_HEIGHT_CM), first.rect)
        assertFalse(first.rect.overlaps(second.rect))
        garden.moveZone(second.id, ZoneRect(1013, 26, 420, 310))
        assertEquals("Moves snap to the 50 cm grid", ZoneRect(1000, 50, 400, 300), garden.zone(second.id)!!.rect)
    }

    @Test
    fun `search finds plants by common name, zone and badge`() = runBlocking {
        DemoGarden.seed(garden)
        val lemons = garden.search("lemon").filter { it.kind == "PLANT" }.map { it.title }
        assertTrue(lemons.toString(), "Mum's lemon" in lemons && "Lemon-scented tea tree" in lemons)
        assertTrue(garden.search("citrus patch").any { it.title == "Back Citrus Patch" })
        val inFront = garden.search("front native").map { it.title }
        assertTrue(inFront.toString(), "Grevillea Robyn Gordon" in inFront)
        assertTrue(garden.search("pollinator magnet").any { it.title == "Rosemary" })
        garden.search("\"lemon* OR (") // Quotes, operators and brackets are stripped, so this must not throw.
        assertTrue(garden.search("lemon\" (").any { it.title == "Mum's lemon" })
        assertTrue(garden.search("   ").isEmpty())
    }

    @Test
    fun `quick adjustments build on each other`() = runBlocking {
        val zone = garden.addZone("Hive Corner", ZoneKind.HIVE_CORNER)
        kotlinx.coroutines.coroutineScope {
            launch { garden.adjustZone(zone.id) { PlotLayout.resizedBySteps(it, 1, 0) } }
            launch { garden.adjustZone(zone.id) { PlotLayout.nudged(it, 0, 1) } }
        }
        val rect = garden.zone(zone.id)!!.rect
        assertEquals(PlotLayout.DEFAULT_WIDTH_CM + PlotLayout.GRID_CM, rect.widthCm)
        assertEquals(PlotLayout.GRID_CM, rect.yCm)
    }

    @Test
    fun `renaming a zone updates its plants in search, and removing it keeps them`() = runBlocking {
        val ids = DemoGarden.seed(garden)
        garden.updateZone(garden.zone(ids.citrus)!!.copy(name = "Orchard Corner"))
        assertTrue(garden.search("orchard corner").any { it.title == "Mum's lemon" })
        garden.deleteZone(ids.citrus)
        assertNull(garden.plant(ids.lemon)!!.zoneId)
        assertFalse(garden.search("orchard corner").any { it.title == "Mum's lemon" })
        assertTrue(garden.search("lemon").any { it.title == "Mum's lemon" })
    }

    @Test
    fun `views give each zone its plants and badges`() = runBlocking {
        val ids = DemoGarden.seed(garden)
        val settings = io.github.wkoller.pollenplot.data.prefs.SettingsStore(ApplicationProvider.getApplicationContext())
        val views = GardenViews(garden, knowledge, settings)
        val zones = views.zones.first { list -> list.isNotEmpty() && list.all { z -> z.plants.all { it.care != null } } }
        val citrus = zones.single { it.zone.id == ids.citrus }
        assertEquals(3, citrus.plants.size)
        assertTrue("Everlastings beside citrus need different watering", ZoneBadge.MIXED_THIRST in citrus.badges)
        val hive = zones.single { it.zone.id == ids.hive }
        assertEquals("Lantana is a weed, and tea tree, rosemary and lavender all feed bees", listOf(ZoneBadge.WEED_FOUND, ZoneBadge.POLLINATOR_HOTSPOT), hive.badges)
        val front = zones.single { it.zone.id == ids.front }
        assertEquals("All five natives need little water", emptyList<ZoneBadge>(), front.badges)
    }

    @Test
    fun `the state decides what counts as a local native`() = runBlocking {
        val ids = DemoGarden.seed(garden)
        val lomandra = garden.plantList.first().single { it.speciesName == "Lomandra longifolia" }
        assertTrue(EcoRole.LOCAL_NATIVE in garden.rolesOf(lomandra))
        state = AuState.WA
        assertFalse(EcoRole.LOCAL_NATIVE in garden.rolesOf(lomandra))
        assertTrue(ids.front.isNotEmpty())
    }

    @Test
    fun `the garden exports as JSON-LD with Darwin Core occurrences`() = runBlocking {
        val ids = DemoGarden.seed(garden)
        val doc = Json.parseToJsonElement(garden.exportJsonLd()).jsonObject
        val nodes = doc["@graph"]!!.jsonArray.map { it.jsonObject }
        val lemon = nodes.single { it["@id"]!!.jsonPrimitive.content == "urn:uuid:${ids.lemon}" }
        assertEquals("Citrus × limon", lemon["dwc:scientificName"]!!.jsonPrimitive.content)
        assertEquals("urn:uuid:${ids.citrus}", lemon["schema:containedInPlace"]!!.jsonObject["@id"]!!.jsonPrimitive.content)
        assertEquals(4 + 15, nodes.size)
    }
}
