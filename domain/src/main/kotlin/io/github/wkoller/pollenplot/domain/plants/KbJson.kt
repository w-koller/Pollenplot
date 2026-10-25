package io.github.wkoller.pollenplot.domain.plants

import io.github.wkoller.pollenplot.domain.season.MonthSet
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.Month

/*
 * The knowledge base as it is written in app/src/main/assets/kb. The files are meant to be read and edited by people,
 * so the format uses plain words ("full_sun", "oct") and these classes turn it into the domain model, collecting every
 * problem instead of stopping at the first.
 */

@Serializable
internal data class CareJson(
    val sun: List<String>? = null,
    val water: String? = null,
    val drought: Int? = null,
    val frost: Int? = null,
    val salt: Int? = null,
    val soil: List<String>? = null,
    val ph: List<Double>? = null,
    val fertiliser: String? = null,
    @SerialName("fertiliser_note") val fertiliserNote: String? = null,
    val compost: CompostJson? = null,
    val wildlife: WildlifeJson? = null,
    val forage: ForageJson? = null,
    @SerialName("ground_cover") val groundCover: Boolean? = null,
    val height: List<Int>? = null,
    val width: List<Int>? = null,
    val flowers: List<String>? = null,
    val peak: List<String>? = null,
    val pruning: String? = null,
    val toxic: ToxicJson? = null,
    val edible: String? = null,
    val description: String? = null,
    @SerialName("care_note") val careNote: String? = null,
)

@Serializable
internal data class CompostJson(val likes: String? = null, val safe: String? = null, val note: String? = null)

@Serializable
internal data class WildlifeJson(
    val pollinator: Int = 0,
    val butterflies: Int = 0,
    val host: Boolean = false,
    val birds: Int = 0,
    val beneficials: Int = 0,
    val frogs: Int = 0,
)

/** Honey bees score [nectar, pollen]; stingless bees [nectar, pollen, resin]; solitary bees [nectar, pollen]. */
@Serializable
internal data class ForageJson(
    val honey: List<Int> = listOf(0, 0),
    val stingless: List<Int> = listOf(0, 0, 0),
    val solitary: List<Int> = listOf(0, 0),
    val buzz: Boolean = false,
)

@Serializable
internal data class ToxicJson(val pets: String? = null, val people: String? = null, val note: String? = null)

@Serializable
internal data class SpeciesJson(
    val name: String,
    val genus: String? = null,
    val family: String? = null,
    val common: List<String> = emptyList(),
    val form: String? = null,
    val native: List<String> = emptyList(),
    val wons: Boolean = false,
    val synonyms: List<String> = emptyList(),
    val sun: List<String>? = null,
    val water: String? = null,
    val drought: Int? = null,
    val frost: Int? = null,
    val salt: Int? = null,
    val soil: List<String>? = null,
    val ph: List<Double>? = null,
    val fertiliser: String? = null,
    @SerialName("fertiliser_note") val fertiliserNote: String? = null,
    val compost: CompostJson? = null,
    val wildlife: WildlifeJson? = null,
    val forage: ForageJson? = null,
    @SerialName("ground_cover") val groundCover: Boolean? = null,
    val height: List<Int>? = null,
    val width: List<Int>? = null,
    val flowers: List<String>? = null,
    val peak: List<String>? = null,
    val pruning: String? = null,
    val toxic: ToxicJson? = null,
    val edible: String? = null,
    val description: String? = null,
    @SerialName("care_note") val careNote: String? = null,
) {
    fun care() = CareJson(
        sun, water, drought, frost, salt, soil, ph, fertiliser, fertiliserNote, compost, wildlife, forage, groundCover,
        height, width, flowers, peak, pruning, toxic, edible, description, careNote,
    )
}

@Serializable
internal data class GroupJson(
    val name: String,
    val family: String? = null,
    val form: String? = null,
    val sun: List<String>? = null,
    val water: String? = null,
    val drought: Int? = null,
    val frost: Int? = null,
    val salt: Int? = null,
    val soil: List<String>? = null,
    val ph: List<Double>? = null,
    val fertiliser: String? = null,
    @SerialName("fertiliser_note") val fertiliserNote: String? = null,
    val compost: CompostJson? = null,
    val wildlife: WildlifeJson? = null,
    val forage: ForageJson? = null,
    @SerialName("ground_cover") val groundCover: Boolean? = null,
    val height: List<Int>? = null,
    val width: List<Int>? = null,
    val flowers: List<String>? = null,
    val peak: List<String>? = null,
    val pruning: String? = null,
    val toxic: ToxicJson? = null,
    val edible: String? = null,
    val description: String? = null,
    @SerialName("care_note") val careNote: String? = null,
) {
    fun care() = CareJson(
        sun, water, drought, frost, salt, soil, ph, fertiliser, fertiliserNote, compost, wildlife, forage, groundCover,
        height, width, flowers, peak, pruning, toxic, edible, description, careNote,
    )
}

@Serializable
internal data class CompanionJson(val a: String, val b: String, val relation: String, val reason: String, val evidence: String = "traditional")

@Serializable
internal data class MetaJson(val version: Int)

/** Turns the JSON into domain objects, adding a message to [problems] for anything that does not fit. */
internal class KbReader(val problems: MutableList<String>) {
    val json = Json { ignoreUnknownKeys = false }

    fun <T> decodeList(file: String, text: String, serializer: kotlinx.serialization.KSerializer<T>): List<T> =
        try {
            json.decodeFromString(ListSerializer(serializer), text)
        } catch (error: SerializationException) {
            problems += "$file: ${error.message}"
            emptyList()
        } catch (error: IllegalArgumentException) {
            problems += "$file: ${error.message}"
            emptyList()
        }

    fun care(where: String, json: CareJson): CareProfile {
        val ph = json.ph
        if (ph != null && (ph.size != 2 || ph[0] > ph[1] || ph[0] < 3.0 || ph[1] > 10.0)) problems += "$where: ph must be [low, high] between 3 and 10"
        return CareProfile(
            sun = json.sun?.mapNotNull { enumOf<Sun>(where, "sun", it) }?.toSet(),
            water = json.water?.let { enumOf<WaterNeed>(where, "water", it) },
            drought = json.drought?.let { score(where, "drought", it) },
            frost = json.frost?.let { score(where, "frost", it) },
            salt = json.salt?.let { score(where, "salt", it) },
            soils = json.soil?.mapNotNull { enumOf<Soil>(where, "soil", it) }?.toSet(),
            phMinTenths = ph?.takeIf { it.size == 2 }?.let { Math.round(it[0] * 10).toInt() },
            phMaxTenths = ph?.takeIf { it.size == 2 }?.let { Math.round(it[1] * 10).toInt() },
            fertiliser = json.fertiliser?.let { enumOf<Fertiliser>(where, "fertiliser", it) },
            fertiliserNote = json.fertiliserNote,
            compostLikes = json.compost?.likes?.let { enumOf<CompostLikes>(where, "compost.likes", it) },
            compostSafe = json.compost?.safe?.let { enumOf<CompostSafe>(where, "compost.safe", it) },
            compostNote = json.compost?.note,
            wildlife = json.wildlife?.let {
                Wildlife(
                    pollinator = score(where, "wildlife.pollinator", it.pollinator),
                    butterflies = score(where, "wildlife.butterflies", it.butterflies),
                    butterflyHost = it.host,
                    birds = score(where, "wildlife.birds", it.birds),
                    beneficials = score(where, "wildlife.beneficials", it.beneficials),
                    frogs = score(where, "wildlife.frogs", it.frogs),
                )
            },
            forage = json.forage?.let { forage(where, it) },
            groundCover = json.groundCover,
            height = json.height?.let { size(where, "height", it) },
            width = json.width?.let { size(where, "width", it) },
            flowering = json.flowers?.let { months(where, "flowers", it) },
            peakFlowering = json.peak?.let { months(where, "peak", it) },
            pruning = json.pruning,
            toxicPets = json.toxic?.pets?.let { enumOf<Toxicity>(where, "toxic.pets", it) },
            toxicPeople = json.toxic?.people?.let { enumOf<Toxicity>(where, "toxic.people", it) },
            toxicNote = json.toxic?.note,
            edible = json.edible,
            description = json.description,
            careNote = json.careNote,
        ).also { care ->
            val flowering = care.flowering
            val peak = care.peakFlowering
            if (peak != null && (flowering == null || (peak - flowering) != MonthSet.NONE)) problems += "$where: peak months must be flowering months"
        }
    }

    private fun forage(where: String, json: ForageJson): Forage? {
        if (json.honey.size != 2 || json.stingless.size != 3 || json.solitary.size != 2) {
            problems += "$where: forage needs honey [nectar, pollen], stingless [nectar, pollen, resin] and solitary [nectar, pollen]"
            return null
        }
        val all = json.honey + json.stingless + json.solitary
        all.forEach { score(where, "forage", it) }
        return Forage(
            honeyNectar = json.honey[0],
            honeyPollen = json.honey[1],
            stinglessNectar = json.stingless[0],
            stinglessPollen = json.stingless[1],
            stinglessResin = json.stingless[2],
            solitaryNectar = json.solitary[0],
            solitaryPollen = json.solitary[1],
            buzz = json.buzz,
        )
    }

    private fun size(where: String, field: String, values: List<Int>): SizeRange? {
        if (values.size != 2 || values[0] < 0 || values[0] > values[1]) {
            problems += "$where: $field must be [smallest, largest] in millimetres"
            return null
        }
        return SizeRange(values[0], values[1])
    }

    fun score(where: String, field: String, value: Int): Int {
        if (value !in 0..3) problems += "$where: $field must be 0 to 3, not $value"
        return value.coerceIn(0, 3)
    }

    private fun months(where: String, field: String, values: List<String>): MonthSet? {
        val months = values.mapNotNull { name ->
            MONTHS[name.lowercase()] ?: run {
                problems += "$where: $field has an unknown month \"$name\""
                null
            }
        }
        return MonthSet.of(months)
    }

    inline fun <reified E : Enum<E>> enumOf(where: String, field: String, value: String): E? =
        enumValues<E>().firstOrNull { it.name.equals(value, ignoreCase = true) } ?: run {
            problems += "$where: $field has an unknown value \"$value\""
            null
        }

    companion object {
        private val MONTHS: Map<String, Month> = Month.entries.associateBy { it.name.take(3).lowercase() }
    }
}
