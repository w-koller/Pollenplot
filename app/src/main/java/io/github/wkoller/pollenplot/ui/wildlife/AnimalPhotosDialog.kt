package io.github.wkoller.pollenplot.ui.wildlife

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
import io.github.wkoller.pollenplot.domain.wildlife.LifeGroup

/**
 * Asks what kind of animal the photos from the gallery show before they are chosen, because the quick models cannot
 * tell a plant from an animal. Birds come first, as on the camera from the Wildlife tab.
 */
@Composable
fun AnimalPhotosDialog(onDismiss: () -> Unit, onChoose: (LifeGroup) -> Unit) {
    var group by rememberSaveable { mutableStateOf(LifeGroup.BIRD) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.animal_photos_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.animal_photos_body), style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CAMERA_GROUPS.filter { it.isWildlife }.forEach { choice ->
                        FilterChip(selected = choice == group, onClick = { group = choice }, label = { Text(stringResource(choice.label)) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onChoose(group) }) { Text(stringResource(R.string.animal_photos_choose)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
