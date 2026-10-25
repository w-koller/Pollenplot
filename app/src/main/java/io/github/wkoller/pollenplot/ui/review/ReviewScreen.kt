package io.github.wkoller.pollenplot.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.data.GardenRepository
import io.github.wkoller.pollenplot.data.GardenViews
import io.github.wkoller.pollenplot.data.PhotoReview
import io.github.wkoller.pollenplot.data.kb.KnowledgeRepository
import io.github.wkoller.pollenplot.data.prefs.SettingsStore
import io.github.wkoller.pollenplot.scan.AddPhotosWorker
import io.github.wkoller.pollenplot.ui.UiFormat
import io.github.wkoller.pollenplot.ui.components.EmptyState
import io.github.wkoller.pollenplot.ui.components.PollenProgressBar
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** How far the photos the user added have got. [total] is 0 until the work has started. */
data class AddProgress(val done: Int, val total: Int)

/** The Review queue: photos with plants waiting for review, and the photos the user added that are still being looked at. */
class ReviewModel(garden: GardenRepository, settings: SettingsStore, work: WorkManager) : ViewModel() {
    val actions = DetectionActions(garden, settings, viewModelScope)

    /**
     * The photos shown: those with plants waiting when the queue opened, then any found while it is open. A photo stays
     * until the queue closes, so the user can see what they decided for each plant.
     */
    private val shown = MutableStateFlow<List<String>?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val reviews: StateFlow<List<PhotoReview>?> = shown
        .flatMapLatest { ids -> if (ids == null) flowOf(null) else garden.photosForReview(ids) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val adding: StateFlow<AddProgress?> = work.getWorkInfosForUniqueWorkFlow(AddPhotosWorker.NAME)
        .map { infos ->
            val running = infos.firstOrNull { it.state == WorkInfo.State.RUNNING }
            when {
                running != null -> AddProgress(running.progress.getInt(AddPhotosWorker.KEY_DONE, 0), running.progress.getInt(AddPhotosWorker.KEY_TOTAL, 0))
                infos.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED } -> AddProgress(0, 0)
                else -> null
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            garden.pendingReviewPhotos.collect { pending ->
                val current = shown.value.orEmpty()
                shown.value = current + pending.filterNot { it in current }
            }
        }
    }
}

/** The Review queue: each photo with plants found in it, and a card for each plant. */
@Composable
fun ReviewScreen(
    model: ReviewModel,
    views: GardenViews,
    knowledge: KnowledgeRepository,
    settings: SettingsStore,
    onBack: () -> Unit,
    onOpenPlant: (String) -> Unit,
    onAddPhotos: () -> Unit,
    onIdentify: () -> Unit,
) {
    val reviews by model.reviews.collectAsStateWithLifecycle()
    val adding by model.adding.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]
    PhotoReviewScreen(
        title = stringResource(R.string.review_title),
        reviews = reviews,
        actions = model.actions,
        views = views,
        knowledge = knowledge,
        settings = settings,
        onBack = onBack,
        onOpenPlant = onOpenPlant,
        top = {
            adding?.let { item(key = "adding") { AddingCard(it, Modifier.readable()) } }
        },
        photoHeading = { review ->
            Text(stringResource(R.string.review_taken, UiFormat.longDate(review.takenAt, locale)), style = MaterialTheme.typography.titleMedium)
        },
        empty = { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val progress = adding
                if (progress != null) {
                    AddingCard(progress, Modifier.readable().padding(top = 8.dp))
                } else {
                    EmptyState(R.drawable.ic_rate_review, stringResource(R.string.review_empty_title), stringResource(R.string.review_empty_body))
                    Button(onClick = onAddPhotos) { Text(stringResource(R.string.add_photos)) }
                    OutlinedButton(onClick = onIdentify) { Text(stringResource(R.string.add_identify_camera)) }
                }
            }
        },
    )
}

/** How far the photos the user added have got, as a sentence and a bar. */
@Composable
private fun AddingCard(progress: AddProgress, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = RoundedCornerShape(24.dp),
        modifier = modifier,
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                if (progress.total > 0) {
                    stringResource(R.string.review_adding_progress, progress.done + 1, progress.total)
                } else {
                    stringResource(R.string.review_adding_waiting)
                },
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            PollenProgressBar(if (progress.total > 0) progress.done.toFloat() / progress.total else null, Modifier.fillMaxWidth())
        }
    }
}
