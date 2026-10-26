package io.github.wkoller.pollenplot.ml

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import androidx.core.graphics.get
import androidx.core.graphics.scale
import io.github.wkoller.pollenplot.domain.id.BoxBp
import io.github.wkoller.pollenplot.domain.id.FlowerHint
import io.github.wkoller.pollenplot.domain.id.ThumbnailMath
import kotlin.math.max
import kotlin.math.roundToInt

/** Bitmap helpers for the camera and the models. */
object Bitmaps {
    /** The bitmap scaled so its longer side is at most [maxSide], or the same bitmap if it already fits. */
    fun scaleDown(bitmap: Bitmap, maxSide: Int): Bitmap {
        val longer = max(bitmap.width, bitmap.height)
        if (longer <= maxSide) return bitmap
        val scale = maxSide.toFloat() / longer
        return bitmap.scale((bitmap.width * scale).roundToInt().coerceAtLeast(1), (bitmap.height * scale).roundToInt().coerceAtLeast(1))
    }

    /** The bitmap turned upright, for a camera image that reports [degrees] of rotation. */
    fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees % 360 == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /** A square cut-out around [box], padded a little, at most [side] pixels across. */
    fun squareCrop(bitmap: Bitmap, box: BoxBp, side: Int): Bitmap {
        val rect = ThumbnailMath.squareAround(box, bitmap.width, bitmap.height)
        val crop = Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.size, rect.size)
        return scaleDown(crop, side)
    }

    /** The square around [box], scaled to [side] x [side], as ARGB pixels row by row, for BioCAP. */
    fun squarePixels(bitmap: Bitmap, box: BoxBp, side: Int): IntArray {
        val rect = ThumbnailMath.squareAround(box, bitmap.width, bitmap.height)
        val square = smoothScale(Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.size, rect.size), side, side)
        return IntArray(side * side).also { square.getPixels(it, 0, side, 0, 0, side, side) }
    }

    /**
     * [bitmap] scaled to [width] x [height] without skipping detail. Android's filtered scaling blends only the four
     * nearest pixels, so a large reduction misses most of the photo; halving first averages each 2 x 2 block, which
     * matches the smooth resizing BioCAP's photos had in training. tools/models/export_biocap.py prepares photos the
     * same way when it evaluates the pack.
     */
    fun smoothScale(bitmap: Bitmap, width: Int, height: Int): Bitmap {
        var current = bitmap
        while (current.width >= width * 2 && current.height >= height * 2) current = current.scale(current.width / 2, current.height / 2)
        return if (current.width == width && current.height == height) current else current.scale(width, height)
    }

    /** Samples a grid of pixels and counts the vivid, non-green ones, for the flowering hint. */
    fun colourStats(bitmap: Bitmap, grid: Int = 48): FlowerHint.ColourStats {
        val hsv = FloatArray(3)
        var sampled = 0
        var vivid = 0
        for (gy in 0 until grid) for (gx in 0 until grid) {
            val x = (gx * (bitmap.width - 1)) / (grid - 1).coerceAtLeast(1)
            val y = (gy * (bitmap.height - 1)) / (grid - 1).coerceAtLeast(1)
            Color.colorToHSV(bitmap[x, y], hsv)
            sampled++
            if (FlowerHint.isVividNonGreen(hsv[0], hsv[1], hsv[2])) vivid++
        }
        return FlowerHint.ColourStats(sampled, vivid)
    }
}
