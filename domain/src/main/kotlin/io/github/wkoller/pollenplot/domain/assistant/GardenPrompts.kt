package io.github.wkoller.pollenplot.domain.assistant

/** A record from the garden the assistant was given for a question about the garden, which its answer may cite. */
data class CitedRecord(val id: String, val kind: RecordKind, val title: String, val text: String)

enum class RecordKind { PLANT, ZONE, SIGHTING }

/** What the language model is told: its rules, the facts for a question, and how its answer cites them. */
object GardenPrompts {
    /** The rules the model is given before every question. */
    val SYSTEM: String = """
        You are the garden assistant in Pollenplot, an app for backyards in Australia, and you run on the user's phone.
        Answer in plain Australian English, in a few short sentences or a short list.
        Use the facts you are given. If they do not answer the question, say so, and say where the user could find out, such as a local nursery, their council or their state's agriculture department.
        Never say how much of any chemical, pesticide, herbicide or hive treatment to use. Say to follow the product's label.
        Never say whether a plant, fruit, mushroom or animal is safe to eat, touch or handle.
        Never say whether a plant is a weed where the user lives, except by repeating the official status in the facts.
        Do not make up records, names, dates or numbers.
    """.trimIndent()

    /** A question about one plant or animal, with what is known about it. */
    fun about(facts: List<String>, question: String): String =
        "Facts about it:\n" + facts.joinToString("\n") { "- $it" } + "\n\nQuestion: ${question.trim()}"

    /**
     * A question about the whole garden, with a [summary] of it and the records that seemed to match, numbered so the
     * answer can cite them.
     */
    fun garden(summary: String, records: List<CitedRecord>, question: String): String = buildString {
        appendLine("The garden: $summary")
        appendLine()
        if (records.isEmpty()) {
            appendLine("No records in the user's garden matched the question.")
        } else {
            appendLine("Records from the user's garden, each with its number:")
            records.forEachIndexed { i, record -> appendLine("[${i + 1}] ${record.text}") }
            appendLine()
            appendLine("When you use a record, cite its number in square brackets, such as [2].")
        }
        append("Question: ${question.trim()}")
    }

    /** The records an answer cites, by number, in the order it first cites them; numbers with no record are left out. */
    fun citations(answer: String, records: List<CitedRecord>): List<CitedRecord> = CITE.findAll(answer)
        .mapNotNull { it.groupValues[1].toIntOrNull() }
        .filter { it in 1..records.size }
        .distinct()
        .map { records[it - 1] }
        .toList()

    /** Which of the suggestions the cut-out shows, asked for as a single number, with 0 for none of them. */
    fun secondOpinion(suggestions: List<String>): String = buildString {
        appendLine("The photo shows one plant or animal from a garden in Australia. Which of these is it?")
        suggestions.forEachIndexed { i, name -> appendLine("${i + 1}. $name") }
        append("Answer with the number only, or 0 if it is none of them.")
    }

    /** The suggestion the model chose, by its index from 0, or null for none of them or an answer that is not a number. */
    fun choice(answer: String, count: Int): Int? = Regex("\\d+").find(answer)?.value?.toIntOrNull()?.takeIf { it in 1..count }?.minus(1)

    /** The words of a question worth looking for in the garden's records, without the words every question has. */
    fun searchWords(question: String): List<String> = question.lowercase().split(Regex("[^\\p{L}\\p{N}]+"))
        .filter { it.length >= 3 && it !in STOP_WORDS }
        .distinct()

    private val CITE = Regex("\\[(\\d{1,2})]")

    private val STOP_WORDS = setOf(
        "what", "which", "who", "whom", "whose", "when", "where", "why", "how", "does", "did", "doing", "done", "the",
        "and", "for", "are", "was", "were", "has", "have", "had", "with", "this", "that", "these", "those", "there",
        "their", "they", "them", "from", "into", "onto", "about", "any", "all", "can", "could", "should", "would",
        "will", "shall", "may", "might", "must", "our", "your", "you", "its", "not", "but", "yet", "too", "very",
        "much", "many", "more", "most", "some", "get", "got", "been", "being", "than", "then", "also", "just", "only",
        "here", "out", "off", "over", "under", "yard", "garden", "backyard", "seen", "see", "saw", "grow", "growing",
        "best", "good", "well", "need", "needs", "like", "likes", "lot", "lots", "time", "year", "every", "each", "near",
    )
}
