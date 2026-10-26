package io.github.wkoller.pollenplot

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import io.github.wkoller.pollenplot.place.LocationSource
import io.github.wkoller.pollenplot.place.PlaceFinder
import io.github.wkoller.pollenplot.web.Web
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.IOException

/** The app with recorded web answers and a phone that is in Taringa, Brisbane, for tests of the Weed identifier. */
class WeedTestApp : PollenplotApp() {
    val web = RecordedWeb()
    val location = FixedLocation(-27.4906, 152.9786)

    override fun createContainer(): AppContainer = AppContainer(this, web = web, location = location)
}

/** Stands in for the phone's location service. */
class FixedLocation(latitude: Double, longitude: Double) : LocationSource {
    var point: Pair<Double, Double>? = latitude to longitude

    override suspend fun current() = point
}

/**
 * Answers the app's web requests with responses recorded from the real services in October 2026, so tests never
 * reach them: Open-Meteo's place search for "4068", the Atlas of Living Australia's council area, lists and records
 * near Taringa, and part of Brisbane City Council's weed list. Any other request fails, as it would offline.
 */
class RecordedWeb : Web {
    /** Every address asked for, in order. */
    val asked = mutableListOf<String>()

    /** Fails every request, as a phone without a connection does. */
    var offline = false

    override suspend fun get(url: String): String {
        asked += url
        if (offline) throw IOException("No connection")
        val file = when {
            url.startsWith(PlaceFinder.SEARCH) -> if ("name=4068" in url || "name=Taringa" in url) "open_meteo_4068.json" else "open_meteo_none.json"
            "spatial.ala.org.au/ws/intersect/" in url -> "ala_intersect.json"
            "data.brisbane.qld.gov.au" in url -> "brisbane_weeds.json"
            "speciesListItems/dr823" in url -> "ala_list_dr823.json"
            "speciesListItems/dr35447" in url -> "ala_list_dr35447.json"
            "biocache-ws.ala.org.au" in url && "taxon_name" in url -> "ala_photos.json"
            "biocache-ws.ala.org.au" in url -> "ala_nearby.json"
            else -> throw IOException("No recorded answer for $url")
        }
        return checkNotNull(javaClass.getResource("/web/$file")) { file }.readText()
    }

    /** Photos are painted on the spot: a green square with a lighter leaf shape, a different shade for each address. */
    override suspend fun bytes(url: String): ByteArray {
        asked += url
        if (offline) throw IOException("No connection")
        if ("images.ala.org.au" !in url) throw IOException("No recorded answer for $url")
        val shade = (url.hashCode() and 0x3F)
        val bitmap = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(40 + shade, 110 + shade, 50))
        canvas.drawOval(30f, 50f, 130f, 110f, Paint().apply { color = Color.rgb(150, 200 + shade / 2, 90) })
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    /** Name matching: every name is accepted as it is, apart from the few the Atlas spells differently. */
    override suspend fun post(url: String, json: String): String {
        asked += url
        if (offline) throw IOException("No connection")
        if ("namematching" !in url) throw IOException("No recorded answer for $url")
        val names = Json.parseToJsonElement(json).jsonArray.map { it.jsonObject["scientificName"]!!.jsonPrimitive.content }
        return JsonArray(
            names.map { name ->
                val accepted = RENAMED[name] ?: name
                JsonObject(mapOf("success" to JsonPrimitive(true), "scientificName" to JsonPrimitive(accepted.replaceFirstChar(Char::uppercaseChar)), "rankID" to JsonPrimitive(7000)))
            },
        ).toString()
    }

    private companion object {
        val RENAMED = mapOf("wedelia trilobata" to "sphagneticola trilobata", "eichhornia crassipes" to "pontederia crassipes")
    }
}
