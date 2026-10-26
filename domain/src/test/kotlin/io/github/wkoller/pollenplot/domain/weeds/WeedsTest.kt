package io.github.wkoller.pollenplot.domain.weeds

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeedsTest {
    private fun resource(name: String) = javaClass.getResource("/weeds/$name")!!.readText()

    /** Rows from Brisbane City Council's open data, chosen for each form their fields take. */
    private val brisbane = BrisbaneWeeds.parse(resource("brisbane_sample.json"))

    private val wons = AlaLists.parse(resource("ala_list_dr823.json"))
    private val alert = AlaLists.parse(resource("ala_list_dr35447.json"))
    private val national = NationalWeeds(wons, alert)

    private fun named(common: String) = brisbane.first { it.common == common }

    private fun listed(vararg species: String) = ListedWeed(WeedNames(species.toSet()), null)

    @Test
    fun `botanical name fields become the plants they cover`() {
        assertEquals(setOf("dyschoriste nagchana", "dyschoriste depressa"), named("Dyschoriste").names.species)
        assertEquals(setOf("ruellia tweediana", "ruellia simplex", "ruellia caerulea"), named("Ruellia").names.species)
        assertEquals(setOf("chrysanthemoides monilifera"), named("Bitou bush").names.species)
        assertEquals(setOf("crotalaria lanceolata"), named("Lance-leaved rattlepod").names.species)
    }

    @Test
    fun `lists write names in several ways`() {
        assertEquals(WeedNames(setOf("salvinia molesta")), WeedNames.parse("Salvinia x molesta"))
        assertEquals(WeedNames(setOf("rubus fruticosus")), WeedNames.parse("Rubus fruticosus aggregate"))
        assertEquals(WeedNames(setOf("ruellia tweediana", "ruellia simplex")), WeedNames.parse("Ruellia tweediana, R. simplex"))
        assertEquals(WeedNames(emptySet(), mapOf("opuntia" to emptySet())), WeedNames.parse("Opuntia"))
        assertEquals(WeedNames(emptySet(), mapOf("salix" to setOf("salix babylonica"))), WeedNames.parse(" Salix spp. except S.babylonica"))
        assertTrue(WeedNames.parse("https://id.biodiversity.org.au/node/apni/2906266").isEmpty)
        assertEquals("salvinia molesta", WeedNames.binomial("Salvinia × molesta"))
    }

    @Test
    fun `a genus listed with exceptions covers the rest of the genus`() {
        val pricklyPear = brisbane.first { "opuntia" in it.names.genera }
        assertTrue(pricklyPear.names.matches(listOf("Opuntia dillenii")))
        assertTrue("Witchweed is listed as a whole genus", named("Witchweed").names.matches(listOf("Striga asiatica")))
        assertFalse("Listed as an exception", pricklyPear.names.matches(listOf("Opuntia stricta")))
        assertFalse("Hyphenated exceptions count too", pricklyPear.names.matches(listOf("Opuntia ficus-indica")))
        assertFalse(pricklyPear.names.matches(listOf("Opuntia")))
        val willows = WeedNames.parse("Salix spp. except S.babylonica")
        assertTrue(willows.matches(listOf("Salix fragilis")))
        assertFalse(willows.matches(listOf("Salix babylonica")))
    }

    @Test
    fun `joined names keep what both leave out of a genus`() {
        val joined = WeedNames.parse("Opuntia spp. other than O. stricta and O. elata") + WeedNames.parse("Opuntia spp. other than O. stricta")
        assertEquals(mapOf("opuntia" to setOf("opuntia stricta")), joined.genera)
        assertEquals(setOf("wedelia trilobata", "sphagneticola trilobata"), WeedNames(setOf("wedelia trilobata")).withAccepted(mapOf("wedelia trilobata" to "sphagneticola trilobata")).species)
    }

    @Test
    fun `categories, report duties and lists are read`() {
        assertEquals(listOf(3), named("Lantana").restricted)
        assertFalse(named("Lantana").reportRequired)
        assertEquals(listOf(1), named("Witchweed").restricted)
        assertTrue(named("Witchweed").reportRequired)
        assertTrue(named("Bitter weed").prohibited)
        assertTrue(named("Dyschoriste").councilPest)
        assertEquals("Fireman's cap", named("Cockspur coral tree").otherCommon.last())
        assertEquals(listOf("Cut stump method", "Foliar spray", "Basal bark treatment", "Stem injection"), named("Cockspur coral tree").control)
        assertEquals(listOf("Simple"), named("Dyschoriste").leafForm)
        assertEquals(listOf("Opposite"), named("Dyschoriste").leafArrangement)
        assertEquals("Acanthaceae", named("Dyschoriste").family)
        assertEquals(
            "https://weeds.brisbane.qld.gov.au/weeds/cockspur-coral-tree",
            BrisbaneWeeds.profileUrl("https://weeds.brisbane.qld.gov.au/", named("Cockspur coral tree")),
        )
        // The site leaves apostrophes out of its page names.
        assertEquals(
            "https://weeds.brisbane.qld.gov.au/weeds/cats-claw-creeper",
            BrisbaneWeeds.profileUrl("https://weeds.brisbane.qld.gov.au", named("Lantana").copy(common = "Cat’s claw creeper")),
        )
    }

    @Test
    fun `the whole export reads the same as a page of records`() {
        val rows = Json.parseToJsonElement(resource("brisbane_sample.json")).jsonObject["results"].toString()
        assertEquals(brisbane, BrisbaneWeeds.parse(rows))
    }

    @Test
    fun `national lists name species, genera and genera with exceptions`() {
        assertEquals(41, wons.size)
        val lantana = wons.first { it.common == "Lantana" }
        assertTrue(lantana.names.matches(listOf("Lantana camara")))
        // Listed by a name the Atlas has since changed, with the new name alongside.
        assertTrue(wons.any { it.names.matches(listOf("Eichhornia crassipes")) && it.names.matches(listOf("Pontederia crassipes")) })
        assertTrue(wons.any { it.names.matches(listOf("Opuntia stricta")) })
        assertTrue(wons.any { it.names.matches(listOf("Salix fragilis")) })
        assertFalse(wons.any { it.names.matches(listOf("Salix babylonica")) })
        // The alert list names each item by an identifier, with the species alongside.
        assertTrue(alert.any { it.common == "Siam Weed" && it.names.matches(listOf("Chromolaena odorata")) })
    }

    @Test
    fun `the most serious source sets the level`() {
        val nearby = NearbyRecords.parse(resource("ala_nearby_sample.json"), radiusKm = 10, sinceYear = 2016)
        val lantana = WeedResolver.resolve(listOf("Lantana camara"), brisbane, national, nearby)
        assertEquals(WeedLevel.RESTRICTED, lantana.level)
        assertTrue(lantana.wons)
        assertEquals(280, lantana.nearbyRecords)
        assertFalse(lantana.mustReport)
        assertEquals("Lantana", lantana.listedCommon)

        // Singapore daisy is found by its older name too.
        assertEquals(WeedLevel.RESTRICTED, WeedResolver.resolve(listOf("Wedelia trilobata", "Sphagneticola trilobata"), brisbane, national).level)
        assertEquals(WeedLevel.PROHIBITED, WeedResolver.resolve(listOf("Helenium amarum"), brisbane, national).level)
        assertTrue(WeedResolver.resolve(listOf("Helenium amarum"), brisbane, national).mustReport)
        assertEquals(WeedLevel.COUNCIL, WeedResolver.resolve(listOf("Dyschoriste depressa"), brisbane, national).level)
        assertEquals(WeedLevel.LISTED, WeedResolver.resolve(listOf("Euphorbia maculata"), brisbane, national).level)
    }

    @Test
    fun `accepted names link a plant to a list that spells it differently`() {
        val council = listOf(named("Lantana").copy(names = WeedNames(setOf("wedelia trilobata"))))
        val accepted = mapOf("sphagneticola trilobata" to "wedelia trilobata")
        assertEquals(WeedLevel.NONE, WeedResolver.resolve(listOf("Sphagneticola trilobata"), council, NationalWeeds()).level)
        assertEquals(WeedLevel.RESTRICTED, WeedResolver.resolve(listOf("Sphagneticola trilobata"), council, NationalWeeds(), accepted = accepted).level)
    }

    @Test
    fun `away from a council list the national lists still apply`() {
        val national = NationalWeeds(wons = listOf(listed("lantana camara")), alert = listOf(listed("chromolaena odorata")))
        assertEquals(WeedLevel.NATIONAL, WeedResolver.resolve(listOf("Lantana camara"), emptyList(), national).level)
        val siamWeed = WeedResolver.resolve(listOf("Chromolaena odorata"), emptyList(), national)
        assertTrue("The states ask to hear about alert list weeds", siamWeed.askedToReport)
        assertFalse(siamWeed.mustReport)
        val grevillea = WeedResolver.resolve(listOf("Grevillea robusta"), emptyList(), national, NearbyRecords(emptyList(), 10, 2016))
        assertEquals(WeedLevel.NONE, grevillea.level)
        assertFalse(grevillea.isWeed)
        assertEquals(0, grevillea.nearbyRecords)
        assertNull(WeedResolver.resolve(listOf("Grevillea robusta"), emptyList(), national).nearbyRecords)
    }

    @Test
    fun `name matching gives the accepted name, or nothing for a name it does not know`() {
        val asked = listOf("wedelia trilobata", "lantana camara", "opuntia stricta", "notaplant whatever", "eichhornia crassipes")
        assertEquals("""[{"scientificName":"wedelia trilobata"},{"scientificName":"lantana camara"}]""", AlaNames.request(asked.take(2)))
        assertEquals(
            mapOf(
                "wedelia trilobata" to "sphagneticola trilobata",
                "lantana camara" to "lantana camara",
                "opuntia stricta" to "opuntia stricta",
                "notaplant whatever" to "",
                "eichhornia crassipes" to "pontederia crassipes",
            ),
            AlaNames.parse(asked, resource("ala_names.json")),
        )
    }

    @Test
    fun `nearby records leave out records without a species`() {
        val nearby = NearbyRecords.parse(resource("ala_nearby_sample.json"), radiusKm = 10, sinceYear = 2016)
        assertEquals(11, nearby.species.size)
        assertEquals(561, nearby.of(listOf("eucalyptus tereticornis")))
        assertEquals(0, nearby.of(listOf("grevillea banksii")))
    }

    @Test
    fun `weeds near the garden come most serious first, then most recorded`() {
        val nearby = NearbyRecords.parse(resource("ala_nearby_sample.json"), radiusKm = 10, sinceYear = 2016)
        val weeds = WeedsNearby.of(brisbane, national, nearby)
        val names = weeds.map { it.name }
        assertTrue("Natives are not weeds here", "Eucalyptus tereticornis" !in names && "Grevillea robusta" !in names)
        assertTrue("Lantana camara" in names && "Sphagneticola trilobata" in names)
        assertTrue(weeds.zipWithNext().all { (a, b) -> a.status.level <= b.status.level })
        val restricted = weeds.filter { it.status.level == WeedLevel.RESTRICTED }
        assertEquals(restricted.sortedByDescending { it.records }, restricted)
    }
}
