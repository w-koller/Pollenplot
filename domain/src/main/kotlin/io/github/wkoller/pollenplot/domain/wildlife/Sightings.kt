package io.github.wkoller.pollenplot.domain.wildlife

import java.time.Instant

/** What an animal was doing when it was seen. */
enum class Behaviour { FEEDING, POLLINATING, NESTING, BASKING, CALLING, MATING, RESTING, PASSING }

/** An animal or fungus seen in the garden, from a photo or noted by the user. */
data class Sighting(
    val id: String,
    /** The knowledge base's name for it, when the knowledge base has it. */
    val speciesName: String?,
    /** The name it was recorded under: the identifier's label or the user's choice. */
    val label: String,
    val group: LifeGroup,
    val zoneId: String?,
    /** The plant it was on, such as a blue-banded bee on a salvia. */
    val plantId: String?,
    val behaviour: Behaviour?,
    val count: Int,
    val photoId: String?,
    /** Its cut-out from the photo, relative to the app's files folder. */
    val thumbnail: String?,
    val seenAt: Instant,
    val notes: String,
)
