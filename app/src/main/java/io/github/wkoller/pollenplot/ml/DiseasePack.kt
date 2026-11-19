package io.github.wkoller.pollenplot.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import io.github.wkoller.pollenplot.BuildConfig
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.domain.problems.DiseaseCall
import io.github.wkoller.pollenplot.domain.problems.ProblemCatalogue
import io.github.wkoller.pollenplot.download.DownloadSpec
import io.github.wkoller.pollenplot.download.DownloadState
import io.github.wkoller.pollenplot.download.VerifiedDownload
import io.github.wkoller.pollenplot.ml.bio.DiseaseHead
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/** What looks at a photo of a plant for problems; the plant problems pack on the phone, and a fake in tests. */
fun interface PlantProblemModel {
    /** The call for [photo], or null when the model is not on the phone or could not look. */
    suspend fun look(photo: Bitmap, catalogue: ProblemCatalogue): DiseaseCall?
}

/** The head with the levels the pack's evaluation chose and the accurate pack it was trained on. */
private class OpenHead(val head: DiseaseHead, val yesBp: Int, val maybeBp: Int)

/**
 * The plant problems pack: a small classifier on the accurate pack's image vectors, downloaded when the user asks.
 * It adds no encoder of its own, so it is offered only while the accurate pack is ready on the phone and is the one
 * the head was trained for (pollenplot.disease.biocap), and its manifest must name that pack too. It is fetched,
 * checked and unpacked as [AccuratePack] is.
 */
class DiseasePack(private val context: Context, scope: CoroutineScope, private val accurate: AccuratePack) : PlantProblemModel {
    /** Published, and made for the accurate pack this version of the app uses. */
    val available: Boolean = BuildConfig.DISEASE_SHA256.isNotBlank() && BuildConfig.DISEASE_BIOCAP == BuildConfig.BIOCAP_PACK

    val download = VerifiedDownload(
        context,
        DownloadSpec(
            key = "disease-pack-${BuildConfig.DISEASE_PACK}",
            url = BuildConfig.DISEASE_URL,
            directory = "models",
            fileName = "disease-pack-${BuildConfig.DISEASE_PACK}.zip",
            bytes = BuildConfig.DISEASE_BYTES,
            sha256 = BuildConfig.DISEASE_SHA256,
            title = context.getString(R.string.models_problems),
            description = context.getString(R.string.models_problems_notification),
            spaceMargin = BuildConfig.DISEASE_BYTES + SPACE_MARGIN,
        ),
    )
    private val directory = File(context.filesDir, "models/disease-pack-${BuildConfig.DISEASE_PACK}")
    private val _state = MutableStateFlow<PackState>(if (available) PackState.Fetching(DownloadState.NotDownloaded) else PackState.NotAvailable)
    val state: StateFlow<PackState> = _state.asStateFlow()

    /** Whether the Models screen offers the pack: it is published and the accurate pack it needs is ready. */
    val offered: StateFlow<Boolean> = accurate.state
        .combine(_state) { needed, _ -> available && needed == PackState.Ready }
        .stateIn(scope, SharingStarted.Eagerly, false)

    private val lock = Mutex()
    private var opened: OpenHead? = null

    init {
        if (available) {
            scope.launch {
                withContext(Dispatchers.IO) { deleteOlderPacks() }
                if (withContext(Dispatchers.IO) { File(directory, MANIFEST).isFile }) {
                    _state.value = PackState.Ready
                } else {
                    download.refresh()
                    if (download.state.value is DownloadState.Downloading || download.state.value is DownloadState.WaitingForWifi) download.watch()
                }
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

    /** Ready to look at photos: this pack and the accurate pack are both on the phone. */
    val ready: Boolean get() = _state.value == PackState.Ready && accurate.ready

    override suspend fun look(photo: Bitmap, catalogue: ProblemCatalogue): DiseaseCall? {
        if (!ready) return null
        val head = open() ?: return null
        val models = accurate.open() ?: return null
        return try {
            withContext(Dispatchers.Default) {
                val shares = head.head.sharesBp(models.middleVector(photo))
                DiseaseCall.of(shares, head.yesBp, head.maybeBp, catalogue)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "The plant problems pack could not look at the photo", e)
            null
        }
    }

    private suspend fun open(): OpenHead? = lock.withLock {
        opened ?: try {
            withContext(Dispatchers.IO) {
                val manifest = JSONObject(File(directory, MANIFEST).readText())
                check(manifest.getInt("biocap_pack") == BuildConfig.BIOCAP_PACK) { "The pack was made for another accurate pack" }
                val classes = manifest.getJSONArray("classes").let { list -> List(list.length()) { list.getString(it) } }
                val head = DiseaseHead.read(
                    classes,
                    File(directory, "weights.f16").readBytes(),
                    File(directory, "bias.f16").readBytes(),
                    manifest.getInt("embedding_dim"),
                    manifest.getDouble("temperature").toFloat(),
                )
                val yes = manifest.getInt("yes_bp")
                OpenHead(head, yes, manifest.optInt("maybe_bp", yes).coerceIn(1, yes))
            }.also { opened = it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "The plant problems pack could not be opened", e)
            null
        }
    }

    /** Removes the pack from the phone. */
    suspend fun delete() = lock.withLock {
        opened = null
        withContext(Dispatchers.IO) { directory.deleteRecursively() }
        download.cancel()
        _state.value = PackState.Fetching(DownloadState.NotDownloaded)
    }

    private fun deleteOlderPacks() {
        val folder = directory.parentFile ?: return
        folder.listFiles { file -> file.name.startsWith("disease-pack-") && file.name != directory.name && file.name != download.file.name }
            ?.forEach { it.deleteRecursively() }
    }

    private suspend fun unpack() {
        _state.value = PackState.Unpacking
        val ok = withContext(Dispatchers.IO) { unpackVerified(download.file, directory, TAG) }
        download.cancel()
        _state.value = if (ok) PackState.Ready else PackState.Damaged
    }

    private companion object {
        const val TAG = "DiseasePack"
        const val MANIFEST = "manifest.json"
        const val SPACE_MARGIN = 10L * 1024 * 1024
    }
}
