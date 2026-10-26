package io.github.wkoller.pollenplot.domain.weeds

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/** A photo of a plant recorded in Australia, with the credit its licence asks for. */
data class RecordPhoto(
    val thumbnail: String,
    val large: String,
    /** The photographer or recorder, as the record names them. */
    val creator: String?,
    /** Where the record came from, such as iNaturalist Australia. */
    val source: String?,
    val licence: String,
)

/** Photos from records on the Atlas of Living Australia, for comparing with the user's own photo. */
object AlaPhotos {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * The photos in an occurrence search. Only photos under a Creative Commons licence or in the public domain are
     * kept, because those allow the app to show them with a credit.
     */
    fun parse(page: String): List<RecordPhoto> =
        json.parseToJsonElement(page).jsonObject["occurrences"]?.jsonArray.orEmpty().mapNotNull { item ->
            val row = item.jsonObject
            val licence = row.text("license")?.trim()?.takeIf(::isOpen) ?: return@mapNotNull null
            val thumbnail = row.text("smallImageUrl") ?: row.text("thumbnailUrl") ?: return@mapNotNull null
            RecordPhoto(
                thumbnail = thumbnail,
                large = row.text("largeImageUrl") ?: thumbnail,
                creator = row.first("collector") ?: row.first("recordedBy"),
                source = row.text("dataResourceName"),
                licence = licence,
            )
        }.distinctBy { it.thumbnail }

    private fun isOpen(licence: String): Boolean {
        val upper = licence.uppercase()
        return upper.startsWith("CC") || "PUBLIC DOMAIN" in upper
    }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.first(key: String): String? = when (val value = this[key]) {
        is JsonArray -> value.firstNotNullOfOrNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf { text -> text.isNotBlank() } }
        is JsonPrimitive -> value.contentOrNull?.takeIf { it.isNotBlank() }
        else -> null
    }
}
