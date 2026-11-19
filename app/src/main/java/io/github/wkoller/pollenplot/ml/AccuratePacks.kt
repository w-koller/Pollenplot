package io.github.wkoller.pollenplot.ml

import android.app.ActivityManager
import android.content.Context
import io.github.wkoller.pollenplot.data.NamedPlant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * The two accurate packs the user may download: BioCAP's, for every phone, and BioCLIP 2's, for high-end phones.
 * BioCLIP 2 names more species, so it identifies whenever it is on the phone; otherwise BioCAP does. Only the pack in
 * use keeps its encoder open, because BioCLIP 2's takes about half a gigabyte of memory.
 */
class AccuratePacks(context: Context, scope: CoroutineScope) {
    val biocap = AccuratePack(context, scope, PackSpec.BIOCAP)
    val bioclip2 = AccuratePack(context, scope, PackSpec.BIOCLIP2)

    /** The pack that identifies: BioCLIP 2's while it is ready, else BioCAP's. */
    val active: AccuratePack get() = if (bioclip2.ready) bioclip2 else biocap

    /** The state of the pack that identifies, or of BioCAP's while neither is ready. */
    val state: StateFlow<PackState> = combine(biocap.state, bioclip2.state) { biocap, bioclip2 ->
        if (bioclip2 == PackState.Ready) bioclip2 else biocap
    }.stateIn(scope, SharingStarted.Eagerly, biocap.state.value)

    val ready: Boolean get() = biocap.ready || bioclip2.ready

    /** The encoder of the pack in use, after closing the other's. */
    suspend fun open(): AccurateModels? {
        val using = active
        (if (using === bioclip2) biocap else bioclip2).release()
        return using.open()
    }

    suspend fun plantNames(): List<NamedPlant> = active.plantNames()

    /** The phone's memory, as BioCLIP 2's recommendation counts it. */
    val memoryBytes: Long = (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).let { manager ->
        ActivityManager.MemoryInfo().also { manager.getMemoryInfo(it) }.totalMem
    }

    /** Whether the phone has the memory recommended for BioCLIP 2: 8 GB, which phones report as a little under. */
    val meetsBioclip2Memory: Boolean get() = memoryBytes >= BIOCLIP2_MEMORY_BYTES

    companion object {
        const val BIOCLIP2_MEMORY_BYTES = 7_500L * 1024 * 1024
    }
}
