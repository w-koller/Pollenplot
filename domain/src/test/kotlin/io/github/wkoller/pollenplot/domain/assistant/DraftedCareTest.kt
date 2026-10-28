package io.github.wkoller.pollenplot.domain.assistant

import io.github.wkoller.pollenplot.domain.plants.Soil
import io.github.wkoller.pollenplot.domain.plants.Sun
import io.github.wkoller.pollenplot.domain.plants.Toxicity
import io.github.wkoller.pollenplot.domain.plants.WaterNeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Month

class DraftedCareTest {
    @Test
    fun `a draft is read from the answer, around whatever the model wrapped it in`() {
        val answer = """
            Here is the care:
            ```json
            {"sun": ["full sun", "Part shade"], "water": "low", "drought": 3, "frost": 1, "soils": ["sand", "loam"],
             "flowering": ["aug", "sep", "October"], "height_m": [1, 2.5], "width_m": [1, 2], "native_to_australia": true,
             "toxic_to_pets": "none", "note": "Prune lightly after flowering."}
            ```
        """.trimIndent()
        val draft = DraftedCare.parse(answer)!!
        assertEquals(listOf(Sun.FULL_SUN, Sun.PART_SHADE), draft.sun)
        assertEquals(WaterNeed.LOW, draft.water)
        assertEquals(3, draft.drought)
        assertEquals(listOf(Soil.SAND, Soil.LOAM), draft.soils)
        assertEquals(listOf(8, 9, 10), draft.flowering)
        assertEquals(true, Month.OCTOBER in draft.floweringMonths)
        assertEquals(listOf(1_000, 2_500), draft.heightMm)
        assertEquals(Toxicity.NONE, draft.toxicToPets)
        assertEquals("It keeps through storage", draft, DraftedCare.fromJson(draft.toJson()))
    }

    @Test
    fun `values outside the allowed ones are left out`() {
        val draft = DraftedCare.parse("""{"sun": ["dappled"], "water": "lots", "drought": 7, "height_m": [3, 1], "width_m": [0, 500], "note": "Water well."}""")!!
        assertEquals(emptyList<Sun>(), draft.sun)
        assertNull(draft.water)
        assertNull("A score runs from 0 to 3", draft.drought)
        assertEquals("Smallest first", emptyList<Int>(), draft.heightMm)
        assertEquals("No plant is 500 m wide", emptyList<Int>(), draft.widthMm)
        assertEquals("Water well.", draft.note)
    }

    @Test
    fun `an answer with nothing usable is no draft`() {
        assertNull(DraftedCare.parse("I don't know this plant."))
        assertNull(DraftedCare.parse("""{"colour": "green"}"""))
        assertNull(DraftedCare.parse("{not json}"))
    }
}
