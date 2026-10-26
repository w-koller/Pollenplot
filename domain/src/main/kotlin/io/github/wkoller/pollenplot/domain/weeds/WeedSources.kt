package io.github.wkoller.pollenplot.domain.weeds

import io.github.wkoller.pollenplot.domain.plants.AuState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Which weed sources apply where, from assets/weeds/sources.json. A council that publishes open weed data can be
 * added there without changing code, as long as its data comes in a [CouncilSource.format] the app reads.
 */
@Serializable
data class WeedSources(
    val version: Int,
    val councils: List<CouncilSource>,
    val states: List<StateSource>,
    val national: NationalSources,
    val nearby: NearbySource,
    val weedsAustralia: WeedsAustraliaLinks,
) {
    /** The council source for a council area, as the Atlas of Living Australia names it, such as "BRISBANE CITY". */
    fun council(lga: String?, state: AuState?): CouncilSource? =
        councils.firstOrNull { it.lga.equals(lga, ignoreCase = true) && (state == null || it.state == state) }

    fun state(state: AuState?): StateSource? = states.firstOrNull { it.state == state }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): WeedSources = json.decodeFromString(serializer(), text)

        /** The only council data format so far: Brisbane City Council's open data export. */
        const val BRISBANE_OPEN_DATA = "brisbane-open-data"
    }
}

/** A council's own weed list. */
@Serializable
data class CouncilSource(
    val id: String,
    val name: String,
    /** The council area as the Atlas of Living Australia names it. */
    val lga: String,
    val state: AuState,
    val format: String,
    /** Where the list is downloaded from. */
    val data: String,
    /** The council's weed website, for profiles and searching. */
    val site: String,
    val licence: String,
    val phone: String? = null,
)

/** A state or territory's weed agency: where its rules are explained and how to report a weed to it. */
@Serializable
data class StateSource(
    val state: AuState,
    val agency: String,
    val url: String,
    val phone: String? = null,
    val email: String? = null,
)

/** The national weed lists on the Atlas of Living Australia, and the services the app uses there. */
@Serializable
data class NationalSources(
    /** The Weeds of National Significance list. */
    val wons: String,
    /** The National Alert List for Environmental Weeds. */
    val alert: String,
    /** The list items service, with {uid} for the list. */
    val list: String,
    /** The name matching service, which takes many names in one request. */
    val names: String,
    /** The council area and state at a point, with {lat} and {lon}. */
    val area: String,
    /** Recent records of a species with photos, with {name}, for comparing with the user's photo. */
    val photos: String,
)

/** Plant records near the garden: the search, with {lat}, {lon}, {radius} and {since}, its radius and how far back it looks. */
@Serializable
data class NearbySource(val url: String, val radiusKm: Int, val years: Int)

/** Weeds Australia has no data service, so the app links to its pages: a search with {name}, and its identification key. */
@Serializable
data class WeedsAustraliaLinks(val search: String, val identify: String)
