package io.github.wkoller.pollenplot.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import io.github.wkoller.pollenplot.data.GardenRepository
import io.github.wkoller.pollenplot.data.GardenViews
import io.github.wkoller.pollenplot.data.db.SearchHit
import io.github.wkoller.pollenplot.data.kb.KnowledgeRepository
import io.github.wkoller.pollenplot.data.prefs.SettingsStore
import io.github.wkoller.pollenplot.domain.assistant.AssistantFacts
import io.github.wkoller.pollenplot.domain.assistant.CitedRecord
import io.github.wkoller.pollenplot.domain.assistant.GardenAssistantGuard
import io.github.wkoller.pollenplot.domain.assistant.GardenPrompts
import io.github.wkoller.pollenplot.domain.assistant.QuestionKind
import io.github.wkoller.pollenplot.domain.assistant.RecordKind
import io.github.wkoller.pollenplot.domain.wildlife.Biodiversity
import io.github.wkoller.pollenplot.search.MeaningSearch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/** What the assistant says back: progress, the answer as it arrives and when it is done, or one of the app's own answers. */
sealed interface AssistantReply {
    /** Gemma 4 is being loaded, which takes a few seconds the first time. */
    data object Loading : AssistantReply

    data class Streaming(val text: String) : AssistantReply

    /** The whole answer, with the garden's records it cites. */
    data class Done(val text: String, val cited: List<CitedRecord> = emptyList()) : AssistantReply

    /** A question the app answers itself instead of the model. */
    data class Fixed(val kind: QuestionKind) : AssistantReply

    /** Gemma 4 is not on the phone yet. */
    data object NotReady : AssistantReply

    data object Failed : AssistantReply
}

/** What a question is about: one plant in the garden, one kind of animal, or the whole garden. */
sealed interface AskSubject {
    data class Plant(val id: String) : AskSubject

    data class Animal(val key: String) : AskSubject

    data object Garden : AskSubject
}

/** The garden assistant, behind an interface so screen tests can stand in for the model, which cannot run on the JVM. */
interface Assistant {
    val ready: Boolean

    /**
     * Answers [question] about [subject], after the earlier questions and answers in [history]. [weedStatus] is the
     * official weed status of a plant where the garden is, as the Weed card says it, which the model may only repeat.
     */
    fun ask(subject: AskSubject, question: String, history: List<Pair<String, String>> = emptyList(), weedStatus: String? = null): Flow<AssistantReply>

    fun stop()
}

/**
 * Answers questions with Gemma 4 on the phone, from the knowledge bases and the garden's own records. Questions where a
 * wrong answer could hurt someone never reach the model, and an answer that gives a chemical amount anyway is replaced
 * by the app's own answer.
 */
class GardenAssistant(
    private val context: Context,
    private val model: GemmaModel,
    private val views: GardenViews,
    private val garden: GardenRepository,
    private val knowledge: KnowledgeRepository,
    private val meaning: MeaningSearch,
    private val settings: SettingsStore,
    /** Keeps a failure in the problem report, so a model that will not load on someone's phone can be looked into. */
    private val onError: (Throwable) -> Unit = {},
) : Assistant {
    @Volatile
    private var current: Conversation? = null

    override val ready: Boolean get() = model.ready

    override fun ask(subject: AskSubject, question: String, history: List<Pair<String, String>>, weedStatus: String?): Flow<AssistantReply> = channelFlow {
        val kind = GardenAssistantGuard.classify(question)
        if (kind != QuestionKind.GENERAL) {
            send(AssistantReply.Fixed(kind))
            return@channelFlow
        }
        if (!model.ready) {
            send(AssistantReply.NotReady)
            return@channelFlow
        }
        val prepared = prepare(subject, question, weedStatus)
        if (!model.isLoaded) send(AssistantReply.Loading)
        try {
            model.withEngine { engine ->
                val earlier = history.takeLast(MAX_HISTORY).flatMap { (q, a) -> listOf(Message.user(q), Message.model(a)) }
                val conversation = engine.createConversation(
                    ConversationConfig(Contents.of(GardenPrompts.SYSTEM), earlier, emptyList(), SamplerConfig(TOP_K, TOP_P, TEMPERATURE, 0)),
                )
                current = conversation
                try {
                    val contents = listOfNotNull(prepared.image?.let { Content.ImageBytes(it) }, Content.Text(prepared.prompt))
                    var text = ""
                    var unsafe = false
                    conversation.sendMessageAsync(Contents.of(contents))
                        .takeWhile { !unsafe }
                        .collect { message ->
                            val chunk = message.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
                            // Some versions send the whole answer so far, others only the new part.
                            text = if (text.isNotEmpty() && chunk.startsWith(text)) chunk else text + chunk
                            if (GardenAssistantGuard.answerGivesAmount(text)) {
                                unsafe = true
                                conversation.cancelProcess()
                            } else {
                                send(AssistantReply.Streaming(text.trim()))
                            }
                        }
                    send(if (unsafe) AssistantReply.Fixed(QuestionKind.CHEMICAL_DOSE) else AssistantReply.Done(text.trim(), GardenPrompts.citations(text, prepared.records)))
                } finally {
                    current = null
                    conversation.close()
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            onError(error)
            send(AssistantReply.Failed)
        }
    }

    override fun stop() {
        current?.cancelProcess()
    }

    /** What the model is sent for a question: the prompt with its facts, a photo when there is one, and the records it may cite. */
    private class Prepared(val prompt: String, val image: ByteArray?, val records: List<CitedRecord> = emptyList())

    private suspend fun prepare(subject: AskSubject, question: String, weedStatus: String?): Prepared = when (subject) {
        is AskSubject.Plant -> {
            val view = views.plant(subject.id).first()
            val facts = if (view == null) emptyList() else AssistantFacts.plant(view.title, view.species, view.care?.care, view.zoneName, view.plant.quantity, weedStatus)
            Prepared(GardenPrompts.about(facts, question), view?.plant?.thumbnail?.let { photo(it) })
        }
        is AskSubject.Animal -> {
            val fauna = knowledge.awaitFauna()
            val animal = fauna.animal(subject.key)
            val seen = views.sightings.first().filter { Biodiversity.keyOf(it.sighting, fauna) == subject.key }
            val facts = animal?.let { AssistantFacts.animal(it, settings.current().gardenState, seen.size) }
                ?: listOf("${seen.firstOrNull()?.sighting?.label ?: subject.key}, not in Pollenplot's animal list", "Seen in the yard: ${seen.size} times")
            Prepared(GardenPrompts.about(facts, question), seen.firstNotNullOfOrNull { it.sighting.thumbnail }?.let { photo(it) })
        }
        AskSubject.Garden -> {
            val records = records(question)
            Prepared(GardenPrompts.garden(summary(), records, question), null, records)
        }
    }

    /**
     * The records a question about the garden is most likely about: those that match most of its words, then those
     * close to it in meaning when the text model is on the phone.
     */
    internal suspend fun records(question: String): List<CitedRecord> {
        val counts = linkedMapOf<String, Pair<SearchHit, Int>>()
        for (word in GardenPrompts.searchWords(question)) {
            garden.search(word, PER_WORD).forEach { hit -> counts[hit.recordId] = hit to ((counts[hit.recordId]?.second ?: 0) + 1) }
        }
        val byWords = counts.values.sortedByDescending { it.second }.map { it.first }.take(MAX_RECORDS)
        val related = runCatching { meaning.related(question, byWords.map { it.recordId }.toSet()) }.getOrDefault(emptyList())
        return (byWords + related).take(MAX_RECORDS).mapNotNull { hit ->
            val kind = RecordKind.entries.firstOrNull { it.name == hit.kind } ?: return@mapNotNull null
            CitedRecord(hit.recordId, kind, hit.title, "${kind.name.lowercase().replaceFirstChar(Char::uppercaseChar)}: ${hit.title}. ${hit.body}")
        }
    }

    /** The zones, how many plants grow in each, and how many sightings there are. */
    private suspend fun summary(): String {
        val zones = views.zones.first()
        val plants = views.plants.first().count { it.isGrowing }
        val sightings = views.sightings.first().size
        val zoneList = zones.joinToString(", ") { "${it.zone.name} with ${it.plants.size} plants" }
        return "${zones.size} zones ($zoneList), $plants plants growing and $sightings sightings of animals recorded."
    }

    /** A cut-out from the app's files, as JPEG, which the model reads; null if it has gone. */
    private suspend fun photo(path: String): ByteArray? = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, path)
        val bitmap = if (file.isFile) BitmapFactory.decodeFile(file.path) else null
        bitmap?.let { ByteArrayOutputStream().also { out -> it.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out) }.toByteArray() }
    }

    private companion object {
        const val MAX_HISTORY = 4
        const val MAX_RECORDS = 8
        const val PER_WORD = 20
        const val TOP_K = 40
        const val TOP_P = 0.9
        const val TEMPERATURE = 0.4
        const val JPEG_QUALITY = 90
    }
}
