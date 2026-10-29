package io.github.wkoller.pollenplot.domain.semantic

import io.github.wkoller.pollenplot.domain.place.GardenPlace
import io.github.wkoller.pollenplot.domain.wildlife.Animal
import io.github.wkoller.pollenplot.domain.wildlife.Sighting
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Sightings as a Darwin Core occurrence table, a CSV file that biodiversity portals take in. The place is the
 * garden's, as rounded as the app keeps it, to about 1 km, so each record says it is uncertain by 1,000 m.
 */
object DarwinCore {
    val COLUMNS = listOf(
        "occurrenceID", "basisOfRecord", "eventDate", "scientificName", "vernacularName", "individualCount", "behavior",
        "occurrenceStatus", "country", "countryCode", "stateProvince", "decimalLatitude", "decimalLongitude",
        "coordinateUncertaintyInMeters", "occurrenceRemarks",
    )

    /** The garden's place is rounded to two decimal places of a degree, about 1 km. */
    const val UNCERTAINTY_METRES = 1_000

    fun csv(sightings: List<Pair<Sighting, Animal?>>, place: GardenPlace?, stateName: String?, zone: ZoneId): String {
        val rows = sightings.sortedBy { it.first.seenAt }.map { (sighting, animal) ->
            listOf(
                Vocab.recordIri(sighting.id),
                if (sighting.photoId != null) "MachineObservation" else "HumanObservation",
                DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(sighting.seenAt.atZone(zone).toOffsetDateTime()),
                animal?.name ?: sighting.speciesName ?: sighting.label,
                animal?.common?.firstOrNull() ?: sighting.label.takeIf { it != (animal?.name ?: sighting.speciesName) }.orEmpty(),
                sighting.count.toString(),
                sighting.behaviour?.name?.lowercase().orEmpty(),
                "present",
                "Australia",
                "AU",
                stateName.orEmpty(),
                place?.latText.orEmpty(),
                place?.lonText.orEmpty(),
                if (place != null) UNCERTAINTY_METRES.toString() else "",
                sighting.notes,
            )
        }
        return (listOf(COLUMNS) + rows).joinToString("\r\n", postfix = "\r\n") { row -> row.joinToString(",") { field(it) } }
    }

    /** A field as RFC 4180 writes it: quoted when it holds a comma, a quote or a line break, with its quotes doubled. */
    fun field(text: String): String = if (text.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + text.replace("\"", "\"\"") + "\"" else text
}
