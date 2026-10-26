package io.github.wkoller.pollenplot.ml.text

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.AfterClass
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * Checks the tokenizer and the embedder against reference outputs from Hugging Face's tokenizers and ONNX Runtime in
 * Python, for the same model file.
 */
class TextEmbedderTest {
    private class Reference(val text: String, val ids: IntArray, val vector: FloatArray)

    private val references: List<Reference> by lazy {
        val json = javaClass.getResource("/bge_reference.json")!!.readText()
        Json.parseToJsonElement(json).jsonArray.map { item ->
            val o = item.jsonObject
            Reference(
                o["text"]!!.jsonPrimitive.content,
                o["ids"]!!.jsonArray.map { it.jsonPrimitive.int }.toIntArray(),
                o["vector"]!!.jsonArray.map { it.jsonPrimitive.float }.toFloatArray(),
            )
        }
    }

    @Test
    fun `tokens match the reference tokenizer`() {
        for (reference in references) {
            assertArrayEquals(reference.text, reference.ids, tokenizer.encode(reference.text))
        }
    }

    @Test
    fun `long text is cut to the token limit with the end marker kept`() {
        val short = WordPieceTokenizer(vocabulary, maxTokens = 8)
        val ids = short.encode("one two three four five six seven eight nine ten")
        assertEquals(8, ids.size)
        assertEquals(vocabulary.indexOf("[SEP]"), ids.last())
    }

    @Test
    fun `vectors match the reference model run`() {
        for (reference in references) {
            val vector = embedder.embedPassage(reference.text)
            assertEquals(TextEmbedder.DIMENSIONS, vector.size)
            assertTrue("${reference.text}: ${dot(vector, reference.vector)}", dot(vector, reference.vector) > 0.9999f)
        }
    }

    @Test
    fun `a question finds the record that means the same`() {
        val grevillea = embedder.embedPassage("Grevillea Robyn Gordon in Front Native Strip. Pollinator magnet, bird feeder, local native.")
        val lemon = embedder.embedPassage("Mum's lemon, Citrus × limon, in Back Citrus Patch. Edible. High thirst.")
        val birds = embedder.embedQuery("plants that attract birds")
        val fruit = embedder.embedQuery("fruit trees")
        assertTrue(dot(birds, grevillea) > dot(birds, lemon))
        assertTrue(dot(fruit, lemon) > dot(fruit, grevillea))
    }

    private fun dot(a: FloatArray, b: FloatArray) = a.indices.fold(0f) { sum, i -> sum + a[i] * b[i] }

    companion object {
        private lateinit var vocabulary: List<String>
        private lateinit var tokenizer: WordPieceTokenizer
        private lateinit var embedder: TextEmbedder

        @BeforeClass
        @JvmStatic
        fun load() {
            vocabulary = File(System.getProperty("bge.vocab")).readLines()
            tokenizer = WordPieceTokenizer(vocabulary)
            embedder = TextEmbedder(System.getProperty("bge.model"), tokenizer)
        }

        @AfterClass
        @JvmStatic
        fun close() = embedder.close()
    }
}
