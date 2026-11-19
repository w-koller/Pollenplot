package io.github.wkoller.pollenplot.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.wkoller.pollenplot.R

/**
 * Asks whether to save changes not yet saved, when the user goes back from a form: Save saves them, Don't save leaves
 * without them, and pressing outside the dialog or going back again stays on the form.
 */
@Composable
fun UnsavedChangesDialog(onSave: () -> Unit, onDiscard: () -> Unit, onKeepEditing: () -> Unit) {
    AlertDialog(
        onDismissRequest = onKeepEditing,
        title = { Text(stringResource(R.string.unsaved_title)) },
        text = { Text(stringResource(R.string.unsaved_body)) },
        confirmButton = { TextButton(onClick = onSave) { Text(stringResource(R.string.unsaved_save)) } },
        dismissButton = { TextButton(onClick = onDiscard) { Text(stringResource(R.string.unsaved_discard)) } },
    )
}
