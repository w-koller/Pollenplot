package io.github.wkoller.pollenplot.data.photos

import android.content.Context
import android.graphics.Bitmap
import io.github.wkoller.pollenplot.ml.Bitmaps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** A photo saved in the app's own storage. [path] is relative to the app's files folder. */
data class StoredPhoto(val id: String, val path: String, val width: Int, val height: Int)

/**
 * Keeps photos and cut-outs in the app's private storage. Photos are downscaled to a sensible size, so the garden's
 * records keep their pictures even if the originals are deleted from the phone's gallery.
 */
class PhotoStore(private val context: Context) {
    fun file(path: String): File = File(context.filesDir, path)

    /** Saves [bitmap] as a JPEG whose longer side is at most [MAX_SIDE] pixels. */
    suspend fun savePhoto(bitmap: Bitmap): StoredPhoto = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val scaled = Bitmaps.scaleDown(bitmap, MAX_SIDE)
        val path = "$PHOTOS/$id.jpg"
        write(path) { scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        StoredPhoto(id, path, scaled.width, scaled.height)
    }

    /** Saves a cut-out as WebP and returns its path. */
    suspend fun saveThumbnail(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        val path = "$THUMBS/${UUID.randomUUID()}.webp"
        write(path) { Bitmaps.scaleDown(bitmap, THUMB_SIDE).compress(Bitmap.CompressFormat.WEBP_LOSSY, WEBP_QUALITY, it) }
        path
    }

    suspend fun delete(path: String) = withContext(Dispatchers.IO) { file(path).delete() }

    private fun write(path: String, block: (java.io.OutputStream) -> Boolean) {
        val target = file(path)
        target.parentFile?.mkdirs()
        val partial = File(target.path + ".part")
        partial.outputStream().use { check(block(it)) { "The picture could not be saved" } }
        check(partial.renameTo(target)) { "The picture could not be saved" }
    }

    companion object {
        const val PHOTOS = "photos"
        const val THUMBS = "thumbs"
        const val MAX_SIDE = 2_048
        const val THUMB_SIDE = 512
        private const val JPEG_QUALITY = 90
        private const val WEBP_QUALITY = 85
    }
}
