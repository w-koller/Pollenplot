package io.github.wkoller.pollenplot.ml.bio

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

/**
 * Runs the published accurate pack's image encoder with ONNX Runtime on the test images in tools/models/fixtures and
 * compares the vectors and best matches with those the export made in Python (biocap_reference.json). The images are
 * 224 x 224, so this checks the pixel preparation, the model run and the species index, not the scaling.
 */
class BioCapTest {
    private val pack = System.getProperty("biocap.dir")?.let(::File)
    private val fixtures = File(System.getProperty("biocap.fixtures") ?: error("biocap.fixtures is set by ml/build.gradle.kts"))

    @Before
    fun packIsPinned() = assumeTrue("The accurate pack is not published and pinned yet", pack?.isDirectory == true)

    @Test
    fun `the encoder gives the export's vectors and matches`() {
        val dir = pack!!
        val manifest = Json.parseToJsonElement(File(dir, "manifest.json").readText()).jsonObject
        val index = SpeciesIndex.read(File(dir, "species.tsv").readLines(), File(dir, "species.f16").readBytes(), manifest["embedding_dim"]!!.jsonPrimitive.int)
        val references = Json.parseToJsonElement(javaClass.getResource("/biocap_reference.json")!!.readText()).jsonArray
        assertTrue(references.isNotEmpty())
        BioCapEncoder(File(dir, "biocap-visual.onnx").path, threads = 2).use { encoder ->
            for (reference in references.map { it.jsonObject }) {
                val file = reference["file"]!!.jsonPrimitive.content
                val image = ImageIO.read(File(fixtures, file))
                val argb = IntArray(BioCapEncoder.SIZE * BioCapEncoder.SIZE)
                image.getRGB(0, 0, BioCapEncoder.SIZE, BioCapEncoder.SIZE, argb, 0, BioCapEncoder.SIZE)
                val vector = encoder.encode(argb)
                val expected = reference["vector"]!!.jsonArray.map { it.jsonPrimitive.float }
                val cosine = vector.indices.sumOf { (vector[it] * expected[it]).toDouble() }
                assertTrue("$file: cosine $cosine", cosine > 0.999)
                assertEquals(file, reference["best"]!!.jsonPrimitive.content, index.nearest(vector, count = 1).single().species.name)
            }
        }
    }
}
