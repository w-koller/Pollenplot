package io.github.wkoller.pollenplot.domain.garden

import io.github.wkoller.pollenplot.domain.plants.EcoRole
import io.github.wkoller.pollenplot.domain.wildlife.LifeGroup
import java.time.Instant

/** Where a plant on the wishlist is: still wanted, planted in the garden, or no longer wanted. */
enum class WishStatus { WANTED, PLANTED, DROPPED }

/**
 * A plant the user would like to grow, often one photographed away from home: what it is, the photo's find it came
 * from, where it was seen, what it cost, and the zone it might go in. [latE7] and [lonE7] are rounded to about 1 km
 * and stay on the phone.
 */
data class Wish(
    val id: String,
    val speciesName: String?,
    val customName: String?,
    /** The find in a photo it was saved from, which keeps the photo and the cut-out. */
    val detectionId: String?,
    val placeName: String = "",
    val latE7: Int? = null,
    val lonE7: Int? = null,
    val priceCents: Long? = null,
    val notes: String = "",
    val targetZoneId: String? = null,
    val status: WishStatus = WishStatus.WANTED,
    val thumbnail: String? = null,
    val createdAt: Instant,
) {
    init {
        require(priceCents == null || priceCents >= 0) { "A price cannot be negative" }
    }
}

/**
 * What a plant found away from home would be in the user's own garden: a native of the garden's state, a weed on a list
 * for the garden's area, not on any of those lists, or not known because no weed list has been downloaded.
 */
enum class WildStatus {
    NATIVE_HERE,
    WEED,
    NOT_LISTED,
    UNKNOWN,
    ;

    companion object {
        /**
         * The status of a species with [roles] in the garden. A weed comes first, because a plant can be native to the
         * state and still be a weed in the garden's area. "Not listed" is said only when the garden's lists are known.
         */
        fun of(roles: Collection<EcoRole>, weed: Boolean, listsKnown: Boolean): WildStatus = when {
            weed || EcoRole.WEED_RISK in roles -> WEED
            EcoRole.LOCAL_NATIVE in roles -> NATIVE_HERE
            listsKnown -> NOT_LISTED
            else -> UNKNOWN
        }
    }
}

object Wishlist {
    /**
     * The badges a wish shows, with Toxic to pets first when [pets] live at home, so it is not missed among the others.
     */
    fun badgeOrder(roles: List<EcoRole>, pets: Boolean): List<EcoRole> =
        if (pets && EcoRole.TOXIC_TO_PETS in roles) listOf(EcoRole.TOXIC_TO_PETS) + (roles - EcoRole.TOXIC_TO_PETS) else roles

    /**
     * The badges a zone would gain if a plant like [added] went in with the [plants] already there, such as Pollinator
     * hotspot, or Mixed thirst as a warning. Badges the zone already has are left out.
     */
    fun zoneEffect(plants: List<PlantFacts>, added: PlantFacts, seen: Set<LifeGroup> = emptySet()): List<ZoneBadge> {
        val now = ZoneBadges.of(plants, seen)
        return ZoneBadges.of(plants + added, seen).filterNot { it in now }
    }

    /**
     * A price typed in dollars, such as "14.95", "\$14.95" or "15", in cents; null for an empty or unreadable price, or
     * one with more than two decimal places.
     */
    fun parsePrice(text: String): Long? {
        val plain = text.trim().removePrefix("$").replace(",", "").trim()
        if (!PRICE.matches(plain)) return null
        return plain.toBigDecimal().movePointRight(2).longValueExact()
    }

    private val PRICE = Regex("""\d{1,7}(\.\d{1,2})?""")

    /** Wanted plants first, then planted, then dropped, the newest first within each. */
    fun ordered(wishes: List<Wish>): List<Wish> = wishes.sortedWith(compareBy<Wish> { it.status.ordinal }.thenByDescending { it.createdAt })
}
