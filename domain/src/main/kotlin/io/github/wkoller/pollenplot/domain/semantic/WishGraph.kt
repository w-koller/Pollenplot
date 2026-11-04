package io.github.wkoller.pollenplot.domain.semantic

import io.github.wkoller.pollenplot.domain.garden.Wish
import io.github.wkoller.pollenplot.domain.plants.Species
import java.math.BigDecimal
import java.time.ZoneOffset

/**
 * The wishlist as linked data: each plant the user would like to grow, named as Darwin Core names a taxon, with where
 * it was seen, its price in Australian dollars as schema.org offers give one, and the zone it might go in.
 */
object WishGraph {
    fun wish(wish: Wish, species: Species?): List<Triple> {
        val iri = Vocab.recordIri(wish.id)
        return GraphBuilder()
            .type(iri, Vocab.PP + "Wish")
            .text(iri, Vocab.DWC + "scientificName", species?.name ?: wish.speciesName)
            .text(iri, Vocab.DWC + "vernacularName", species?.common?.firstOrNull())
            .text(iri, Vocab.SCHEMA + "name", wish.customName)
            .text(iri, Vocab.PP + "wishStatus", wish.status.name.lowercase())
            .text(iri, Vocab.PP + "seenAt", wish.placeName.ifBlank { null })
            .decimal(iri, Vocab.SCHEMA + "price", wish.priceCents?.let { BigDecimal.valueOf(it, 2).toPlainString() })
            .text(iri, Vocab.SCHEMA + "priceCurrency", wish.priceCents?.let { "AUD" })
            .iri(iri, Vocab.PP + "targetZone", wish.targetZoneId?.let(Vocab::recordIri))
            .date(iri, Vocab.SCHEMA + "dateCreated", wish.createdAt.atOffset(ZoneOffset.UTC).toLocalDate().toString())
            .text(iri, Vocab.SCHEMA + "description", wish.notes.ifBlank { null })
            .build()
    }
}
