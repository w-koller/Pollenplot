package io.github.wkoller.pollenplot.domain.wildlife

import io.github.wkoller.pollenplot.domain.plants.AuState
import io.github.wkoller.pollenplot.domain.plants.KnowledgeBase
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** What a living thing in a photo is, broadly: plants, and the groups the Wildlife tab files sightings under. */
enum class LifeGroup {
    PLANT, BIRD, INSECT, SPIDER, REPTILE, FROG, MAMMAL, FISH, MOLLUSC, FUNGUS, OTHER;

    /** Whether sightings of it belong on the Wildlife tab: every group but plants, fungi included. */
    val isWildlife: Boolean get() = this != PLANT

    companion object {
        /** A group by its name in the species index and the knowledge base, such as "bird". */
        fun of(name: String?): LifeGroup? = entries.firstOrNull { it.name.equals(name?.trim(), ignoreCase = true) }
    }
}

/** What an animal does in a garden. */
enum class FaunaRole { POLLINATOR, PEST_CONTROLLER, PLANT_PEST, SEED_DISPERSER, DECOMPOSER }

/** How an animal or fungus can hurt people or pets. */
enum class Hazard { VENOMOUS, STINGS, BITES, SWOOPS, POISONOUS }

/** What the law asks about an animal in some states, in plain words, with the official page it comes from. */
data class AnimalRule(val states: Set<AuState>, val text: String, val report: Boolean, val source: String)

/** One animal or fungus in the knowledge base. Its [name] is the scientific name and its key. */
data class Animal(
    val name: String,
    val common: List<String>,
    val group: LifeGroup,
    val family: String,
    /** The states it is native to; empty for an animal brought to Australia. */
    val nativeStates: Set<AuState>,
    val roles: Set<FaunaRole>,
    /** The genera or species it feeds on, lives on or nests in, as the plant knowledge base names them. */
    val plants: List<String>,
    val plantNote: String?,
    val habitat: String?,
    val hazard: Hazard?,
    val hazardNote: String?,
    val rules: List<AnimalRule>,
    val description: String,
    val synonyms: List<String>,
) {
    val isNative: Boolean get() = nativeStates.isNotEmpty()

    val displayName: String get() = common.firstOrNull() ?: name

    /** The rules that apply in [state]. */
    fun rulesIn(state: AuState?): List<AnimalRule> = if (state == null) emptyList() else rules.filter { state in it.states }
}

/** The animals and fungi the app knows about, from app/src/main/assets/kb/fauna. */
class FaunaBase(animals: List<Animal>) {
    val all: List<Animal> = animals.sortedBy { it.displayName.lowercase() }

    private val byName: Map<String, Animal> = buildMap {
        animals.forEach { animal -> (listOf(animal.name) + animal.synonyms).forEach { put(KnowledgeBase.normalise(it), animal) } }
    }

    /** The animal with this scientific name or synonym, ignoring case and spacing. */
    fun animal(name: String): Animal? = byName[KnowledgeBase.normalise(name)]

    /** The animals that use a plant, by its species name or genus. */
    fun usersOf(speciesName: String): List<Animal> {
        val species = KnowledgeBase.normalise(speciesName)
        val genus = species.substringBefore(' ')
        return all.filter { animal -> animal.plants.any { KnowledgeBase.normalise(it).let { p -> p == species || p == genus } } }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = false }

        /** The animals in each fauna file, with a message for anything that does not fit, as the plant files have. */
        fun parse(files: Map<String, String>): FaunaParseResult {
            val problems = mutableListOf<String>()
            val animals = files.flatMap { (file, text) ->
                try {
                    json.decodeFromString(ListSerializer(AnimalJson.serializer()), text).map { file to it }
                } catch (error: SerializationException) {
                    problems += "$file: ${error.message}"
                    emptyList()
                } catch (error: IllegalArgumentException) {
                    problems += "$file: ${error.message}"
                    emptyList()
                }
            }.mapNotNull { (file, a) -> a.toAnimal("$file: ${a.name}", problems) }
            val seen = mutableMapOf<String, String>()
            animals.forEach { animal ->
                (listOf(animal.name) + animal.synonyms).forEach { name ->
                    val key = KnowledgeBase.normalise(name)
                    seen[key]?.let { other -> problems += "$name is used by both ${animal.name} and $other" }
                    seen[key] = animal.name
                }
            }
            return FaunaParseResult(FaunaBase(animals), problems)
        }
    }
}

data class FaunaParseResult(val fauna: FaunaBase, val problems: List<String>)

@Serializable
internal data class RuleJson(val states: List<String>, val text: String, val report: Boolean = false, val source: String)

@Serializable
internal data class AnimalJson(
    val name: String,
    val common: List<String> = emptyList(),
    val group: String,
    val family: String? = null,
    val native: List<String> = emptyList(),
    val introduced: Boolean = false,
    val roles: List<String> = emptyList(),
    val plants: List<String> = emptyList(),
    @SerialName("plant_note") val plantNote: String? = null,
    val habitat: String? = null,
    val hazard: String? = null,
    @SerialName("hazard_note") val hazardNote: String? = null,
    val rules: List<RuleJson> = emptyList(),
    val description: String? = null,
    val synonyms: List<String> = emptyList(),
) {
    fun toAnimal(where: String, problems: MutableList<String>): Animal? {
        val start = problems.size
        val group = LifeGroup.of(group)
        if (group == null || group == LifeGroup.PLANT) problems += "$where: \"${this.group}\" is not an animal or fungus group"
        if (common.isEmpty()) problems += "$where: needs at least one common name"
        if (family.isNullOrBlank()) problems += "$where: needs a family"
        if (description.isNullOrBlank()) problems += "$where: needs a description"
        if (native.isEmpty() == !introduced) problems += "$where: give the states it is native to, or say it is introduced, not both"
        val states = native.mapNotNull { name -> AuState.entries.firstOrNull { it.name == name } ?: null.also { problems += "$where: unknown state $name" } }
        val roles = roles.mapNotNull { name -> FaunaRole.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: null.also { problems += "$where: unknown role $name" } }
        val hazard = hazard?.let { name -> Hazard.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: null.also { problems += "$where: unknown hazard $name" } }
        if (hazard != null && hazardNote.isNullOrBlank()) problems += "$where: a hazard needs a note saying what to do"
        val rules = rules.map { rule ->
            if (!rule.source.startsWith("https://")) problems += "$where: a rule's source must be an https address"
            if (rule.states.isEmpty()) problems += "$where: a rule must name its states"
            AnimalRule(
                states = rule.states.mapNotNull { name -> AuState.entries.firstOrNull { it.name == name } ?: null.also { problems += "$where: unknown state $name in a rule" } }.toSet(),
                text = rule.text,
                report = rule.report,
                source = rule.source,
            )
        }
        if (problems.size > start || group == null) return null
        return Animal(name, common, group, family!!, states.toSet(), roles.toSet(), plants, plantNote, habitat, hazard, hazardNote, rules, description!!, synonyms)
    }
}
