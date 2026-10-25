package io.github.wkoller.pollenplot.domain.plants

/**
 * The plant knowledge base: species, the genera and families that fill gaps, and companion pairings. It is read-only
 * reference data that ships with the app, so it is held in memory and looked up by name.
 */
class KnowledgeBase(
    val version: Int,
    species: List<Species>,
    genera: List<GroupProfile>,
    families: List<GroupProfile>,
    val companions: List<CompanionPair>,
) {
    val allSpecies: List<Species> = species.sortedBy { it.displayName.lowercase() }
    val allGenera: List<GroupProfile> = genera
    val allFamilies: List<GroupProfile> = families

    private val byName: Map<String, Species> = buildMap {
        species.forEach { s ->
            put(normalise(s.name), s)
            s.synonyms.forEach { put(normalise(it), s) }
        }
    }
    private val generaByName = genera.associateBy { normalise(it.name) }
    private val familiesByName = families.associateBy { normalise(it.name) }

    /** The species with this scientific name or synonym, ignoring case, spacing and the style of hybrid sign. */
    fun species(name: String): Species? = byName[normalise(name)]

    fun genus(name: String): GroupProfile? = generaByName[normalise(name)]

    fun family(name: String): GroupProfile? = familiesByName[normalise(name)]

    /** Whether [name] is a genus that appears in the knowledge base, as a genus record or through its species. */
    fun isKnownGenus(name: String): Boolean = genus(name) != null || allSpecies.any { normalise(it.genus) == normalise(name) }

    /**
     * Species whose common or scientific name, genus or family matches [query]. Names that start with the query come
     * first, then names with a word that starts with it, then any other match.
     */
    fun search(query: String, limit: Int = 30): List<Species> {
        val q = normalise(query)
        if (q.isEmpty()) return emptyList()
        return allSpecies
            .mapNotNull { s ->
                val names = s.common + s.name + s.synonyms
                val rank = names.minOf { name -> rank(normalise(name), q) }
                    .coerceAtMost(if (normalise(s.genus) == q || normalise(s.family) == q) 3 else Int.MAX_VALUE)
                rank.takeIf { it < Int.MAX_VALUE }?.let { s to it }
            }
            .sortedWith(compareBy({ it.second }, { it.first.displayName.lowercase() }))
            .take(limit)
            .map { it.first }
    }

    private fun rank(name: String, q: String): Int = when {
        name == q -> 0
        name.startsWith(q) -> 1
        name.split(' ', '-', '\'').any { it.startsWith(q) } -> 2
        name.contains(q) -> 4
        else -> Int.MAX_VALUE
    }

    companion object {
        /** Lower case, single spaces, plain quotes, and "×" for a hybrid whether it was typed as "x" or "×". */
        fun normalise(name: String): String = name.trim().lowercase()
            .replace('‘', '\'').replace('’', '\'')
            .replace(Regex("""\s+"""), " ")
            .replace(Regex("""(^| )x """), "$1× ")

        /** Reads the knowledge base files. Every problem found is returned, and entries with problems are still kept where possible. */
        fun parse(files: KbFiles): ParseResult {
            val problems = mutableListOf<String>()
            val reader = KbReader(problems)
            val version = runCatching { reader.json.decodeFromString(MetaJson.serializer(), files.meta).version }
                .getOrElse {
                    problems += "meta.json: ${it.message}"
                    0
                }
            val genera = reader.decodeList("genera.json", files.genera, GroupJson.serializer()).map { g ->
                GroupProfile(g.name, g.family, g.form?.let { reader.enumOf<GrowthForm>(g.name, "form", it) }, reader.care(g.name, g.care()))
            }
            val families = reader.decodeList("families.json", files.families, GroupJson.serializer()).map { f ->
                GroupProfile(f.name, null, f.form?.let { reader.enumOf<GrowthForm>(f.name, "form", it) }, reader.care(f.name, f.care()))
            }
            val genusFamily = genera.associate { normalise(it.name) to it.family }
            val species = files.species.flatMap { (file, text) -> reader.decodeList(file, text, SpeciesJson.serializer()) }.mapNotNull { s ->
                val genus = s.genus ?: s.name.substringBefore(' ')
                val family = s.family ?: genusFamily[normalise(genus)]
                if (family == null) {
                    problems += "${s.name}: no family, and the genus $genus is not in genera.json"
                    return@mapNotNull null
                }
                if (s.common.isEmpty()) problems += "${s.name}: needs at least one common name"
                if (s.description.isNullOrBlank()) problems += "${s.name}: needs a description"
                Species(
                    name = s.name,
                    genus = genus,
                    family = family,
                    common = s.common,
                    form = s.form?.let { reader.enumOf<GrowthForm>(s.name, "form", it) },
                    nativeStates = s.native.mapNotNull { reader.enumOf<AuState>(s.name, "native", it) }.toSet(),
                    wons = s.wons,
                    synonyms = s.synonyms,
                    care = reader.care(s.name, s.care()),
                )
            }
            val seen = mutableMapOf<String, String>()
            species.forEach { s ->
                (listOf(s.name) + s.synonyms).forEach { name ->
                    val key = normalise(name)
                    seen[key]?.let { other -> problems += "$name is used by both ${s.name} and $other" }
                    seen[key] = s.name
                }
            }
            val familyNames = families.map { normalise(it.name) }.toSet()
            (species.map { it.family } + genera.mapNotNull { it.family }).toSet().forEach { family ->
                if (normalise(family) !in familyNames) problems += "The family $family is used but not described in families.json"
            }
            val kb = KnowledgeBase(version, species, genera, families, emptyList())
            val companions = reader.decodeList("companions.json", files.companions, CompanionJson.serializer()).mapNotNull { c ->
                val relation = reader.enumOf<CompanionRelation>("companion ${c.a} and ${c.b}", "relation", c.relation)
                val evidence = reader.enumOf<Evidence>("companion ${c.a} and ${c.b}", "evidence", c.evidence)
                listOf(c.a, c.b).forEach { side ->
                    if (kb.species(side) == null && !kb.isKnownGenus(side) && kb.family(side) == null) {
                        problems += "companions.json: $side is not a species, genus or family in the knowledge base"
                    }
                }
                if (relation == null || evidence == null) null else CompanionPair(c.a, c.b, relation, c.reason, evidence)
            }
            return ParseResult(KnowledgeBase(version, species, genera, families, companions), problems)
        }
    }
}

/** The text of each knowledge base file. [species] maps a file name to its text, as there are several species files. */
data class KbFiles(
    val meta: String,
    val species: Map<String, String>,
    val genera: String,
    val families: String,
    val companions: String,
)

data class ParseResult(val knowledgeBase: KnowledgeBase, val problems: List<String>)
