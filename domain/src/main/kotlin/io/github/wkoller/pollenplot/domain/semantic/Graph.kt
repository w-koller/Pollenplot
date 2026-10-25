package io.github.wkoller.pollenplot.domain.semantic

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** The vocabularies Pollenplot's records are described in, with the prefixes used in JSON-LD. */
object Vocab {
    const val DWC = "http://rs.tdwg.org/dwc/terms/"
    const val DCTERMS = "http://purl.org/dc/terms/"
    const val SCHEMA = "https://schema.org/"
    const val SOSA = "http://www.w3.org/ns/sosa/"
    const val XSD = "http://www.w3.org/2001/XMLSchema#"

    /** Pollenplot's own terms for garden ideas the standard vocabularies do not cover, such as eco-roles. */
    const val PP = "urn:pollenplot:vocab:"

    val prefixes: Map<String, String> = linkedMapOf(
        "dwc" to DWC,
        "dcterms" to DCTERMS,
        "schema" to SCHEMA,
        "sosa" to SOSA,
        "xsd" to XSD,
        "pp" to PP,
    )

    /** The IRI for a user record's UUID key. */
    fun recordIri(id: String) = "urn:uuid:$id"
}

sealed interface Term {
    data class Iri(val value: String) : Term

    /** A literal value. [datatype] is an XSD type IRI for numbers and dates; plain text has none. */
    data class Literal(val value: String, val datatype: String? = null) : Term
}

data class Triple(val subject: String, val predicate: String, val obj: Term)

/** Collects triples about records. Blank or missing values are left out rather than written as empty strings. */
class GraphBuilder {
    private val triples = mutableListOf<Triple>()

    fun type(subject: String, type: String) = apply { triples += Triple(subject, RDF_TYPE, Term.Iri(type)) }

    fun iri(subject: String, predicate: String, value: String?) = apply {
        if (!value.isNullOrBlank()) triples += Triple(subject, predicate, Term.Iri(value))
    }

    fun text(subject: String, predicate: String, value: String?) = apply {
        if (!value.isNullOrBlank()) triples += Triple(subject, predicate, Term.Literal(value))
    }

    fun integer(subject: String, predicate: String, value: Long?) = apply {
        if (value != null) triples += Triple(subject, predicate, Term.Literal(value.toString(), Vocab.XSD + "integer"))
    }

    fun decimal(subject: String, predicate: String, value: String?) = apply {
        if (value != null) triples += Triple(subject, predicate, Term.Literal(value, Vocab.XSD + "decimal"))
    }

    fun date(subject: String, predicate: String, isoDate: String?) = apply {
        if (isoDate != null) triples += Triple(subject, predicate, Term.Literal(isoDate, Vocab.XSD + "date"))
    }

    fun boolean(subject: String, predicate: String, value: Boolean?) = apply {
        if (value != null) triples += Triple(subject, predicate, Term.Literal(value.toString(), Vocab.XSD + "boolean"))
    }

    fun add(triple: Triple) = apply { triples += triple }

    fun build(): List<Triple> = triples.toList()

    companion object {
        const val RDF_TYPE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"
    }
}

/**
 * Writes triples as JSON-LD: one node per subject in an `@graph`, with the vocabulary prefixes in the `@context`, so
 * the file reads as plain JSON and any linked-data tool can expand it.
 */
object JsonLd {
    fun document(triples: List<Triple>): JsonObject {
        val nodes = triples.groupBy { it.subject }.map { (subject, statements) ->
            buildJsonObject {
                put("@id", subject)
                val types = statements.filter { it.predicate == GraphBuilder.RDF_TYPE }.map { compact((it.obj as Term.Iri).value) }
                if (types.isNotEmpty()) put("@type", if (types.size == 1) JsonPrimitive(types[0]) else JsonArray(types.map(::JsonPrimitive)))
                statements.filter { it.predicate != GraphBuilder.RDF_TYPE }.groupBy { it.predicate }.forEach { (predicate, values) ->
                    val items = values.map { value(it.obj) }
                    put(compact(predicate), if (items.size == 1) items[0] else JsonArray(items))
                }
            }
        }
        return buildJsonObject {
            put("@context", JsonObject(Vocab.prefixes.mapValues { JsonPrimitive(it.value) }))
            put("@graph", JsonArray(nodes))
        }
    }

    private fun value(term: Term): JsonElement = when (term) {
        is Term.Iri -> buildJsonObject { put("@id", compact(term.value)) }
        is Term.Literal -> if (term.datatype == null) {
            JsonPrimitive(term.value)
        } else {
            buildJsonObject {
                put("@value", term.value)
                put("@type", compact(term.datatype))
            }
        }
    }

    /** Shortens an IRI to prefix:name when one of the known vocabularies covers it. */
    fun compact(iri: String): String {
        for ((prefix, namespace) in Vocab.prefixes) {
            if (iri.startsWith(namespace) && iri.length > namespace.length) return "$prefix:${iri.removePrefix(namespace)}"
        }
        return iri
    }
}
