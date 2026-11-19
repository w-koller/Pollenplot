package io.github.wkoller.pollenplot.domain.plants

/** A plant the models can name, with its genus, family and common name, from `assets/kb/taxa.tsv`. */
data class Taxon(val name: String, val genus: String, val family: String, val common: String?)

/**
 * Every plant the identification models can name, by scientific name or an alias. The knowledge base describes a few
 * hundred of them; for the rest this says which genus and family to take facts from, through
 * [CareResolver.resolveGroup], and what the plant is called.
 */
class Taxa(taxa: List<Pair<Taxon, List<String>>>) {
    private val byName: Map<String, Taxon> = buildMap {
        // A plant's own name wins over another plant's alias.
        taxa.forEach { (taxon, aliases) -> aliases.forEach { putIfAbsent(KnowledgeBase.normalise(it), taxon) } }
        taxa.forEach { (taxon, _) -> put(KnowledgeBase.normalise(taxon.name), taxon) }
    }

    /** Every plant in the table. */
    val all: Set<Taxon> = byName.values.toSet()

    val size: Int get() = all.size

    fun of(name: String): Taxon? = byName[KnowledgeBase.normalise(name)]

    companion object {
        val EMPTY = Taxa(emptyList())

        /** Reads the table: lines of name, genus, family, common name and aliases split by `|`, with `#` comments. */
        fun parse(text: String): Taxa = Taxa(
            text.lineSequence()
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .mapNotNull { line ->
                    val fields = line.split('\t')
                    if (fields.size < 3 || fields[0].isBlank() || fields[1].isBlank() || fields[2].isBlank()) return@mapNotNull null
                    Taxon(fields[0].trim(), fields[1].trim(), fields[2].trim(), fields.getOrNull(3)?.trim()?.ifEmpty { null }) to
                        fields.getOrNull(4).orEmpty().split('|').map(String::trim).filter(String::isNotEmpty)
                }
                .toList(),
        )
    }
}
