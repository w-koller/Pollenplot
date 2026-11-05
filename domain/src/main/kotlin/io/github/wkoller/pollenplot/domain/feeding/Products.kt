package io.github.wkoller.pollenplot.domain.feeding

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.LocalDate

/** What kind of product a feed is, which decides how it is described and whether it counts as a conditioner. */
enum class ProductKind {
    /** A seaweed or similar treatment that is not mainly a fertiliser. */
    CONDITIONER,
    LIQUID,
    SLOW_RELEASE,
    ORGANIC,
    COMPOST,
}

/** Nitrogen, phosphorus and potassium as a label gives them, in basis points of weight (or weight per volume). */
data class Npk(val nBp: Int, val pBp: Int, val kBp: Int) {
    init {
        require(nBp in 0..10_000 && pBp in 0..10_000 && kBp in 0..10_000) { "A label's percentages lie from 0 to 100" }
    }
}

/**
 * A fertiliser or conditioner. A built-in product has a [key], the maker's page it was checked against and the day it
 * was checked; one the user added has an [id] and no source.
 */
data class Product(
    val key: String,
    val name: String,
    val maker: String?,
    val kind: ProductKind,
    val npk: Npk?,
    val source: String? = null,
    val lastChecked: LocalDate? = null,
    val own: Boolean = false,
) {
    val title: String get() = listOfNotNull(maker, name).joinToString(" ")
}

@Serializable
internal data class ProductJson(
    val key: String,
    val name: String,
    val maker: String,
    val kind: String,
    /** N, P and K in per cent, as the label prints them; left out when the maker's page gives none. */
    val npk: List<Double>? = null,
    val source: String,
    @SerialName("last_checked") val lastChecked: String,
)

/** The built-in products from assets/kb/products.json. */
object Products {
    private val json = Json { ignoreUnknownKeys = false }

    data class Result(val products: List<Product>, val problems: List<String>)

    fun parse(text: String): Result {
        val problems = mutableListOf<String>()
        val raw = runCatching { json.decodeFromString(ListSerializer(ProductJson.serializer()), text) }
            .getOrElse { return Result(emptyList(), listOf("products.json: ${it.message}")) }
        val products = raw.mapNotNull { p ->
            val where = "products.json ${p.key}"
            val kind = ProductKind.entries.firstOrNull { it.name.equals(p.kind, ignoreCase = true) }
            if (kind == null) problems += "$where: unknown kind \"${p.kind}\""
            if (!p.source.startsWith("https://")) problems += "$where: the source must be an https link to the maker's page"
            val checked = runCatching { LocalDate.parse(p.lastChecked) }.getOrNull()
            if (checked == null) problems += "$where: last_checked must be a date such as 2026-11-05"
            val npk = p.npk?.let { values ->
                if (values.size != 3 || values.any { it < 0 || it > 100 }) {
                    problems += "$where: npk must be [N, P, K] in per cent"
                    null
                } else {
                    Npk(bp(values[0]), bp(values[1]), bp(values[2]))
                }
            }
            if (kind == null || checked == null) null else Product(p.key, p.name, p.maker, kind, npk, p.source, checked)
        }
        products.groupBy { it.key }.filterValues { it.size > 1 }.keys.forEach { problems += "products.json: $it appears twice" }
        return Result(products, problems)
    }

    private fun bp(percent: Double) = Math.round(percent * 100).toInt()
}
