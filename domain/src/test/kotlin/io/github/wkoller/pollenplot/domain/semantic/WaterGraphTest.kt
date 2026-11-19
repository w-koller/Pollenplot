package io.github.wkoller.pollenplot.domain.semantic

import io.github.wkoller.pollenplot.domain.irrigation.WaterMethod
import io.github.wkoller.pollenplot.domain.irrigation.WateringLine
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class WaterGraphTest {
    private val front = "11111111-1111-1111-1111-111111111111"
    private val veg = "33333333-3333-3333-3333-333333333333"
    private val line = WateringLine("44444444-4444-4444-4444-444444444444", "Front timer", WaterMethod.DRIPPERS, setOf(veg, front), 12_000, poolingMinutes = 20)

    private fun node(doc: JsonObject, id: String): JsonObject =
        doc["@graph"]!!.jsonArray.map { it.jsonObject }.single { it["@id"]!!.jsonPrimitive.content == id }

    private fun ids(value: Any?): List<String> = when (value) {
        is kotlinx.serialization.json.JsonArray -> value.map { it.jsonObject["@id"]!!.jsonPrimitive.content }
        is JsonObject -> listOf(value["@id"]!!.jsonPrimitive.content)
        else -> emptyList()
    }

    @Test
    fun `a watering line names its method, flow and zones`() {
        val l = node(JsonLd.document(WaterGraph.line(line)), "urn:uuid:${line.id}")
        assertEquals("pp:WateringLine", l["@type"]!!.jsonPrimitive.content)
        assertEquals("Front timer", l["schema:name"]!!.jsonPrimitive.content)
        assertEquals("drippers", l["pp:waterMethod"]!!.jsonPrimitive.content)
        assertEquals("12000", l["pp:flowMlPerHour"]!!.jsonObject["@value"]!!.jsonPrimitive.content)
        assertEquals("20", l["pp:poolingMinutes"]!!.jsonObject["@value"]!!.jsonPrimitive.content)
        assertEquals(listOf("urn:uuid:$front", "urn:uuid:$veg"), ids(l["pp:zone"]))
        assertNull("Blank notes are left out", l["schema:description"])
    }

    @Test
    fun `a watering is an event on a day for its line or zone`() {
        val id = "55555555-5555-5555-5555-555555555555"
        val w = node(JsonLd.document(WaterGraph.watering(id, line.id, null, LocalDate.of(2026, 10, 28), "Deep soak")), "urn:uuid:$id")
        assertEquals("pp:Watering", w["@type"]!!.jsonPrimitive.content)
        assertEquals("2026-10-28", w["dwc:eventDate"]!!.jsonObject["@value"]!!.jsonPrimitive.content)
        assertEquals(listOf("urn:uuid:${line.id}"), ids(w["pp:line"]))
        assertNull(w["pp:zone"])
        assertEquals("Deep soak", w["schema:description"]!!.jsonPrimitive.content)

        val byHand = node(JsonLd.document(WaterGraph.watering(id, null, veg, LocalDate.of(2026, 10, 28), "")), "urn:uuid:$id")
        assertEquals(listOf("urn:uuid:$veg"), ids(byHand["pp:zone"]))
    }
}
