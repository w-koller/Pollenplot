package io.github.wkoller.pollenplot.domain.assistant

import io.github.wkoller.pollenplot.domain.plants.AuState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GardenPromptsTest {
    private val records = listOf(
        CitedRecord("p1", RecordKind.PLANT, "Mum's lemon", "Plant: Mum's lemon, Citrus × limon, in Back Citrus Patch"),
        CitedRecord("z1", RecordKind.ZONE, "Hive Corner", "Zone: Hive Corner, part shade"),
        CitedRecord("s1", RecordKind.SIGHTING, "Noisy miner", "Sighting: Noisy miner feeding on Rosemary"),
    )

    @Test
    fun `garden records are numbered and the answer's citations point back at them`() {
        val prompt = GardenPrompts.garden("4 zones and 16 plants", records, "What feeds on the rosemary?")
        assertTrue(prompt, prompt.startsWith("The garden: 4 zones and 16 plants"))
        assertTrue(prompt, "[3] Sighting: Noisy miner feeding on Rosemary" in prompt)
        val cited = GardenPrompts.citations("Noisy miners feed on it [3], near the hive corner [2][3]. See also [9].", records)
        assertEquals("In the order first cited, each once, and nothing for a number with no record", listOf("s1", "z1"), cited.map { it.id })
    }

    @Test
    fun `a question is searched by its own words`() {
        assertEquals(listOf("feeds", "rosemary"), GardenPrompts.searchWords("What feeds on the rosemary in my garden?"))
        assertEquals(listOf("frogs", "pond"), GardenPrompts.searchWords("Have I seen frogs near the pond?"))
    }

    @Test
    fun `the second opinion is a number, or none of them`() {
        assertEquals(1, GardenPrompts.choice("2", 5))
        assertEquals(0, GardenPrompts.choice("It is 1. Callistemon", 5))
        assertNull("0 means none of them", GardenPrompts.choice("0", 5))
        assertNull(GardenPrompts.choice("I am not sure", 5))
        assertNull(GardenPrompts.choice("7", 5))
    }

    @Test
    fun `every state has a rescue service with its official page`() {
        AuState.entries.forEach { state ->
            val contact = WildlifeRescue.forState(state)!!
            assertTrue(contact.source, contact.source.startsWith("https://"))
            assertTrue("Only the Northern Territory lists groups by town instead of a number", contact.phone != null || state == AuState.NT)
        }
        assertNull(WildlifeRescue.forState(null))
    }

    @Test
    fun `the rules keep the model away from doses, eating and weed status`() {
        listOf("label", "safe to eat", "weed").forEach { assertTrue(it, it in GardenPrompts.SYSTEM) }
    }
}
