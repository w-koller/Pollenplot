package io.github.wkoller.pollenplot.ml

import android.graphics.Bitmap
import io.github.wkoller.pollenplot.domain.id.AccurateRegions
import io.github.wkoller.pollenplot.domain.id.BoxBp
import io.github.wkoller.pollenplot.domain.id.IdSource
import io.github.wkoller.pollenplot.domain.id.LabelScore
import io.github.wkoller.pollenplot.domain.id.TileResult
import io.github.wkoller.pollenplot.domain.wildlife.LifeGroup
import io.github.wkoller.pollenplot.ml.bio.BioCapEncoder
import io.github.wkoller.pollenplot.ml.bio.SpeciesIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** BioCAP and the species it can name, open and ready. */
class AccurateModels(private val encoder: BioCapEncoder, val index: SpeciesIndex, private val logitScale: Float) : AutoCloseable {
    /**
     * BioCAP's labels for the square around [box] in [photo]: the closest species of [group] as shares, best first,
     * with the index's common names. A pack whose index has no groups names plants only.
     */
    fun labels(photo: Bitmap, box: BoxBp, group: LifeGroup = LifeGroup.PLANT): List<LabelScore> {
        if (!index.hasGroups && group != LifeGroup.PLANT) return emptyList()
        val vector = encoder.encode(Bitmaps.squarePixels(photo, box, BioCapEncoder.SIZE))
        val wanted = group.name.lowercase()
        val near = index.nearest(vector, count = CONSIDERED) { species -> species.group == null || species.group == wanted }
        val common = near.associate { it.species.name to it.species.common }
        return AccurateRegions.shares(near.map { it.species.name to it.similarity }, logitScale).take(KEPT).map { it.copy(common = common[it.label]) }
    }

    override fun close() = encoder.close()

    private companion object {
        /** The closest species whose shares are worked out; the rest add next to nothing at BioCAP's scale. */
        const val CONSIDERED = 20
        const val KEPT = 5
    }
}

/**
 * The quick model first, then BioCAP. For plants, BioCAP names each plant the quick model found and looks again at the
 * parts of the photo the quick model was unsure about, which is where Australian natives usually are. For an animal,
 * it names the animal in the middle of the frame. Without the accurate pack on the phone, this is the quick model alone.
 */
class AccuratePlantIdentifier(private val fast: FastPlantIdentifier, private val models: suspend () -> AccurateModels?) : Identifier {
    override suspend fun identify(photo: Bitmap, group: LifeGroup): Identification {
        val accurate = models() ?: return fast.identify(photo, group)
        if (group != LifeGroup.PLANT) return identifyAnimal(photo, group, accurate)
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
            Identification(found = quick.showsPlant || added.isNotEmpty(), regions = named + added)
        }
    }

    /** The animal in the middle of the frame, named by its quick model, if it has one, and by BioCAP. */
    private suspend fun identifyAnimal(photo: Bitmap, group: LifeGroup, accurate: AccurateModels): Identification {
        val quick = fast.identify(photo, group).regions.firstOrNull()
        val biocap = withContext(Dispatchers.Default) { accurate.labels(photo, FastPlantIdentifier.CENTRE, group) }
        val labels = quick?.labels.orEmpty() + if (biocap.isEmpty()) emptyMap() else mapOf(IdSource.BIOCAP to biocap)
        if (labels.isEmpty()) return Identification(false, emptyList())
        val region = quick?.let { FoundRegion(it.box, labels, it.thumbnail, flowering = false, group = group) }
            ?: FastPlantIdentifier.region(photo, FastPlantIdentifier.CENTRE, labels, group)
        return Identification(true, listOf(region))
    }

    override suspend fun glance(frame: Bitmap, group: LifeGroup): TileResult = fast.glance(frame, group)

    override fun close() = fast.close()

    private companion object {
        const val MAX_REGIONS = 6
    }
}
