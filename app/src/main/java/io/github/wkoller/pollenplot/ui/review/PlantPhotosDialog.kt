package io.github.wkoller.pollenplot.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.domain.id.PhotoScope

/**
 * Asks whether the plant photos about to be chosen show one plant each or garden beds of several, as the camera's
 * Plant and Garden bed choices do. One plant comes first: a photo of one plant looked at for several is how one plant
 * comes to have several cards.
 */
@Composable
fun PlantPhotosDialog(onDismiss: () -> Unit, onChoose: (PhotoScope) -> Unit) {
    var one by rememberSaveable { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.plant_photos_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.plant_photos_body), style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = one, onClick = { one = true }, label = { Text(stringResource(R.string.plant_photos_one)) })
                    FilterChip(selected = !one, onClick = { one = false }, label = { Text(stringResource(R.string.plant_photos_several)) })
                }
            }
        },
        confirmButton = { TextButton(onClick = { onChoose(if (one) PhotoScope.ONE else PhotoScope.SEVERAL) }) { Text(stringResource(R.string.plant_photos_choose)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
