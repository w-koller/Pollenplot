package io.github.wkoller.pollenplot.ml

import android.graphics.Bitmap
import io.github.wkoller.pollenplot.domain.id.AccurateRegions
import io.github.wkoller.pollenplot.domain.id.BoxBp
import io.github.wkoller.pollenplot.domain.id.IdSource
import io.github.wkoller.pollenplot.domain.id.LabelScore
import io.github.wkoller.pollenplot.domain.id.TileResult
import io.github.wkoller.pollenplot.ml.bio.BioCapEncoder
import io.github.wkoller.pollenplot.ml.bio.SpeciesIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** BioCAP and the species it can name, open and ready. */
class AccurateModels(private val encoder: BioCapEncoder, val index: SpeciesIndex, private val logitScale: Float) : AutoCloseable {
    /** BioCAP's labels for the square around [box] in [photo]: the closest species as shares, best first. */
    fun labels(photo: Bitmap, box: BoxBp): List<LabelScore> {
        val vector = encoder.encode(Bitmaps.squarePixels(photo, box, BioCapEncoder.SIZE))
        val near = index.nearest(vector, count = CONSIDERED)
        return AccurateRegions.shares(near.map { it.species.name to it.similarity }, logitScale).take(KEPT)
    }

    override fun close() = encoder.close()

    private companion object {
        /** The closest species whose shares are worked out; the rest add next to nothing at BioCAP's scale. */
        const val CONSIDERED = 20
        const val KEPT = 5
    }
}

/**
 * The quick model first, then BioCAP: it names each plant the quick model found, and looks again at the parts of the
 * photo the quick model was unsure about, which is where Australian natives usually are. Without the accurate pack
 * on the phone, this is the quick model alone.
 */
class AccuratePlantIdentifier(private val fast: FastPlantIdentifier, private val models: suspend () -> AccurateModels?) : PlantIdentifier {
    override suspend fun identify(photo: Bitmap): Identification {
        val accurate = models() ?: return fast.identify(photo)
        val quick = fast.scan(photo)
        return withContext(Dispatchers.Default) {
            val named = quick.regions.map { region ->
                FoundRegion(region.box, region.labels + (IdSource.BIOCAP to accurate.labels(photo, region.box)), region.thumbnail, region.flowering)
            }
            val checked = AccurateRegions.tilesToCheck(quick.tiles, named.map { it.box })
                .associateWith { box -> accurate.labels(photo, box) }
            val existing = named.mapNotNull { region -> region.labels[IdSource.BIOCAP]?.firstOrNull()?.let { region.box to it.label } }
            val added = AccurateRegions.select(checked, existing, max = MAX_REGIONS - named.size).map { (box, labels) ->
                FastPlantIdentifier.region(photo, box, mapOf(IdSource.BIOCAP to labels))
            }
            Identification(showsPlant = quick.showsPlant || added.isNotEmpty(), regions = named + added)
        }
    }

    override suspend fun glance(frame: Bitmap): TileResult = fast.glance(frame)

    override fun close() = fast.close()

    private companion object {
        const val MAX_REGIONS = 6
    }
}
