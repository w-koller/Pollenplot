package io.github.wkoller.pollenplot.ui.settings

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.ai.GemmaModel
import io.github.wkoller.pollenplot.download.DownloadState
import io.github.wkoller.pollenplot.ml.AccuratePack
import io.github.wkoller.pollenplot.ml.PackState
import io.github.wkoller.pollenplot.search.MeaningSearch
import io.github.wkoller.pollenplot.ui.components.DetailScaffold
import io.github.wkoller.pollenplot.ui.components.PollenProgressBar
import kotlinx.coroutines.launch

/** Settings › Models: the models the app can download, how big they are, and whether they are on the phone. */
@Composable
fun ModelsScreen(meaning: MeaningSearch, accurate: AccuratePack, gemma: GemmaModel, onBack: () -> Unit) {
    DetailScaffold(title = stringResource(R.string.models_title), onBack = onBack) { padding ->
        ModelsContent(meaning, accurate, gemma, padding)
    }
}

/**
 * A card for each model with its download controls, for Settings and the first-launch setup. [setup] puts accurate
 * identification first and marks it as recommended, and [top] adds items above the cards.
 */
@Composable
fun ModelsContent(
    meaning: MeaningSearch,
    accurate: AccuratePack,
    gemma: GemmaModel,
    padding: PaddingValues,
    setup: Boolean = false,
    top: LazyListScope.() -> Unit = {},
) {
    val context = LocalContext.current
    val state by meaning.state.collectAsStateWithLifecycle()
    val pack by accurate.state.collectAsStateWithLifecycle()
    val assistant by gemma.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val meaningItem: LazyListScope.() -> Unit = {
        item {
            ModelCard(
                icon = R.drawable.ic_search,
                title = stringResource(R.string.models_meaning),
                body = stringResource(R.string.models_meaning_body, Formatter.formatShortFileSize(context, meaning.download.sizeBytes)),
            ) {
                DownloadControls(
                    state = state,
                    totalBytes = meaning.download.sizeBytes,
                    ready = R.string.models_meaning_ready,
                    onDownload = { meaning.download.start() },
                    onUseMobileData = { meaning.download.restartWithMobileData() },
                    onCancel = { meaning.download.cancel() },
                    onDelete = { scope.launch { meaning.delete() } },
                )
            }
        }
    }
    val gemmaItem: LazyListScope.() -> Unit = {
        item {
            ModelCard(
                icon = R.drawable.ic_forum,
                title = stringResource(R.string.models_gemma),
                body = stringResource(R.string.models_gemma_body, Formatter.formatShortFileSize(context, gemma.download.sizeBytes)),
            ) {
                DownloadControls(
                    state = assistant,
                    totalBytes = gemma.download.sizeBytes,
                    ready = R.string.models_gemma_ready,
                    onDownload = { gemma.download.start() },
                    onUseMobileData = { gemma.download.restartWithMobileData() },
                    onCancel = { gemma.download.cancel() },
                    onDelete = { scope.launch { gemma.delete() } },
                )
            }
        }
    }
    val accurateItem: LazyListScope.() -> Unit = {
        item {
            val current = pack
            ModelCard(
                icon = R.drawable.ic_local_florist,
                title = if (setup) stringResource(R.string.setup_recommended_model, stringResource(R.string.models_accurate)) else stringResource(R.string.models_accurate),
                body = if (accurate.available) {
                    stringResource(R.string.models_accurate_body, Formatter.formatShortFileSize(context, accurate.download.sizeBytes))
                } else {
                    stringResource(R.string.models_accurate_body_short)
                },
            ) {
                val status = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                when (current) {
                    PackState.NotAvailable -> Text(stringResource(R.string.models_not_ready), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    PackState.Unpacking -> {
                        Text(stringResource(R.string.models_unpacking), style = MaterialTheme.typography.bodyMedium, modifier = status)
                        PollenProgressBar(null, Modifier.fillMaxWidth())
                    }
                    PackState.Ready -> {
                        Text(stringResource(R.string.models_accurate_ready), style = MaterialTheme.typography.bodyMedium, modifier = status)
                        OutlinedButton(onClick = { scope.launch { accurate.delete() } }) { Text(stringResource(R.string.models_delete)) }
                    }
                    PackState.Damaged -> {
                        Text(stringResource(R.string.models_damaged), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = status)
                        Button(onClick = { accurate.download.start() }) { Text(stringResource(R.string.models_try_again)) }
                    }
                    is PackState.Fetching -> DownloadControls(
                        state = current.download,
                        totalBytes = accurate.download.sizeBytes,
                        ready = R.string.models_unpacking,
                        onDownload = { accurate.download.start() },
                        onUseMobileData = { accurate.download.restartWithMobileData() },
                        onCancel = { accurate.download.cancel() },
                        onDelete = { scope.launch { accurate.delete() } },
                    )
                }
            }
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        top()
        item {
            Text(
                stringResource(if (setup) R.string.setup_models_intro else R.string.models_intro),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        // Accurate identification matters most to a new garden, so setup offers it first.
        if (setup) accurateItem()
        meaningItem()
        gemmaItem()
        if (!setup) accurateItem()
    }
}

/** What the download is doing, as a sentence, with the buttons that make sense now. [ready] says it is done. */
@Composable
private fun DownloadControls(
    state: DownloadState,
    totalBytes: Long,
    ready: Int,
    onDownload: () -> Unit,
    onUseMobileData: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val status = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    when (state) {
        DownloadState.NotDownloaded -> Button(onClick = onDownload) { Text(stringResource(R.string.models_download)) }
        DownloadState.WaitingForWifi -> {
            Text(stringResource(R.string.models_waiting_wifi), style = MaterialTheme.typography.bodyMedium, modifier = status)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onUseMobileData) { Text(stringResource(R.string.models_use_mobile_data)) }
                OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
            }
        }
        is DownloadState.Downloading -> {
            val total = if (state.totalBytes > 0) state.totalBytes else totalBytes
            Text(
                stringResource(R.string.models_downloading, Formatter.formatShortFileSize(context, state.downloadedBytes), Formatter.formatShortFileSize(context, total)),
                style = MaterialTheme.typography.bodyMedium,
                modifier = status,
            )
            PollenProgressBar(if (total > 0) state.downloadedBytes.toFloat() / total else null, Modifier.fillMaxWidth())
            OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
        }
        DownloadState.Verifying -> {
            Text(stringResource(R.string.models_verifying), style = MaterialTheme.typography.bodyMedium, modifier = status)
            PollenProgressBar(null, Modifier.fillMaxWidth())
        }
        DownloadState.Complete -> {
            Text(stringResource(ready), style = MaterialTheme.typography.bodyMedium, modifier = status)
            OutlinedButton(onClick = onDelete) { Text(stringResource(R.string.models_delete)) }
        }
        is DownloadState.Failed -> {
            Text(
                stringResource(
                    when (state.reason) {
                        DownloadState.Reason.NOT_ENOUGH_SPACE -> R.string.models_failed_space
                        DownloadState.Reason.DOWNLOAD_FAILED -> R.string.models_failed_download
                        DownloadState.Reason.CHECK_FAILED -> R.string.models_failed_check
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = status,
            )
            Button(onClick = onDownload) { Text(stringResource(R.string.models_try_again)) }
        }
    }
}

@Composable
private fun ModelCard(icon: Int, title: String, body: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            }
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}
