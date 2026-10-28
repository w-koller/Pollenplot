package io.github.wkoller.pollenplot.ui.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.ai.AskSubject
import io.github.wkoller.pollenplot.ai.Assistant
import io.github.wkoller.pollenplot.ai.AssistantReply
import io.github.wkoller.pollenplot.data.GardenViews
import io.github.wkoller.pollenplot.data.Locality
import io.github.wkoller.pollenplot.data.kb.KnowledgeRepository
import io.github.wkoller.pollenplot.domain.assistant.CitedRecord
import io.github.wkoller.pollenplot.domain.assistant.QuestionKind
import io.github.wkoller.pollenplot.domain.assistant.RecordKind
import io.github.wkoller.pollenplot.domain.assistant.WildlifeRescue
import io.github.wkoller.pollenplot.domain.plants.AuState
import io.github.wkoller.pollenplot.ui.components.DetailScaffold
import io.github.wkoller.pollenplot.ui.components.PollenLoadingIndicator
import io.github.wkoller.pollenplot.ui.review.readable
import io.github.wkoller.pollenplot.ui.weeds.weedHeadline
import kotlinx.coroutines.flow.flowOf

/**
 * Questions to Gemma 4 about a plant, a kind of animal or the whole garden, with each answer as it arrives and the
 * records it drew on. Questions where a wrong answer could hurt someone get Pollenplot's own answer instead.
 */
@Composable
fun AskScreen(
    model: AskModel,
    assistant: Assistant,
    views: GardenViews,
    knowledge: KnowledgeRepository,
    onBack: () -> Unit,
    onOpenModels: () -> Unit,
    onOpenRecord: (CitedRecord) -> Unit,
) {
    val exchanges by model.exchanges.collectAsStateWithLifecycle()
    val locality by views.locality.collectAsStateWithLifecycle(initialValue = Locality.NONE)
    val fauna by knowledge.fauna.collectAsStateWithLifecycle()
    val subject = model.subject
    val plantFlow = remember(subject) { (subject as? AskSubject.Plant)?.let { views.plant(it.id) } ?: flowOf(null) }
    val plant by plantFlow.collectAsStateWithLifecycle(initialValue = null)
    val weedStatus = plant?.let { view -> view.weed.takeIf { it.isWeed }?.let { weedHeadline(it, locality) } }
    var question by rememberSaveable { mutableStateOf("") }
    val busy = exchanges.lastOrNull()?.reply.let { it is AssistantReply.Loading || it is AssistantReply.Streaming }
    val list = rememberLazyListState()
    LaunchedEffect(exchanges.size) { if (exchanges.isNotEmpty()) list.animateScrollToItem(exchanges.size) }
    val send = {
        if (question.isNotBlank() && !busy) {
            model.ask(question, weedStatus)
            question = ""
        }
    }

    DetailScaffold(title = stringResource(if (subject == AskSubject.Garden) R.string.ask_garden_title else R.string.ask_title), onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item(key = "intro") {
                    Column(Modifier.readable(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        val name = when (subject) {
                            is AskSubject.Plant -> plant?.title
                            is AskSubject.Animal -> fauna?.animal(subject.key)?.displayName ?: subject.key
                            AskSubject.Garden -> null
                        }
                        Text(
                            when (subject) {
                                is AskSubject.Plant -> stringResource(R.string.ask_intro_plant, name.orEmpty())
                                is AskSubject.Animal -> stringResource(R.string.ask_intro_animal, name.orEmpty().lowercase())
                                AskSubject.Garden -> stringResource(R.string.ask_intro_garden)
                            },
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        if (!assistant.ready) NotReady(onOpenModels)
                    }
                }
                itemsIndexed(exchanges, key = { i, _ -> "exchange-$i" }) { _, exchange ->
                    Column(Modifier.readable(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Question(exchange.question)
                        Reply(exchange.reply, locality.state, onOpenModels, onOpenRecord)
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    label = { Text(stringResource(R.string.ask_hint)) },
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    modifier = Modifier.weight(1f),
                )
                if (busy) {
                    OutlinedButton(onClick = model::stop) { Text(stringResource(R.string.ask_stop)) }
                } else {
                    Button(onClick = send, enabled = question.isNotBlank()) { Text(stringResource(R.string.ask_send)) }
                }
            }
        }
    }
}

@Composable
private fun Question(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(20.dp)) {
            Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
        }
    }
}

@Composable
private fun Reply(reply: AssistantReply, state: AuState?, onOpenModels: () -> Unit, onOpenRecord: (CitedRecord) -> Unit) {
    when (reply) {
        AssistantReply.Loading -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            PollenLoadingIndicator(Modifier.size(32.dp))
            Text(stringResource(R.string.ask_loading), style = MaterialTheme.typography.bodyMedium)
        }
        is AssistantReply.Streaming -> Text(reply.text, style = MaterialTheme.typography.bodyLarge)
        is AssistantReply.Done -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(reply.text, style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.ask_written_by), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (reply.cited.isNotEmpty()) {
                Text(stringResource(R.string.ask_sources), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    reply.cited.forEach { record ->
                        AssistChip(
                            onClick = { onOpenRecord(record) },
                            label = { Text(record.title) },
                            leadingIcon = { Icon(painterResource(record.kind.icon), contentDescription = null, modifier = Modifier.size(18.dp)) },
                        )
                    }
                }
            }
        }
        is AssistantReply.Fixed -> FixedAnswer(reply.kind, state)
        AssistantReply.NotReady -> NotReady(onOpenModels)
        AssistantReply.Failed -> Text(stringResource(R.string.ask_failed), style = MaterialTheme.typography.bodyLarge)
    }
}

/** Pollenplot's own answer to a question the model is kept away from, with the number to call. */
@Composable
private fun FixedAnswer(kind: QuestionKind, state: AuState?) {
    val uri = LocalUriHandler.current
    val urgent = kind == QuestionKind.BITE_OR_STING || kind == QuestionKind.POISONING
    Surface(
        color = if (urgent) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = if (urgent) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(if (urgent) R.drawable.ic_warning else R.drawable.ic_info), contentDescription = null)
                Text(stringResource(R.string.ask_app_answer), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            }
            when (kind) {
                QuestionKind.INJURED_WILDLIFE -> {
                    val contact = WildlifeRescue.forState(state)
                    val phone = contact?.phone
                    Text(
                        when {
                            contact == null -> stringResource(R.string.fixed_wildlife_no_state)
                            phone == null -> stringResource(R.string.fixed_wildlife_page, contact.name)
                            else -> stringResource(R.string.fixed_wildlife, contact.name, phone)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(stringResource(R.string.fixed_wildlife_care), style = MaterialTheme.typography.bodyLarge)
                    if (contact != null) {
                        TextButton(onClick = { uri.openUri(contact.source) }) {
                            Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                            Text(stringResource(R.string.fixed_source))
                        }
                    }
                }
                else -> Text(stringResource(kind.answer), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun NotReady(onOpenModels: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.ask_not_ready_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.ask_not_ready_body), style = MaterialTheme.typography.bodyLarge)
            Button(onClick = onOpenModels) { Text(stringResource(R.string.ask_open_models)) }
        }
    }
}

/** The app's own answer for each kind of question the model is kept away from, apart from injured wildlife. */
private val QuestionKind.answer: Int
    get() = when (this) {
        QuestionKind.BITE_OR_STING -> R.string.fixed_bite
        QuestionKind.POISONING -> R.string.fixed_poisoning
        QuestionKind.EATING -> R.string.fixed_eating
        QuestionKind.CHEMICAL_DOSE -> R.string.fixed_chemical
        QuestionKind.BEE_OR_EXOTIC_PEST -> R.string.fixed_bee_pest
        QuestionKind.HANDLING -> R.string.fixed_handling
        QuestionKind.INJURED_WILDLIFE -> R.string.fixed_wildlife_no_state
        QuestionKind.GENERAL -> R.string.ask_failed
    }

private val RecordKind.icon: Int
    get() = when (this) {
        RecordKind.PLANT -> R.drawable.ic_potted_plant
        RecordKind.ZONE -> R.drawable.ic_yard
        RecordKind.SIGHTING -> R.drawable.ic_raven
    }
