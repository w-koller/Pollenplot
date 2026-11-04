package io.github.wkoller.pollenplot.domain.garden

import io.github.wkoller.pollenplot.domain.plants.EcoRole
import io.github.wkoller.pollenplot.domain.plants.WaterNeed
import io.github.wkoller.pollenplot.domain.semantic.SearchKind
import io.github.wkoller.pollenplot.domain.semantic.SearchText
import io.github.wkoller.pollenplot.domain.semantic.Term
import io.github.wkoller.pollenplot.domain.semantic.Vocab
import io.github.wkoller.pollenplot.domain.semantic.WishGraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class WishlistTest {
    @Test
    fun `a weed is a weed even where it is native, and not listed is said only when the lists are known`() {
        assertEquals(WildStatus.WEED, WildStatus.of(listOf(EcoRole.LOCAL_NATIVE), weed = true, listsKnown = true))
        assertEquals(WildStatus.WEED, WildStatus.of(listOf(EcoRole.WEED_RISK), weed = false, listsKnown = false))
        assertEquals(WildStatus.NATIVE_HERE, WildStatus.of(listOf(EcoRole.LOCAL_NATIVE, EcoRole.POLLINATOR_MAGNET), weed = false, listsKnown = false))
        assertEquals(WildStatus.NOT_LISTED, WildStatus.of(listOf(EcoRole.POLLINATOR_MAGNET), weed = false, listsKnown = true))
        assertEquals(WildStatus.UNKNOWN, WildStatus.of(emptyList(), weed = false, listsKnown = false))
    }

    @Test
    fun `toxic to pets comes first only while pets live at home`() {
        val roles = listOf(EcoRole.POLLINATOR_MAGNET, EcoRole.TOXIC_TO_PETS, EcoRole.LOCAL_NATIVE)
        assertEquals(roles, Wishlist.badgeOrder(roles, pets = false))
        assertEquals(listOf(EcoRole.TOXIC_TO_PETS, EcoRole.POLLINATOR_MAGNET, EcoRole.LOCAL_NATIVE), Wishlist.badgeOrder(roles, pets = true))
    }

    @Test
    fun `a zone gains only the badges it does not have yet`() {
        val feeder = PlantFacts(WaterNeed.LOW, listOf(EcoRole.POLLINATOR_MAGNET))
        assertEquals(listOf(ZoneBadge.POLLINATOR_HOTSPOT), Wishlist.zoneEffect(listOf(feeder, feeder), feeder))
        assertEquals(emptyList<ZoneBadge>(), Wishlist.zoneEffect(listOf(feeder, feeder, feeder), feeder))
        val thirsty = PlantFacts(WaterNeed.HIGH, emptyList())
        assertEquals(listOf(ZoneBadge.MIXED_THIRST), Wishlist.zoneEffect(listOf(PlantFacts(WaterNeed.VERY_LOW, emptyList())), thirsty))
    }

    @Test
    fun `wanted plants come first, the newest first`() {
        val old = wish("a", Instant.parse("2026-01-01T00:00:00Z"))
        val new = wish("b", Instant.parse("2026-06-01T00:00:00Z"))
        val planted = wish("c", Instant.parse("2026-09-01T00:00:00Z")).copy(status = WishStatus.PLANTED)
        assertEquals(listOf("b", "a", "c"), Wishlist.ordered(listOf(old, planted, new)).map { it.id })
    }

    @Test
    fun `a wish is found by where it was seen and its notes, and its price is exported in dollars`() {
        val wish = wish("w", Instant.parse("2026-10-28T00:00:00Z")).copy(placeName = "Mt Coot-tha gardens", notes = "Ask the nursery", priceCents = 1_495)
        val doc = SearchText.wish(wish, null, "Front Native Strip")
        assertEquals(SearchKind.WISH, doc.kind)
        assertTrue(doc.body, "seen at Mt Coot-tha gardens" in doc.body && "for Front Native Strip" in doc.body && "Ask the nursery" in doc.body)
        val triples = WishGraph.wish(wish, null)
        assertTrue(triples.any { it.predicate == Vocab.SCHEMA + "price" && (it.obj as Term.Literal).value == "14.95" })
        // The place's coordinates stay on the phone.
        assertTrue(triples.none { it.predicate.contains("latitude", ignoreCase = true) })
    }

    @Test
    fun `prices are read in dollars and kept in cents`() {
        assertEquals(1_495L, Wishlist.parsePrice("14.95"))
        assertEquals(1_495L, Wishlist.parsePrice(" \$14.95 "))
        assertEquals(1_500L, Wishlist.parsePrice("15"))
        assertEquals(1_490L, Wishlist.parsePrice("14.9"))
        assertEquals(120_000L, Wishlist.parsePrice("1,200"))
        assertEquals(null, Wishlist.parsePrice(""))
        assertEquals(null, Wishlist.parsePrice("14.955"))
        assertEquals(null, Wishlist.parsePrice("-3"))
        assertEquals(null, Wishlist.parsePrice("cheap"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a price cannot be negative`() {
        wish("w", Instant.EPOCH).copy(priceCents = -1)
    }

    private fun wish(id: String, at: Instant) = Wish(id, "Grevillea robusta", null, null, createdAt = at)
}
