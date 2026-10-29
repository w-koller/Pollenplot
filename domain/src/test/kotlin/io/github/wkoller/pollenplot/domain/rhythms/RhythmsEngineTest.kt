package io.github.wkoller.pollenplot.domain.rhythms

import io.github.wkoller.pollenplot.domain.plants.AuState
import io.github.wkoller.pollenplot.domain.plants.CareProfile
import io.github.wkoller.pollenplot.domain.plants.Forage
import io.github.wkoller.pollenplot.domain.plants.GrowthForm
import io.github.wkoller.pollenplot.domain.plants.Species
import io.github.wkoller.pollenplot.domain.plants.Sun
import io.github.wkoller.pollenplot.domain.season.MonthSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Month

class RhythmsEngineTest {
    private fun months(vararg months: Month) = months.fold(MonthSet.NONE) { set, month -> set + MonthSet.of(month) }

    private fun forage(honey: Int = 0, stingless: Int = 0, resin: Int = 0, solitary: Int = 0, buzz: Boolean = false) =
        Forage(honey, honey, stingless, stingless, resin, solitary, solitary, buzz)

    private fun plant(title: String, form: GrowthForm, quantity: Int, flowering: MonthSet, forage: Forage, seen: MonthSet = MonthSet.NONE) =
        RhythmPlant(title, title, null, form, quantity, flowering, MonthSet.NONE, seen, forage)

    private val lemon = plant("Lemon", GrowthForm.TREE, 2, months(Month.AUGUST, Month.SEPTEMBER, Month.OCTOBER), forage(honey = 3, stingless = 2, solitary = 1))
    private val salvia = plant("Salvia", GrowthForm.SHRUB, 5, months(Month.OCTOBER, Month.NOVEMBER, Month.DECEMBER), forage(honey = 2, stingless = 1, solitary = 3, buzz = false))
    private val tomato = plant("Tomato", GrowthForm.VEGETABLE, 1, months(Month.DECEMBER, Month.JANUARY), forage(solitary = 2, buzz = true))

    @Test
    fun `honey bees count mass flowering, native bees count variety`() {
        val lanes = RhythmsEngine.lanes(listOf(lemon, salvia, tomato)).associateBy { it.group }
        // October: the lemon (3 × 2 for a tree × 2 plants) and five salvias, counted as three (2 × 2 × 3).
        assertEquals(12 + 12, lanes.getValue(BeeGroup.HONEY).points[Month.OCTOBER.ordinal])
        assertEquals(ForageLevel.PLENTY, lanes.getValue(BeeGroup.HONEY).level(Month.OCTOBER))
        // Five salvias count once for solitary bees; the tomato's buzz pollination adds one.
        assertEquals(1 + 3, lanes.getValue(BeeGroup.SOLITARY).points[Month.OCTOBER.ordinal])
        assertEquals(3 + 2 + 1, lanes.getValue(BeeGroup.SOLITARY).points[Month.DECEMBER.ordinal])
        assertEquals(ForageLevel.LITTLE, lanes.getValue(BeeGroup.STINGLESS).level(Month.NOVEMBER))
    }

    @Test
    fun `a month with nothing is a gap, unless nothing feeds the group at all`() {
        val lanes = RhythmsEngine.lanes(listOf(lemon)).associateBy { it.group }
        assertTrue(Month.JUNE in lanes.getValue(BeeGroup.HONEY).gaps)
        assertTrue(Month.SEPTEMBER !in lanes.getValue(BeeGroup.HONEY).gaps)
        val bare = RhythmsEngine.lanes(listOf(plant("Lawn", GrowthForm.LAWN, 1, months(Month.MAY), forage()))).associateBy { it.group }
        assertTrue(bare.getValue(BeeGroup.HONEY).gaps.isEmpty())
        assertTrue(!bare.getValue(BeeGroup.HONEY).fed)
    }

    @Test
    fun `flowers seen in photos count as flowering`() {
        val seen = lemon.copy(seenFlowering = months(Month.JULY))
        assertEquals(ForageLevel.PLENTY, RhythmsEngine.lanes(listOf(seen)).first { it.group == BeeGroup.HONEY }.level(Month.JULY))
    }

    @Test
    fun `suggestions fill a gap with local natives first and never a weed`() {
        fun species(name: String, common: String, native: Set<AuState>) =
            Species(name, name.substringBefore(' '), "Family", listOf(common), GrowthForm.SHRUB, native, false, emptyList(), CareProfile())
        val winter = months(Month.JUNE, Month.JULY)
        val candidates = listOf(
            CandidatePlant(species("Grevillea robusta", "Silky oak", setOf(AuState.QLD)), winter, forage(honey = 3), setOf(Sun.FULL_SUN), weed = false),
            CandidatePlant(species("Lantana camara", "Lantana", emptySet()), winter, forage(honey = 3), setOf(Sun.FULL_SUN), weed = true),
            CandidatePlant(species("Banksia ericifolia", "Heath banksia", setOf(AuState.NSW)), winter, forage(honey = 3), setOf(Sun.FULL_SUN), weed = false),
            CandidatePlant(species("Grevillea lowflower", "Low one", setOf(AuState.QLD)), winter, forage(honey = 1), setOf(Sun.FULL_SUN), weed = false),
            CandidatePlant(species("Correa reflexa", "Native fuchsia", setOf(AuState.QLD)), months(Month.MARCH), forage(honey = 3), null, weed = false),
        )
        val picked = RhythmsEngine.suggestions(BeeGroup.HONEY, listOf(Month.JUNE, Month.JULY), candidates, emptySet(), AuState.QLD, listOf("Front Native Strip" to Sun.FULL_SUN))
        assertEquals(listOf("Grevillea robusta", "Banksia ericifolia"), picked.map { it.species.name })
        assertEquals("Front Native Strip", picked.first().zoneName)
        assertEquals(listOf(Month.JUNE, Month.JULY), picked.first().fills)
    }
}
