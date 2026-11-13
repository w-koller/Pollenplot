package io.github.wkoller.pollenplot.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.wkoller.pollenplot.R

/**
 * What a record, read once, turned out to be: there, or gone. A page starts with null while it reads, and a [Found]
 * with no value means the record is not in the garden, as after it was deleted and a link to it was followed.
 */
class Found<T>(val value: T?)

/** Says a record is no longer in the garden, on the page that would have shown it, in place of a blank screen. */
@Composable
fun MissingRecord(padding: PaddingValues) {
    EmptyState(
        icon = R.drawable.ic_search,
        title = stringResource(R.string.missing_title),
        body = stringResource(R.string.missing_body),
        modifier = Modifier.padding(padding),
    )
}
