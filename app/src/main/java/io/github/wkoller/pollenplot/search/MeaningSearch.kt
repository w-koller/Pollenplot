package io.github.wkoller.pollenplot.search

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.wkoller.pollenplot.BuildConfig
import io.github.wkoller.pollenplot.PollenplotApp
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.data.GardenRepository
import io.github.wkoller.pollenplot.data.db.SearchHit
import io.github.wkoller.pollenplot.domain.semantic.MeaningMatch
import io.github.wkoller.pollenplot.download.DownloadSpec
import io.github.wkoller.pollenplot.download.DownloadState
import io.github.wkoller.pollenplot.download.VerifiedDownload
import io.github.wkoller.pollenplot.ml.text.Embedder
import io.github.wkoller.pollenplot.ml.text.TextEmbedder
import io.github.wkoller.pollenplot.ml.text.WordPieceTokenizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Search by meaning: the optional text model, downloaded when the user asks, and the vectors it makes for each record.
 * Search then also offers records related to what was typed, such as lantana for "poisonous to dogs".
 */
class MeaningSearch(
    private val context: Context,
    private val garden: GardenRepository,
    scope: CoroutineScope,
    /** Tests give a stand-in, as the model's runtime cannot load off a phone. */
    private val fixedEmbedder: Embedder? = null,
) {
    val download = VerifiedDownload(
        context,
        DownloadSpec(
            key = MODEL,
            url = BuildConfig.BGE_URL,
            directory = "models",
            fileName = "$MODEL.onnx",
            bytes = BuildConfig.BGE_BYTES,
            sha256 = BuildConfig.BGE_SHA256,
            title = context.getString(R.string.models_meaning),
            description = context.getString(R.string.models_downloading_notification),
            spaceMargin = SPACE_MARGIN,
        ),
    )
    val state: StateFlow<DownloadState> = if (fixedEmbedder != null) MutableStateFlow(DownloadState.Complete) else download.state
    private val lock = Mutex()
    private var embedder: Embedder? = null

    init {
        scope.launch {
            download.refresh()
            if (state.value is DownloadState.Downloading || state.value is DownloadState.WaitingForWifi) download.watch()
        }
    }

    val installed: Boolean get() = state.value == DownloadState.Complete

    /** Records related in meaning to [query], leaving out those the words already [found]. Empty without the model. */
    suspend fun related(query: String, found: Set<String>): List<SearchHit> {
        if (query.isBlank()) return emptyList()
        val model = embedder() ?: return emptyList()
        val vector = withContext(Dispatchers.Default) { model.embedQuery(query.trim()) }
        return garden.searchHits(MeaningMatch.pick(garden.meaningScores(MODEL, vector), found))
    }

    /** Works out the meaning of every record that is new or changed since last time. */
    suspend fun refreshIndex() {
        val model = embedder() ?: return
        withContext(Dispatchers.Default) {
            for ((recordId, text) in garden.textsToEmbed(MODEL)) {
                garden.saveMeaning(recordId, MODEL, text, model.embedPassage(text))
            }
            garden.pruneMeanings(MODEL)
        }
    }

    /** Removes the model and every vector it made. */
    suspend fun delete() = lock.withLock {
        embedder?.close()
        embedder = null
        download.cancel()
        garden.clearMeanings()
    }

    private suspend fun embedder(): Embedder? = fixedEmbedder ?: lock.withLock {
        if (!installed) return@withLock null
        embedder ?: try {
            withContext(Dispatchers.IO) {
                val vocabulary = context.assets.open(VOCABULARY).bufferedReader().use { it.readLines() }
                TextEmbedder(download.file.path, WordPieceTokenizer(vocabulary))
            }.also { embedder = it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "The text model could not be opened", e)
            null
        }
    }

    companion object {
        const val MODEL = "bge-small-en-v1.5-q"
        private const val VOCABULARY = "ml/bge_small_en_vocab.txt"
        private const val TAG = "MeaningSearch"

        /** Room for the runtime to read the model as it opens it. */
        private const val SPACE_MARGIN = 20L * 1024 * 1024
    }
}

/** Works out the meaning of new and changed records in the background. */
class MeaningIndexWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        (applicationContext as PollenplotApp).container.meaning.refreshIndex()
        return Result.success()
    }

    companion object {
        private const val NAME = "meaning-index"

        fun enqueue(context: Context) {
            WorkManager.getInstance(context)
                .enqueueUniqueWork(NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, OneTimeWorkRequestBuilder<MeaningIndexWorker>().build())
        }
    }
}
