package io.github.wkoller.pollenplot.data

import io.github.wkoller.pollenplot.data.kb.KnowledgeRepository
import io.github.wkoller.pollenplot.data.prefs.SettingsStore
import io.github.wkoller.pollenplot.domain.garden.PlantFacts
import io.github.wkoller.pollenplot.domain.garden.Wish
import io.github.wkoller.pollenplot.domain.garden.Wishlist
import io.github.wkoller.pollenplot.domain.garden.ZoneBadge
import io.github.wkoller.pollenplot.domain.plants.CareResolver
import io.github.wkoller.pollenplot.domain.plants.EcoRole
import io.github.wkoller.pollenplot.domain.plants.ResolvedCare
import io.github.wkoller.pollenplot.domain.plants.Species
import io.github.wkoller.pollenplot.domain.weeds.WeedStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

/**
 * A wish as the screens show it: its species and care, its badges in the garden (Toxic to pets first while pets live at
 * home), its weed status where the garden is, its zone, and the badges that zone would gain with it.
 */
data class WishView(
    val wish: Wish,
    val species: Species?,
    val care: ResolvedCare?,
    val roles: List<EcoRole>,
    val weed: WeedStatus,
    val zoneName: String?,
    val zoneEffect: List<ZoneBadge>,
) {
    val title: String get() = wish.customName ?: species?.displayName ?: wish.speciesName.orEmpty()

    val scientificName: String? get() = (species?.name ?: wish.speciesName)?.takeIf { it != title }
}

/** The wishlist joined with the knowledge base and the garden. */
class WishViews(wishes: WishRepository, views: GardenViews, knowledge: KnowledgeRepository, settings: SettingsStore) {
    val list: Flow<List<WishView>> = combine(
        wishes.wishes,
        views.zones,
        knowledge.knowledge.filterNotNull(),
        views.locality,
        settings.settings,
    ) { list, zones, kb, locality, current ->
        Wishlist.ordered(list).map { wish ->
            val species = wish.speciesName?.let(kb::species)
            val care = species?.let { CareResolver.resolve(kb, it) }
            val roles = if (species != null && care != null) locality.roles(species, care.care) else emptyList()
            val zone = zones.firstOrNull { it.zone.id == wish.targetZoneId }
            val effect = if (zone != null && care != null) {
                Wishlist.zoneEffect(zone.plants.map { PlantFacts(it.care?.care?.water, it.roles) }, PlantFacts(care.care.water, roles))
            } else {
                emptyList()
            }
            WishView(
                wish, species, care, Wishlist.badgeOrder(roles, current.petsAtHome),
                species?.let(locality::weed) ?: wish.speciesName?.let(locality::weed) ?: WeedStatus.NONE,
                zone?.zone?.name, effect,
            )
        }
    }

    fun wish(id: String): Flow<WishView?> = list.map { views -> views.firstOrNull { it.wish.id == id } }
}
