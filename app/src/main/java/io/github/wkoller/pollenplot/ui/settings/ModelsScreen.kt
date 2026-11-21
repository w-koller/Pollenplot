package io.github.wkoller.pollenplot.ui.settings

import io.github.wkoller.pollenplot.ui.theme.Spacing
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
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
import io.github.wkoller.pollenplot.download.VerifiedDownload
import io.github.wkoller.pollenplot.ml.AccuratePack
import io.github.wkoller.pollenplot.ml.DiseasePack
import io.github.wkoller.pollenplot.ml.AccuratePacks
import io.github.wkoller.pollenplot.ml.PackState
import io.github.wkoller.pollenplot.search.MeaningSearch
import io.github.wkoller.pollenplot.ui.components.DetailScaffold
import io.github.wkoller.pollenplot.ui.components.PollenProgressBar
import kotlinx.coroutines.launch

/** Settings › Models: the models the app can download, how big they are, and whether they are on the phone. */
@Composable
fun ModelsScreen(meaning: MeaningSearch, accurate: AccuratePacks, gemma: GemmaModel, problems: DiseasePack?, onBack: () -> Unit) {
    DetailScaffold(title = stringResource(R.string.models_title), onBack = onBack) { padding ->
        ModelsContent(meaning, accurate, gemma, padding, problems = problems)
    }
}

/**
 * A card for each model with its download controls, for Settings and the first-launch setup. [setup] puts the
 * identification packs first, with BioCAP's marked as recommended; [problems] adds the plant problems pack's card
 * after the others (Settings only), and [top] adds items above the cards.
 */
@Composable
fun ModelsContent(
    meaning: MeaningSearch,
    accurate: AccuratePacks,
    gemma: GemmaModel,
    padding: PaddingValues,
    setup: Boolean = false,
    problems: DiseasePack? = null,
    top: LazyListScope.() -> Unit = {},
) {
    val context = LocalContext.current
    val state by meaning.state.collectAsStateWithLifecycle()
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
    val problemsItem: LazyListScope.() -> Unit = {
        if (problems != null) {
            item {
                val current by problems.state.collectAsStateWithLifecycle()
                val offered by problems.offered.collectAsStateWithLifecycle()
                ModelCard(
                    icon = R.drawable.ic_bug_report,
                    title = stringResource(R.string.models_problems),
                    body = if (problems.available) {
                        stringResource(R.string.models_problems_body, Formatter.formatShortFileSize(context, problems.download.sizeBytes))
                    } else {
                        stringResource(R.string.models_problems_body_short)
                    },
                ) {
                    when {
                        !problems.available -> Text(stringResource(R.string.models_not_ready), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        // It runs on BioCAP's vectors, so it waits for the accurate pack; a pack already here can still be deleted.
                        !offered && current != PackState.Ready -> Text(stringResource(R.string.models_problems_needs_accurate), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else -> PackControls(current, problems.download, R.string.models_problems_ready) { scope.launch { problems.delete() } }
                    }
                }
            }
        }
    }
    val biocapItem: LazyListScope.() -> Unit = {
        item { PackCard(accurate, accurate.biocap, setup) }
    }
    val bioclip2Item: LazyListScope.() -> Unit = {
        item { PackCard(accurate, accurate.bioclip2, setup) }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        top()
        item {
            Text(
                stringResource(if (setup) R.string.setup_models_intro else R.string.models_intro),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s),
            )
        }
        // Accurate identification matters most to a new garden, so setup offers it first.
        if (setup) {
            biocapItem()
            bioclip2Item()
        }
        meaningItem()
        gemmaItem()
        if (!setup) {
            biocapItem()
            bioclip2Item()
        }
        // Plant problems needs the accurate pack, so setup leaves it to Settings.
        if (!setup) problemsItem()
    }
}

/** What a model pack is doing, as a sentence, with the buttons that make sense now. [ready] says it is on the phone. */
@Composable
private fun PackControls(current: PackState, download: VerifiedDownload, ready: Int, onDelete: () -> Unit) {
    val status = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    when (current) {
        PackState.NotAvailable -> Text(stringResource(R.string.models_not_ready), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        PackState.Unpacking -> {
            Text(stringResource(R.string.models_unpacking), style = MaterialTheme.typography.bodyMedium, modifier = status)
            PollenProgressBar(null, Modifier.fillMaxWidth())
        }
        PackState.Ready -> {
            Text(stringResource(ready), style = MaterialTheme.typography.bodyMedium, modifier = status)
            OutlinedButton(onClick = onDelete) { Text(stringResource(R.string.models_delete)) }
        }
        PackState.Damaged -> {
            Text(stringResource(R.string.models_damaged), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = status)
            Button(onClick = { download.start() }) { Text(stringResource(R.string.models_try_again)) }
        }
        is PackState.Fetching -> DownloadControls(
            state = current.download,
            totalBytes = download.sizeBytes,
            ready = R.string.models_unpacking,
            onDownload = { download.start() },
            onUseMobileData = { download.restartWithMobileData() },
            onCancel = { download.cancel() },
            onDelete = onDelete,
        )
    }
}

/**
 * An accurate pack's card: BioCAP's for every phone, or BioCLIP 2's, marked for high-end phones with its recommended
 * requirements in a tooltip and a warning on a phone with less memory than recommended.
 */
@Composable
private fun PackCard(packs: AccuratePacks, accurate: AccuratePack, setup: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val current by accurate.state.collectAsStateWithLifecycle()
    val other by (if (accurate === packs.bioclip2) packs.biocap else packs.bioclip2).state.collectAsStateWithLifecycle()
    val bioclip2 = accurate === packs.bioclip2
    val size = Formatter.formatShortFileSize(context, accurate.download.sizeBytes)
    val title = stringResource(if (bioclip2) R.string.models_bioclip2 else R.string.models_accurate)
    ModelCard(
        icon = R.drawable.ic_local_florist,
        title = if (setup && !bioclip2) stringResource(R.string.setup_recommended_model, title) else title,
        body = when {
            bioclip2 && accurate.available -> stringResource(R.string.models_bioclip2_body, size)
            bioclip2 -> stringResource(R.string.models_bioclip2_body_short)
            accurate.available -> stringResource(R.string.models_accurate_body, size)
            else -> stringResource(R.string.models_accurate_body_short)
        },
    ) {
        if (bioclip2) {
            HighEndRequirements()
            if (packs.memoryBytes > 0 && !packs.meetsBioclip2Memory) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.Top) {
                    Icon(painterResource(R.drawable.ic_warning), contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text(
                        stringResource(R.string.models_bioclip2_low_memory, Formatter.formatShortFileSize(context, packs.memoryBytes)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        val status = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        when (val state = current) {
            PackState.NotAvailable -> Text(stringResource(R.string.models_not_ready), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PackState.Unpacking -> {
                Text(stringResource(R.string.models_unpacking), style = MaterialTheme.typography.bodyMedium, modifier = status)
                PollenProgressBar(null, Modifier.fillMaxWidth())
            }
            PackState.Ready -> {
                val ready = when {
                    bioclip2 -> R.string.models_bioclip2_ready
                    // BioCLIP 2 identifies while both are on the phone.
                    other == PackState.Ready -> R.string.models_accurate_ready_unused
                    else -> R.string.models_accurate_ready
                }
                Text(stringResource(ready), style = MaterialTheme.typography.bodyMedium, modifier = status)
                OutlinedButton(onClick = { scope.launch { accurate.delete() } }) { Text(stringResource(R.string.models_delete)) }
            }
            PackState.Damaged -> {
                Text(stringResource(R.string.models_damaged), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = status)
                Button(onClick = { accurate.download.start() }) { Text(stringResource(R.string.models_try_again)) }
            }
            is PackState.Fetching -> DownloadControls(
                state = state.download,
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

/** "For high-end phones", with a button that shows the recommended requirements in a tooltip. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HighEndRequirements() {
    val scope = rememberCoroutineScope()
    val tooltip = rememberTooltipState(isPersistent = true)
    val heading = stringResource(R.string.models_requirements)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.models_high_end), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
            tooltip = {
                RichTooltip(title = { Text(heading) }) { Text(stringResource(R.string.models_bioclip2_requirements)) }
            },
            state = tooltip,
        ) {
            IconButton(onClick = { scope.launch { tooltip.show() } }) {
                Icon(painterResource(R.drawable.ic_info), contentDescription = heading)
            }
        }
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
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
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
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.l),
    ) {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            }
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}
