package io.github.wkoller.pollenplot.domain.wildlife

import java.time.Instant
import java.time.ZoneId

/** A kind of native bee seen in the yard in a year: how many times, and when last. */
data class SeenBee(val key: String, val animal: Animal, val sightings: Int, val lastSeen: Instant)

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
            .map { (key, seen) -> SeenBee(key, seen.first().second!!, seen.size, seen.maxOf { it.first.seenAt }) }
            .sortedWith(compareByDescending<SeenBee> { it.sightings }.thenBy { it.animal.displayName })
}
