package io.github.wkoller.pollenplot.ml.text

import java.text.Normalizer

/**
 * BERT's uncased WordPiece tokenizer, as bge-small-en-v1.5 uses it. Text is cleaned, lower-cased and stripped of
 * accents; it is split at spaces and punctuation; and each word is split into the longest pieces the vocabulary has,
 * later pieces marked with "##". The result starts with [CLS] and ends with [SEP], and is cut to [maxTokens].
 */
class WordPieceTokenizer(vocabulary: List<String>, private val maxTokens: Int = DEFAULT_MAX_TOKENS) {
    private val ids: Map<String, Int> = HashMap<String, Int>(vocabulary.size * 2).apply {
        vocabulary.forEachIndexed { index, token -> putIfAbsent(token, index) }
    }
    private val cls = idOf("[CLS]")
    private val sep = idOf("[SEP]")
    private val unknown = idOf("[UNK]")

    init {
        require(maxTokens >= 2) { "There must be room for [CLS] and [SEP]" }
    }

    private fun idOf(token: String): Int = requireNotNull(ids[token]) { "The vocabulary has no $token" }

    /** The token IDs for [text]. */
    fun encode(text: String): IntArray {
        val pieces = ArrayList<Int>()
        pieces += cls
        for (word in words(text)) {
            for (id in wordPieces(word)) {
                if (pieces.size == maxTokens - 1) break
                pieces += id
            }
            if (pieces.size == maxTokens - 1) break
        }
        pieces += sep
        return pieces.toIntArray()
    }

    /** The words of [text] after cleaning: lower case, no accents, punctuation split off as words of its own. */
    internal fun words(text: String): List<String> {
        val cleaned = StringBuilder(text.length)
        text.codePoints().forEach { c ->
            when {
                c == 0 || c == 0xFFFD || isControl(c) -> Unit
                isWhitespace(c) -> cleaned.append(' ')
                // Chinese, Japanese and Korean ideographs are words of their own.
                isIdeograph(c) -> cleaned.append(' ').appendCodePoint(c).append(' ')
                else -> cleaned.appendCodePoint(c)
            }
        }
        val folded = Normalizer.normalize(cleaned.toString().lowercase(), Normalizer.Form.NFD)
            .filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
        val words = ArrayList<String>()
        val current = StringBuilder()
        fun flush() {
            if (current.isNotEmpty()) words += current.toString()
            current.setLength(0)
        }
        folded.codePoints().forEach { c ->
            when {
                isWhitespace(c) -> flush()
                isPunctuation(c) -> {
                    flush()
                    words += String(Character.toChars(c))
                }
                else -> current.appendCodePoint(c)
            }
        }
        flush()
        return words
    }

    /** The longest-first pieces of one word, or [UNK] if the word cannot be made from the vocabulary. */
    internal fun wordPieces(word: String): List<Int> {
        val points = word.codePoints().toArray()
        if (points.size > MAX_WORD_CHARS) return listOf(unknown)
        val pieces = ArrayList<Int>()
        var start = 0
        while (start < points.size) {
            var end = points.size
            var found: Int? = null
            while (start < end) {
                val piece = (if (start > 0) "##" else "") + String(points, start, end - start)
                found = ids[piece]
                if (found != null) break
                end--
            }
            if (found == null) return listOf(unknown)
            pieces += found
            start = end
        }
        return pieces
    }

    private fun isWhitespace(c: Int) = c == ' '.code || c == '\t'.code || c == '\n'.code || c == '\r'.code ||
        Character.getType(c) == Character.SPACE_SEPARATOR.toInt()

    private fun isControl(c: Int): Boolean {
        if (c == '\t'.code || c == '\n'.code || c == '\r'.code) return false
        val type = Character.getType(c)
        return type == Character.CONTROL.toInt() || type == Character.FORMAT.toInt()
    }

    /** BERT counts every non-letter, non-digit ASCII symbol as punctuation, as well as Unicode punctuation. */
    private fun isPunctuation(c: Int): Boolean {
        if (c in 33..47 || c in 58..64 || c in 91..96 || c in 123..126) return true
        return when (Character.getType(c).toByte()) {
            Character.CONNECTOR_PUNCTUATION, Character.DASH_PUNCTUATION, Character.START_PUNCTUATION,
            Character.END_PUNCTUATION, Character.INITIAL_QUOTE_PUNCTUATION, Character.FINAL_QUOTE_PUNCTUATION,
            Character.OTHER_PUNCTUATION,
            -> true
            else -> false
        }
    }

    private fun isIdeograph(c: Int) = c in 0x4E00..0x9FFF || c in 0x3400..0x4DBF || c in 0x20000..0x2A6DF ||
        c in 0x2A700..0x2B73F || c in 0x2B740..0x2B81F || c in 0x2B820..0x2CEAF || c in 0xF900..0xFAFF ||
        c in 0x2F800..0x2FA1F

    companion object {
        /** Garden records are a sentence or two, so this is plenty and keeps each run quick. */
        const val DEFAULT_MAX_TOKENS = 128

        /** BERT treats longer words as unknown rather than splitting them. */
        private const val MAX_WORD_CHARS = 100
    }
}
