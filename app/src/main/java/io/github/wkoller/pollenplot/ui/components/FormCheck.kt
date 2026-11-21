package io.github.wkoller.pollenplot.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.ui.theme.Spacing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Checks a form when its Save button is pressed. Save is never greyed out. A press with something missing or wrong
 * shows each field's own "Please …" message, scrolls to the first of them and puts the cursor in it; a press with
 * everything in order saves. Each form keeps its own rules and works out its problems from what has been entered, so
 * a message goes as soon as the field is put right. This only decides when the messages show and where the reader is
 * taken.
 */
@Stable
class FormCheck internal constructor(tried: MutableState<Boolean>, private val scope: CoroutineScope) {
    private var pressed by tried
    private val places = mutableMapOf<String, Place>()

    /** Whether Save has been pressed while something needed fixing; from then on each field shows its problem. */
    val tried: Boolean get() = pressed

    internal class Place {
        val bring = BringIntoViewRequester()
        val focus = FocusRequester()
        var focusable = false
        var list: LazyListState? = null
        var index = -1
    }

    internal fun place(key: String): Place = places.getOrPut(key) { Place() }

    /** Starts over, as after a part of the form was added and its fields were cleared for the next one. */
    fun reset() {
        pressed = false
    }

    /** [problem] once it should show, which is after Save has been pressed, or null. */
    fun shown(problem: String?): String? = problem.takeIf { pressed }

    /** Whether a field with [problem] is drawn as wrong now. */
    fun isError(problem: String?): Boolean = shown(problem) != null

    /**
     * A field's line under it: its problem with a warning icon once it shows, otherwise [help] when there is some, or
     * nothing.
     */
    fun supporting(problem: String?, help: String? = null): (@Composable () -> Unit)? {
        val shown = shown(problem)
        return when {
            shown != null -> {
                { ProblemLine(shown) }
            }
            help != null -> {
                { Text(help) }
            }
            else -> null
        }
    }

    /**
     * Runs [save] when none of [problems] has a message. Otherwise it shows them all and takes the reader to the first
     * one, by its key, so the problems are given in the form's own order.
     */
    fun submit(vararg problems: Pair<String, String?>, save: () -> Unit) {
        if (passes(*problems)) save()
    }

    /**
     * Whether none of [problems] has a message, so the form may be saved. When one has, it shows them all, takes the
     * reader to the first, by its key in the order given, and says no.
     */
    fun passes(vararg problems: Pair<String, String?>): Boolean {
        val first = problems.firstOrNull { it.second != null } ?: return true
        pressed = true
        val place = places[first.first] ?: return false
        scope.launch {
            // An item of a lazy list may not be laid out yet, so the list goes to it first.
            place.list?.let { list -> if (place.index >= 0) list.animateScrollToItem(place.index) }
            runCatching { place.bring.bringIntoView() }
            // A field that has left the screen cannot take focus, and that is no reason to fail.
            if (place.focusable) runCatching { place.focus.requestFocus() }
        }
        return false
    }
}

/** A form check that remembers across a turn of the phone whether Save has been pressed. */
@Composable
fun rememberFormCheck(): FormCheck {
    val tried = rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    return remember(tried, scope) { FormCheck(tried, scope) }
}

/**
 * Marks a field that [check] can take the reader to, named [key] in [FormCheck.submit]. A text field takes the cursor
 * ([focus]); a group of chips is only scrolled to. A field inside a lazy list gives the [list] and its item's [index].
 * While its [problem] shows, a screen reader says it as the field's error. A problem that is [live], such as a number
 * that cannot be read, shows as it is typed rather than once Save has been pressed.
 */
fun Modifier.formField(
    check: FormCheck,
    key: String,
    problem: String? = null,
    focus: Boolean = true,
    list: LazyListState? = null,
    index: Int = -1,
    live: Boolean = false,
): Modifier {
    val place = check.place(key)
    place.focusable = focus
    place.list = list
    place.index = index
    val shown = if (live) problem else check.shown(problem)
    return this
        .bringIntoViewRequester(place.bring)
        .then(if (focus) Modifier.focusRequester(place.focus) else Modifier)
        .then(if (shown != null) Modifier.semantics { error(shown) } else Modifier)
}

/** A problem's words with the warning icon before them, so it never rests on colour alone. */
@Composable
fun ProblemLine(text: String, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(R.drawable.ic_warning), contentDescription = null, modifier = Modifier.size(16.dp))
        Text(text)
    }
}

/**
 * The problem with a group of chips or a picker, shown under it in the error colour once Save has been pressed. The
 * line is always there, empty until it has something to say, so a screen reader reads the message out as it appears.
 */
@Composable
fun FieldProblem(problem: String?, modifier: Modifier = Modifier) {
    Box(modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        if (problem != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_warning), contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                Text(problem, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
