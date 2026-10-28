package io.github.wkoller.pollenplot.domain.garden

import io.github.wkoller.pollenplot.domain.plants.EcoRole
import io.github.wkoller.pollenplot.domain.plants.WaterNeed
import io.github.wkoller.pollenplot.domain.wildlife.LifeGroup
import org.junit.Assert.assertEquals
import org.junit.Test

class ZoneBadgesTest {
    private val citrus = PlantFacts(WaterNeed.HIGH, listOf(EcoRole.POLLINATOR_MAGNET, EcoRole.EDIBLE))
    private val grevillea = PlantFacts(WaterNeed.LOW, listOf(EcoRole.POLLINATOR_MAGNET, EcoRole.BIRD_FEEDER))
    private val lomandra = PlantFacts(WaterNeed.LOW, listOf(EcoRole.FROG_SAFE))
    private val tomato = PlantFacts(WaterNeed.HIGH, listOf(EcoRole.NATIVE_BEE_FAVOURITE))
    private val lantana = PlantFacts(WaterNeed.LOW, listOf(EcoRole.WEED_RISK, EcoRole.POLLINATOR_MAGNET))

    @Test
    fun `natives beside thirsty plants show mixed thirst`() {
        assertEquals(listOf(ZoneBadge.MIXED_THIRST), ZoneBadges.of(listOf(citrus, lomandra)))
        assertEquals(emptyList<ZoneBadge>(), ZoneBadges.of(listOf(grevillea, lomandra)))
        val everlasting = PlantFacts(WaterNeed.VERY_LOW, emptyList())
        val lemon = PlantFacts(WaterNeed.MODERATE, emptyList())
        assertEquals("0.1 beside 0.5 is five times the water", listOf(ZoneBadge.MIXED_THIRST), ZoneBadges.of(listOf(everlasting, lemon)))
        assertEquals("0.25 beside 0.5 is only twice the water", emptyList<ZoneBadge>(), ZoneBadges.of(listOf(lomandra, lemon)))
        assertEquals("Unknown water needs are left out", emptyList<ZoneBadge>(), ZoneBadges.of(listOf(lemon, PlantFacts(null, emptyList()))))
    }

    @Test
    fun `three bee plants make a hotspot`() {
        assertEquals(listOf(ZoneBadge.POLLINATOR_HOTSPOT), ZoneBadges.of(listOf(grevillea, grevillea.copy(), PlantFacts(WaterNeed.LOW, listOf(EcoRole.NATIVE_BEE_FAVOURITE)))))
        assertEquals(emptyList<ZoneBadge>(), ZoneBadges.of(listOf(grevillea, lomandra)))
    }

    @Test
    fun `weeds come first`() {
        assertEquals(
            listOf(ZoneBadge.WEED_FOUND, ZoneBadge.MIXED_THIRST, ZoneBadge.POLLINATOR_HOTSPOT),
            ZoneBadges.of(listOf(lantana, citrus, tomato)),
        )
        assertEquals(emptyList<ZoneBadge>(), ZoneBadges.of(emptyList()))
    }

    @Test
    fun `frogs seen in a zone confirm it suits them`() {
        assertEquals(listOf(ZoneBadge.FROGS_SEEN), ZoneBadges.of(listOf(lomandra), seen = setOf(LifeGroup.FROG, LifeGroup.BIRD)))
        assertEquals("Frog-safe plants alone are not a sighting", emptyList<ZoneBadge>(), ZoneBadges.of(listOf(lomandra), seen = setOf(LifeGroup.BIRD)))
        assertEquals("Problems still come first", listOf(ZoneBadge.WEED_FOUND, ZoneBadge.FROGS_SEEN), ZoneBadges.of(listOf(lantana), seen = setOf(LifeGroup.FROG)))
    }
}
