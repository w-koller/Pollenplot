package io.github.wkoller.pollenplot.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.ui.components.DetailScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate

/** The two exports, each saved through the system's file picker, with what became of the last save. */
@Composable
fun ExportScreen(today: LocalDate, jsonLd: suspend () -> String, darwinCore: suspend () -> String, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var jsonLdSaved by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var darwinCoreSaved by rememberSaveable { mutableStateOf<Boolean?>(null) }

    fun save(uri: android.net.Uri?, text: suspend () -> String, done: (Boolean) -> Unit) {
        if (uri == null) return
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                try {
                    val content = text()
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(content.toByteArray()) } != null
                } catch (e: IOException) {
                    false
                } catch (e: SecurityException) {
                    false
                }
            }
            done(saved)
        }
    }

    val saveJsonLd = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/ld+json")) { uri ->
        save(uri, jsonLd) { jsonLdSaved = it }
    }
    val saveDarwinCore = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        save(uri, darwinCore) { darwinCoreSaved = it }
    }

    DetailScaffold(title = stringResource(R.string.export_title), onBack = onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item {
                Text(stringResource(R.string.export_body), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            item {
                ExportItem(R.string.export_jsonld, R.string.export_jsonld_body, jsonLdSaved) { saveJsonLd.launch("pollenplot-records-$today.jsonld") }
            }
            item {
                ExportItem(R.string.export_dwc, R.string.export_dwc_body, darwinCoreSaved) { saveDarwinCore.launch("pollenplot-sightings-$today.csv") }
            }
        }
    }
}

@Composable
private fun ExportItem(title: Int, body: Int, saved: Boolean?, onSave: () -> Unit) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = onSave) { Text(stringResource(R.string.export_save)) }
        saved?.let { ok ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                Icon(painterResource(if (ok) R.drawable.ic_check else R.drawable.ic_warning), contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(if (ok) R.string.export_saved else R.string.export_failed), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
