package io.github.wkoller.pollenplot.domain.semantic

import io.github.wkoller.pollenplot.domain.feeding.Feed
import io.github.wkoller.pollenplot.domain.feeding.Product

/**
 * Feeds as linked data: a feed given on a day to zones, naming its product and the rate the user typed, and the user's
 * own products with their label analysis.
 */
object FeedGraph {
    fun feed(feed: Feed, product: Product?): List<Triple> {
        val iri = Vocab.recordIri(feed.id)
        val builder = GraphBuilder()
            .type(iri, Vocab.PP + "Feed")
            .date(iri, Vocab.DWC + "eventDate", feed.date.toString())
            .text(iri, Vocab.PP + "product", product?.title ?: feed.productKey)
            .text(iri, Vocab.PP + "rate", feed.rateText.ifBlank { null })
            .text(iri, Vocab.SCHEMA + "description", feed.notes.ifBlank { null })
        feed.zoneIds.sorted().forEach { builder.iri(iri, Vocab.PP + "zone", Vocab.recordIri(it)) }
        return builder.build()
    }

    fun product(id: String, product: Product): List<Triple> {
        val iri = Vocab.recordIri(id)
        return GraphBuilder()
            .type(iri, Vocab.PP + "FeedProduct")
            .text(iri, Vocab.SCHEMA + "name", product.name)
            .text(iri, Vocab.SCHEMA + "manufacturer", product.maker)
            .text(iri, Vocab.PP + "productKind", product.kind.name.lowercase())
            .integer(iri, Vocab.PP + "nitrogenBp", product.npk?.nBp?.toLong())
            .integer(iri, Vocab.PP + "phosphorusBp", product.npk?.pBp?.toLong())
            .integer(iri, Vocab.PP + "potassiumBp", product.npk?.kBp?.toLong())
            .build()
    }
}
