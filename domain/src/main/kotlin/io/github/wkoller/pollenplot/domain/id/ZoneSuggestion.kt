package io.github.wkoller.pollenplot.domain.id

/**
 * Which zone to offer for a plant found in a photo. Plants in one photo usually grow together, so the zone most of the
 * photo's other confirmed plants went into comes first; otherwise the zone the user chose last; otherwise none.
 */
object ZoneSuggestion {
    /**
     * [photoZones] are the zones of the plants already confirmed from the same photo (null for "no zone"), [lastUsed]
     * the zone chosen for the last plant added, and [zones] the zones that still exist.
     */
    fun suggest(photoZones: List<String?>, lastUsed: String?, zones: Set<String>): String? {
        val counts = photoZones.filterNotNull().filter { it in zones }.groupingBy { it }.eachCount()
        val best = counts.maxOfOrNull { it.value } ?: return lastUsed?.takeIf { it in zones }
        // Ties go to the zone confirmed first, so the suggestion does not jump around as plants are added.
        return photoZones.first { it != null && counts[it] == best }
    }
}
