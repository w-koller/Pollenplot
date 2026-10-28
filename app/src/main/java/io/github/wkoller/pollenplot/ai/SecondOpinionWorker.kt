package io.github.wkoller.pollenplot.ai

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.wkoller.pollenplot.PollenplotApp
import io.github.wkoller.pollenplot.domain.garden.PlantStatus
import io.github.wkoller.pollenplot.domain.id.Candidate
import io.github.wkoller.pollenplot.domain.id.CandidateFusion
import io.github.wkoller.pollenplot.domain.id.IdSource
import io.github.wkoller.pollenplot.domain.plants.KnowledgeBase
import io.github.wkoller.pollenplot.domain.wildlife.FaunaBase
import io.github.wkoller.pollenplot.domain.wildlife.LifeGroup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Asks Gemma 4 for a second opinion on the finds waiting for review whose suggestions are unsure, while the phone is
 * charging and not in use. Its choice is one more vote among the models' suggestions. Each find is asked about once;
 * a run stops after eight minutes and queues the rest.
 */
class SecondOpinionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as PollenplotApp).container
        if (!container.assistant.ready || !container.settingsStore.current().secondOpinions) return Result.success()
        val started = container.clock.millis()
        val kb = container.knowledge.await()
        val fauna = container.knowledge.awaitFauna()
        val growing = container.garden.plantList.first().filter { it.status == PlantStatus.GROWING }.mapNotNull { it.speciesName }.toSet()
        for (detection in container.garden.waitingForSecondOpinion(LIMIT)) {
            if (isStopped) return Result.success()
            if (container.clock.millis() - started > RUN_BUDGET_MS) {
                enqueue(applicationContext)
                return Result.success()
            }
            // The accurate pass weighs a plant's suggestions again from the models alone, which would drop Gemma's
            // vote, so a plant BioCAP has not looked at yet waits for it; that pass asks for second opinions after.
            if (detection.group == LifeGroup.PLANT && container.accurate.ready && detection.candidates.none { IdSource.BIOCAP in it.sources }) continue
            if (!CandidateFusion.needsSecondOpinion(detection.candidates)) {
                container.garden.recordSecondOpinion(detection.id, null)
                continue
            }
            val names = detection.candidates.map { name(it, detection.group, kb, fauna) }
            val choice = try {
                container.assistant.secondOpinion(detection.thumbnail, names)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Gemma 4 could not give a second opinion", e)
                return Result.retry()
            }
            val garden = if (detection.group == LifeGroup.PLANT) growing else emptySet()
            container.garden.recordSecondOpinion(detection.id, CandidateFusion.withSecondOpinion(detection.candidates, choice, garden, container::recordsNearby))
        }
        return Result.success()
    }

    /** A suggestion as Gemma 4 is shown it: its common name and scientific name, as far as they are known. */
    private fun name(candidate: Candidate, group: LifeGroup, kb: KnowledgeBase, fauna: FaunaBase): String {
        val scientific = candidate.speciesName ?: candidate.label
        val common = if (group == LifeGroup.PLANT) kb.species(scientific)?.displayName else fauna.animal(scientific)?.displayName
        return listOfNotNull(common ?: candidate.common, scientific).distinct().joinToString(", ")
    }

    companion object {
        private const val TAG = "SecondOpinion"
        private const val NAME = "second-opinion"
        private const val LIMIT = 500
        private val RUN_BUDGET_MS = TimeUnit.MINUTES.toMillis(8)
        private val idleAndCharging = Constraints.Builder().setRequiresCharging(true).setRequiresDeviceIdle(true).build()

        /**
         * Asks for second opinions the next time the phone is charging and idle. Robolectric tests set up WorkManager
         * after the app has started, so until then there is none to queue with.
         */
        fun enqueue(context: Context) {
            val work = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
            work.enqueueUniqueWork(NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, OneTimeWorkRequestBuilder<SecondOpinionWorker>().setConstraints(idleAndCharging).build())
        }
    }
}
