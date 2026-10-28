package io.github.wkoller.pollenplot.domain.assistant

import io.github.wkoller.pollenplot.domain.plants.AuState

/** Who to call about a sick, injured or orphaned wild animal, and the official page that says so. */
data class RescueContact(val name: String, val phone: String?, val source: String)

/**
 * The wildlife rescue service for each state and territory, checked against each one's official page on 28 October
 * 2026. The Northern Territory lists local groups town by town, so its page is given instead of a number.
 */
object WildlifeRescue {
    fun forState(state: AuState?): RescueContact? = when (state) {
        AuState.QLD -> RescueContact("RSPCA Queensland", "1300 264 625", "https://www.qld.gov.au/environment/plants-animals/wildlife/injured-and-orphaned-wildlife/injured")
        AuState.NSW -> RescueContact("WIRES", "1300 094 737", "https://www.wires.org.au/report-a-rescue")
        AuState.VIC -> RescueContact("Wildlife Victoria", "03 8400 7300", "https://wildlifevictoria.org.au/about/emergency-response-service")
        AuState.SA -> RescueContact("Fauna Rescue SA", "08 8289 0896", "https://environment.sa.gov.au/goodliving/posts/2021/11/wildlife-rescue-contacts-south-australia")
        AuState.WA -> RescueContact("the Wildcare Helpline", "08 9474 9055", "https://www.dbca.wa.gov.au/wildcare")
        AuState.TAS -> RescueContact("Bonorong Wildlife Rescue", "0447 264 625", "https://nre.tas.gov.au/wildlife-management/injured-and-orphaned-wildlife")
        AuState.NT -> RescueContact("a local wildlife rescue group", null, "https://nt.gov.au/environment/animals/report-injured-wildlife-or-rescue")
        AuState.ACT -> RescueContact("ACT Wildlife", "0432 300 033", "https://rspca-act.org.au/our-services-services/wildlife")
        null -> null
    }
}
