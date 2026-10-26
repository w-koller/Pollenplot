package io.github.wkoller.pollenplot.place

import io.github.wkoller.pollenplot.domain.place.Area
import io.github.wkoller.pollenplot.domain.place.GardenPlace
import io.github.wkoller.pollenplot.domain.place.PlaceMatch
import io.github.wkoller.pollenplot.domain.place.Places
import io.github.wkoller.pollenplot.domain.weeds.WeedSources
import io.github.wkoller.pollenplot.web.Web
import java.net.URLEncoder

/**
 * Finds the garden's place: suburbs and postcodes through Open-Meteo's geocoding (GeoNames data), and the council area
 * and state at a point through the Atlas of Living Australia. Only the search words or the rounded point are sent.
 */
class PlaceFinder(private val web: Web, private val sources: () -> WeedSources) {
    /** Places in Australia that match a suburb, town or postcode. */
    suspend fun search(query: String): List<PlaceMatch> {
        val name = URLEncoder.encode(query.trim(), "UTF-8")
        return Places.parseSearch(web.get("$SEARCH?name=$name&count=10&language=en&format=json&countryCode=AU"))
    }

    /** The council area and state the garden is in. */
    suspend fun area(place: GardenPlace): Area =
        Places.parseArea(web.get(sources().national.area.replace("{lat}", place.latText).replace("{lon}", place.lonText)))

    companion object {
        const val SEARCH = "https://geocoding-api.open-meteo.com/v1/search"
    }
}
