package io.github.wkoller.pollenplot.ui.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.wkoller.pollenplot.ai.AskSubject
import io.github.wkoller.pollenplot.ai.Assistant
import io.github.wkoller.pollenplot.ai.AssistantReply
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One question and the reply to it so far. */
data class Exchange(val question: String, val reply: AssistantReply)

/** The questions asked about [subject] and their replies, kept while the phone is turned. */
class AskModel(private val assistant: Assistant, val subject: AskSubject) : ViewModel() {
    private val _exchanges = MutableStateFlow<List<Exchange>>(emptyList())
    val exchanges: StateFlow<List<Exchange>> = _exchanges.asStateFlow()
    private var job: Job? = null

    val busy: Boolean get() = job?.isActive == true

    /** Asks [question], with the earlier answered questions as context. [weedStatus] is the plant's official status. */
    fun ask(question: String, weedStatus: String? = null) {
        val text = question.trim()
        if (text.isEmpty() || busy) return
        val history = _exchanges.value.mapNotNull { e -> (e.reply as? AssistantReply.Done)?.let { e.question to it.text } }
        _exchanges.update { it + Exchange(text, AssistantReply.Loading) }
        val index = _exchanges.value.lastIndex
        job = viewModelScope.launch {
            assistant.ask(subject, text, history, weedStatus).collect { reply ->
                _exchanges.update { list -> list.mapIndexed { i, e -> if (i == index) e.copy(reply = reply) else e } }
            }
        }
    }

    fun stop() {
        assistant.stop()
    }
}
