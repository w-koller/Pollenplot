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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import io.github.wkoller.pollenplot.report.ReportPart
import io.github.wkoller.pollenplot.ui.components.DetailScaffold
import io.github.wkoller.pollenplot.ui.plot.ChipGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.time.LocalDate

/** How far back a report reaches. */
private enum class ReportPeriod(val months: Long, val label: Int) {
    MONTH(1, R.string.report_month),
    QUARTER(3, R.string.report_quarter),
    YEAR(12, R.string.report_year),
}

/**
 * The garden report, as a PDF or a spreadsheet for a period and the parts chosen, and the two exports of the records,
 * each saved through the system's file picker with what became of the last save.
 */
@Composable
fun ExportScreen(
    today: LocalDate,
    report: suspend (Set<ReportPart>, LocalDate, LocalDate, Boolean, OutputStream) -> Unit,
    jsonLd: suspend () -> String,
    darwinCore: suspend () -> String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var period by rememberSaveable { mutableStateOf(ReportPeriod.QUARTER) }
    var parts by rememberSaveable { mutableStateOf(ReportPart.entries.toSet()) }
    var reportSaved by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var jsonLdSaved by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var darwinCoreSaved by rememberSaveable { mutableStateOf<Boolean?>(null) }

    fun save(uri: android.net.Uri?, write: suspend (OutputStream) -> Unit, done: (Boolean) -> Unit) {
        if (uri == null) return
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { write(it) } != null
                } catch (e: IOException) {
                    false
                } catch (e: SecurityException) {
                    false
                }
            }
            done(saved)
        }
    }
    fun text(content: suspend () -> String): suspend (OutputStream) -> Unit = { out -> out.write(content().toByteArray()) }
    fun reportTo(pdf: Boolean): suspend (OutputStream) -> Unit = { out -> report(parts, today.minusMonths(period.months), today, pdf, out) }

    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        save(uri, reportTo(pdf = true)) { reportSaved = it }
    }
    val saveXlsx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(XLSX)) { uri ->
        save(uri, reportTo(pdf = false)) { reportSaved = it }
    }
    val saveJsonLd = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/ld+json")) { uri ->
        save(uri, text(jsonLd)) { jsonLdSaved = it }
    }
    val saveDarwinCore = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        save(uri, text(darwinCore)) { darwinCoreSaved = it }
    }

    DetailScaffold(title = stringResource(R.string.export_title), onBack = onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item {
                Text(stringResource(R.string.export_body), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.report), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.report_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ChipGroup(stringResource(R.string.report_period), ReportPeriod.entries, period, { stringResource(it.label) }) { period = it }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.report_sections), style = MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ReportPart.entries.forEach { part ->
                                FilterChip(selected = part in parts, onClick = { parts = if (part in parts) parts - part else parts + part }, label = { Text(stringResource(part.label)) })
                            }
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = parts.isNotEmpty(), onClick = { savePdf.launch("pollenplot-report-$today.pdf") }) { Text(stringResource(R.string.report_save_pdf)) }
                        OutlinedButton(enabled = parts.isNotEmpty(), onClick = { saveXlsx.launch("pollenplot-report-$today.xlsx") }) { Text(stringResource(R.string.report_save_xlsx)) }
                    }
                    reportSaved?.let { Saved(it) }
                }
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
        saved?.let { Saved(it) }
    }
}

/** What became of a save, in words and with an icon. */
@Composable
private fun Saved(ok: Boolean) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Icon(painterResource(if (ok) R.drawable.ic_check else R.drawable.ic_warning), contentDescription = null, modifier = Modifier.size(18.dp))
        Text(stringResource(if (ok) R.string.export_saved else R.string.export_failed), style = MaterialTheme.typography.bodyMedium)
    }
}

private const val XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
