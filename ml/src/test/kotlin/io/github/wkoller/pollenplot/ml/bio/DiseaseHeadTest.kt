package io.github.wkoller.pollenplot.ml.bio

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * The plant problems head works out the same shares as disease_head.head_shares in Python: on a small head with
 * random numbers (tools/models/fixtures/disease_head_reference.json, written by `disease_head.py fixture`), and, once
 * a pack is published and pinned, on the pack itself with the vectors and shares its reference.json holds.
 */
class DiseaseHeadTest {
    private val fixtures = File(System.getProperty("biocap.fixtures") ?: error("biocap.fixtures is set by ml/build.gradle.kts"))

    @Test
    fun `the head gives Python's shares for the reference head`() {
        val data = Json.parseToJsonElement(File(fixtures, "disease_head_reference.json").readText()).jsonObject
        val classes = data["classes"]!!.jsonArray.map { it.jsonPrimitive.content }
        val head = DiseaseHead.read(
            classes,
            hex(data["weights_f16"]!!.jsonPrimitive.content),
            hex(data["bias_f16"]!!.jsonPrimitive.content),
            data["dimensions"]!!.jsonPrimitive.int,
            data["temperature"]!!.jsonPrimitive.float,
        )
        val cases = data["cases"]!!.jsonArray.map { it.jsonObject }
        assertTrue(cases.isNotEmpty())
        cases.forEach { check(head, it) }
    }

    @Test
    fun `shares sum to one and a hotter temperature evens them out`() {
        val head = DiseaseHead(listOf("healthy", "rust"), floatArrayOf(4f, 0f, -4f, 0f), floatArrayOf(0f, 0f), dimensions = 2, temperature = 1f)
        val cool = head.shares(floatArrayOf(1f, 0f))
        assertEquals(1f, cool.sum(), 1e-6f)
        assertEquals(1f / (1f + kotlin.math.exp(-8f)), cool[0], 1e-6f)
        val warm = DiseaseHead(listOf("healthy", "rust"), floatArrayOf(4f, 0f, -4f, 0f), floatArrayOf(0f, 0f), dimensions = 2, temperature = 4f).shares(floatArrayOf(1f, 0f))
        assertTrue(warm[0] < cool[0] && warm[0] > 0.5f)
        assertEquals(mapOf("healthy" to 9997, "rust" to 3), head.sharesBp(floatArrayOf(1f, 0f)))
    }

    @Test
    fun `the published pack gives its own reference shares`() {
        val dir = System.getProperty("disease.dir")?.let(::File)
        assumeTrue("The plant problems pack is not published and pinned yet", dir?.isDirectory == true)
        val manifest = Json.parseToJsonElement(File(dir, "manifest.json").readText()).jsonObject
        val head = DiseaseHead.read(
            manifest["classes"]!!.jsonArray.map { it.jsonPrimitive.content },
            File(dir, "weights.f16").readBytes(),
            File(dir, "bias.f16").readBytes(),
            manifest["embedding_dim"]!!.jsonPrimitive.int,
            manifest["temperature"]!!.jsonPrimitive.float,
        )
        val cases = Json.parseToJsonElement(File(dir, "reference.json").readText()).jsonArray.map { it.jsonObject }
        assertTrue(cases.isNotEmpty())
        cases.forEach { check(head, it) }
    }

    private fun check(head: DiseaseHead, case: JsonObject) {
        val vector = case["vector"]!!.jsonArray.map { it.jsonPrimitive.float }.toFloatArray()
        val expected = case["shares"]!!.jsonArray.map { it.jsonPrimitive.float }
        val shares = head.shares(vector)
        expected.forEachIndexed { i, share -> assertEquals("${head.classes[i]}", share, shares[i], 1e-5f) }
    }

    private fun hex(text: String) = ByteArray(text.length / 2) { text.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
