package io.github.wkoller.pollenplot.data

import io.github.wkoller.pollenplot.data.db.PollenplotDatabase
import io.github.wkoller.pollenplot.data.db.SoilFeedEntity
import io.github.wkoller.pollenplot.data.db.SoilFeedZoneEntity
import io.github.wkoller.pollenplot.data.db.SoilProductEntity
import io.github.wkoller.pollenplot.domain.feeding.Feed
import io.github.wkoller.pollenplot.domain.feeding.FeedingGuide
import io.github.wkoller.pollenplot.domain.feeding.Npk
import io.github.wkoller.pollenplot.domain.feeding.Product
import io.github.wkoller.pollenplot.domain.feeding.ProductKind
import io.github.wkoller.pollenplot.domain.semantic.FeedGraph
import io.github.wkoller.pollenplot.domain.semantic.Triple
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

/** The built-in products and feeding schedules, read from assets/kb once they are first needed. */
class FeedLibrary(private val loadProducts: () -> List<Product>, private val loadGuide: () -> FeedingGuide) {
    val products: List<Product> by lazy { loadProducts() }
    val guide: FeedingGuide by lazy { loadGuide() }
}

/**
 * Feeds given to zones and the user's own products. The rate is kept as the user typed it: the app never suggests an
 * amount of fertiliser.
 */
class FeedRepository(private val db: PollenplotDatabase, private val clock: Clock, private val library: FeedLibrary) {
    private val dao = db.feedDao()

    /** The built-in products, then the user's own, each by name. */
    val products: Flow<List<Product>> = combine(flow { emit(library.products) }, dao.observeProducts()) { builtIn, own ->
        builtIn.sortedBy { it.title.lowercase() } + own.map { it.toDomain() }
    }

    /** Every feed, the latest first, with the zones it went on. */
    val feeds: Flow<List<Feed>> = combine(dao.observeFeeds(), dao.observeFeedZones()) { feeds, zones ->
        val byFeed = zones.groupBy({ it.feedId }, { it.zoneId })
        feeds.map { it.toDomain(byFeed[it.id].orEmpty().toSet()) }
    }

    /** Each product's kind, by key, for deciding which feeds were conditioners. */
    val kinds: Flow<Map<String, ProductKind>> = products.map { list -> list.associate { it.key to it.kind } }

    suspend fun addProduct(name: String, maker: String?, kind: ProductKind, npk: Npk?): Product {
        val id = UUID.randomUUID().toString()
        dao.upsertProduct(SoilProductEntity(id, name.trim(), maker?.trim()?.ifBlank { null }, kind.name, npk?.nBp, npk?.pBp, npk?.kBp, clock.millis()))
        return Product(id, name.trim(), maker?.trim()?.ifBlank { null }, kind, npk, own = true)
    }

    suspend fun addFeed(productKey: String, date: LocalDate, zoneIds: Set<String>, rateText: String, notes: String): Feed {
        val feed = Feed(UUID.randomUUID().toString(), productKey, date, zoneIds, rateText.trim(), notes.trim())
        db.withTransaction {
            dao.upsertFeed(SoilFeedEntity(feed.id, productKey, date.toEpochDay(), feed.rateText, feed.notes, clock.millis()))
            dao.upsertFeedZones(zoneIds.map { SoilFeedZoneEntity(feed.id, it) })
        }
        return feed
    }

    suspend fun deleteFeed(id: String) = dao.deleteFeed(id)

    /** Every feed and the user's own products as linked data. */
    suspend fun triples(): List<Triple> {
        val own = dao.allProducts()
        val products = library.products.associateBy { it.key } + own.associate { it.id to it.toDomain() }
        val zones = dao.allFeedZones().groupBy({ it.feedId }, { it.zoneId })
        return own.flatMap { FeedGraph.product(it.id, it.toDomain()) } +
            dao.allFeeds().flatMap { entity -> entity.toDomain(zones[entity.id].orEmpty().toSet()).let { FeedGraph.feed(it, products[it.productKey]) } }
    }

    private fun SoilProductEntity.toDomain() = Product(
        key = id,
        name = name,
        maker = maker,
        kind = ProductKind.entries.firstOrNull { it.name == kind } ?: ProductKind.LIQUID,
        npk = if (nBp != null && pBp != null && kBp != null) Npk(nBp, pBp, kBp) else null,
        own = true,
    )

    private fun SoilFeedEntity.toDomain(zones: Set<String>) = Feed(id, productKey, LocalDate.ofEpochDay(dateEpochDay), zones, rateText, notes)
}
