package io.github.wkoller.pollenplot.ui.identify

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.wkoller.pollenplot.data.DetectionRecord
import io.github.wkoller.pollenplot.data.GardenRepository
import io.github.wkoller.pollenplot.data.PhotoAnalysis
import io.github.wkoller.pollenplot.data.kb.KnowledgeRepository
import io.github.wkoller.pollenplot.domain.garden.PlantStatus
import io.github.wkoller.pollenplot.domain.id.Candidate
import io.github.wkoller.pollenplot.domain.id.CandidateFusion
import io.github.wkoller.pollenplot.domain.id.IdSource
import io.github.wkoller.pollenplot.domain.id.PhotoOrigin
import io.github.wkoller.pollenplot.ml.PlantIdentifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** What the Identify screen is showing. */
sealed interface IdentifyStage {
    data object Camera : IdentifyStage

    data object Analysing : IdentifyStage

    data class Results(val photoId: String) : IdentifyStage

    data object NoneFound : IdentifyStage

    data object Failed : IdentifyStage
}

/** A photo with the plants found in it, for the results. */
data class ResultPhoto(val path: String, val width: Int, val height: Int, val detections: List<DetectionRecord>)

/**
 * The Identify screen's state: live suggestions while the camera is pointed at a plant, then the photo being looked at,
 * then the plants found in it. The results survive the phone being turned, and the app being closed in the background,
 * because only the photo's ID is kept and everything else is read back from the database.
 */
class IdentifyModel(
    private val analysis: PhotoAnalysis,
    private val identifier: () -> PlantIdentifier,
    private val garden: GardenRepository,
    private val knowledge: KnowledgeRepository,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val _stage = MutableStateFlow(saved.get<String>(PHOTO_ID)?.let { IdentifyStage.Results(it) } ?: IdentifyStage.Camera)
    val stage: StateFlow<IdentifyStage> = _stage.asStateFlow()

    private val _live = MutableStateFlow<List<Candidate>>(emptyList())

    /** The best suggestions for what the camera is pointed at, or none while it is not pointed at a plant. */
    val live: StateFlow<List<Candidate>> = _live.asStateFlow()

    private val _capturing = MutableStateFlow(false)

    /** Whether a photo is being taken, so the button can wait for it. */
    val capturing: StateFlow<Boolean> = _capturing.asStateFlow()

    private val _captured = MutableStateFlow<Bitmap?>(null)

    /** The photo being looked at, shown while the plants in it are found. */
    val captured: StateFlow<Bitmap?> = _captured.asStateFlow()

    private val _results = MutableStateFlow<ResultPhoto?>(null)

    /** The photo being reviewed and the plants found in it, kept up to date as each one is decided. */
    val results: StateFlow<ResultPhoto?> = _results.asStateFlow()

    private var watching: Job? = null

    /** Species growing in the garden, read once per camera session; the live suggestions only need to be roughly right. */
    private var gardenSpecies: Set<String>? = null

    init {
        saved.get<String>(PHOTO_ID)?.let(::watch)
    }

    /** Looks at one camera frame and updates the live suggestions. */
    suspend fun glance(frame: Bitmap) {
        if (_stage.value != IdentifyStage.Camera) return
        val result = try {
            identifier().glance(frame)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "The live suggestions failed", e)
            return
        }
        _live.value = if (result.backgroundBp >= LIVE_MAX_BACKGROUND_BP) {
            emptyList()
        } else {
            val growing = gardenSpecies ?: garden.plantList.first()
                .filter { it.status == PlantStatus.GROWING }.mapNotNull { it.speciesName }.toSet()
                .also { gardenSpecies = it }
            CandidateFusion.fuse(mapOf(IdSource.AIY_PLANTS to result.labels), knowledge.knowledge.value, growing)
                .filter { it.scoreBp >= LIVE_MIN_SCORE_BP }
                .take(LIVE_COUNT)
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
    suspend fun analyse(photo: Bitmap) {
        _captured.value = photo
        _stage.value = IdentifyStage.Analysing
        _stage.value = try {
            val result = analysis.analyse(photo, PhotoOrigin.CAMERA)
            val stored = result.photo
            if (stored == null || result.detections.isEmpty()) {
                IdentifyStage.NoneFound
            } else {
                saved[PHOTO_ID] = stored.id
                // The plants found are shown at once, in the database's order, and the database keeps them up to date.
                val detections = result.detections.sortedWith(compareBy({ it.box.left }, { it.box.top }))
                _results.value = ResultPhoto(stored.path, stored.width, stored.height, detections)
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

    /** Goes back to the camera for another photo. Plants not yet reviewed wait in the Review queue. */
    fun takeAnother() {
        saved.remove<String>(PHOTO_ID)
        watching?.cancel()
        watching = null
        _results.value = null
        _live.value = emptyList()
        gardenSpecies = null
        _stage.value = IdentifyStage.Camera
    }

    /** Follows a photo's plants in the database, so each card shows what was decided for it. */
    private fun watch(photoId: String) {
        watching?.cancel()
        watching = viewModelScope.launch {
            garden.detectionsInPhoto(photoId).collect { detections ->
                garden.photo(photoId)?.let { _results.value = ResultPhoto(it.path, it.width, it.height, detections) }
            }
        }
    }

    fun add(detection: DetectionRecord, speciesName: String?, customName: String?, zoneId: String?, flowering: Boolean) {
        viewModelScope.launch { garden.confirmDetection(detection.id, speciesName, customName, zoneId, flowering = flowering) }
    }

    fun attach(detection: DetectionRecord, plantId: String, flowering: Boolean) {
        viewModelScope.launch { garden.attachDetection(detection.id, plantId, flowering) }
    }

    fun reject(detection: DetectionRecord) {
        viewModelScope.launch { garden.rejectDetection(detection.id) }
    }

    fun undoReject(detection: DetectionRecord) {
        viewModelScope.launch { garden.reopenDetection(detection.id) }
    }

    private companion object {
        const val TAG = "Identify"
        const val PHOTO_ID = "photoId"

        /** Live suggestions are shown only when the model is at least this sure, so the chips do not flicker with noise. */
        const val LIVE_MIN_SCORE_BP = 1_500

        /** When the model thinks the frame shows no plant at least this strongly, no suggestions are shown. */
        const val LIVE_MAX_BACKGROUND_BP = 5_000
        const val LIVE_COUNT = 3
    }
}
