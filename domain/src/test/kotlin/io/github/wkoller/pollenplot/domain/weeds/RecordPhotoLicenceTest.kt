package io.github.wkoller.pollenplot.domain.weeds

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordPhotoLicenceTest {
    private fun creditOnly(licence: String) = RecordPhoto("t", "l", null, null, licence).creditOnly

    @Test
    fun onlyLicencesThatAskForACreditAreKeptForTheLibrary() {
        val kept = listOf("CC0", "CC-BY 4.0 (Int)", "CC BY 4.0", "CC-BY", "Public Domain", "CC0 1.0")
        val left = listOf("CC-BY-NC 4.0 (Int)", "CC BY-NC-SA 4.0", "CC-BY-ND", "CC-BY-SA 4.0", "All rights reserved")
        assertEquals(kept, kept.filter(::creditOnly))
        assertEquals(emptyList<String>(), left.filter(::creditOnly))
    }
}
