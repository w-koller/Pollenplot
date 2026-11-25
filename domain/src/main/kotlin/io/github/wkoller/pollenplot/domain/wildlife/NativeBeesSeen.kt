package io.github.wkoller.pollenplot.domain.wildlife

import java.time.Instant
import java.time.ZoneId

/**
 * A kind of native bee seen in the yard in a year: how many times, when last, and the cut-out of the latest sighting
 * that has one.
 */
data class SeenBee(val key: String, val animal: Animal, val sightings: Int, val lastSeen: Instant, val thumbnail: String? = null)

/**
 * The native bees seen in the yard, for the Native bees section. A native bee is a native insect of one of the seven
 * bee families; the European honey bee, brought to Australia, is not one.
 */
object NativeBeesSeen {
    val BEE_FAMILIES = setOf("Andrenidae", "Apidae", "Colletidae", "Halictidae", "Megachilidae", "Melittidae", "Stenotritidae")

    fun isNativeBee(animal: Animal): Boolean = animal.isNative && animal.group == LifeGroup.INSECT && animal.family in BEE_FAMILIES

    /** Each kind seen in [year], the most often seen first. */
    fun of(sightings: List<Pair<Sighting, Animal?>>, year: Int, zone: ZoneId): List<SeenBee> =
        sightings.filter { (sighting, animal) -> animal != null && isNativeBee(animal) && sighting.seenAt.atZone(zone).year == year }
            .groupBy { (_, animal) -> animal!!.name }
            .map { (key, seen) ->
                val latest = seen.map { it.first }.sortedByDescending { it.seenAt }
                SeenBee(key, seen.first().second!!, seen.size, latest.first().seenAt, latest.firstNotNullOfOrNull { it.thumbnail })
            }
            .sortedWith(compareByDescending<SeenBee> { it.sightings }.thenBy { it.animal.displayName })
}
