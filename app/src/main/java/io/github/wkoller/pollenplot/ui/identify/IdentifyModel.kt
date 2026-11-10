package io.github.wkoller.pollenplot.ui.identify

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.wkoller.pollenplot.data.GardenRepository
import io.github.wkoller.pollenplot.data.PhotoAnalysis
import io.github.wkoller.pollenplot.data.PhotoReview
import io.github.wkoller.pollenplot.data.WishRepository
import io.github.wkoller.pollenplot.data.photos.PhotoStore
import io.github.wkoller.pollenplot.data.kb.KnowledgeRepository
import io.github.wkoller.pollenplot.data.prefs.SettingsStore
import io.github.wkoller.pollenplot.domain.garden.PlantStatus
import io.github.wkoller.pollenplot.domain.id.Candidate
import io.github.wkoller.pollenplot.domain.id.CandidateFusion
import io.github.wkoller.pollenplot.domain.id.IdHistory
import io.github.wkoller.pollenplot.domain.id.IdSource
import io.github.wkoller.pollenplot.domain.id.PhotoOrigin
import io.github.wkoller.pollenplot.domain.id.PhotoScope
import io.github.wkoller.pollenplot.domain.wildlife.LifeGroup
import io.github.wkoller.pollenplot.ml.AiyModel
import io.github.wkoller.pollenplot.ml.Identifier
import io.github.wkoller.pollenplot.ui.review.DetectionActions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant

/** What the Identify screen is showing. */
sealed interface IdentifyStage {
    data object Camera : IdentifyStage

    data object Analysing : IdentifyStage

    data class Results(val photoId: String) : IdentifyStage

    data object NoneFound : IdentifyStage

    data object Failed : IdentifyStage
}

/**
 * The Identify screen's state: what the user is photographing (a plant, or a bird, insect or other animal), live
 * suggestions while the camera is pointed at it, then the photo being looked at, then what was found in it. The
 * results survive the phone being turned, and the app being closed in the background, because only the photo's ID and
 * the choice are kept and everything else is read back from the database.
 */
class IdentifyModel(
    private val analysis: PhotoAnalysis,
    private val identifier: () -> Identifier,
    private val garden: GardenRepository,
    private val knowledge: KnowledgeRepository,
    settings: SettingsStore,
    private val saved: SavedStateHandle,
    /** How many times a plant has been recorded near the garden, for the boost such plants get. */
    private val nearby: (String) -> Int = { 0 },
    /** Photos are taken away from home, to find out what a plant is and perhaps save it to the wishlist. */
    val field: Boolean = false,
    wishes: WishRepository? = null,
    /** Reads a photo the user chose, at most [PhotoStore.MAX_SIDE] across, and when it was taken. */
    private val readPhoto: suspend (Uri) -> Pair<Bitmap, Instant?>? = { null },
    /**
     * Lets go of a photo taken away from home with nothing saved from it. It runs outside this model's scope, because
     * the model may be going.
     */
    private val discardFieldPhoto: (String) -> Unit = {},
) : ViewModel() {
    private val _stage = MutableStateFlow(saved.get<String>(PHOTO_ID)?.let { IdentifyStage.Results(it) } ?: IdentifyStage.Camera)
    val stage: StateFlow<IdentifyStage> = _stage.asStateFlow()

    private val _group = MutableStateFlow(saved.get<String>(GROUP)?.let(LifeGroup::of) ?: LifeGroup.PLANT)

    /** What the user is photographing. The quick models cannot tell a plant from an animal, so the user says. */
    val group: StateFlow<LifeGroup> = _group.asStateFlow()

    private val _scope = MutableStateFlow(saved.get<String>(SCOPE)?.let(PhotoScope::valueOf) ?: PhotoScope.ONE)

    /** Whether a plant photo shows one plant or a garden bed of several. Animal photos show one animal. */
    val scope: StateFlow<PhotoScope> = _scope.asStateFlow()

    private val _live = MutableStateFlow<List<Candidate>>(emptyList())

    /** The best suggestions for what the camera is pointed at, or none while it is not pointed at a plant. */
    val live: StateFlow<List<Candidate>> = _live.asStateFlow()

    private val _capturing = MutableStateFlow(false)

    /** Whether a photo is being taken, so the button can wait for it. */
    val capturing: StateFlow<Boolean> = _capturing.asStateFlow()

    private val _captured = MutableStateFlow<Bitmap?>(null)

    /** The photo being looked at, shown while the plants in it are found. */
    val captured: StateFlow<Bitmap?> = _captured.asStateFlow()

    private val _results = MutableStateFlow<PhotoReview?>(null)

    /** The photo being reviewed and the plants found in it, kept up to date as each one is decided. */
    val results: StateFlow<PhotoReview?> = _results.asStateFlow()

    /** What the review cards do. */
    val actions = DetectionActions(garden, settings, viewModelScope, wishes, analysis)

    private var watching: Job? = null

    /** Species growing in the garden, read once per camera session; the live suggestions only need to be roughly right. */
    private var gardenSpecies: Set<String>? = null

    /** What the user's earlier decisions say about the suggestions, read once per camera session like the garden's species. */
    private var history: IdHistory? = null

    init {
        saved.get<String>(PHOTO_ID)?.let(::watch)
    }

    /** Photographs a different kind of thing from now on, and for plants, one plant or a bed of several. */
    fun choose(group: LifeGroup, scope: PhotoScope = PhotoScope.ONE) {
        if (_group.value == group && _scope.value == scope) return
        if (_group.value != group) _live.value = emptyList()
        _group.value = group
        _scope.value = scope
        saved[GROUP] = group.name
        saved[SCOPE] = scope.name
    }

    /** Looks at one camera frame and updates the live suggestions. */
    suspend fun glance(frame: Bitmap) {
        if (_stage.value != IdentifyStage.Camera) return
        val group = _group.value
        val model = AiyModel.of(group) ?: return
        val result = try {
            identifier().glance(frame, group)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "The live suggestions failed", e)
            return
        }
        // The choice may have changed while the frame was looked at.
        if (group != _group.value) return
        _live.value = if (result.backgroundBp >= LIVE_MAX_BACKGROUND_BP) {
            emptyList()
        } else {
            val plant = group == LifeGroup.PLANT
            val growing = if (!plant) {
                emptySet()
            } else {
                gardenSpecies ?: garden.plantList.first()
                    .filter { it.status == PlantStatus.GROWING }.mapNotNull { it.speciesName }.toSet()
                    .also { gardenSpecies = it }
            }
            val corrected = history ?: garden.idHistory().also { history = it }
            val kb = knowledge.knowledge.value
            val fauna = knowledge.fauna.value
            val nameOf: (String) -> String? = if (plant) { label -> kb?.species(label)?.name } else { label -> fauna?.animal(label)?.name }
            corrected.apply(CandidateFusion.fuse(mapOf(model.source to result.labels), nameOf, growing, nearby = nearby), nameOf)
                .filter { it.scoreBp >= LIVE_MIN_SCORE_BP }
                .take(LIVE_COUNT)
        }
    }

    /** Where photos taken here come from. */
    private fun origin(): PhotoOrigin = if (field) PhotoOrigin.FIELD else PhotoOrigin.CAMERA

    /** Finds the plants in a photo the user chose from the phone or a cloud album, read once and not kept linked. */
    fun choosePhoto(uri: Uri) {
        if (_stage.value != IdentifyStage.Camera || _capturing.value) return
        _capturing.value = true
        viewModelScope.launch {
            val read = try {
                readPhoto(uri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "The chosen photo could not be read", e)
                null
            } finally {
                _capturing.value = false
            }
            if (read == null) {
                _stage.value = IdentifyStage.Failed
                return@launch
            }
            analyse(read.first, if (field) PhotoOrigin.FIELD else PhotoOrigin.IMPORT, read.second)
        }
    }

    /** Takes a photo with [take] and finds the plants in it. */
    fun capture(take: suspend () -> Bitmap) {
        if (_stage.value != IdentifyStage.Camera || _capturing.value) return
        _capturing.value = true
        viewModelScope.launch {
            val photo = try {
                take()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "The photo could not be taken", e)
                _stage.value = IdentifyStage.Failed
                return@launch
            } finally {
                _capturing.value = false
            }
            analyse(photo)
        }
    }

    /** Finds the plants in [photo], keeps it if it shows any, and shows them for review. */
    suspend fun analyse(photo: Bitmap, from: PhotoOrigin = origin(), takenAt: Instant? = null) {
        _captured.value = photo
        _stage.value = IdentifyStage.Analysing
        _stage.value = try {
            val result = if (takenAt != null) {
                analysis.analyse(photo, from, takenAt, group = _group.value, scope = _scope.value)
            } else {
                analysis.analyse(photo, from, group = _group.value, scope = _scope.value)
            }
            val stored = result.photo
            if (stored == null || result.detections.isEmpty()) {
                IdentifyStage.NoneFound
            } else {
                saved[PHOTO_ID] = stored.id
                // The plants found are shown at once, in the database's order, and the database keeps them up to date.
                val detections = result.detections.sortedWith(compareBy({ it.box.left }, { it.box.top }))
                _results.value = PhotoReview(stored, detections.first().takenAt, detections)
                watch(stored.id)
                IdentifyStage.Results(stored.id)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "The photo could not be identified", e)
            IdentifyStage.Failed
        } finally {
            _captured.value = null
        }
    }

    /**
     * Goes back to the camera for another photo. Plants not yet reviewed wait in the Review queue; a photo taken away
     * from home with nothing saved from it is let go.
     */
    fun takeAnother() {
        letGo()
        saved.remove<String>(PHOTO_ID)
        watching?.cancel()
        watching = null
        _results.value = null
        _live.value = emptyList()
        gardenSpecies = null
        history = null
        _stage.value = IdentifyStage.Camera
    }

    override fun onCleared() {
        letGo()
    }

    private fun letGo() {
        if (field) saved.get<String>(PHOTO_ID)?.let(discardFieldPhoto)
    }

    /** Follows a photo's plants in the database, so each card shows what was decided for it. */
    private fun watch(photoId: String) {
        watching?.cancel()
        watching = viewModelScope.launch {
            garden.photosForReview(listOf(photoId)).collect { reviews -> reviews.firstOrNull()?.let { _results.value = it } }
        }
    }

    private companion object {
        const val TAG = "Identify"
        const val PHOTO_ID = "photoId"
        const val GROUP = "group"
        const val SCOPE = "scope"

        /** Live suggestions are shown only when the model is at least this sure, so the chips do not flicker with noise. */
        const val LIVE_MIN_SCORE_BP = 1_500

        /** When the model thinks the frame shows no plant at least this strongly, no suggestions are shown. */
        const val LIVE_MAX_BACKGROUND_BP = 5_000
        const val LIVE_COUNT = 3
    }
}
