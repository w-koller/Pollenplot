package io.github.wkoller.pollenplot.domain.id

import io.github.wkoller.pollenplot.domain.season.MonthSet
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Month

class FlowerCallTest {
    private val spring = MonthSet.range(Month.SEPTEMBER, Month.NOVEMBER)

    @Test
    fun `bioCAP's yes counts most, even out of season`() {
        assertEquals(FlowerCall(true, FlowerReason.MODEL), FlowerCall.decide(model = true, months = spring, month = Month.MARCH, colour = false))
    }

    @Test
    fun `a plant out of season is not in flower whatever its colours`() {
        assertEquals(FlowerCall(false, FlowerReason.OUT_OF_SEASON), FlowerCall.decide(model = null, months = spring, month = Month.MARCH, colour = true))
        assertEquals(FlowerCall(false, FlowerReason.OUT_OF_SEASON), FlowerCall.decide(model = false, months = spring, month = Month.MARCH, colour = true))
    }

    @Test
    fun `bioCAP's no counts over colours`() {
        assertEquals(FlowerCall(false, FlowerReason.MODEL), FlowerCall.decide(model = false, months = spring, month = Month.OCTOBER, colour = true))
    }

    @Test
    fun `colours decide when nothing else does`() {
        assertEquals(FlowerCall(true, FlowerReason.COLOUR), FlowerCall.decide(model = null, months = null, month = Month.MARCH, colour = true))
        assertEquals(FlowerCall(true, FlowerReason.COLOUR), FlowerCall.decide(model = null, months = spring, month = Month.OCTOBER, colour = true))
    }

    @Test
    fun `with no sign of flowers the plant is not in flower`() {
        assertEquals(FlowerCall(false, FlowerReason.NO_SIGN), FlowerCall.decide(model = null, months = null, month = Month.OCTOBER, colour = false))
        assertEquals(FlowerCall(false, FlowerReason.NO_SIGN), FlowerCall.decide(model = null, months = MonthSet.NONE, month = Month.OCTOBER, colour = false))
    }

    @Test
    fun `bioCAP answers only outside the band it was unsure in`() {
        assertEquals(true, FlowerCall.model(8_000, yesBp = 7_000, noBp = 3_000))
        assertEquals(null, FlowerCall.model(5_000, yesBp = 7_000, noBp = 3_000))
        assertEquals(false, FlowerCall.model(2_000, yesBp = 7_000, noBp = 3_000))
    }
}
