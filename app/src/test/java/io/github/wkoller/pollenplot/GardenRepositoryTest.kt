package io.github.wkoller.pollenplot

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.wkoller.pollenplot.data.DetectionDraft
import io.github.wkoller.pollenplot.data.DetectionRecord
import io.github.wkoller.pollenplot.data.GardenRepository
import io.github.wkoller.pollenplot.data.GardenViews
import io.github.wkoller.pollenplot.data.db.PollenplotDatabase
import io.github.wkoller.pollenplot.data.kb.KnowledgeRepository
import io.github.wkoller.pollenplot.data.photos.StoredPhoto
import io.github.wkoller.pollenplot.domain.garden.PlotLayout
import io.github.wkoller.pollenplot.domain.garden.ZoneBadge
import io.github.wkoller.pollenplot.domain.garden.ZoneKind
import io.github.wkoller.pollenplot.domain.garden.ZoneRect
import io.github.wkoller.pollenplot.domain.id.BoxBp
import io.github.wkoller.pollenplot.domain.id.Candidate
import io.github.wkoller.pollenplot.domain.id.DetectionStatus
import io.github.wkoller.pollenplot.domain.id.IdSource
import io.github.wkoller.pollenplot.domain.id.PhotoOrigin
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
import java.time.LocalDate
import java.time.ZoneOffset

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
        // One settings store per process: the app's own, as DataStore refuses a second for the same file.
        val views = GardenViews(garden, knowledge, ApplicationProvider.getApplicationContext<PollenplotApp>().container.settingsStore)
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

    private val aiy = setOf(IdSource.AIY_PLANTS)

    /** A photo of a bottlebrush on the left and rosemary on the right, taken on 3 October 2026. */
    private suspend fun recordBedPhoto(): List<DetectionRecord> {
        val photo = StoredPhoto("photo-1", "photos/photo-1.jpg", 2_048, 1_536)
        val takenAt = LocalDate.of(2026, 10, 3).atTime(9, 30).toInstant(ZoneOffset.UTC)
        garden.recordPhoto(
            photo,
            PhotoOrigin.CAMERA,
            takenAt,
            listOf(
                DetectionDraft(BoxBp(6_000, 0, 10_000, 6_000), "thumbs/rosemary.webp", false, listOf(Candidate("Rosmarinus officinalis", "Salvia rosmarinus", 6_450, aiy))),
                DetectionDraft(
                    BoxBp(0, 0, 6_000, 6_000),
                    "thumbs/bottlebrush.webp",
                    true,
                    listOf(Candidate("Callistemon citrinus", "Melaleuca citrina", 7_400, aiy), Candidate("Hibiscus syriacus", null, 900, aiy)),
                ),
            ),
        )
        return garden.detectionsInPhoto(photo.id).first()
    }

    @Test
    fun `plants found in a photo wait for review in the order they appear`() = runBlocking {
        val found = recordBedPhoto()
        assertEquals("From the left of the photo to the right", listOf("thumbs/bottlebrush.webp", "thumbs/rosemary.webp"), found.map { it.thumbnail })
        assertTrue(found.all { it.status == DetectionStatus.NEEDS_REVIEW })
        assertEquals(listOf("Callistemon citrinus", "Hibiscus syriacus"), found[0].candidates.map { it.label })
        assertEquals(listOf("Melaleuca citrina", null), found[0].candidates.map { it.speciesName })
        assertEquals(aiy, found[0].candidates[0].sources)
        assertEquals(StoredPhoto("photo-1", "photos/photo-1.jpg", 2_048, 1_536), garden.photo("photo-1"))
    }

    @Test
    fun `a confirmed plant gets the cut-out as its picture and a flowering record`() = runBlocking {
        val zone = garden.addZone("Front Native Strip", ZoneKind.GARDEN_BED)
        val bottlebrush = recordBedPhoto()[0]
        val plant = garden.confirmDetection(bottlebrush.id, "Melaleuca citrina", null, zone.id, flowering = true)

        assertEquals("thumbs/bottlebrush.webp", garden.plant(plant.id)!!.thumbnail)
        assertEquals(zone.id, garden.plant(plant.id)!!.zoneId)
        val stored = garden.detection(bottlebrush.id)!!
        assertEquals(DetectionStatus.CONFIRMED, stored.status)
        assertEquals(plant.id, stored.plantId)
        assertEquals("The day the photo was taken", listOf(LocalDate.of(2026, 10, 3)), garden.floweringOf(plant.id))
        assertTrue("The new plant can be searched for", garden.search("bottlebrush").any { it.recordId == plant.id })
    }

    @Test
    fun `a photo added to a plant already in the garden does not replace its picture`() = runBlocking {
        val found = recordBedPhoto()
        val rosemary = garden.addPlant("Salvia rosmarinus", null, null)
        garden.attachDetection(found[1].id, rosemary.id, flowering = false)
        assertEquals("A plant without a picture takes the cut-out", "thumbs/rosemary.webp", garden.plant(rosemary.id)!!.thumbnail)
        assertEquals(emptyList<LocalDate>(), garden.floweringOf(rosemary.id))
        assertEquals(rosemary.id, garden.detection(found[1].id)!!.plantId)

        garden.attachDetection(found[0].id, rosemary.id, flowering = true)
        assertEquals("A plant keeps the picture it has", "thumbs/rosemary.webp", garden.plant(rosemary.id)!!.thumbnail)
        assertEquals(listOf(LocalDate.of(2026, 10, 3)), garden.floweringOf(rosemary.id))
        assertEquals("No second plant was added", 1, garden.plantList.first().size)
    }

    @Test
    fun `a part of a photo marked as not a plant can be put back in review`() = runBlocking {
        val found = recordBedPhoto()
        garden.rejectDetection(found[0].id)
        assertEquals(DetectionStatus.REJECTED, garden.detection(found[0].id)!!.status)
        garden.reopenDetection(found[0].id)
        assertEquals(DetectionStatus.NEEDS_REVIEW, garden.detection(found[0].id)!!.status)

        val plant = garden.confirmDetection(found[1].id, "Salvia rosmarinus", null, null, flowering = false)
        garden.reopenDetection(found[1].id)
        assertEquals("Only a rejection can be undone this way", DetectionStatus.CONFIRMED, garden.detection(found[1].id)!!.status)
        assertEquals(plant.id, garden.detection(found[1].id)!!.plantId)
    }

    @Test
    fun `meaning is worked out again only for records whose text changed`() = runBlocking {
        val ids = DemoGarden.seed(garden)
        val model = "test-model"
        val first = garden.textsToEmbed(model)
        assertEquals("Every zone and plant needs its meaning at first", 4 + 15, first.size)
        first.forEachIndexed { i, (id, text) -> garden.saveMeaning(id, model, text, unit(i)) }
        assertEquals(emptyList<Pair<String, String>>(), garden.textsToEmbed(model))

        val lemon = garden.plant(ids.lemon)!!
        garden.updatePlant(lemon.copy(notes = "Fruits twice a year"))
        assertEquals(listOf(ids.lemon), garden.textsToEmbed(model).map { it.first })

        garden.deletePlant(ids.grevillea)
        garden.pruneMeanings(model)
        assertTrue(ids.grevillea !in garden.meaningScores(model, unit(0)).keys)
        assertTrue("Another model's vectors are dropped", garden.meaningScores("other-model", unit(0)).isEmpty())
    }

    @Test
    fun `meaning scores are the similarity of each record to the query`() = runBlocking {
        val zone = garden.addZone("Front Native Strip", ZoneKind.GARDEN_BED)
        garden.saveMeaning(zone.id, "m", "text", floatArrayOf(0.6f, 0.8f))
        assertEquals(1f, garden.meaningScores("m", floatArrayOf(0.6f, 0.8f))[zone.id]!!, 1e-6f)
        assertEquals(listOf("Front Native Strip"), garden.searchHits(listOf(zone.id, "missing")).map { it.title })
    }

    @Test
    fun `search finds plural and other forms of a word`() = runBlocking {
        DemoGarden.seed(garden)
        assertTrue("Lantana is a weed risk", garden.search("weeds").any { it.title == "Lantana" })
    }

    /** A vector with 1 in place [i], so each record's vector differs. */
    private fun unit(i: Int) = FloatArray(32).also { it[i % 32] = 1f }
}
