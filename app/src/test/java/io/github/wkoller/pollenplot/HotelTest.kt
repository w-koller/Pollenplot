package io.github.wkoller.pollenplot

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import io.github.wkoller.pollenplot.domain.bees.stingless.Facing
import io.github.wkoller.pollenplot.domain.garden.ZoneKind
import io.github.wkoller.pollenplot.domain.hotels.BeeHotel
import io.github.wkoller.pollenplot.domain.hotels.HotelMaterial
import io.github.wkoller.pollenplot.domain.hotels.HotelObservation
import io.github.wkoller.pollenplot.domain.hotels.HotelPest
import io.github.wkoller.pollenplot.domain.hotels.HotelSection
import io.github.wkoller.pollenplot.domain.hotels.HotelUpkeep
import io.github.wkoller.pollenplot.domain.hotels.Seal
import io.github.wkoller.pollenplot.domain.hotels.UpkeepKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Bee hotels, their sections, looks and upkeep, on the fixed spring morning of 28 October 2026. */
@RunWith(AndroidJUnit4::class)
@Config(application = WildlifeTestApp::class)
class HotelTest {
    private val app: WildlifeTestApp get() = ApplicationProvider.getApplicationContext()
    private val hotels get() = app.container.hotels

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
    }

    @Test
    fun `a hotel keeps its sections and records, and they go with it`() = runBlocking {
        val corner = app.container.garden.addZone("Hive Corner", ZoneKind.HIVE_CORNER)
        val hotel = BeeHotel(
            hotels.newRecordId(), "Fence hotel", corner.id, Facing.NE, heightMm = 1_200, installedOn = LocalDate.of(2026, 3, 1),
            sections = listOf(HotelSection(HotelMaterial.DRILLED_TIMBER, 8, 120, 24), HotelSection(HotelMaterial.BAMBOO, null, null, 12)),
        )
        hotels.saveHotel(hotel)
        assertEquals(36, hotels.hotels.first().single().holes)

        // Changing the sections replaces them.
        hotels.saveHotel(hotel.copy(sections = listOf(HotelSection(HotelMaterial.PITHY_STEMS, null, null, 10))))
        assertEquals(listOf(HotelSection(HotelMaterial.PITHY_STEMS, null, null, 10)), hotels.hotels.first().single().sections)

        hotels.saveObservation(HotelObservation(hotels.newRecordId(), hotel.id, LocalDate.of(2026, 10, 20), mapOf(Seal.LEAF to 3, Seal.CLAY to 1), busy = 2, emerged = null, pests = setOf(HotelPest.ANTS)))
        val look = hotels.observations.first().single()
        assertEquals(mapOf(Seal.LEAF to 3, Seal.CLAY to 1), look.sealed)
        assertEquals(setOf(HotelPest.ANTS), look.pests)

        val view = app.container.beeViews.hotels.first().single()
        assertEquals("Hive Corner", view.zoneName)
        assertTrue("Spring, and never cleaned", view.cleanThisSpring)
        hotels.saveUpkeep(HotelUpkeep(hotels.newRecordId(), hotel.id, LocalDate.of(2026, 10, 28), UpkeepKind.NEW_LINERS))
        assertFalse(app.container.beeViews.hotels.first().single().cleanThisSpring)

        assertTrue(app.container.garden.search("hotel").any { it.recordId == hotel.id && it.kind == "HOTEL" })
        val export = app.container.exportJsonLd()
        assertTrue(export.contains("pp:BeeHotel"))
        assertTrue(export.contains("pp:sealedWithLeaf"))

        hotels.deleteHotel(hotel.id)
        assertTrue(hotels.observations.first().isEmpty())
        assertTrue(hotels.upkeep.first().isEmpty())
        assertTrue(app.container.garden.search("hotel").none { it.recordId == hotel.id })
    }
}
