package io.github.wkoller.pollenplot.domain.wildlife

import java.time.Instant

/** What an animal was doing when it was seen. */
enum class Behaviour { FEEDING, POLLINATING, NESTING, BASKING, CALLING, MATING, RESTING, PASSING }

/** An animal or fungus seen in the garden, from a photo or noted by the user. */
data class Sighting(
    val id: String,
    /** Its scientific name, when known: the knowledge base's name for it, or the identifier's. */
    val speciesName: String?,
    /** The name it was recorded under: the identifier's label or the user's choice. */
    val label: String,
    val group: LifeGroup,
    val zoneId: String?,
    /** The plants it was on, such as a blue-banded bee on a salvia and then a lavender. None when it was on no plant. */
    val plantIds: List<String>,
    /** Everything it was seen doing, in the order of [Behaviour]. None when the user was not sure. */
    val behaviours: Set<Behaviour>,
    val count: Int,
    val photoId: String?,
    /** Its cut-out from the photo, relative to the app's files folder. */
    val thumbnail: String?,
    val seenAt: Instant,
    val notes: String,
) {
    /** What it was doing as lower-case words in a fixed order, for the exports. */
    val behaviourWords: List<String> get() = Behaviour.entries.filter { it in behaviours }.map { it.name.lowercase() }
}
