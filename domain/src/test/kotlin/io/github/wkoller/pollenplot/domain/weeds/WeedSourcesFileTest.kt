package io.github.wkoller.pollenplot.domain.weeds

import io.github.wkoller.pollenplot.domain.plants.AuState
import io.github.wkoller.pollenplot.domain.wildlife.LifeGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Checks the weed sources file the app ships. */
class WeedSourcesFileTest {
    private val sources = WeedSources.parse(File(System.getProperty("weeds.dir"), "sources.json").readText())

    @Test
    fun `every state and territory has a weed agency to link to`() {
        assertEquals(AuState.entries.toSet(), sources.states.map { it.state }.toSet())
        sources.states.forEach { state ->
            assertTrue(state.url, state.url.startsWith("https://"))
            assertTrue("${state.state} needs a way to report a weed", state.phone != null || state.email != null || state.state == AuState.SA)
        }
    }

    @Test
    fun `councils are found by the Atlas of Living Australia's area names`() {
        val brisbane = sources.council("BRISBANE CITY", AuState.QLD)
        assertNotNull(brisbane)
        assertEquals(WeedSources.BRISBANE_OPEN_DATA, brisbane!!.format)
        assertEquals(brisbane, sources.council("Brisbane City", null))
        assertNull(sources.council("BRISBANE CITY", AuState.NSW))
        assertNull(sources.council("MORETON BAY", AuState.QLD))
        sources.councils.forEach { assertTrue(it.data.startsWith("https://") && it.site.startsWith("https://")) }
    }

    @Test
    fun `web addresses have the parts the app fills in`() {
        assertTrue("{uid}" in sources.national.list)
        assertTrue("{lat}" in sources.national.area && "{lon}" in sources.national.area)
        listOf("{lat}", "{lon}", "{radius}", "{since}").forEach { assertTrue(it, it in sources.nearby.url) }
        assertTrue("{name}" in sources.weedsAustralia.search)
        assertTrue("{name}" in sources.national.photos)
        listOf(sources.national.list, sources.national.names, sources.national.area, sources.national.photos, sources.nearby.url, sources.weedsAustralia.identify)
            .forEach { assertTrue(it, it.startsWith("https://")) }
    }

    @Test
    fun `every wildlife group is looked up near the garden`() {
        val animals = checkNotNull(sources.nearby.animals)
        assertTrue(animals.startsWith("https://"))
        listOf("{taxon}", "{lat}", "{lon}", "{radius}", "{since}").forEach { assertTrue(it, it in animals) }
        val groups = sources.nearby.groups.keys.map { checkNotNull(LifeGroup.of(it)) { "$it is not a group" } }
        assertEquals(LifeGroup.entries.filter { it.isWildlife && it != LifeGroup.OTHER }.toSet(), groups.toSet())
    }
}
