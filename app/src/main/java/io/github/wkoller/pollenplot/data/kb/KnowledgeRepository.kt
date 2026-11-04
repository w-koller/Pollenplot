package io.github.wkoller.pollenplot.data.kb

import android.content.Context
import io.github.wkoller.pollenplot.CrashReporter
import io.github.wkoller.pollenplot.domain.plants.CareProfile
import io.github.wkoller.pollenplot.domain.plants.KbFiles
import io.github.wkoller.pollenplot.domain.plants.KnowledgeBase
import io.github.wkoller.pollenplot.domain.wildlife.FaunaBase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The plant and animal knowledge bases from app/src/main/assets/kb, read once in the background when the app starts.
 * They are a few hundred kilobytes of reference data, so they are held in memory rather than in a database. The plant
 * knowledge base carries the user's [corrections], and is sent again whenever they change.
 */
class KnowledgeRepository(
    private val context: Context,
    scope: CoroutineScope,
    corrections: Flow<Map<String, CareProfile>> = flowOf(emptyMap()),
) {
    private val loaded = MutableStateFlow<KnowledgeBase?>(null)
    val knowledge: StateFlow<KnowledgeBase?> = combine(loaded, corrections) { kb, corrected ->
        if (kb == null || corrected.isEmpty()) kb else kb.withCorrections(corrected)
    }.stateIn(scope, SharingStarted.Eagerly, null)
    private val _fauna = MutableStateFlow<FaunaBase?>(null)
    val fauna: StateFlow<FaunaBase?> = _fauna.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            loaded.value = load()
            _fauna.value = loadFauna()
        }
    }

    suspend fun await(): KnowledgeBase = knowledge.filterNotNull().first()

    suspend fun awaitFauna(): FaunaBase = fauna.filterNotNull().first()

    private fun loadFauna(): FaunaBase {
        val assets = context.assets
        val files = assets.list("$DIR/fauna").orEmpty().filter { it.endsWith(".json") }.sorted()
            .associateWith { name -> assets.open("$DIR/fauna/$name").bufferedReader().use { it.readText() } }
        val result = FaunaBase.parse(files)
        if (result.problems.isNotEmpty()) {
            CrashReporter.record(context, IllegalStateException("Fauna problems:\n" + result.problems.joinToString("\n")))
        }
        return result.fauna
    }

    private fun load(): KnowledgeBase {
        val assets = context.assets
        fun read(path: String) = assets.open("$DIR/$path").bufferedReader().use { it.readText() }
        val species = assets.list("$DIR/species").orEmpty().filter { it.endsWith(".json") }.sorted()
            .associateWith { read("species/$it") }
        val result = KnowledgeBase.parse(
            KbFiles(
                meta = read("meta.json"),
                species = species,
                genera = read("genera.json"),
                families = read("families.json"),
                companions = read("companions.json"),
            ),
        )
        // The tests stop a faulty knowledge base from shipping, so this only records a problem for the report.
        if (result.problems.isNotEmpty()) {
            CrashReporter.record(context, IllegalStateException("Knowledge base problems:\n" + result.problems.joinToString("\n")))
        }
        return result.knowledgeBase
    }

    private companion object {
        const val DIR = "kb"
    }
}
