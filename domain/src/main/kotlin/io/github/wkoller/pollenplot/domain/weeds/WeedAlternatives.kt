package io.github.wkoller.pollenplot.domain.weeds

import io.github.wkoller.pollenplot.domain.plants.AuState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A published list of plants to grow instead of garden weeds, and the states it was written for. */
@Serializable
data class AlternativeSource(val key: String, val title: String, val url: String, val states: List<String>) {
    val auStates: Set<AuState> get() = states.mapNotNull { name -> AuState.entries.firstOrNull { it.name == name } }.toSet()
}

/** One plant a source suggests growing instead of a weed. */
@Serializable
data class Alternative(val species: String, val source: String)

/** A garden weed and the plants its sources suggest growing instead. */
@Serializable
data class WeedWithAlternatives(val weed: String, val common: String? = null, val alternatives: List<Alternative>)

/** A suggested plant with the source that suggests it. */
data class SuggestedAlternative(val speciesName: String, val source: AlternativeSource)

/**
 * Plants to grow instead of garden weeds, as published by sources such as the nursery industry's Grow Me Instead
 * booklets. Each pair names its source, and a source covers the states it was written for.
 */
@Serializable
data class WeedAlternatives(val version: Int, val sources: List<AlternativeSource>, val weeds: List<WeedWithAlternatives>) {
    private val sourceByKey: Map<String, AlternativeSource> by lazy { sources.associateBy { it.key } }
    private val byBinomial: Map<String, WeedWithAlternatives> by lazy { weeds.associateBy { WeedNames.binomial(it.weed) } }

    /**
     * The plants suggested instead of the weed known by [names] (its scientific name and synonyms), each once, from
     * the sources written for [state]; with no state, from every source. The order is the file's.
     */
    fun forWeed(names: Collection<String>, state: AuState?): List<SuggestedAlternative> {
        val entry = names.firstNotNullOfOrNull { byBinomial[WeedNames.binomial(it)] } ?: return emptyList()
        return entry.alternatives.mapNotNull { alt ->
            val source = sourceByKey[alt.source] ?: return@mapNotNull null
            if (state != null && state !in source.auStates) null else SuggestedAlternative(alt.species, source)
        }.distinctBy { it.speciesName }
    }

    /** What is wrong with the file, or nothing: unknown sources and states, sources without links, and empty entries. */
    fun problems(): List<String> = buildList {
        sources.forEach { source ->
            if (!source.url.startsWith("https://")) add("Source ${source.key} needs an https link")
            if (source.states.isEmpty()) add("Source ${source.key} names no states")
            source.states.filter { name -> AuState.entries.none { it.name == name } }.forEach { add("Source ${source.key} names an unknown state $it") }
        }
        sources.groupBy { it.key }.filter { it.value.size > 1 }.keys.forEach { add("Source $it is listed twice") }
        weeds.groupBy { WeedNames.binomial(it.weed) }.filter { it.value.size > 1 }.keys.forEach { add("Weed $it is listed twice") }
        weeds.forEach { weed ->
            if (weed.alternatives.isEmpty()) add("${weed.weed} has no alternatives")
            weed.alternatives.filter { it.source !in sourceByKey }.forEach { add("${weed.weed}: ${it.species} names an unknown source ${it.source}") }
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        val EMPTY = WeedAlternatives(1, emptyList(), emptyList())

        fun parse(text: String): WeedAlternatives = json.decodeFromString(serializer(), text)
    }
}
