package io.github.wkoller.pollenplot.domain.weeds

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The Atlas of Living Australia's name matching, which says which name it accepts for a plant, such as Sphagneticola
 * trilobata for Wedelia trilobata. Weed lists, records and the identifier's labels do not always use the same name.
 */
object AlaNames {
    private val json = Json { ignoreUnknownKeys = true }

    /** The species rank and below, in the service's rank numbers. A match to a genus does not name a species. */
    private const val SPECIES_RANK = 7000

    /** The body of a request to match [binomials], in the order the answer comes back. */
    fun request(binomials: List<String>): String =
        JsonArray(binomials.map { JsonObject(mapOf("scientificName" to JsonPrimitive(it))) }).toString()

    /**
     * The accepted binomial for each of [binomials], from the service's answer to [request]. A name the service could
     * not match to a species maps to an empty string, so it is not asked about again.
     */
    fun parse(binomials: List<String>, answer: String): Map<String, String> {
        val results = json.parseToJsonElement(answer).jsonArray
        require(results.size == binomials.size) { "Expected ${binomials.size} matches, got ${results.size}" }
        return binomials.zip(results).associate { (name, result) ->
            val row = result as? JsonObject
            val ok = row?.get("success")?.jsonPrimitive?.booleanOrNull == true && (row["rankID"]?.jsonPrimitive?.intOrNull ?: 0) >= SPECIES_RANK
            val accepted = row?.get("scientificName")?.jsonPrimitive?.contentOrNull?.takeIf { ok }?.let(WeedNames::binomial)
            name to (accepted?.takeIf { ' ' in it } ?: "")
        }
    }
}
