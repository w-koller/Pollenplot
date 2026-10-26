package io.github.wkoller.pollenplot.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import android.provider.MediaStore
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.github.wkoller.pollenplot.PollenplotApp
import io.github.wkoller.pollenplot.domain.garden.PlantStatus
import io.github.wkoller.pollenplot.domain.id.CandidateFusion
import io.github.wkoller.pollenplot.domain.id.IdSource
import io.github.wkoller.pollenplot.domain.id.LabelScore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.withContext

/** Looks for plants in the photos the user added, one at a time, and says what it found. */
class AddPhotosWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as PollenplotApp).container
        val uris = inputData.getStringArray(KEY_URIS).orEmpty().map(Uri::parse)
        var tally = ScanTally()
        uris.forEachIndexed { done, uri ->
            setProgress(workDataOf(KEY_DONE to done, KEY_TOTAL to uris.size))
            tally = tally.withPhoto(container.scanner.scanAddedPhoto(uri))
        }
        container.notifications.showAddedResult(tally)
        return Result.success()
    }

    companion object {
        const val NAME = "add-photos"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        private const val KEY_URIS = "uris"

        fun input(uris: List<Uri>): Data = workDataOf(KEY_URIS to uris.map { it.toString() }.toTypedArray())

        /** Queues [uris] behind any photos still being looked at. */
        fun enqueue(context: Context, uris: List<Uri>) {
            val request = OneTimeWorkRequestBuilder<AddPhotosWorker>()
                .setInputData(input(uris))
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}

/**
 * Looks for plants in the gallery's new photos. A run stops after about eight minutes, well inside Android's limit for
 * background work, and the next run carries on where it stopped. It tells the user when it found plants.
 */
class LibraryScanWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as PollenplotApp).container
        val settings = container.settingsStore.current()
        if (!settings.scanLibrary || container.gallery.access() == GalleryAccess.NONE) return Result.success()
        val turnedOn = Instant.ofEpochMilli(settings.scanSince ?: container.clock.millis())
        val since = turnedOn.minus(Duration.ofDays(settings.scanBackDays.toLong()))
        val photos = container.gallery.photosSince(since, settings.scanAlbums)
        val unscanned = container.garden.unscanned(photos.map { it.id }).toSet()
        val started = SystemClock.elapsedRealtime()
        var tally = ScanTally()
        var more = false
        for (photo in photos.filter { it.id in unscanned }) {
            if (isStopped) return Result.success()
            if (SystemClock.elapsedRealtime() - started > RUN_BUDGET_MS) {
                more = true
                break
            }
            tally = tally.withPhoto(container.scanner.scanLibraryPhoto(photo))
        }
        container.notifications.showScanResult(tally)
        if (more) ScanScheduler.scanNow(applicationContext)
        // The accurate pass follows while the phone charges, for new photos with plants and those without.
        if (container.accurate.ready && tally.photos > 0) AccurateScanWorker.enqueue(applicationContext)
        return Result.success()
    }

    private companion object {
        val RUN_BUDGET_MS = TimeUnit.MINUTES.toMillis(8)
    }
}

/** Waits for new photos in the gallery, starts a scan, and waits again. */
class LibraryWatchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        ScanScheduler.scanNow(applicationContext)
        // A content trigger fires once, so the watch queues itself again behind this run.
        ScanScheduler.watch(applicationContext, ExistingWorkPolicy.APPEND_OR_REPLACE)
        return Result.success()
    }
}

/** Starts and stops the gallery scans to match the user's settings. */
object ScanScheduler {
    private const val SCAN = "library-scan"
    private const val WATCH = "library-watch"
    private const val SWEEP = "library-sweep"

    private val batteryNotLow = Constraints.Builder().setRequiresBatteryNotLow(true).build()

    /** Scans now and watches for new photos when scanning is on; otherwise stops everything. */
    fun update(context: Context, scanning: Boolean) {
        val work = WorkManager.getInstance(context)
        if (!scanning) {
            listOf(SCAN, WATCH, SWEEP).forEach(work::cancelUniqueWork)
            return
        }
        scanNow(context)
        watch(context, ExistingWorkPolicy.REPLACE)
        // A daily sweep catches photos the watch missed, such as those copied while the phone was off.
        val sweep = PeriodicWorkRequestBuilder<LibraryScanWorker>(1, TimeUnit.DAYS).setConstraints(batteryNotLow).build()
        work.enqueueUniquePeriodicWork(SWEEP, ExistingPeriodicWorkPolicy.KEEP, sweep)
    }

    /** Whether a scan is running now. */
    fun scanning(context: Context): Flow<Boolean> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(SCAN).map { infos -> infos.any { it.state == WorkInfo.State.RUNNING } }

    fun scanNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<LibraryScanWorker>().setConstraints(batteryNotLow).build()
        WorkManager.getInstance(context).enqueueUniqueWork(SCAN, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    fun watch(context: Context, policy: ExistingWorkPolicy) {
        val constraints = Constraints.Builder()
            .addContentUriTrigger(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true)
            // Photos often arrive in bursts, so the scan waits for the gallery to settle.
            .setTriggerContentUpdateDelay(Duration.ofSeconds(30))
            .setTriggerContentMaxDelay(Duration.ofMinutes(5))
            .build()
        val request = OneTimeWorkRequestBuilder<LibraryWatchWorker>().setConstraints(constraints).build()
        WorkManager.getInstance(context).enqueueUniqueWork(WATCH, policy, request)
    }
}

/**
 * The accurate pass, while the phone charges: BioCAP's suggestions for plants waiting for review, and a second look at
 * gallery photos the quick model saw no plant in, where Australian natives usually end up. Like the gallery scan, a
 * run stops after about eight minutes and the next one carries on.
 */
class AccurateScanWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as PollenplotApp).container
        val models = container.accurate.open() ?: return Result.success()
        val started = SystemClock.elapsedRealtime()
        fun outOfTime() = SystemClock.elapsedRealtime() - started > RUN_BUDGET_MS
        var more = false

        // Plants waiting for review get BioCAP's suggestions alongside the quick model's.
        val kb = container.knowledge.await()
        val growing = container.garden.plantList.first().filter { it.status == PlantStatus.GROWING }.mapNotNull { it.speciesName }.toSet()
        val photos = mutableMapOf<String, Bitmap?>()
        for (detection in container.garden.pendingWithout(IdSource.BIOCAP)) {
            if (isStopped) return Result.success()
            if (outOfTime()) {
                more = true
                break
            }
            val photo = photos.getOrPut(detection.photoId) {
                container.garden.photo(detection.photoId)?.let { BitmapFactory.decodeFile(container.photos.file(it.path).path) }
            } ?: continue
            val quick = detection.candidates.filter { IdSource.AIY_PLANTS in it.sources }
                .map { LabelScore(it.label, CandidateFusion.withoutBoost(it, growing)) }
            val accurate = withContext(Dispatchers.Default) { models.labels(photo, detection.box) }
            container.garden.replaceCandidates(
                detection.id,
                CandidateFusion.fuse(mapOf(IdSource.AIY_PLANTS to quick, IdSource.BIOCAP to accurate), kb, growing),
            )
        }

        // Gallery photos the quick model saw no plant in get a second look.
        var tally = ScanTally()
        val settings = container.settingsStore.current()
        if (!more && settings.scanLibrary && container.gallery.access() != GalleryAccess.NONE) {
            for ((mediaId, takenAt) in container.garden.mediaToRecheck(BATCH)) {
                if (isStopped) return Result.success()
                if (outOfTime()) {
                    more = true
                    break
                }
                tally = tally.withPhoto(container.scanner.recheckLibraryPhoto(mediaId, takenAt))
            }
        }
        container.notifications.showScanResult(tally)
        if (more) enqueue(applicationContext)
        return Result.success()
    }

    companion object {
        private const val NAME = "accurate-scan"
        private const val DAILY = "accurate-scan-daily"
        private const val BATCH = 500
        private val RUN_BUDGET_MS = TimeUnit.MINUTES.toMillis(8)
        private val charging = Constraints.Builder().setRequiresCharging(true).setRequiresBatteryNotLow(true).build()

        /** Runs the accurate pass the next time the phone charges, and every day after. */
        fun enqueue(context: Context) {
            val work = WorkManager.getInstance(context)
            work.enqueueUniqueWork(NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, OneTimeWorkRequestBuilder<AccurateScanWorker>().setConstraints(charging).build())
            work.enqueueUniquePeriodicWork(
                DAILY,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<AccurateScanWorker>(1, TimeUnit.DAYS).setConstraints(charging).build(),
            )
        }
    }
}
