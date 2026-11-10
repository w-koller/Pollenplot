package io.github.wkoller.pollenplot.ml

import android.graphics.Bitmap
import io.github.wkoller.pollenplot.domain.id.AccurateRegions
import io.github.wkoller.pollenplot.domain.id.BoxBp
import io.github.wkoller.pollenplot.domain.id.FlowerCall
import io.github.wkoller.pollenplot.domain.id.IdSource
import io.github.wkoller.pollenplot.domain.id.LabelScore
import io.github.wkoller.pollenplot.domain.id.PhotoScope
import io.github.wkoller.pollenplot.domain.id.RegionPlanner
import io.github.wkoller.pollenplot.domain.id.ThumbnailMath
import io.github.wkoller.pollenplot.domain.id.TileResult
import io.github.wkoller.pollenplot.domain.wildlife.LifeGroup
import io.github.wkoller.pollenplot.ml.bio.BioCapEncoder
import io.github.wkoller.pollenplot.ml.bio.FlowerVectors
import io.github.wkoller.pollenplot.ml.bio.SpeciesIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What BioCAP saw in one part of a photo: its labels, and for a plant whether it is in flower, when it is sure. */
class AccurateLook(val labels: List<LabelScore>, val flowering: Boolean?)

/** BioCAP and the species it can name, open and ready, with its flower vectors when the pack has them. */
class AccurateModels(
    private val encoder: BioCapEncoder,
    val index: SpeciesIndex,
    private val logitScale: Float,
    private val flowers: FlowerVectors? = null,
) : AutoCloseable {
    /**
     * BioCAP's labels for the square around [box] in [photo]: the closest species of [group] as shares, best first,
     * with the index's common names. A pack whose index has no groups names plants only.
     */
    fun labels(photo: Bitmap, box: BoxBp, group: LifeGroup = LifeGroup.PLANT): List<LabelScore> = look(photo, box, group).labels

    /** As [labels], with BioCAP's answer to whether a plant is in flower from the same look at the photo. */
    fun look(photo: Bitmap, box: BoxBp, group: LifeGroup = LifeGroup.PLANT): AccurateLook {
        if (!index.hasGroups && group != LifeGroup.PLANT) return AccurateLook(emptyList(), null)
        val vector = encoder.encode(Bitmaps.squarePixels(photo, box, BioCapEncoder.SIZE))
        val flowering = flowers?.takeIf { group == LifeGroup.PLANT }?.let { FlowerCall.model(it.flowerBp(vector, logitScale), it.yesBp, it.noBp) }
        return AccurateLook(labelsNear(vector, group), flowering)
    }

    /**
     * BioCAP's look at a photo of one plant: three looks as one. It looks at the square around the middle box, at the
     * whole middle square and at the middle 40%, where one flower or a few leaves fill the frame, and takes the mean
     * of the three vectors, whose similarity to a species is the mean of the three looks'. On 360 Australian plant
     * photos of known species that put the species among the first five in 22 photos where one look had not, and
     * lost it in 8 (`FusionEvalTest`); for animals it gained and lost as many, so an animal gets one look.
     * eval_fusion.py records the same three looks, so a change here needs the same change there. Whether the plant
     * is in flower is asked of the first look alone, which the pack's flower thresholds were chosen with.
     */
    fun lookAtOne(photo: Bitmap): AccurateLook {
        val group = LifeGroup.PLANT
        val squares = listOf(
            ThumbnailMath.squareAround(FastPlantIdentifier.CENTRE, photo.width, photo.height),
            ThumbnailMath.squareAround(BoxBp.centreSquare(photo.width, photo.height, BoxBp.FULL), photo.width, photo.height, paddingBp = 0),
            ThumbnailMath.squareAround(BoxBp.centreSquare(photo.width, photo.height, TIGHT_BP), photo.width, photo.height, paddingBp = 0),
        )
        // On a photo that is not square the first two are the same square, which is encoded once and counts twice.
        val vectors = squares.distinct().associateWith { square ->
            val pixels = Bitmaps.smoothScale(Bitmap.createBitmap(photo, square.left, square.top, square.size, square.size), BioCapEncoder.SIZE, BioCapEncoder.SIZE)
            encoder.encode(IntArray(BioCapEncoder.SIZE * BioCapEncoder.SIZE).also { pixels.getPixels(it, 0, BioCapEncoder.SIZE, 0, 0, BioCapEncoder.SIZE, BioCapEncoder.SIZE) })
        }
        val mean = FloatArray(index.dimensions)
        for (square in squares) vectors.getValue(square).forEachIndexed { i, value -> mean[i] += value / squares.size }
        val flowering = flowers?.let { FlowerCall.model(it.flowerBp(vectors.getValue(squares.first()), logitScale), it.yesBp, it.noBp) }
        return AccurateLook(labelsNear(mean, group), flowering)
    }

    /** The closest species of [group] to [vector] as shares, best first, with the index's common names. */
    private fun labelsNear(vector: FloatArray, group: LifeGroup): List<LabelScore> {
        val wanted = group.name.lowercase()
        val near = index.nearest(vector, count = CONSIDERED) { species -> species.group == null || species.group == wanted }
        val common = near.associate { it.species.name to it.species.common }
        return AccurateRegions.shares(near.map { it.species.name to it.similarity }, logitScale).take(KEPT).map { it.copy(common = common[it.label]) }
    }

    /**
     * What [box] in [photo] most likely shows, from the groups of BioCAP's closest species of all; a plant when no
     * group is clear, or when the pack's index has no groups.
     */
    fun groupOf(photo: Bitmap, box: BoxBp): LifeGroup {
        if (!index.hasGroups) return LifeGroup.PLANT
        val vector = encoder.encode(Bitmaps.squarePixels(photo, box, BioCapEncoder.SIZE))
        val near = index.nearest(vector, count = CONSIDERED)
        return AccurateRegions.groupOf(near.mapNotNull { match -> match.species.group?.let { it to match.similarity } }, logitScale)
            ?.let(LifeGroup::of) ?: LifeGroup.PLANT
    }

    override fun close() = encoder.close()

    private companion object {
        /** The closest species whose shares are worked out; the rest add next to nothing at BioCAP's scale. */
        const val CONSIDERED = 20
        const val KEPT = 5

        /** The share of a photo's shorter side that the closest of the three looks at one plant takes. */
        const val TIGHT_BP = 4_000
    }
}

/**
 * The quick model first, then BioCAP. For plants, BioCAP names each plant the quick model found and looks again at the
 * parts of the photo the quick model was unsure about, which is where Australian natives usually are. For an animal,
 * it names the animal in the middle of the frame. Without the accurate pack on the phone, this is the quick model alone.
 */
class AccuratePlantIdentifier(private val fast: FastPlantIdentifier, private val models: suspend () -> AccurateModels?) : Identifier {
    override suspend fun identify(photo: Bitmap, group: LifeGroup, scope: PhotoScope): Identification {
        val accurate = models() ?: return fast.identify(photo, group, scope)
        if (group != LifeGroup.PLANT) return identifyAnimal(photo, group, accurate)
        if (scope == PhotoScope.ONE) return identifyOne(photo, accurate)
        val quick = fast.scan(photo)
        return withContext(Dispatchers.Default) {
            val named = quick.regions.map { region ->
                val look = accurate.look(photo, region.box)
                FoundRegion(region.box, region.labels + (IdSource.BIOCAP to look.labels), region.thumbnail, region.flowering, modelFlowering = look.flowering)
            }
            val looks = AccurateRegions.tilesToCheck(quick.tiles, named.map { it.box }).associateWith { box -> accurate.look(photo, box) }
            val existing = named.mapNotNull { region -> region.labels[IdSource.BIOCAP]?.firstOrNull()?.let { region.box to it.label } }
            val added = AccurateRegions.select(looks.mapValues { it.value.labels }, existing, max = MAX_REGIONS - named.size).map { (box, labels) ->
                FastPlantIdentifier.region(photo, box, mapOf(IdSource.BIOCAP to labels), modelFlowering = looks.getValue(box).flowering)
            }
            Identification(found = quick.showsPlant || added.isNotEmpty(), regions = named + added)
        }
    }

    /** The one plant in the middle of the frame, named by the quick model, if it is sure of a plant, and by BioCAP. */
    private suspend fun identifyOne(photo: Bitmap, accurate: AccurateModels): Identification {
        val quick = fast.one(photo)
        val look = withContext(Dispatchers.Default) { accurate.lookAtOne(photo) }
        val biocap = look.labels
        val labels = quick?.labels.orEmpty() + if (biocap.isEmpty()) emptyMap() else mapOf(IdSource.BIOCAP to biocap)
        // BioCAP names a species for any photo, so on its own it counts only when it is as sure as for a new region.
        if (quick == null && (biocap.firstOrNull()?.scoreBp ?: 0) < AccurateRegions.MIN_SHARE_BP) return Identification(false, emptyList())
        val region = quick?.let { FoundRegion(it.box, labels, it.thumbnail, it.flowering, modelFlowering = look.flowering) }
            ?: FastPlantIdentifier.region(photo, FastPlantIdentifier.CENTRE, labels, modelFlowering = look.flowering)
        return Identification(true, listOf(region))
    }

    /** The animal in the middle of the frame, named by its quick model, if it has one, and by BioCAP. */
    private suspend fun identifyAnimal(photo: Bitmap, group: LifeGroup, accurate: AccurateModels): Identification {
        val quick = fast.identify(photo, group).regions.firstOrNull()
        // One look: three looks as one named no more animals than this one did, so an animal is not looked at thrice.
        val biocap = withContext(Dispatchers.Default) { accurate.labels(photo, FastPlantIdentifier.CENTRE, group) }
        val labels = quick?.labels.orEmpty() + if (biocap.isEmpty()) emptyMap() else mapOf(IdSource.BIOCAP to biocap)
        if (labels.isEmpty()) return Identification(false, emptyList())
        val region = quick?.let { FoundRegion(it.box, labels, it.thumbnail, flowering = false, group = group) }
            ?: FastPlantIdentifier.region(photo, FastPlantIdentifier.CENTRE, labels, group)
        return Identification(true, listOf(region))
    }

    override suspend fun glance(frame: Bitmap, group: LifeGroup): TileResult = fast.glance(frame, group)

    override suspend fun groupOf(photo: Bitmap): LifeGroup {
        val accurate = models() ?: return LifeGroup.PLANT
        return withContext(Dispatchers.Default) { accurate.groupOf(photo, FastPlantIdentifier.CENTRE) }
    }

    override fun close() = fast.close()

    private companion object {
        const val MAX_REGIONS = RegionPlanner.MAX_REGIONS
    }
}
