package io.github.wkoller.pollenplot.data.db

import io.github.wkoller.pollenplot.domain.garden.GardenPlant
import io.github.wkoller.pollenplot.domain.garden.PlantStatus
import io.github.wkoller.pollenplot.domain.garden.Zone
import io.github.wkoller.pollenplot.domain.garden.ZoneKind
import io.github.wkoller.pollenplot.domain.garden.ZoneRect
import io.github.wkoller.pollenplot.domain.plants.Soil
import io.github.wkoller.pollenplot.domain.plants.Sun
import io.github.wkoller.pollenplot.domain.semantic.Link
import io.github.wkoller.pollenplot.domain.semantic.Provenance
import java.time.LocalDate

/** Reads a stored enum name, falling back to [default] if a later version wrote a value this one does not know. */
private inline fun <reified E : Enum<E>> enumOrNull(name: String?): E? = name?.let { n -> enumValues<E>().firstOrNull { it.name == n } }

fun ZoneEntity.toDomain() = Zone(
    id = id,
    name = name,
    kind = enumOrNull<ZoneKind>(kind) ?: ZoneKind.OTHER,
    rect = ZoneRect(xCm, yCm, widthCm, heightCm),
    sun = enumOrNull<Sun>(sun),
    soil = enumOrNull<Soil>(soil),
    mulched = mulched,
    measuredAreaCm2 = measuredAreaCm2,
    notes = notes,
)

fun Zone.toEntity(createdAt: Long, updatedAt: Long) = ZoneEntity(
    id = id,
    name = name,
    kind = kind.name,
    xCm = rect.xCm,
    yCm = rect.yCm,
    widthCm = rect.widthCm,
    heightCm = rect.heightCm,
    sun = sun?.name,
    soil = soil?.name,
    mulched = mulched,
    measuredAreaCm2 = measuredAreaCm2,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun PlantEntity.toDomain() = GardenPlant(
    id = id,
    speciesName = speciesName,
    customName = customName,
    zoneId = zoneId,
    quantity = quantity,
    plantedOn = plantedOnEpochDay?.let(LocalDate::ofEpochDay),
    status = enumOrNull<PlantStatus>(status) ?: PlantStatus.GROWING,
    notes = notes,
    thumbnail = thumbnail,
)

fun GardenPlant.toEntity(createdAt: Long, updatedAt: Long) = PlantEntity(
    id = id,
    speciesName = speciesName,
    customName = customName,
    zoneId = zoneId,
    quantity = quantity,
    plantedOnEpochDay = plantedOn?.toEpochDay(),
    status = status.name,
    notes = notes,
    thumbnail = thumbnail,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun LinkEntity.toDomain() = Link(subjectId, predicate, objectId, enumOrNull<Provenance>(provenance) ?: Provenance.USER, confidenceBp)
