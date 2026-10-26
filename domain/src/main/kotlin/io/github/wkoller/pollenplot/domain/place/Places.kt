package io.github.wkoller.pollenplot.domain.place

import io.github.wkoller.pollenplot.domain.plants.AuState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.roundToLong

/**
 * The garden's location, rounded to two decimal places of a degree, so it marks an area about 1 km across rather than
 * a house. Only this rounded point is ever sent anywhere. [council] is the council area as the Atlas of Living
 * Australia names it, such as "BRISBANE CITY", once it is known.
 */
data class GardenPlace(val latE7: Int, val lonE7: Int, val name: String? = null, val council: String? = null) {
    /** The latitude as written in web requests, such as "-27.50". */
    val latText: String get() = Places.text(latE7)

    val lonText: String get() = Places.text(lonE7)

    companion object {
        fun at(latitude: Double, longitude: Double, name: String? = null, council: String? = null) =
            GardenPlace(Places.round(latitude), Places.round(longitude), name, council)
    }
}

/** A place a search found, such as a suburb, with its district, state and postcodes. */
data class PlaceMatch(
    val name: String,
    val district: String?,
    val state: AuState?,
    val postcodes: List<String>,
    val latitude: Double,
    val longitude: Double,
)

/** The council area and state a point is in, as far as they are known. */
data class Area(val council: String?, val state: AuState?)

object Places {
    private val json = Json { ignoreUnknownKeys = true }

    /** Degrees rounded to two decimal places, in ten-millionths of a degree. */
    fun round(degrees: Double): Int = ((degrees * 100).roundToLong() * 100_000).toInt()

    /** A rounded coordinate with two decimal places, such as "-27.50". */
    fun text(e7: Int): String = BigDecimal(e7).movePointLeft(7).setScale(2, RoundingMode.HALF_UP).toPlainString()

    /** Whether a point is in Australia, roughly: the mainland, Tasmania and the islands near them. */
    fun inAustralia(latitude: Double, longitude: Double): Boolean = latitude in -44.5..-9.0 && longitude in 112.0..160.0

    /** A council area's name, written in capitals by the Atlas of Living Australia, in title case: "Brisbane City". */
    fun councilName(lga: String): String = lga.trim().lowercase().split(Regex("\\s+")).joinToString(" ") { word ->
        word.split('-').joinToString("-") { part -> part.replaceFirstChar(Char::uppercaseChar) }
    }

    /** A state or territory from its full name, such as "Queensland". */
    fun state(name: String?): AuState? = when (name?.trim()?.lowercase()) {
        "queensland" -> AuState.QLD
        "new south wales" -> AuState.NSW
        "victoria" -> AuState.VIC
        "tasmania" -> AuState.TAS
        "south australia" -> AuState.SA
        "western australia" -> AuState.WA
        "northern territory" -> AuState.NT
        "australian capital territory" -> AuState.ACT
        else -> null
    }

    /**
     * Places from Open-Meteo's geocoding search, keeping towns, suburbs and districts. Islands, parks and other features
     * are left out unless they have a postcode.
     */
    fun parseSearch(page: String): List<PlaceMatch> {
        val results = json.parseToJsonElement(page).jsonObject["results"]?.jsonArray.orEmpty()
        return results.map { it.jsonObject }.mapNotNull { row ->
            val postcodes = row["postcodes"]?.jsonArray?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
            val feature = row.text("feature_code").orEmpty()
            if (row.text("country_code") != "AU" || !(feature.startsWith("PPL") || feature.startsWith("ADM") || postcodes.isNotEmpty())) return@mapNotNull null
            PlaceMatch(
                name = row.text("name") ?: return@mapNotNull null,
                district = row.text("admin2"),
                state = state(row.text("admin1")),
                postcodes = postcodes,
                latitude = row["latitude"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null,
                longitude = row["longitude"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null,
            )
        }
    }

    /** The council area and state from the Atlas of Living Australia's intersect service for its LGA and state layers. */
    fun parseArea(page: String): Area {
        val fields = json.parseToJsonElement(page).jsonArray.map { it.jsonObject }
        fun value(field: String) = fields.firstOrNull { it.text("field") == field }?.text("value")?.takeIf { it.isNotBlank() }
        return Area(council = value(COUNCIL_LAYER), state = state(value(STATE_LAYER)))
    }

    /** Local Government Areas (PSMA 2018) on the Atlas of Living Australia. Council sources are matched to its names. */
    const val COUNCIL_LAYER = "cl10923"

    /** Australian States and Territories on the Atlas of Living Australia. */
    const val STATE_LAYER = "cl22"

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
}
