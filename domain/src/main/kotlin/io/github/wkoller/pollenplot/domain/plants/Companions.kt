package io.github.wkoller.pollenplot.domain.plants

/** A companion pairing seen from one plant's side: the other plant, and the plants in the garden that are that plant. */
data class CompanionAdvice(val pair: CompanionPair, val otherName: String, val inGarden: List<Species>)

object Companions {
    /** Whether one side of a pairing (a species, genus or family name) covers [species]. */
    fun covers(side: String, species: Species): Boolean {
        val s = KnowledgeBase.normalise(side)
        return s == KnowledgeBase.normalise(species.name) ||
            species.synonyms.any { KnowledgeBase.normalise(it) == s } ||
            s == KnowledgeBase.normalise(species.genus) ||
            s == KnowledgeBase.normalise(species.family)
    }

    /**
     * The pairings for [species], each with the garden plants that are its companion. Pairings that apply to plants in
     * the garden come first, then the rest as suggestions.
     */
    fun forSpecies(kb: KnowledgeBase, species: Species, garden: Collection<Species>): List<CompanionAdvice> =
        kb.companions.mapNotNull { pair ->
            val other = when {
                covers(pair.a, species) -> pair.b
                covers(pair.b, species) -> pair.a
                else -> return@mapNotNull null
            }
            val matches = garden.filter { it.name != species.name && covers(other, it) }.distinctBy { it.name }
            CompanionAdvice(pair, other, matches)
        }.sortedWith(compareBy({ it.inGarden.isEmpty() }, { it.pair.relation }, { it.otherName }))
}
