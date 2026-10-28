package io.github.wkoller.pollenplot.domain.assistant

import io.github.wkoller.pollenplot.domain.plants.AuState
import io.github.wkoller.pollenplot.domain.plants.CareProfile
import io.github.wkoller.pollenplot.domain.plants.Species
import io.github.wkoller.pollenplot.domain.plants.Toxicity
import io.github.wkoller.pollenplot.domain.season.MonthSet
import io.github.wkoller.pollenplot.domain.wildlife.Animal
import java.time.format.TextStyle
import java.util.Locale

/**
 * What the knowledge bases and the garden's records say about one plant or animal, as short plain lines for the
 * language model. A fact the knowledge base does not have is left out rather than guessed, and nothing says whether
 * something can be eaten: the assistant never decides that.
 */
object AssistantFacts {
    fun plant(title: String, species: Species?, care: CareProfile?, zoneName: String?, quantity: Int, weedStatus: String?): List<String> = buildList {
        add(listOfNotNull(title, species?.let { "${it.name}, family ${it.family}" }).joinToString(", "))
        species?.form?.let { add("Growth form: ${words(it.name)}") }
        species?.let {
            add(if (it.isAustralianNative) "Native to ${it.nativeStates.sortedBy { s -> s.ordinal }.joinToString(", ") { s -> s.name }}" else "Not native to Australia")
        }
        add(if (zoneName != null) "In the garden: $quantity in $zoneName" else "In the garden: $quantity, not in a zone")
        weedStatus?.let { add("Weed status where the garden is, from the official lists: $it") }
        care ?: return@buildList
        care.description?.let { add(it) }
        care.sun?.let { sun -> add("Light: ${sun.sortedBy { it.ordinal }.joinToString(", ") { words(it.name) }}") }
        care.water?.let { add("Water need once established: ${words(it.name)}") }
        care.drought?.let { add("Drought tolerance: $it of 3") }
        care.frost?.let { add("Frost tolerance: $it of 3") }
        care.salt?.let { add("Salt tolerance: $it of 3") }
        care.soils?.let { soils -> add("Soils: ${soils.sortedBy { it.ordinal }.joinToString(", ") { words(it.name) }}") }
        val phMin = care.phMinTenths
        val phMax = care.phMaxTenths
        if (phMin != null && phMax != null) add("Soil pH: ${phMin / 10.0} to ${phMax / 10.0}")
        care.fertiliser?.let { add("Feeding: ${words(it.name)}") }
        care.fertiliserNote?.let { add(it) }
        care.compostNote?.let { add(it) }
        care.wildlife?.let { w ->
            add("Wildlife value, 0 to 3: pollinators ${w.pollinator}, butterflies ${w.butterflies}, birds ${w.birds}, beneficial insects ${w.beneficials}, frogs ${w.frogs}")
            if (w.butterflyHost) add("Its leaves feed butterfly caterpillars")
        }
        care.forage?.let { f ->
            add("Bee forage, 0 to 3: honey bees ${f.honeyBest}, native bees ${f.nativeBest}${if (f.buzz) "; its flowers need buzz pollination" else ""}")
        }
        care.flowering?.let { add("Flowers in: ${months(it)}") }
        care.height?.let { add("Height: ${it.minMm / 1000.0} to ${it.maxMm / 1000.0} m") }
        care.width?.let { add("Width: ${it.minMm / 1000.0} to ${it.maxMm / 1000.0} m") }
        care.pruning?.let { add("Pruning: $it") }
        care.toxicPets?.let { add("Toxic to pets: ${toxicity(it)}") }
        care.toxicPeople?.let { add("Toxic to people: ${toxicity(it)}") }
        care.toxicNote?.let { add(it) }
        care.careNote?.let { add(it) }
    }

    fun animal(animal: Animal, state: AuState?, sightings: Int): List<String> = buildList {
        add("${animal.displayName}, ${animal.name}, family ${animal.family}, a ${words(animal.group.name)}")
        add(if (animal.isNative) "Native to ${animal.nativeStates.sortedBy { it.ordinal }.joinToString(", ") { it.name }}" else "Introduced to Australia")
        add(animal.description)
        if (animal.roles.isNotEmpty()) add("In a garden: ${animal.roles.sortedBy { it.ordinal }.joinToString(", ") { words(it.name) }}")
        animal.plantNote?.let { add(it) }
        if (animal.plants.isNotEmpty()) add("Plants it uses: ${animal.plants.joinToString(", ")}")
        animal.habitat?.let { add("What it needs: $it") }
        animal.hazard?.let { add("Hazard: ${words(it.name)}. ${animal.hazardNote.orEmpty()}".trim()) }
        animal.rulesIn(state).forEach { add("The law where the garden is: ${it.text}") }
        add("Seen in the yard: $sightings ${if (sightings == 1) "time" else "times"}")
    }

    private fun words(name: String) = name.lowercase().replace('_', ' ')

    private fun toxicity(level: Toxicity) = when (level) {
        Toxicity.NONE -> "not known to be"
        Toxicity.MILD -> "mildly"
        Toxicity.SEVERE -> "severely"
    }

    private fun months(set: MonthSet) = set.months.joinToString(", ") { it.getDisplayName(TextStyle.FULL, Locale.ENGLISH) }
}
