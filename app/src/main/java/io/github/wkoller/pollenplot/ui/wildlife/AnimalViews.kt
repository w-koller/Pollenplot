package io.github.wkoller.pollenplot.ui.wildlife

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.domain.plants.AuState
import io.github.wkoller.pollenplot.domain.wildlife.Animal

/**
 * What to know before going near an animal: its hazard, with what to do, and the rules for it in the garden's state,
 * such as fire ants having to be reported. These come from the knowledge base, never from the identifier, and the
 * card says nothing about handling an animal that has no hazard recorded.
 */
@Composable
fun SafetyNotice(animal: Animal, state: AuState?, modifier: Modifier = Modifier) {
    val rules = animal.rulesIn(state)
    val hazard = animal.hazard
    if (hazard == null && rules.isEmpty()) return
    val uri = LocalUriHandler.current
    Surface(color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer, shape = RoundedCornerShape(20.dp), modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (hazard != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_warning), contentDescription = null)
                    Text(stringResource(hazard.label), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                }
                animal.hazardNote?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
            }
            rules.forEach { rule ->
                Text(rule.text, style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = { uri.openUri(rule.source) }) {
                    Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text(stringResource(R.string.animal_rule_source))
                }
            }
        }
    }
}
