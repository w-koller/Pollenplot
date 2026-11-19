package io.github.wkoller.pollenplot.domain.plants

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaxaTest {
    private val taxa = Taxa.parse(
        """
        # name	genus	family	common	aliases
        Acacia longifolia	Acacia	Fabaceae	Sydney golden wattle	
        Melaleuca citrina	Melaleuca	Myrtaceae	Crimson bottlebrush	Callistemon citrinus
        Broken line
        Callistemon citrinus	Callistemon	Myrtaceae		
        """.trimIndent(),
    )

    @Test
    fun aPlantIsFoundByItsNameIgnoringCase() {
        assertEquals("Fabaceae", taxa.of("acacia LONGIFOLIA")?.family)
        assertEquals("Sydney golden wattle", taxa.of("Acacia longifolia")?.common)
    }

    @Test
    fun aPlantsOwnNameComesBeforeAnotherPlantsAlias() {
        assertEquals("Callistemon citrinus", taxa.of("Callistemon citrinus")?.name)
        assertNull("No common name is kept as none", taxa.of("Callistemon citrinus")?.common)
    }

    @Test
    fun linesWithoutAGenusAndFamilyAreLeftOut() {
        assertNull(taxa.of("Broken line"))
        assertEquals(3, taxa.size)
    }

    @Test
    fun theGroupsFactsFillAPlantWithoutARecord() {
        val kb = KnowledgeBase(
            version = 1,
            species = emptyList(),
            genera = listOf(GroupProfile("Acacia", "Fabaceae", GrowthForm.SHRUB, CareProfile(sun = setOf(Sun.FULL_SUN)))),
            families = listOf(GroupProfile("Fabaceae", null, null, CareProfile(water = WaterNeed.LOW))),
            companions = emptyList(),
            taxa = taxa,
        )
        val care = CareResolver.resolveGroup(kb, "Acacia", "Fabaceae")!!
        assertEquals(setOf(Sun.FULL_SUN), care.care.sun)
        assertEquals(FactLevel.GENUS, care.levelOf(CareField.SUN))
        assertEquals(FactLevel.FAMILY, care.levelOf(CareField.WATER))
        assertNull("Neither group is known", CareResolver.resolveGroup(kb, "Nogenus", "Nofamily"))
    }
}
