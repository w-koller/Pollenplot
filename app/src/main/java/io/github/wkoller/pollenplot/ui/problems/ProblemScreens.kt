package io.github.wkoller.pollenplot.ui.problems

import io.github.wkoller.pollenplot.ui.theme.IconSize
import io.github.wkoller.pollenplot.ui.components.readable
import io.github.wkoller.pollenplot.ui.components.FactCard
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import io.github.wkoller.pollenplot.ui.components.ChoiceChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import io.github.wkoller.pollenplot.ui.components.HeroPictureSize
import io.github.wkoller.pollenplot.ui.components.PictureAvatar
import io.github.wkoller.pollenplot.ui.components.RecordHero
import io.github.wkoller.pollenplot.ui.components.groupShape
import io.github.wkoller.pollenplot.ui.components.heroGone
import io.github.wkoller.pollenplot.ui.theme.Spacing
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wkoller.pollenplot.R
import kotlinx.coroutines.flow.map
import io.github.wkoller.pollenplot.ui.components.MissingRecord
import io.github.wkoller.pollenplot.ui.components.Found
import io.github.wkoller.pollenplot.data.ProblemRepository
import io.github.wkoller.pollenplot.data.ProblemView
import io.github.wkoller.pollenplot.data.ProblemViews
import io.github.wkoller.pollenplot.domain.problems.DiseaseCall
import io.github.wkoller.pollenplot.domain.problems.DiseaseConfidence
import io.github.wkoller.pollenplot.domain.problems.ProblemCatalogue
import io.github.wkoller.pollenplot.domain.problems.ProblemTasks
import io.github.wkoller.pollenplot.domain.problems.TreatmentMethod
import io.github.wkoller.pollenplot.ui.UiFormat
import io.github.wkoller.pollenplot.ui.bees.DateRow
import io.github.wkoller.pollenplot.ui.components.Badge
import io.github.wkoller.pollenplot.ui.components.DetailScaffold
import io.github.wkoller.pollenplot.ui.components.PastDateDialog
import io.github.wkoller.pollenplot.ui.plants.BadgeTone
import io.github.wkoller.pollenplot.ui.review.Cutout
import io.github.wkoller.pollenplot.ui.weeds.LinkButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@get:StringRes
val TreatmentMethod.label: Int
    get() = when (this) {
        TreatmentMethod.WHITE_OIL -> R.string.treatment_white_oil
        TreatmentMethod.SOAP_SPRAY -> R.string.treatment_soap_spray
        TreatmentMethod.POTASSIUM_BICARBONATE -> R.string.treatment_potassium_bicarbonate
        TreatmentMethod.HOSE_OFF -> R.string.treatment_hose_off
        TreatmentMethod.HAND_REMOVE -> R.string.treatment_hand_remove
        TreatmentMethod.PRUNE_OUT -> R.string.treatment_prune_out
        TreatmentMethod.TRAP -> R.string.treatment_trap
        TreatmentMethod.BENEFICIALS -> R.string.treatment_beneficials
        TreatmentMethod.OTHER_SPRAY -> R.string.treatment_other_spray
        TreatmentMethod.OTHER -> R.string.treatment_other
    }

/** A problem's status, as an icon and a word. */
@Composable
private fun ProblemStatus(view: ProblemView) {
    if (view.problem.open) {
        Badge(R.drawable.ic_bug_report, stringResource(R.string.problem_open), BadgeTone.WARNING)
    } else {
        Badge(R.drawable.ic_check_circle, stringResource(R.string.problem_resolved), BadgeTone.PLANT)
    }
}

/** The problems logged for a plant, open ones first, each opening its page, and a button to log another. */
@Composable
fun ProblemsCard(problems: List<ProblemView>, onOpen: (String) -> Unit, onLog: () -> Unit) {
    FactCard(R.drawable.ic_bug_report, stringResource(R.string.problems_title)) {
        if (problems.isEmpty()) Text(stringResource(R.string.problems_none), style = MaterialTheme.typography.bodyLarge)
        problems.sortedWith(compareBy({ !it.problem.open }, { -it.problem.observedOn.toEpochDay() })).forEach { view ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { onOpen(view.problem.id) }.padding(vertical = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    Text(view.name, style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.problem_seen_on, UiFormat.longDate(view.problem.observedOn)), style = MaterialTheme.typography.bodySmall)
                }
                ProblemStatus(view)
            }
        }
        OutlinedButton(onClick = onLog) { Text(stringResource(R.string.problem_log)) }
    }
}

/**
 * Logs a problem on a plant: one from the catalogue or something else by name, the day it was seen, a photo the user
 * chooses (kept without looking for plants in it) and notes. With [lookForProblems], which is there while the plant
 * problems pack is on the phone, a chosen photo is looked at and the problems it looks like are offered as chips; the
 * user still chooses one and saves.
 */
@Composable
fun LogProblemScreen(
    plantId: String,
    catalogue: ProblemCatalogue,
    problems: ProblemRepository,
    today: LocalDate,
    savePhoto: suspend (Uri) -> String?,
    lookForProblems: (suspend (String) -> DiseaseCall?)? = null,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var key by rememberSaveable { mutableStateOf<String?>(null) }
    var other by rememberSaveable { mutableStateOf(false) }
    var otherName by rememberSaveable { mutableStateOf("") }
    var day by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var photo by rememberSaveable { mutableStateOf<String?>(null) }
    var photoFailed by rememberSaveable { mutableStateOf(false) }
    var notes by rememberSaveable { mutableStateOf("") }
    val date = LocalDate.ofEpochDay(day)
    val choose = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val saved = savePhoto(uri)
                photoFailed = saved == null
                if (saved != null) photo = saved
            }
        }
    }
    val ready = (key != null && !other) || (other && otherName.isNotBlank())
    var call by remember { mutableStateOf<DiseaseCall?>(null) }
    var looking by remember { mutableStateOf(false) }
    LaunchedEffect(photo, lookForProblems) {
        val chosen = photo
        call = null
        if (chosen != null && lookForProblems != null) {
            looking = true
            call = try {
                lookForProblems(chosen)
            } finally {
                looking = false
            }
        }
    }

    DetailScaffold(title = stringResource(R.string.problem_log_title), onBack = onBack) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(Spacing.l).readable(),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            Text(stringResource(R.string.problem_what), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            Column(Modifier.selectableGroup()) {
                catalogue.problems.forEach { info ->
                    ChoiceRow(info.name, selected = !other && key == info.key) {
                        key = info.key
                        other = false
                    }
                }
                ChoiceRow(stringResource(R.string.problem_other), selected = other) { other = true }
            }
            if (other) {
                OutlinedTextField(
                    value = otherName,
                    onValueChange = { otherName = it },
                    label = { Text(stringResource(R.string.problem_other_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            DateRow(date) { pickingDate = true }
            photo?.let { Cutout(it, 160.dp) }
            OutlinedButton(onClick = { choose.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                Icon(painterResource(R.drawable.ic_add_photo_alternate), contentDescription = null, modifier = Modifier.size(IconSize.small))
                Text(stringResource(if (photo == null) R.string.problem_add_photo else R.string.problem_change_photo), modifier = Modifier.padding(start = Spacing.s))
            }
            if (photoFailed) Text(stringResource(R.string.problem_photo_failed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            if (looking) {
                Text(stringResource(R.string.problem_looking), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            call?.let { found ->
                LooksLike(found, selectedKey = key.takeIf { !other }) { chosen ->
                    key = chosen
                    other = false
                }
            }
            OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text(stringResource(R.string.honey_notes)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = {
                    scope.launch {
                        problems.add(plantId, key.takeIf { !other }, otherName.takeIf { other }, date, photo, notes)
                        // The database finishes on its own thread; going back has to happen on the main one.
                        withContext(Dispatchers.Main) { onBack() }
                    }
                },
                enabled = ready,
            ) { Text(stringResource(R.string.honey_save)) }
        }
    }
    if (pickingDate) {
        PastDateDialog(date, today, onDismiss = { pickingDate = false }) {
            day = it.toEpochDay()
            pickingDate = false
        }
    }
}

/** What the plant problems model made of the photo: chips for the problems it looks like, or a sentence. */
@Composable
private fun LooksLike(call: DiseaseCall, selectedKey: String?, onChoose: (String) -> Unit) {
    val status = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    when (call) {
        is DiseaseCall.NoProblemFound -> Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically, modifier = status) {
            Icon(painterResource(R.drawable.ic_check_circle), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.problem_none_found), style = MaterialTheme.typography.bodyMedium)
        }
        DiseaseCall.Unsure -> Text(stringResource(R.string.problem_unsure), style = MaterialTheme.typography.bodyMedium, modifier = status)
        is DiseaseCall.LooksLike -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = status) {
            Text(stringResource(R.string.problem_looks_like), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                call.guesses.forEach { guess ->
                    val label = stringResource(
                        if (guess.confidence == DiseaseConfidence.LIKELY) R.string.problem_guess_likely else R.string.problem_guess_possible,
                        guess.problem.name,
                    )
                    ChoiceChip(
                        selected = selectedKey == guess.problem.key,
                        onClick = { onChoose(guess.problem.key) },
                        label = { Text(label) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_bug_report), contentDescription = null, modifier = Modifier.size(IconSize.small)) },
                    )
                }
            }
            Text(stringResource(R.string.problem_looks_like_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChoiceRow(title: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(title, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * One problem: what it is and what the catalogue's source says to do, its photo, its treatments, and buttons to record
 * a treatment, mark it resolved or delete it.
 */
@Composable
fun ProblemScreen(
    id: String,
    views: ProblemViews,
    problems: ProblemRepository,
    onBack: () -> Unit,
    onRecordTreatment: () -> Unit,
    onOpenAnimal: (String) -> Unit = {},
) {
    val found by remember(id) { views.problem(id).map { Found(it) } }.collectAsStateWithLifecycle(initialValue = null)
    val view = found?.value
    val scope = rememberCoroutineScope()
    val notices = io.github.wkoller.pollenplot.ui.components.LocalNotices.current
    val resolvedText = stringResource(R.string.problem_resolved_notice)
    val uri = LocalUriHandler.current
    var deleting by rememberSaveable { mutableStateOf(false) }
    val shown = view
    val scroll = rememberScrollState()

    DetailScaffold(
        title = shown?.name ?: stringResource(R.string.problem_title),
        onBack = onBack,
        titleShown = heroGone(scroll),
        actions = {
            if (shown != null) {
                IconButton(onClick = { deleting = true }) { Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.problem_delete)) }
            }
        },
    ) { padding ->
        if (shown == null) {
            if (found != null) MissingRecord(padding)
            return@DetailScaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(scroll).padding(Spacing.l).readable(),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            RecordHero(
                title = shown.name,
                details = listOfNotNull(
                    shown.plantTitle?.let { stringResource(R.string.problem_on_plant, it) },
                    stringResource(R.string.problem_seen_on, UiFormat.longDate(shown.problem.observedOn)),
                ),
                tags = { ProblemStatus(shown) },
            ) {
                // A pest or a disease is shown in banksia rust, as follow-ups on problems are on the Today card.
                PictureAvatar(
                    R.drawable.ic_bug_report,
                    HeroPictureSize,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            shown.problem.photo?.let { Cutout(it, PHOTO_SIZE, Modifier.align(Alignment.CenterHorizontally)) }
            if (shown.problem.notes.isNotBlank()) Text(shown.problem.notes, style = MaterialTheme.typography.bodyLarge)
            shown.info?.let { info ->
                FactCard(R.drawable.ic_info, stringResource(R.string.problem_info_heading)) {
                    Text(info.description, style = MaterialTheme.typography.bodyLarge)
                    Text(info.advice, style = MaterialTheme.typography.bodyLarge)
                    info.fauna?.let { name -> TextButton(onClick = { onOpenAnimal(name) }) { Text(stringResource(R.string.problem_open_animal)) } }
                    LinkButton(R.drawable.ic_open_in_new, stringResource(R.string.problem_source, info.sourceTitle)) { uri.openUri(info.sourceUrl) }
                }
            }
            Text(
                stringResource(R.string.problem_treatments),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = Spacing.s).semantics { heading() },
            )
            if (shown.treatments.isEmpty()) Text(stringResource(R.string.problem_no_treatments), style = MaterialTheme.typography.bodyLarge)
            shown.treatments.forEachIndexed { index, treatment ->
                // The treatments as one group of joined cards, the way records are grouped on the hive pages.
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = groupShape(index, shown.treatments.size), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                        Text(stringResource(R.string.treatment_line, stringResource(treatment.method.label), UiFormat.longDate(treatment.appliedOn)), style = MaterialTheme.typography.bodyLarge)
                        if (treatment.productText.isNotBlank()) Text(treatment.productText, style = MaterialTheme.typography.bodyMedium)
                        treatment.followUpOn?.let { Text(stringResource(R.string.treatment_follow_up_on, UiFormat.longDate(it)), style = MaterialTheme.typography.bodyMedium) }
                        if (treatment.notes.isNotBlank()) Text(treatment.notes, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                if (shown.problem.open) Button(onClick = onRecordTreatment) { Text(stringResource(R.string.problem_record_treatment)) }
                OutlinedButton(onClick = {
                    scope.launch {
                        if (shown.problem.open) {
                            problems.setResolved(id, views.today())
                            notices?.show(resolvedText) { problems.setResolved(id, null) }
                        } else {
                            problems.setResolved(id, null)
                        }
                    }
                }) {
                    Text(stringResource(if (shown.problem.open) R.string.problem_mark_resolved else R.string.problem_mark_open))
                }
            }
        }
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(stringResource(R.string.problem_delete_title)) },
            text = { Text(stringResource(R.string.problem_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    scope.launch {
                        problems.delete(id)
                        withContext(Dispatchers.Main) { onBack() }
                    }
                }) { Text(stringResource(R.string.problem_delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** How large a problem's own photo is shown on its page. */
private val PHOTO_SIZE = 200.dp

/**
 * Records a treatment of a problem and when to look again. A spray asks first when plants nearby are likely in flower,
 * with BeeAware's advice to spray in the evening.
 */
@Composable
fun TreatmentScreen(problemId: String, plantId: String, views: ProblemViews, problems: ProblemRepository, today: LocalDate, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val inFlower by remember(plantId) { views.inFlowerNear(plantId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    var method by rememberSaveable { mutableStateOf<TreatmentMethod?>(null) }
    var product by rememberSaveable { mutableStateOf("") }
    var day by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var followDays by rememberSaveable { mutableStateOf<Long?>(ProblemTasks.FOLLOW_UP_DAYS) }
    var notes by rememberSaveable { mutableStateOf("") }
    var warning by rememberSaveable { mutableStateOf(false) }
    val date = LocalDate.ofEpochDay(day)

    fun save() {
        val chosen = method ?: return
        scope.launch {
            problems.addTreatment(problemId, chosen, product, date, followDays?.let { date.plusDays(it) }, notes)
            withContext(Dispatchers.Main) { onBack() }
        }
    }

    DetailScaffold(title = stringResource(R.string.treatment_title), onBack = onBack) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(Spacing.l).readable(),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            Text(stringResource(R.string.treatment_method), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                TreatmentMethod.entries.forEach { option ->
                    ChoiceChip(selected = option == method, onClick = { method = option }, label = { Text(stringResource(option.label)) })
                }
            }
            OutlinedTextField(value = product, onValueChange = { product = it }, label = { Text(stringResource(R.string.treatment_product)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.feed_no_amounts), style = MaterialTheme.typography.bodyMedium)
            DateRow(date) { pickingDate = true }
            Text(stringResource(R.string.treatment_follow_up), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                listOf(3L, ProblemTasks.FOLLOW_UP_DAYS, 14L).forEach { days ->
                    ChoiceChip(selected = followDays == days, onClick = { followDays = days }, label = { Text(stringResource(R.string.treatment_follow_up_days, days.toInt())) })
                }
                ChoiceChip(selected = followDays == null, onClick = { followDays = null }, label = { Text(stringResource(R.string.treatment_follow_up_none)) })
            }
            Text(stringResource(R.string.treatment_follow_up_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text(stringResource(R.string.honey_notes)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = { if (method?.spray == true && inFlower.isNotEmpty()) warning = true else save() },
                enabled = method != null,
            ) { Text(stringResource(R.string.honey_save)) }
        }
    }
    if (warning) {
        AlertDialog(
            onDismissRequest = { warning = false },
            title = { Text(stringResource(R.string.spray_title)) },
            text = { Text(stringResource(R.string.spray_body, UiFormat.joinAnd(inFlower))) },
            confirmButton = {
                TextButton(onClick = {
                    warning = false
                    save()
                }) { Text(stringResource(R.string.spray_save)) }
            },
            dismissButton = { TextButton(onClick = { warning = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (pickingDate) {
        PastDateDialog(date, today, onDismiss = { pickingDate = false }) {
            day = it.toEpochDay()
            pickingDate = false
        }
    }
}
