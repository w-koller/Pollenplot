package io.github.wkoller.pollenplot.ml

import android.content.Context
import android.util.Log
import io.github.wkoller.pollenplot.BuildConfig
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.download.DownloadSpec
import io.github.wkoller.pollenplot.download.DownloadState
import io.github.wkoller.pollenplot.download.VerifiedDownload
import io.github.wkoller.pollenplot.ml.bio.BioCapEncoder
import io.github.wkoller.pollenplot.ml.bio.SpeciesIndex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipInputStream

/** Where the accurate pack is: not published for this version, being fetched, being unpacked, or ready. */
sealed interface PackState {
    data object NotAvailable : PackState

    data class Fetching(val download: DownloadState) : PackState

    data object Unpacking : PackState

    data object Ready : PackState

    /** The unpacked files did not match the pack's manifest, so they were removed. */
    data object Damaged : PackState
}

/**
 * The accurate identification pack: BioCAP and the species it can name, downloaded once when the user asks. The zip
 * is checked against its pinned SHA-256, unpacked, each file checked against the pack's manifest, and then deleted.
 */
class AccuratePack(private val context: Context, scope: CoroutineScope) {
    val available: Boolean = BuildConfig.BIOCAP_SHA256.isNotBlank()

    val download = VerifiedDownload(
        context,
        DownloadSpec(
            key = "biocap-pack-${BuildConfig.BIOCAP_PACK}",
            url = BuildConfig.BIOCAP_URL,
            directory = "models",
            fileName = "biocap-pack-${BuildConfig.BIOCAP_PACK}.zip",
            bytes = BuildConfig.BIOCAP_BYTES,
            sha256 = BuildConfig.BIOCAP_SHA256,
            title = context.getString(R.string.models_accurate),
            description = context.getString(R.string.models_accurate_notification),
            // Unpacking needs room for a second copy before the zip is deleted.
            spaceMargin = BuildConfig.BIOCAP_BYTES + SPACE_MARGIN,
        ),
    )
    private val directory = File(context.filesDir, "models/biocap-pack-${BuildConfig.BIOCAP_PACK}")
    private val _state = MutableStateFlow<PackState>(if (available) PackState.Fetching(DownloadState.NotDownloaded) else PackState.NotAvailable)
    val state: StateFlow<PackState> = _state.asStateFlow()
    private val lock = Mutex()
    private var models: AccurateModels? = null

    init {
        if (available) {
            scope.launch {
                if (withContext(Dispatchers.IO) { File(directory, MANIFEST).isFile }) {
                    _state.value = PackState.Ready
                } else {
                    download.refresh()
                    if (download.state.value is DownloadState.Downloading || download.state.value is DownloadState.WaitingForWifi) download.watch()
                }
                // Kept watching, so a pack deleted and fetched again in the same session is unpacked too.
                download.state.collect { fetched ->
                    when {
                        _state.value == PackState.Ready || _state.value == PackState.Unpacking -> Unit
                        fetched == DownloadState.Complete -> unpack()
                        else -> _state.value = PackState.Fetching(fetched)
                    }
                }
            }
        }
    }

    val ready: Boolean get() = _state.value == PackState.Ready

    /** BioCAP and its species, opened the first time they are needed; null until the pack is ready. */
    suspend fun open(): AccurateModels? = lock.withLock {
        if (!ready) return@withLock null
        models ?: try {
            withContext(Dispatchers.IO) {
                val manifest = JSONObject(File(directory, MANIFEST).readText())
                val index = SpeciesIndex.read(File(directory, "species.tsv").readLines(), File(directory, "species.f16").readBytes(), manifest.getInt("embedding_dim"))
                AccurateModels(BioCapEncoder(File(directory, "biocap-visual.onnx").path), index, manifest.getDouble("logit_scale").toFloat())
            }.also { models = it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "The accurate pack could not be opened", e)
            null
        }
    }

    /** Removes the pack from the phone. */
    suspend fun delete() = lock.withLock {
        models?.close()
        models = null
        withContext(Dispatchers.IO) { directory.deleteRecursively() }
        download.cancel()
        _state.value = PackState.Fetching(DownloadState.NotDownloaded)
    }

    private suspend fun unpack() {
        _state.value = PackState.Unpacking
        val ok = withContext(Dispatchers.IO) {
            val staging = File(directory.path + ".part")
            staging.deleteRecursively()
            staging.mkdirs()
            try {
                ZipInputStream(download.file.inputStream().buffered()).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        val target = File(staging, entry.name)
                        // Entries stay inside the pack's folder.
                        check(!entry.isDirectory && target.canonicalPath.startsWith(staging.canonicalPath + File.separator)) { "Unexpected entry ${entry.name}" }
                        target.outputStream().use { zip.copyTo(it) }
                    }
                }
                val files = JSONObject(File(staging, MANIFEST).readText()).getJSONObject("files")
                val matches = files.keys().asSequence().all { name -> VerifiedDownload.sha256(File(staging, name)) == files.getString(name) }
                if (matches) {
                    directory.deleteRecursively()
                    check(staging.renameTo(directory)) { "The pack could not be moved into place" }
                }
                matches
            } catch (e: Exception) {
                Log.w(TAG, "The accurate pack could not be unpacked", e)
                false
            } finally {
                staging.deleteRecursively()
            }
        }
        // The zip is no longer needed either way: unpacked, or to be fetched again.
        download.cancel()
        _state.value = if (ok) PackState.Ready else PackState.Damaged
    }

    private companion object {
        const val TAG = "AccuratePack"
        const val MANIFEST = "manifest.json"
        const val SPACE_MARGIN = 50L * 1024 * 1024
    }
}
