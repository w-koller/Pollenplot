package io.github.wkoller.pollenplot.domain.assistant

import io.github.wkoller.pollenplot.domain.plants.Soil
import io.github.wkoller.pollenplot.domain.plants.Sun
import io.github.wkoller.pollenplot.domain.plants.Toxicity
import io.github.wkoller.pollenplot.domain.plants.WaterNeed
import io.github.wkoller.pollenplot.domain.season.MonthSet
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import java.time.Month

/**
 * Care facts Gemma 4 drafted for a plant the knowledge base does not have. The app shows them with a note that Gemma 4
 * wrote them, and they never become part of the knowledge base. Every value is checked when the draft is read, and
 * one that is not one of the allowed values is left out.
 */
@Serializable
data class DraftedCare(
    val sun: List<Sun> = emptyList(),
    val water: WaterNeed? = null,
    /** Tolerance from 0 (none) to 3 (high). */
    val drought: Int? = null,
    val frost: Int? = null,
    val soils: List<Soil> = emptyList(),
    /** Months 1 to 12, for the Southern Hemisphere. */
    val flowering: List<Int> = emptyList(),
    val heightMm: List<Int> = emptyList(),
    val widthMm: List<Int> = emptyList(),
    val nativeToAustralia: Boolean? = null,
    val toxicToPets: Toxicity? = null,
    val note: String? = null,
) {
    val isEmpty: Boolean
        get() = sun.isEmpty() && water == null && drought == null && frost == null && soils.isEmpty() && flowering.isEmpty() &&
            heightMm.isEmpty() && widthMm.isEmpty() && nativeToAustralia == null && toxicToPets == null && note == null

    val floweringMonths: MonthSet get() = MonthSet.of(flowering.map { Month.of(it) })

    fun toJson(): String = json.encodeToString(this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromJson(text: String): DraftedCare = json.decodeFromString(serializer(), text)

        /** What Gemma 4 is asked for a plant, by its scientific name and common name. */
        fun prompt(name: String): String = """
            Draft the care of the plant $name for a garden in Australia, as a JSON object with only these keys, leaving out any you are not sure of:
            "sun": a list of "full sun", "part shade" and "full shade";
            "water": one of "very low", "low", "moderate" and "high", once it is established;
            "drought" and "frost": its tolerance, from 0 for none to 3 for high;
            "soils": a list of "sand", "loam", "clay", "wet" and "potting mix";
            "flowering": a list of the months it flowers in the Southern Hemisphere, as "jan" to "dec";
            "height_m" and "width_m": its smallest and largest size when grown, in metres, such as [1, 2];
            "native_to_australia": true or false;
            "toxic_to_pets": one of "none", "mild" and "severe";
            "note": one sentence of care advice.
            Answer with the JSON object only.
        """.trimIndent()

        /** The draft in Gemma 4's answer, or null when it has no JSON object or nothing in it is usable. */
        fun parse(answer: String): DraftedCare? {
            val start = answer.indexOf('{')
            val end = answer.lastIndexOf('}')
            if (start < 0 || end <= start) return null
            val obj = runCatching { json.parseToJsonElement(answer.substring(start, end + 1)).jsonObject }.getOrNull() ?: return null
            val draft = DraftedCare(
                sun = obj.words("sun").mapNotNull { word -> Sun.entries.firstOrNull { it.name == word } }.distinct(),
                water = obj.word("water")?.let { word -> WaterNeed.entries.firstOrNull { it.name == word } },
                drought = obj.score("drought"),
                frost = obj.score("frost"),
                soils = obj.words("soils").mapNotNull { word -> Soil.entries.firstOrNull { it.name == word } }.distinct(),
                flowering = obj.words("flowering").mapNotNull { word -> MONTHS.indexOf(word.take(3)).takeIf { it >= 0 }?.plus(1) }.distinct().sorted(),
                heightMm = obj.metres("height_m"),
                widthMm = obj.metres("width_m"),
                nativeToAustralia = (obj["native_to_australia"] as? JsonPrimitive)?.booleanOrNull,
                toxicToPets = obj.word("toxic_to_pets")?.let { word -> Toxicity.entries.firstOrNull { it.name == word } },
                note = (obj["note"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_NOTE },
            )
            return draft.takeUnless { it.isEmpty }
        }

        private fun JsonObject.word(key: String): String? =
            (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.uppercase()?.replace(' ', '_')

        private fun JsonObject.words(key: String): List<String> = when (val value = this[key]) {
            is JsonArray -> value.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.uppercase()?.replace(' ', '_') }
            is JsonPrimitive -> listOfNotNull(value.contentOrNull?.trim()?.uppercase()?.replace(' ', '_'))
            else -> emptyList()
        }

        private fun JsonObject.score(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull?.takeIf { it in 0..3 }

        /** A size as [smallest, largest] in whole millimetres, if it is a sensible size for a garden plant. */
        private fun JsonObject.metres(key: String): List<Int> {
            val values = (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.doubleOrNull } ?: return emptyList()
            if (values.size != 2) return emptyList()
            val (low, high) = values.map { (it * 1000).toInt() }
            return if (low in 1..high && high <= MAX_SIZE_MM) listOf(low, high) else emptyList()
        }

        private val MONTHS = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")
        private const val MAX_SIZE_MM = 100_000
        private const val MAX_NOTE = 400
    }
}
