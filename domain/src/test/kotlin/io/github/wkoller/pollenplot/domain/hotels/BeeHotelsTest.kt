package io.github.wkoller.pollenplot.domain.hotels

import io.github.wkoller.pollenplot.domain.season.Hemisphere
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BeeHotelsTest {
    @Test
    fun `a seal says who is likely inside`() {
        assertEquals(listOf(Occupant.LEAFCUTTER_BEE), SealGuide.likely(Seal.LEAF))
        assertEquals(listOf(Occupant.MASKED_BEE, Occupant.WASP_MIMIC_BEE), SealGuide.likely(Seal.MEMBRANE))
        assertFalse("A clay plug is a potter wasp's, not a bee's", SealGuide.likely(Seal.CLAY).any(SealGuide::isBee))
        assertTrue(SealGuide.likely(Seal.OTHER).isEmpty())
    }

    @Test
    fun `a hotel's state is its latest look and clean-out`() {
        val looks = listOf(
            HotelObservation("a", "h1", LocalDate.of(2026, 1, 10), mapOf(Seal.LEAF to 3), busy = 2, emerged = null, pests = emptySet()),
            HotelObservation("b", "h1", LocalDate.of(2026, 3, 1), mapOf(Seal.LEAF to 4, Seal.RESIN to 2), busy = 1, emerged = null, pests = setOf(HotelPest.ANTS)),
            HotelObservation("c", "h2", LocalDate.of(2026, 4, 1), emptyMap(), busy = null, emerged = null, pests = emptySet()),
        )
        val upkeep = listOf(
            HotelUpkeep("u", "h1", LocalDate.of(2025, 11, 20), UpkeepKind.NEW_LINERS),
            HotelUpkeep("v", "h1", LocalDate.of(2026, 2, 1), UpkeepKind.MOVED),
        )
        val state = HotelState.of("h1", looks, upkeep)
        assertEquals("b", state.latest?.id)
        assertEquals(6, state.latest?.sealedHoles)
        assertEquals("Moving it is not cleaning it", LocalDate.of(2025, 11, 20), state.lastCleaned)
    }

    @Test
    fun `in spring, a hotel not cleaned since the last spring is worth a look`() {
        val spring = LocalDate.of(2026, 10, 28)
        assertTrue(HotelCare.cleanInSpring(HotelState(null, null), spring, Hemisphere.SOUTHERN))
        assertTrue(HotelCare.cleanInSpring(HotelState(null, LocalDate.of(2025, 11, 1)), spring, Hemisphere.SOUTHERN))
        assertFalse("Cleaned this spring", HotelCare.cleanInSpring(HotelState(null, LocalDate.of(2026, 9, 20)), spring, Hemisphere.SOUTHERN))
        assertFalse("Not in winter", HotelCare.cleanInSpring(HotelState(null, null), LocalDate.of(2026, 7, 1), Hemisphere.SOUTHERN))
    }
}
