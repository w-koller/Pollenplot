package io.github.wkoller.pollenplot.domain.id

import io.github.wkoller.pollenplot.domain.wildlife.FaunaBase

/**
 * What a review card adds for the suggestion or species chosen on it, so a card and a selection of cards added at once
 * name each find the same way. [picked] is a species the user searched for, which wins over the suggestion at [index].
 */
object ReviewPick {
    /** A plant to add: its species when the knowledge base may know it, or the model's own name to match up later. */
    data class Plant(val speciesName: String?, val customName: String?)

    /** A sighting to add: its scientific name when known, and the name to show for it. */
    data class Animal(val speciesName: String?, val label: String)

    /** The plant to add, or null when nothing is chosen because the models made no suggestion. */
    fun plant(candidates: List<Candidate>, picked: String? = null, index: Int = 0): Plant? {
        if (picked != null) return Plant(picked, null)
        val candidate = candidates.getOrNull(index) ?: return null
        return Plant(candidate.speciesName, if (candidate.speciesName == null) candidate.label else null)
    }

    /**
     * The sighting to add, or null when nothing is chosen. An animal the knowledge base does not have is named by the
     * model's common name and keeps its scientific name; a name the user typed may be a common name, so it is only the
     * label.
     */
    fun animal(candidates: List<Candidate>, fauna: FaunaBase?, picked: String? = null, index: Int = 0): Animal? {
        val candidate = if (picked == null) candidates.getOrNull(index) ?: return null else null
        val name = picked ?: candidate!!.let { it.speciesName ?: it.label }
        val animal = fauna?.animal(name)
        val common = candidate?.common.takeIf { animal == null }
        val scientific = animal?.name ?: candidate?.let { it.speciesName ?: it.label }
        return Animal(scientific, animal?.displayName ?: common ?: name)
    }
}
