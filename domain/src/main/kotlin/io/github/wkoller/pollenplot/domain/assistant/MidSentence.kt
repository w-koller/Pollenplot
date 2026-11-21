package io.github.wkoller.pollenplot.domain.assistant

/**
 * A common name as it reads in the middle of a sentence. "Blue-banded bee" becomes "blue-banded bee" and "Scale"
 * "scale", while a name that starts with a place, a people or a person, such as "Australian magpie", "Queensland fruit
 * fly" or "Lewin's honeyeater", keeps its capital.
 */
object MidSentence {
    fun name(name: String): String {
        val first = name.substringBefore(' ').substringBefore('-')
        if (first.isEmpty() || !first[0].isUpperCase()) return name
        val proper = first.drop(1).any(Char::isUpperCase) || first.endsWith("'s") || first.endsWith("’s") || first in PROPER
        return if (proper) name else name.replaceFirstChar(Char::lowercaseChar)
    }

    /** The first words of Australian common names that are places or peoples. */
    private val PROPER = setOf(
        "Australian", "Asian", "African", "American", "European", "Indian", "Chinese", "Japanese", "Mexican", "Argentine",
        "Brazilian", "Madagascar", "Queensland", "Tasmanian", "Victorian", "Torresian", "Sydney", "Brisbane", "Melbourne",
        "Darwin", "Christmas", "Moreton", "Gould", "Lewin", "Gippsland", "Kimberley", "Murray", "Norfolk",
    )
}
