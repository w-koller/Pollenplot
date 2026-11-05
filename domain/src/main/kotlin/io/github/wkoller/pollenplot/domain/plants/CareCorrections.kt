package io.github.wkoller.pollenplot.domain.plants

import io.github.wkoller.pollenplot.domain.season.MonthSet
import kotlinx.serialization.json.Json

/**
 * The facts a user may correct for a species, and how each correction is stored: as the knowledge base writes that
 * field, such as `{"sun":["full_sun"]}`, so a stored correction reads like the files in assets/kb. Safety facts
 * (toxicity and whether a plant is edible) are not offered.
 */
object CareCorrections {
    /** The facts the plant page offers to correct, in the order it shows them. */
    val EDITABLE: List<CareField> = listOf(
        CareField.SUN, CareField.WATER, CareField.FROST, CareField.SOILS, CareField.FERTILISER, CareField.FLOWERING,
        CareField.HEIGHT, CareField.WIDTH, CareField.PRUNING_MONTHS,
    )

    // Defaults are not written, so a stored value holds only its own field.
    private val json = Json

    /** [field] of [care] as stored, or null when [care] has no value for it or the field cannot be corrected. */
    fun encode(field: CareField, care: CareProfile): String? {
        val value = when (field) {
            CareField.SUN -> care.sun?.let { sun -> CareJson(sun = sun.sortedBy { it.ordinal }.map { it.name.lowercase() }) }
            CareField.WATER -> care.water?.let { CareJson(water = it.name.lowercase()) }
            CareField.FROST -> care.frost?.let { CareJson(frost = it) }
            CareField.SOILS -> care.soils?.let { soils -> CareJson(soil = soils.sortedBy { it.ordinal }.map { it.name.lowercase() }) }
            CareField.FERTILISER -> care.fertiliser?.let { CareJson(fertiliser = it.name.lowercase()) }
            CareField.FLOWERING -> care.flowering?.let { CareJson(flowers = months(it)) }
            CareField.HEIGHT -> care.height?.let { CareJson(height = listOf(it.minMm, it.maxMm)) }
            CareField.WIDTH -> care.width?.let { CareJson(width = listOf(it.minMm, it.maxMm)) }
            CareField.PRUNING_MONTHS -> care.pruningMonths?.let { CareJson(pruningMonths = months(it)) }
            else -> null
        } ?: return null
        return json.encodeToString(CareJson.serializer(), value)
    }

    /**
     * The stored corrections for one species as a care profile with only those facts set. A value that no longer
     * reads, or a field that cannot be corrected, is left out.
     */
    fun decode(values: Map<CareField, String>): CareProfile {
        val reader = KbReader(mutableListOf())
        val parts = values.filterKeys { it in EDITABLE }.mapNotNull { (_, text) ->
            runCatching { reader.care("correction", reader.json.decodeFromString(CareJson.serializer(), text)) }.getOrNull()
        }
        return CareProfile(
            sun = parts.firstNotNullOfOrNull { it.sun },
            water = parts.firstNotNullOfOrNull { it.water },
            frost = parts.firstNotNullOfOrNull { it.frost },
            soils = parts.firstNotNullOfOrNull { it.soils },
            fertiliser = parts.firstNotNullOfOrNull { it.fertiliser },
            flowering = parts.firstNotNullOfOrNull { it.flowering },
            height = parts.firstNotNullOfOrNull { it.height },
            width = parts.firstNotNullOfOrNull { it.width },
            pruningMonths = parts.firstNotNullOfOrNull { it.pruningMonths },
        )
    }

    /** Whether [field] of [a] and [b] say the same, so saving one over the other would change nothing. */
    fun same(field: CareField, a: CareProfile, b: CareProfile): Boolean = encode(field, a) == encode(field, b)

    private fun months(set: MonthSet) = set.months.map { it.name.take(3).lowercase() }
}

/** One fact the user corrected for every plant of a species, stored as [CareCorrections.encode] writes it. */
data class CareCorrection(val id: String, val speciesName: String, val field: CareField, val value: String, val updatedAt: java.time.Instant)
