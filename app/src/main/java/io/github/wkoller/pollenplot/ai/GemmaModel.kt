package io.github.wkoller.pollenplot.ai

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import io.github.wkoller.pollenplot.BuildConfig
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.download.DownloadSpec
import io.github.wkoller.pollenplot.download.DownloadState
import io.github.wkoller.pollenplot.download.VerifiedDownload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Gemma 4 E2B on the phone, for the garden assistant. It is downloaded when the user asks (on Wi-Fi unless they agree
 * to mobile data, resumable, and checked against its pinned SHA-256), loaded into LiteRT-LM only while it is in use,
 * and released after two idle minutes. It reads photos as well as text, so it can look at a plant's cut-out.
 */
class GemmaModel(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Mutex()
    private var engine: Engine? = null
    private var idleJob: Job? = null

    val download = VerifiedDownload(
        context,
        DownloadSpec(
            key = "gemma-4-e2b",
            url = BuildConfig.GEMMA_URL,
            directory = "models",
            fileName = FILE,
            bytes = BuildConfig.GEMMA_BYTES,
            sha256 = BuildConfig.GEMMA_SHA256,
            title = context.getString(R.string.models_gemma),
            description = context.getString(R.string.models_gemma_notification),
            spaceMargin = SPACE_MARGIN,
        ),
    )

    val state: StateFlow<DownloadState> get() = download.state

    val ready: Boolean get() = download.state.value == DownloadState.Complete

    val isLoaded: Boolean get() = engine != null

    init {
        scope.launch {
            download.refresh()
            if (download.state.value is DownloadState.Downloading || download.state.value is DownloadState.WaitingForWifi) download.watch()
        }
    }

    /** Runs [block] with the model loaded, loading it first if it is not. */
    suspend fun <T> withEngine(block: suspend (Engine) -> T): T = lock.withLock {
        idleJob?.cancel()
        val loaded = engine ?: load().also { engine = it }
        try {
            block(loaded)
        } finally {
            idleJob = scope.launch {
                delay(IDLE_UNLOAD_MS)
                unload()
            }
        }
    }

    /** Removes the model from the phone. */
    suspend fun delete() {
        unload()
        download.cancel()
    }

    fun unload() {
        scope.launch {
            lock.withLock {
                engine?.close()
                engine = null
            }
        }
    }

    private suspend fun load(): Engine = withContext(Dispatchers.Default) {
        check(ready) { "Gemma 4 is not on the phone" }
        // The GPU is faster and uses less memory; not every phone supports it, so the CPU is the fallback, for the
        // text and the image encoder alike.
        var lastError: Throwable? = null
        for (backend in listOf<() -> Backend>({ Backend.GPU() }, { Backend.CPU() })) {
            try {
                val candidate = Engine(EngineConfig(download.file.path, backend(), backend(), null, MAX_TOKENS, MAX_IMAGES, context.cacheDir.path))
                candidate.initialize()
                return@withContext candidate
            } catch (error: Throwable) {
                lastError = error
            }
        }
        throw IllegalStateException("Gemma 4 could not be loaded", lastError)
    }

    private companion object {
        const val FILE = "gemma-4-E2B-it.litertlm"
        const val SPACE_MARGIN = 400L * 1024 * 1024
        const val MAX_TOKENS = 4096
        const val MAX_IMAGES = 1
        const val IDLE_UNLOAD_MS = 2 * 60 * 1000L
    }
}
