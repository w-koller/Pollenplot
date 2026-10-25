package io.github.wkoller.pollenplot.scan

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import java.io.IOException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max

/** A photo in the phone's gallery. [takenAt] falls back to when the photo was added if the camera did not record it. */
data class GalleryPhoto(val id: Long, val uri: Uri, val takenAt: Instant, val albumId: String?)

/** An album in the gallery, with how many photos it holds. [camera] marks the camera's own album. */
data class Album(val id: String, val name: String, val count: Int, val camera: Boolean)

/** How much of the gallery the app may see. With [PARTIAL], only the photos the user chose are visible. */
enum class GalleryAccess { FULL, PARTIAL, NONE }

/** The phone's gallery. The app reads photos through this, so tests can use photos of their own. */
interface Gallery {
    fun access(): GalleryAccess

    /** Photos taken at or after [since] in [albums] (null for the camera's own album), the newest first. */
    fun photosSince(since: Instant, albums: Set<String>?): List<GalleryPhoto>

    fun albums(): List<Album>

    /** The photo upright, at most [maxSide] pixels on its longer side, in memory the models can read; null if unreadable. */
    fun load(uri: Uri, maxSide: Int): Bitmap?

    /** When a photo was taken, from the gallery or the photo itself; null if it does not say. */
    fun takenAt(uri: Uri): Instant?
}

/** The gallery through Android's media store. */
class MediaStoreGallery(private val context: Context) : Gallery {
    private val resolver: ContentResolver = context.contentResolver

    override fun access(): GalleryAccess {
        fun granted(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && granted(Manifest.permission.READ_MEDIA_IMAGES) -> GalleryAccess.FULL
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && granted(Manifest.permission.READ_EXTERNAL_STORAGE) -> GalleryAccess.FULL
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> GalleryAccess.PARTIAL
            else -> GalleryAccess.NONE
        }
    }

    override fun photosSince(since: Instant, albums: Set<String>?): List<GalleryPhoto> {
        val clauses = mutableListOf("(${MediaStore.Images.Media.DATE_TAKEN} >= ? OR (${MediaStore.Images.Media.DATE_TAKEN} IS NULL AND ${MediaStore.Images.Media.DATE_ADDED} >= ?))")
        val args = mutableListOf(since.toEpochMilli().toString(), since.epochSecond.toString())
        if (albums == null) {
            // The camera's own album, found by its folder so it works in every language.
            clauses += "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
            args += "${CAMERA_FOLDER}%"
        } else {
            if (albums.isEmpty()) return emptyList()
            clauses += "${MediaStore.Images.Media.BUCKET_ID} IN (${albums.joinToString(",") { "?" }})"
            args += albums
        }
        return query(clauses.joinToString(" AND "), args.toTypedArray())
    }

    override fun albums(): List<Album> {
        val albums = linkedMapOf<String, Album>()
        val projection = arrayOf(MediaStore.Images.Media.BUCKET_ID, MediaStore.Images.Media.BUCKET_DISPLAY_NAME, MediaStore.Images.Media.RELATIVE_PATH)
        resolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0) ?: continue
                val name = cursor.getString(1) ?: continue
                val album = albums[id] ?: Album(id, name, 0, camera = cursor.getString(2)?.startsWith(CAMERA_FOLDER) == true)
                albums[id] = album.copy(count = album.count + 1)
            }
        }
        // The camera's album first, then the fullest.
        return albums.values.sortedWith(compareByDescending<Album> { it.camera }.thenByDescending { it.count })
    }

    override fun load(uri: Uri, maxSide: Int): Bitmap? = try {
        // ImageDecoder turns the photo upright from its EXIF orientation. Software memory lets the models read pixels.
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longer = max(info.size.width, info.size.height)
            if (longer > maxSide) {
                val target = info.size.scaled(maxSide.toFloat() / longer)
                decoder.setTargetSize(target.width, target.height)
            }
        }
    } catch (e: IOException) {
        Log.w(TAG, "A photo could not be read", e)
        null
    } catch (e: SecurityException) {
        Log.w(TAG, "The app may no longer read a photo", e)
        null
    }

    override fun takenAt(uri: Uri): Instant? {
        val fromStore = runCatching {
            resolver.query(uri, arrayOf(MediaStore.Images.Media.DATE_TAKEN), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0).takeIf { it > 0 }?.let(Instant::ofEpochMilli) else null
            }
        }.getOrNull()
        return fromStore ?: runCatching {
            resolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)?.let { text ->
                    LocalDateTime.parse(text, EXIF_DATE).atZone(ZoneId.systemDefault()).toInstant()
                }
            }
        }.getOrNull()
    }

    private fun query(selection: String, args: Array<String>): List<GalleryPhoto> {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.BUCKET_ID,
        )
        val queryArgs = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, args)
            putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(MediaStore.Images.Media.DATE_ADDED))
            putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
        }
        val photos = mutableListOf<GalleryPhoto>()
        resolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, queryArgs, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val taken = if (cursor.isNull(1)) null else cursor.getLong(1).takeIf { it > 0 }?.let(Instant::ofEpochMilli)
                val added = Instant.ofEpochSecond(cursor.getLong(2))
                photos += GalleryPhoto(id, ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id), taken ?: added, cursor.getString(3))
            }
        }
        return photos.sortedByDescending { it.takenAt }
    }

    private fun Size.scaled(factor: Float) = Size((width * factor).toInt().coerceAtLeast(1), (height * factor).toInt().coerceAtLeast(1))

    private companion object {
        const val TAG = "Gallery"
        const val CAMERA_FOLDER = "DCIM/Camera/"
        val EXIF_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")
    }
}
