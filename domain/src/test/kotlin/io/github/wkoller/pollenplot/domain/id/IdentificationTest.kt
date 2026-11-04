package io.github.wkoller.pollenplot.domain.id

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentificationTest {
    private fun tile(box: BoxBp, label: String?, score: Int, background: Int = 100) =
        TileResult(box, if (label == null) emptyList() else listOf(LabelScore(label, score)), background)

    @Test
    fun `tiles cover the photo at three scales`() {
        val tiles = RegionPlanner.tiles()
        assertEquals(1 + 4 + 9, tiles.size)
        assertEquals(BoxBp.WHOLE, tiles[0])
        assertEquals(BoxBp(4_000, 4_000, 10_000, 10_000), tiles[4])
        assertEquals(BoxBp(6_000, 6_000, 10_000, 10_000), tiles.last())
    }

    @Test
    fun `iou measures overlap`() {
        val a = BoxBp(0, 0, 5_000, 5_000)
        assertEquals(1.0, a.iou(a), 1e-9)
        assertEquals(0.0, a.iou(BoxBp(5_000, 0, 10_000, 5_000)), 1e-9)
        assertEquals(1.0 / 7.0, a.iou(BoxBp(2_500, 2_500, 7_500, 7_500)), 1e-9)
    }

    @Test
    fun `confident tiles become regions, one per plant`() {
        val left = BoxBp(0, 0, 4_000, 4_000)
        val leftBig = BoxBp(0, 0, 6_000, 6_000)
        val right = BoxBp(6_000, 6_000, 10_000, 10_000)
        val results = listOf(
            tile(BoxBp.WHOLE, "Lantana camara", 2_000),
            tile(leftBig, "Lantana camara", 7_000),
            tile(left, "Lantana camara", 8_000),
            tile(right, "Strelitzia reginae", 6_000),
            tile(BoxBp(3_000, 3_000, 7_000, 7_000), "Rosa chinensis", 3_000, background = 4_000),
        )
        val regions = RegionPlanner.select(results)
        assertEquals("The same lantana seen in two overlapping tiles is one region", listOf(left, right), regions.map { it.box })
        assertTrue(RegionPlanner.showsPlant(results, regions))
    }

    @Test
    fun `labels that differ only in case are one species`() {
        val results = listOf(
            tile(BoxBp(0, 0, 6_000, 6_000), "Lantana camara", 7_000),
            tile(BoxBp(2_000, 2_000, 8_000, 8_000), "lantana Camara ", 6_000),
        )
        assertEquals(1, RegionPlanner.select(results).size)
    }

    @Test
    fun `a photo gives at most four regions`() {
        val boxes = RegionPlanner.tiles().drop(5)
        val results = boxes.mapIndexed { i, box -> tile(box, "Species $i", 9_000 - i * 100) }
        assertEquals(RegionPlanner.MAX_REGIONS, RegionPlanner.select(results).size)
    }

    @Test
    fun `one plant takes the better score of the middle and the whole photo`() {
        val centre = BoxBp(1_500, 1_500, 8_500, 8_500)
        val one = RegionPlanner.one(
            TileResult(centre, listOf(LabelScore("Rosa chinensis", 5_000), LabelScore("Rosa gallica", 1_000)), 2_000),
            TileResult(BoxBp.WHOLE, listOf(LabelScore("Rosa gallica", 3_000), LabelScore("rosa chinensis", 4_000)), 3_000),
        )!!
        assertEquals(centre, one.box)
        assertEquals(listOf(5_000, 3_000), one.labels.map { it.scoreBp })
        assertEquals(2_000, one.backgroundBp)
        val nothing = TileResult(BoxBp.WHOLE, listOf(LabelScore("Zea mays", 600)), 9_000)
        assertEquals(null, RegionPlanner.one(nothing.copy(box = centre), nothing))
    }

    @Test
    fun `covered by measures how much of a box lies inside another`() {
        val small = BoxBp(0, 0, 4_000, 4_000)
        assertEquals(1.0, small.coveredBy(BoxBp.WHOLE), 1e-9)
        assertEquals(0.25, small.coveredBy(BoxBp(2_000, 2_000, 10_000, 10_000)), 1e-9)
        assertEquals(0.0, small.coveredBy(BoxBp(6_000, 6_000, 10_000, 10_000)), 1e-9)
    }

    @Test
    fun `with no confident tile the whole photo still gets suggestions`() {
        val results = listOf(tile(BoxBp.WHOLE, "Zea mays", 1_200, background = 900), tile(BoxBp(0, 0, 4_000, 4_000), "Zea mays", 1_000))
        assertEquals(listOf(BoxBp.WHOLE), RegionPlanner.select(results).map { it.box })
        val nothing = listOf(tile(BoxBp.WHOLE, "Zea mays", 500, background = 9_000))
        assertTrue(RegionPlanner.select(nothing).isEmpty())
        assertFalse(RegionPlanner.showsPlant(nothing, emptyList()))
    }

    @Test
    fun `thumbnails are square, padded and inside the image`() {
        assertEquals(PixelRect(320, 270, 360), ThumbnailMath.squareAround(BoxBp(4_000, 3_000, 6_000, 6_000), 1_000, 1_000, paddingBp = 1_000))
        val corner = ThumbnailMath.squareAround(BoxBp(9_000, 9_000, 10_000, 10_000), 4_000, 3_000)
        assertTrue(corner.left + corner.size <= 4_000 && corner.top + corner.size <= 3_000)
        val whole = ThumbnailMath.squareAround(BoxBp.WHOLE, 4_000, 3_000)
        assertEquals("The whole photo fits the shorter side", 3_000, whole.size)
    }

    @Test
    fun `flower hint looks for flower colours that are not leaves, sky or mulch`() {
        assertTrue(FlowerHint.isFlowerColour(hue = 0f, saturation = 0.8f, value = 0.8f))
        assertTrue("Purple", FlowerHint.isFlowerColour(hue = 280f, saturation = 0.6f, value = 0.6f))
        assertTrue("A bright orange flower", FlowerHint.isFlowerColour(hue = 30f, saturation = 0.8f, value = 0.9f))
        assertFalse("Leaf green", FlowerHint.isFlowerColour(hue = 110f, saturation = 0.7f, value = 0.6f))
        assertFalse("Grey path", FlowerHint.isFlowerColour(hue = 30f, saturation = 0.1f, value = 0.6f))
        assertFalse("Mulch", FlowerHint.isFlowerColour(hue = 27f, saturation = 0.5f, value = 0.45f))
        assertFalse("Sky at the top", FlowerHint.isFlowerColour(hue = 210f, saturation = 0.6f, value = 0.9f, y = 1_000))
        assertTrue("Blue flowers lower down", FlowerHint.isFlowerColour(hue = 210f, saturation = 0.6f, value = 0.9f, y = 6_000))
        assertTrue(FlowerHint.sampled(5_000, 5_000))
        assertFalse("The edge of the cut-out", FlowerHint.sampled(500, 5_000))
        assertTrue(FlowerHint.likely(FlowerHint.ColourStats(sampled = 1_000, vividNonGreen = 90)))
        assertFalse(FlowerHint.likely(FlowerHint.ColourStats(sampled = 1_000, vividNonGreen = 60)))
        assertFalse(FlowerHint.likely(FlowerHint.ColourStats(sampled = 0, vividNonGreen = 0)))
    }

    @Test
    fun `confidence words`() {
        assertEquals(Confidence.HIGH, Confidence.of(6_000))
        assertEquals(Confidence.MEDIUM, Confidence.of(3_000))
        assertEquals(Confidence.LOW, Confidence.of(2_999))
    }
}
