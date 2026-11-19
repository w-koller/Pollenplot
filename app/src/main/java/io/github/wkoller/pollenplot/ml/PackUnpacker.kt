package io.github.wkoller.pollenplot.ml

import android.util.Log
import io.github.wkoller.pollenplot.download.VerifiedDownload
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Unpacks a downloaded model pack into [directory]: each entry into a staging folder beside it, each file checked
 * against the SHA-256 the pack's manifest.json lists, and only then moved into place. Returns whether it worked; the
 * staging folder is removed either way.
 */
internal fun unpackVerified(zip: File, directory: File, tag: String): Boolean {
    val staging = File(directory.path + ".part")
    staging.deleteRecursively()
    staging.mkdirs()
    return try {
        ZipInputStream(zip.inputStream().buffered()).use { entries ->
            while (true) {
                val entry = entries.nextEntry ?: break
                val target = File(staging, entry.name)
                // Entries stay inside the pack's folder.
                check(!entry.isDirectory && target.canonicalPath.startsWith(staging.canonicalPath + File.separator)) { "Unexpected entry ${entry.name}" }
                target.outputStream().use { entries.copyTo(it) }
            }
        }
        val files = JSONObject(File(staging, "manifest.json").readText()).getJSONObject("files")
        val matches = files.keys().asSequence().all { name -> VerifiedDownload.sha256(File(staging, name)) == files.getString(name) }
        if (matches) {
            directory.deleteRecursively()
            check(staging.renameTo(directory)) { "The pack could not be moved into place" }
        }
        matches
    } catch (e: Exception) {
        Log.w(tag, "The pack could not be unpacked", e)
        false
    } finally {
        staging.deleteRecursively()
    }
}
