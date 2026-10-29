package io.github.wkoller.pollenplot.data

import androidx.room.withTransaction
import io.github.wkoller.pollenplot.data.db.CandidateEntity
import io.github.wkoller.pollenplot.data.db.CareDraftEntity
import io.github.wkoller.pollenplot.data.db.DetectionEntity
import io.github.wkoller.pollenplot.data.db.EmbeddingEntity
import io.github.wkoller.pollenplot.data.db.FloweringEntity
import io.github.wkoller.pollenplot.data.db.LinkEntity
import io.github.wkoller.pollenplot.data.db.PhotoEntity
import io.github.wkoller.pollenplot.data.db.PollenplotDatabase
import io.github.wkoller.pollenplot.data.db.ScannedMediaEntity
import io.github.wkoller.pollenplot.data.db.SearchEntity
import io.github.wkoller.pollenplot.data.db.SearchHit
import io.github.wkoller.pollenplot.data.db.toDomain
import io.github.wkoller.pollenplot.data.db.toEntity
import io.github.wkoller.pollenplot.data.photos.StoredPhoto
import io.github.wkoller.pollenplot.domain.assistant.DraftedCare
import io.github.wkoller.pollenplot.domain.garden.GardenPlant
import io.github.wkoller.pollenplot.domain.garden.PlotLayout
import io.github.wkoller.pollenplot.domain.garden.Zone
import io.github.wkoller.pollenplot.domain.garden.ZoneKind
import io.github.wkoller.pollenplot.domain.garden.ZoneRect
import io.github.wkoller.pollenplot.domain.id.BoxBp
import io.github.wkoller.pollenplot.domain.id.Candidate
import io.github.wkoller.pollenplot.domain.id.DetectionStatus
import io.github.wkoller.pollenplot.domain.id.IdSource
import io.github.wkoller.pollenplot.domain.id.PhotoOrigin
import io.github.wkoller.pollenplot.domain.plants.CareResolver
import io.github.wkoller.pollenplot.domain.plants.EcoRole
import io.github.wkoller.pollenplot.domain.plants.KnowledgeBase
import io.github.wkoller.pollenplot.domain.semantic.GardenGraph
import io.github.wkoller.pollenplot.domain.semantic.JsonLd
import io.github.wkoller.pollenplot.domain.semantic.Link
import io.github.wkoller.pollenplot.domain.semantic.MeaningMatch
import io.github.wkoller.pollenplot.domain.semantic.SearchText
import io.github.wkoller.pollenplot.domain.semantic.Triple
import io.github.wkoller.pollenplot.domain.wildlife.Behaviour
import io.github.wkoller.pollenplot.domain.wildlife.FaunaBase
import io.github.wkoller.pollenplot.domain.wildlife.LifeGroup
import io.github.wkoller.pollenplot.domain.wildlife.Sighting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** SQLite allows a limited number of arguments in one query, so long lists are looked up in parts. */
private const val MAX_QUERY_ARGS = 500

/**
 * Zones, plants and the links between records. Every write also updates the search index, so "Search your garden"
 * always matches what is stored, and every record can be exported as linked data.
 */
class GardenRepository(
    private val db: PollenplotDatabase,
    private val clock: Clock,
    private val knowledge: suspend () -> KnowledgeBase,
    /** The garden's state and weed lists, which some badges in the search index depend on. */
    private val locality: suspend () -> Locality,
    /** The animal knowledge base, for sightings in the search index and the export. */
    private val fauna: suspend () -> FaunaBase = { FaunaBase(emptyList()) },
) {
    private val zones = db.zoneDao()
    private val plants = db.plantDao()
    private val links = db.linkDao()
    private val search = db.searchDao()
    private val photos = db.photoDao()
    private val detections = db.detectionDao()
    private val flowering = db.floweringDao()
    private val scanned = db.scannedMediaDao()
    private val embeddings = db.embeddingDao()
    private val sightings = db.sightingDao()
    private val drafts = db.careDraftDao()
    private val layoutLock = Mutex()

    val zoneList: Flow<List<Zone>> = zones.observeAll().map { list -> list.map { it.toDomain() } }
    val plantList: Flow<List<GardenPlant>> = plants.observeAll().map { list -> list.map { it.toDomain() } }

    private fun now() = clock.millis()

    suspend fun zone(id: String): Zone? = zones.get(id)?.toDomain()

    suspend fun plant(id: String): GardenPlant? = plants.get(id)?.toDomain()

    /** Adds a zone in the first free spot on the canvas and returns it. */
    suspend fun addZone(name: String, kind: ZoneKind): Zone {
        val existing = zones.all().map { it.toDomain().rect }
        val zone = Zone(UUID.randomUUID().toString(), name.trim(), kind, PlotLayout.placeNew(existing))
        val time = now()
        db.withTransaction {
            zones.upsert(zone.toEntity(time, time))
            index(zone)
        }
        return zone
    }

    /** Saves changes to a zone. A new name is also written into the search text of the plants in it. */
    suspend fun updateZone(zone: Zone) {
        val old = zones.get(zone.id) ?: return
        db.withTransaction {
            zones.upsert(zone.toEntity(old.createdAt, now()))
            index(zone)
            if (old.name != zone.name) plants.inZone(zone.id).forEach { index(it.toDomain()) }
        }
    }

    suspend fun moveZone(id: String, rect: ZoneRect) = adjustZone(id) { rect }

    /**
     * Changes a zone's place or size starting from where it is now, so quick taps on the move and resize buttons build
     * on each other instead of each starting from an older position. The result snaps to the grid.
     */
    suspend fun adjustZone(id: String, change: (ZoneRect) -> ZoneRect) = layoutLock.withLock {
        val zone = zone(id) ?: return@withLock
        updateZone(zone.copy(rect = PlotLayout.snapped(change(zone.rect))))
    }

    /** Removes a zone. Its plants stay in the garden without a zone. */
    suspend fun deleteZone(id: String) = db.withTransaction {
        val affected = plants.inZone(id).map { it.toDomain() }
        zones.delete(id)
        links.deleteFor(id)
        search.delete(id)
        affected.forEach { index(it.copy(zoneId = null)) }
    }

    suspend fun addPlant(
        speciesName: String?,
        customName: String?,
        zoneId: String?,
        quantity: Int = 1,
        plantedOn: LocalDate? = null,
    ): GardenPlant {
        val plant = GardenPlant(UUID.randomUUID().toString(), speciesName, customName?.trim()?.ifBlank { null }, zoneId, quantity, plantedOn)
        val time = now()
        db.withTransaction {
            plants.upsert(plant.toEntity(time, time))
            index(plant)
        }
        return plant
    }

    suspend fun updatePlant(plant: GardenPlant) {
        val old = plants.get(plant.id) ?: return
        db.withTransaction {
            plants.upsert(plant.toEntity(old.createdAt, now()))
            index(plant)
        }
    }

    suspend fun deletePlant(id: String) = db.withTransaction {
        plants.delete(id)
        links.deleteFor(id)
        search.delete(id)
    }

    /**
     * Keeps a photo and the plants found in it. Each region becomes a detection that waits for review, with its
     * suggestions in rank order.
     */
    suspend fun recordPhoto(
        photo: StoredPhoto,
        origin: PhotoOrigin,
        takenAt: Instant,
        drafts: List<DetectionDraft>,
        latE7: Int? = null,
        lonE7: Int? = null,
    ): List<DetectionRecord> = db.withTransaction {
        val time = now()
        photos.insert(PhotoEntity(photo.id, photo.path, photo.width, photo.height, origin.name, takenAt.toEpochMilli(), time, latE7, lonE7))
        drafts.map { draft ->
            val id = UUID.randomUUID().toString()
            detections.insert(
                DetectionEntity(
                    id, photo.id, draft.box.left, draft.box.top, draft.box.right, draft.box.bottom, draft.thumbnail, draft.flowering,
                    DetectionStatus.NEEDS_REVIEW.name, null, time, draft.group.name,
                ),
            )
            detections.insertCandidates(
                draft.candidates.mapIndexed { rank, c -> CandidateEntity(id, rank, c.label, c.speciesName, c.scoreBp, c.sources.joinToString(",") { it.name }, c.common) },
            )
            DetectionRecord(id, photo.id, draft.box, draft.thumbnail, draft.flowering, DetectionStatus.NEEDS_REVIEW, null, draft.candidates, takenAt, draft.group)
        }
    }

    suspend fun photo(id: String): StoredPhoto? = photos.get(id)?.let { StoredPhoto(it.id, it.path, it.width, it.height) }

    /** How many plants found in photos wait for review. */
    val pendingReviewCount: Flow<Int> = detections.observePendingCount()

    /** The photos with plants waiting for review, the most recently taken first. */
    val pendingReviewPhotos: Flow<List<String>> = detections.observePendingPhotoIds()

    /** These photos with every plant found in each, in the order given, updated as each plant is reviewed. */
    fun photosForReview(photoIds: List<String>): Flow<List<PhotoReview>> =
        if (photoIds.isEmpty()) {
            flowOf(emptyList())
        } else {
            detections.observeForPhotos(photoIds).map { list ->
                val byPhoto = list.groupBy { it.photoId }
                val kept = photos.getAll(photoIds).associateBy { it.id }
                photoIds.mapNotNull { id ->
                    val photo = kept[id] ?: return@mapNotNull null
                    PhotoReview(
                        StoredPhoto(photo.id, photo.path, photo.width, photo.height),
                        Instant.ofEpochMilli(photo.takenAt),
                        byPhoto[id].orEmpty().map { record(it) },
                    )
                }
            }
        }

    /** The gallery photos among [mediaIds] that no scan has looked at yet. */
    suspend fun unscanned(mediaIds: List<Long>): List<Long> {
        val known = mediaIds.chunked(MAX_QUERY_ARGS).flatMap { scanned.known(it) }.toSet()
        return mediaIds.filterNot { it in known }
    }

    /** Remembers that a scan has looked at a gallery photo, and what it found. */
    suspend fun recordScan(mediaId: Long, takenAt: Instant, outcome: ScanOutcome, photoId: String?, accurate: Boolean = false) =
        scanned.upsert(ScannedMediaEntity(mediaId, takenAt.toEpochMilli(), now(), outcome.name, photoId, accurate))

    /** Gallery photos the quick model saw no plant in, for the accurate pack to look at again, the newest first. */
    suspend fun mediaToRecheck(limit: Int): List<Pair<Long, Instant>> =
        scanned.toRecheck(limit).map { it.mediaId to Instant.ofEpochMilli(it.takenAt) }

    /** Plants waiting for review that [source] has not suggested names for yet. */
    suspend fun pendingWithout(source: IdSource): List<DetectionRecord> = detections.pendingWithout(source.name).map { record(it) }

    /** Gives a plant waiting for review a new list of suggestions, best first. */
    suspend fun replaceCandidates(detectionId: String, candidates: List<Candidate>) = db.withTransaction {
        detections.deleteCandidates(detectionId)
        detections.insertCandidates(
            candidates.mapIndexed { rank, c -> CandidateEntity(detectionId, rank, c.label, c.speciesName, c.scoreBp, c.sources.joinToString(",") { it.name }, c.common) },
        )
        detections.touch(detectionId)
    }

    /** The care Gemma 4 drafted for a plant, and whether the user kept it; null when there is none. */
    fun careDraft(plantId: String): Flow<Pair<DraftedCare, Boolean>?> = drafts.observe(plantId).map { entity ->
        entity?.let { runCatching { DraftedCare.fromJson(it.json) }.getOrNull()?.let { draft -> draft to it.kept } }
    }

    suspend fun saveCareDraft(plantId: String, draft: DraftedCare) = drafts.upsert(CareDraftEntity(plantId, draft.toJson(), now()))

    suspend fun keepCareDraft(plantId: String) = drafts.keep(plantId)

    suspend fun deleteCareDraft(plantId: String) = drafts.delete(plantId)

    /** Finds waiting for review that Gemma 4 has not given a second opinion on yet, the newest first. */
    suspend fun waitingForSecondOpinion(limit: Int): List<DetectionRecord> = detections.waitingForSecondOpinion(limit).map { record(it) }

    /** Records that a find has had its second opinion, with its suggestions weighed again when there was one. */
    suspend fun recordSecondOpinion(detectionId: String, candidates: List<Candidate>?) = db.withTransaction {
        if (candidates != null) replaceCandidates(detectionId, candidates)
        detections.markSecondOpinion(detectionId)
    }

    suspend fun detection(id: String): DetectionRecord? = detections.get(id)?.let { record(it) }

    /** The plants found in one photo, updated as each is reviewed. */
    fun detectionsInPhoto(photoId: String): Flow<List<DetectionRecord>> =
        detections.observeForPhoto(photoId).map { list -> list.map { record(it) } }

    private suspend fun record(entity: DetectionEntity): DetectionRecord {
        val takenAt = photos.get(entity.photoId)?.takenAt?.let(Instant::ofEpochMilli) ?: Instant.ofEpochMilli(entity.createdAt)
        val candidates = detections.candidates(entity.id).map { c ->
            Candidate(c.label, c.speciesName, c.scoreBp, c.sources.split(',').mapNotNull { name -> IdSource.entries.firstOrNull { it.name == name } }.toSet(), c.common)
        }
        return DetectionRecord(
            entity.id, entity.photoId, BoxBp(entity.leftBp, entity.topBp, entity.rightBp, entity.bottomBp), entity.thumbnail, entity.flowering,
            DetectionStatus.entries.firstOrNull { it.name == entity.status } ?: DetectionStatus.NEEDS_REVIEW, entity.plantId, candidates, takenAt,
            LifeGroup.of(entity.lifeGroup) ?: LifeGroup.PLANT, entity.sightingId,
        )
    }

    /** Makes a detection a new plant in the garden, with its cut-out as the plant's picture. */
    suspend fun confirmDetection(
        detectionId: String,
        speciesName: String?,
        customName: String?,
        zoneId: String?,
        quantity: Int = 1,
        flowering: Boolean,
    ): GardenPlant = db.withTransaction {
        val detection = detection(detectionId) ?: error("No detection $detectionId")
        val plant = GardenPlant(UUID.randomUUID().toString(), speciesName, customName?.trim()?.ifBlank { null }, zoneId, quantity, thumbnail = detection.thumbnail)
        val time = now()
        plants.upsert(plant.toEntity(time, time))
        detections.setStatus(detectionId, DetectionStatus.CONFIRMED.name, plant.id, null)
        if (flowering) recordFlowering(plant.id, detection)
        index(plant)
        plant
    }

    /** Adds a detection's photo to a plant already in the garden, instead of adding the plant twice. */
    suspend fun attachDetection(detectionId: String, plantId: String, flowering: Boolean) = db.withTransaction {
        val detection = detection(detectionId) ?: return@withTransaction
        val plant = plants.get(plantId) ?: return@withTransaction
        detections.setStatus(detectionId, DetectionStatus.CONFIRMED.name, plantId, null)
        if (plant.thumbnail == null) plants.upsert(plant.copy(thumbnail = detection.thumbnail, updatedAt = now()))
        if (flowering) recordFlowering(plantId, detection)
    }

    /** Marks a detection as not a plant. */
    suspend fun rejectDetection(detectionId: String) = detections.setStatus(detectionId, DetectionStatus.REJECTED.name, null, null)

    /** Puts a detection marked as not a plant back in review. */
    suspend fun reopenDetection(detectionId: String) = db.withTransaction {
        val entity = detections.get(detectionId) ?: return@withTransaction
        if (entity.status == DetectionStatus.REJECTED.name) detections.setStatus(detectionId, DetectionStatus.NEEDS_REVIEW.name, null, null)
    }

    /** Every sighting, the most recent first. */
    val sightingList: Flow<List<Sighting>> = sightings.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun sighting(id: String): Sighting? = sightings.get(id)?.toDomain()

    /** Records an animal or fungus seen in the garden. */
    suspend fun addSighting(sighting: Sighting) = db.withTransaction {
        val time = now()
        sightings.upsert(sighting.toEntity(time, time))
        index(sighting)
    }

    suspend fun updateSighting(sighting: Sighting) = db.withTransaction {
        val existing = sightings.get(sighting.id) ?: return@withTransaction
        sightings.upsert(sighting.toEntity(existing.createdAt, now()))
        index(sighting)
    }

    suspend fun deleteSighting(id: String) = db.withTransaction {
        sightings.delete(id)
        links.deleteFor(id)
        search.delete(id)
    }

    /** Records an animal seen without a photo, such as a kookaburra heard calling. */
    suspend fun recordSighting(
        speciesName: String?,
        label: String,
        group: LifeGroup,
        zoneId: String?,
        plantId: String?,
        behaviour: Behaviour?,
        count: Int,
        seenAt: Instant,
        notes: String,
    ): Sighting {
        val sighting = Sighting(
            id = UUID.randomUUID().toString(),
            speciesName = speciesName,
            label = label.trim(),
            group = speciesName?.let { fauna().animal(it)?.group } ?: group,
            zoneId = zoneId,
            plantId = plantId,
            behaviour = behaviour,
            count = count.coerceAtLeast(1),
            photoId = null,
            thumbnail = null,
            seenAt = seenAt,
            notes = notes.trim(),
        )
        addSighting(sighting)
        return sighting
    }

    /** Makes a detection a sighting, with the photo, its cut-out and when the photo was taken. */
    suspend fun confirmSighting(
        detectionId: String,
        speciesName: String?,
        label: String,
        group: LifeGroup,
        zoneId: String?,
        plantId: String?,
        behaviour: Behaviour?,
        count: Int = 1,
    ): Sighting = db.withTransaction {
        val detection = detection(detectionId) ?: error("No detection $detectionId")
        val sighting = Sighting(
            id = UUID.randomUUID().toString(),
            speciesName = speciesName,
            label = label,
            // The user may have chosen an animal of another group than the camera was set to.
            group = speciesName?.let { fauna().animal(it)?.group } ?: group,
            zoneId = zoneId,
            plantId = plantId,
            behaviour = behaviour,
            count = count,
            photoId = detection.photoId,
            thumbnail = detection.thumbnail,
            seenAt = detection.takenAt,
            notes = "",
        )
        addSighting(sighting)
        detections.setStatus(detectionId, DetectionStatus.CONFIRMED.name, null, sighting.id)
        sighting
    }

    suspend fun floweringOf(plantId: String): List<LocalDate> = flowering.forPlant(plantId).map { LocalDate.ofEpochDay(it.observedOnEpochDay) }

    /** Every time a plant was seen flowering in a photo, as the plant and the day, the oldest first. */
    val floweringList: Flow<List<Pair<String, LocalDate>>> = flowering.observeAll().map { list -> list.map { it.plantId to LocalDate.ofEpochDay(it.observedOnEpochDay) } }

    private suspend fun recordFlowering(plantId: String, detection: DetectionRecord) {
        val day = detection.takenAt.atZone(clock.zone).toLocalDate()
        flowering.insert(FloweringEntity(UUID.randomUUID().toString(), plantId, day.toEpochDay(), detection.photoId, "PHOTO"))
    }

    suspend fun link(link: Link) = links.upsert(
        LinkEntity(link.subjectId, link.predicate, link.objectId, link.provenance.name, link.confidenceBp, now()),
    )

    /**
     * Finds records whose words start with each word of [query]. The words are reduced to letters and digits, so
     * nothing the user types can break the search syntax.
     */
    suspend fun search(query: String, limit: Int = 50): List<SearchHit> {
        val words = query.lowercase().split(Regex("""[^\p{L}\p{N}]+""")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        return search.search(words.joinToString(" ") { "$it*" }, limit)
    }

    /** Emits whenever the search index changes, so the meaning of changed records can be worked out again. */
    val searchChanges: Flow<Int> = search.observeChanges()

    /** The records whose meaning [model] has not worked out yet, or whose text has changed since, with their text. */
    suspend fun textsToEmbed(model: String): List<Pair<String, String>> {
        val known = embeddings.all(model).associate { it.recordId to it.textHash }
        return search.all().map { it.recordId to meaningText(it) }.filter { (id, text) -> known[id] != text.hashCode() }
    }

    suspend fun saveMeaning(recordId: String, model: String, text: String, vector: FloatArray) =
        embeddings.upsert(EmbeddingEntity(recordId, model, text.hashCode(), vector.toBytes()))

    /** Drops the meaning of records that are gone and of any other model. */
    suspend fun pruneMeanings(model: String) = embeddings.prune(model)

    suspend fun clearMeanings() = embeddings.clear()

    /** How close each record is to [query] in meaning, from 0 for unrelated to 1 for the same. */
    suspend fun meaningScores(model: String, query: FloatArray): Map<String, Float> =
        embeddings.all(model).associate { it.recordId to MeaningMatch.similarity(query, it.vector.toFloats()) }

    /** The search entries for [ids], in the order given. */
    suspend fun searchHits(ids: List<String>): List<SearchHit> {
        if (ids.isEmpty()) return emptyList()
        val byId = search.byIds(ids).associateBy { it.recordId }
        return ids.mapNotNull { byId[it] }
    }

    /** Rebuilds the whole search index, for example after the knowledge base, the garden's state or its weed lists change. */
    suspend fun reindexAll() = db.withTransaction {
        search.clear()
        zones.all().forEach { index(it.toDomain()) }
        plants.all().forEach { index(it.toDomain()) }
        sightings.all().forEach { index(it.toDomain()) }
    }

    /** The eco-roles of a plant in this garden. */
    suspend fun rolesOf(plant: GardenPlant): List<EcoRole> {
        val kb = knowledge()
        val species = plant.speciesName?.let(kb::species) ?: return emptyList()
        return locality().roles(species, CareResolver.resolve(kb, species).care)
    }

    /** Every garden record as JSON-LD, using Darwin Core, schema.org and Pollenplot's own terms. */
    suspend fun exportJsonLd(): String = JsonLd.document(triples()).toString()

    /** Every garden record as linked data: zones, plants, sightings and the links between records. */
    suspend fun triples(): List<Triple> {
        val kb = knowledge()
        val triples = mutableListOf<Triple>()
        zones.all().forEach { triples += GardenGraph.zone(it.toDomain()) }
        plants.all().forEach { entity ->
            val plant = entity.toDomain()
            triples += GardenGraph.plant(plant, plant.speciesName?.let(kb::species), rolesOf(plant))
        }
        val animals = fauna()
        sightings.all().forEach { entity ->
            val sighting = entity.toDomain()
            triples += GardenGraph.sighting(sighting, (sighting.speciesName ?: sighting.label).let(animals::animal))
        }
        links.all().forEach { triples += GardenGraph.link(it.toDomain()) }
        return triples
    }

    private suspend fun index(zone: Zone) {
        val doc = SearchText.zone(zone)
        search.replace(SearchEntity(recordId = doc.id, kind = doc.kind.name, title = doc.title, body = doc.body))
    }

    private suspend fun index(sighting: Sighting) {
        val animal = (sighting.speciesName ?: sighting.label).let(fauna()::animal)
        val zoneName = sighting.zoneId?.let { zones.get(it)?.name }
        val plantTitle = sighting.plantId?.let { plants.get(it) }?.let { plant ->
            plant.customName ?: plant.speciesName?.let { knowledge().species(it)?.displayName ?: it }
        }
        val doc = SearchText.sighting(sighting, animal, zoneName, plantTitle)
        search.replace(SearchEntity(recordId = doc.id, kind = doc.kind.name, title = doc.title, body = doc.body))
    }

    private suspend fun index(plant: GardenPlant) {
        val kb = knowledge()
        val species = plant.speciesName?.let(kb::species)
        val zoneName = plant.zoneId?.let { zones.get(it)?.name }
        val doc = SearchText.plant(plant, species, zoneName, rolesOf(plant))
        search.replace(SearchEntity(recordId = doc.id, kind = doc.kind.name, title = doc.title, body = doc.body))
    }
}

/** The text a record's meaning is worked out from: its title, then what the search index holds for it. */
internal fun meaningText(hit: SearchHit) = "${hit.title}. ${hit.body}"

private fun FloatArray.toBytes(): ByteArray {
    val buffer = ByteBuffer.allocate(size * Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
    forEach(buffer::putFloat)
    return buffer.array()
}

private fun ByteArray.toFloats(): FloatArray {
    val buffer = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)
    return FloatArray(size / Float.SIZE_BYTES) { buffer.getFloat() }
}

/** What a scan found in a gallery photo. */
enum class ScanOutcome { PLANTS, NO_PLANT, FAILED }

/** A kept photo with every plant found in it, for review. */
data class PhotoReview(val photo: StoredPhoto, val takenAt: Instant, val detections: List<DetectionRecord>)

/**
 * One region of a photo, ready to store: where it is, its cut-out, whether it was in flower, the suggestions, and
 * what it seems to show.
 */
data class DetectionDraft(
    val box: BoxBp,
    val thumbnail: String,
    val flowering: Boolean,
    val candidates: List<Candidate>,
    val group: LifeGroup = LifeGroup.PLANT,
)

/** A stored detection with its suggestions, for review. */
data class DetectionRecord(
    val id: String,
    val photoId: String,
    val box: BoxBp,
    val thumbnail: String,
    val flowering: Boolean,
    val status: DetectionStatus,
    val plantId: String?,
    val candidates: List<Candidate>,
    val takenAt: Instant,
    /** What it seems to show: a plant, or an animal or fungus to record as a sighting. */
    val group: LifeGroup = LifeGroup.PLANT,
    val sightingId: String? = null,
)
