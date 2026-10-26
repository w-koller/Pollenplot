package io.github.wkoller.pollenplot.weeds

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import io.github.wkoller.pollenplot.domain.weeds.AlaPhotos
import io.github.wkoller.pollenplot.domain.weeds.RecordPhoto
import io.github.wkoller.pollenplot.domain.weeds.WeedSources
import io.github.wkoller.pollenplot.web.Web
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

/**
 * Photos of a plant from records on the Atlas of Living Australia, fetched when the user compares a plant with them.
 * Only the plant's name is sent. The photos are kept in memory while the app runs, not saved.
 */
class WeedPhotos(private val web: Web, private val sources: () -> WeedSources) {
    private val images = LruCache<String, ImageBitmap>(MAX_IMAGES)

    /** Recent photos of [name], a species or a genus, under open licences. */
    suspend fun photos(name: String): List<RecordPhoto> {
        val query = URLEncoder.encode(name, "UTF-8").replace("+", "%20")
        return AlaPhotos.parse(web.get(sources().national.photos.replace("{name}", query))).take(MAX_PHOTOS)
    }

    /** A photo, or null when it cannot be loaded. */
    suspend fun image(url: String): ImageBitmap? {
        images.get(url)?.let { return it }
        val bytes = try {
            web.bytes(url)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return null
        }
        val bitmap = withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) } ?: return null
        return bitmap.asImageBitmap().also { images.put(url, it) }
    }

    private companion object {
        const val MAX_PHOTOS = 6
        const val MAX_IMAGES = 24
    }
}
