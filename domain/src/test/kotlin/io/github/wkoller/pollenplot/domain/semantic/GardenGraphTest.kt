package io.github.wkoller.pollenplot.domain.semantic

import io.github.wkoller.pollenplot.domain.bees.honey.BroodPattern
import io.github.wkoller.pollenplot.domain.bees.honey.HiveSign
import io.github.wkoller.pollenplot.domain.bees.honey.HiveStatus
import io.github.wkoller.pollenplot.domain.bees.honey.HiveType
import io.github.wkoller.pollenplot.domain.bees.honey.HoneyHive
import io.github.wkoller.pollenplot.domain.bees.honey.Inspection
import io.github.wkoller.pollenplot.domain.bees.honey.QueenCells
import io.github.wkoller.pollenplot.domain.garden.GardenPlant
import io.github.wkoller.pollenplot.domain.garden.Zone
import io.github.wkoller.pollenplot.domain.garden.ZoneKind
import io.github.wkoller.pollenplot.domain.garden.ZoneRect
import io.github.wkoller.pollenplot.domain.plants.CareProfile
import io.github.wkoller.pollenplot.domain.plants.EcoRole
import io.github.wkoller.pollenplot.domain.plants.GrowthForm
import io.github.wkoller.pollenplot.domain.plants.Species
import io.github.wkoller.pollenplot.domain.plants.Sun
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GardenGraphTest {
    private val zone = Zone("11111111-1111-1111-1111-111111111111", "Back Citrus Patch", ZoneKind.ORCHARD, ZoneRect(0, 0, 400, 250), sun = Sun.FULL_SUN, mulched = true)
    private val lemon = Species("Citrus × limon", "Citrus", "Rutaceae", listOf("Lemon"), GrowthForm.TREE, emptySet(), false, emptyList(), CareProfile())
    private val plant = GardenPlant(
        "22222222-2222-2222-2222-222222222222", "Citrus × limon", "Mum's lemon", zone.id, quantity = 1, plantedOn = LocalDate.of(2024, 9, 1),
    )

    private fun node(doc: JsonObject, id: String): JsonObject =
        doc["@graph"]!!.jsonArray.map { it.jsonObject }.single { it["@id"]!!.jsonPrimitive.content == id }

    @Test
    fun `a zone is a schema-org place with its area in square metres`() {
        val doc = JsonLd.document(GardenGraph.zone(zone))
        val z = node(doc, "urn:uuid:${zone.id}")
        assertEquals(listOf("schema:Place", "pp:Zone"), (z["@type"] as JsonArray).map { it.jsonPrimitive.content })
        assertEquals("Back Citrus Patch", z["schema:name"]!!.jsonPrimitive.content)
        assertEquals("10", z["pp:areaSquareMetres"]!!.jsonObject["@value"]!!.jsonPrimitive.content)
        assertEquals("xsd:decimal", z["pp:areaSquareMetres"]!!.jsonObject["@type"]!!.jsonPrimitive.content)
        assertEquals("full_sun", z["pp:sun"]!!.jsonPrimitive.content)
        assertEquals("http://rs.tdwg.org/dwc/terms/", doc["@context"]!!.jsonObject["dwc"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a garden plant is a Darwin Core occurrence placed in its zone`() {
        val doc = JsonLd.document(GardenGraph.plant(plant, lemon, listOf(EcoRole.POLLINATOR_MAGNET, EcoRole.EDIBLE)))
        val p = node(doc, "urn:uuid:${plant.id}")
        assertTrue((p["@type"] as JsonArray).any { it.jsonPrimitive.content == "dwc:Occurrence" })
        assertEquals("Citrus × limon", p["dwc:scientificName"]!!.jsonPrimitive.content)
        assertEquals("Lemon", p["dwc:vernacularName"]!!.jsonPrimitive.content)
        assertEquals("Mum's lemon", p["schema:name"]!!.jsonPrimitive.content)
        assertEquals("urn:uuid:${zone.id}", p["schema:containedInPlace"]!!.jsonObject["@id"]!!.jsonPrimitive.content)
        assertEquals("2024-09-01", p["pp:plantedOn"]!!.jsonObject["@value"]!!.jsonPrimitive.content)
        assertEquals(listOf("pp:PollinatorMagnet", "pp:Edible"), p["pp:ecoRole"]!!.jsonArray.map { it.jsonObject["@id"]!!.jsonPrimitive.content })
        assertEquals("present", p["dwc:occurrenceStatus"]!!.jsonPrimitive.content)
    }

    @Test
    fun `blank values are left out and links join two records`() {
        val bare = GardenPlant("33333333-3333-3333-3333-333333333333", null, "Unknown shrub", null)
        val p = node(JsonLd.document(GardenGraph.plant(bare, null, emptyList())), "urn:uuid:${bare.id}")
        assertTrue(p.keys.none { it == "dwc:scientificName" || it == "schema:containedInPlace" || it == "dwc:occurrenceRemarks" })

        val link = GardenGraph.link(Link(plant.id, Vocab.PP + "companionOf", "44444444-4444-4444-4444-444444444444", Provenance.USER))
        assertEquals("urn:uuid:44444444-4444-4444-4444-444444444444", (link.single().obj as Term.Iri).value)
    }

    @Test
    fun `search text uses plain words`() {
        val doc = SearchText.plant(plant, lemon, "Back Citrus Patch", listOf(EcoRole.POLLINATOR_MAGNET))
        assertEquals("Mum's lemon", doc.title)
        assertTrue(doc.body, doc.body.contains("Lemon"))
        assertTrue(doc.body, doc.body.contains("in Back Citrus Patch"))
        assertTrue(doc.body, doc.body.contains("pollinator magnet"))
        assertEquals("orchard. full sun. mulched", SearchText.zone(zone).body)
    }

    @Test
    fun `an inspection is an event about its hive, with each sign as a term`() {
        val inspection = Inspection(
            "44444444-4444-4444-4444-444444444444", "33333333-3333-3333-3333-333333333333", LocalDate.of(2026, 10, 20), queenSeen = true,
            eggsSeen = false, brood = BroodPattern.PATCHY, temperament = null, framesOfBees = 6, framesOfStores = null, queenCells = QueenCells.SWARM,
            signs = setOf(HiveSign.SMALL_HIVE_BEETLE, HiveSign.CHALKBROOD), actions = "Added a trap",
        )
        val doc = JsonLd.document(HoneyGraph.inspection(inspection))
        val node = node(doc, "urn:uuid:44444444-4444-4444-4444-444444444444")
        assertEquals(listOf("schema:Event", "pp:HiveInspection"), node["@type"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals("urn:uuid:33333333-3333-3333-3333-333333333333", node["schema:about"]!!.jsonObject["@id"]!!.jsonPrimitive.content)
        assertEquals("2026-10-20", node["schema:startDate"]!!.jsonObject["@value"]!!.jsonPrimitive.content)
        assertEquals(listOf("pp:Chalkbrood", "pp:SmallHiveBeetle"), node["pp:sign"]!!.jsonArray.map { it.jsonObject["@id"]!!.jsonPrimitive.content })
        assertTrue("A value not noted is left out", "pp:temperament" !in node && "pp:framesOfStores" !in node)
    }

    @Test
    fun `a hive is found by its kind, its place and its queen`() {
        val hive = HoneyHive("33333333-3333-3333-3333-333333333333", "Hive 1", HiveType.LANGSTROTH_8, null, zone.id, 1, 1, 2025, HiveStatus.ACTIVE, null)
        val doc = SearchText.hive(hive, "Hive Corner", "Home")
        assertEquals(SearchKind.HIVE, doc.kind)
        assertEquals("Hive 1", doc.title)
        assertEquals("honey bee hive. langstroth 8. in Hive Corner. apiary Home. queen from 2025, marked blue. active", doc.body)
    }
}
