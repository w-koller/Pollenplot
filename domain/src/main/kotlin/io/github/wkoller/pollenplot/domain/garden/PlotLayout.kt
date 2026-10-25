package io.github.wkoller.pollenplot.domain.garden

import kotlin.math.roundToInt

/** Rules for laying out zones on the Plot canvas. Positions and sizes snap to a 50 cm grid. */
object PlotLayout {
    const val GRID_CM = 50
    const val MIN_SIDE_CM = 50
    const val DEFAULT_WIDTH_CM = 400
    const val DEFAULT_HEIGHT_CM = 300

    /** The space kept between zones that the app places itself. */
    const val GAP_CM = 100

    /** New zones are laid out in rows of this many before a new row starts. */
    private const val ZONES_PER_ROW = 3

    fun snap(valueCm: Int): Int = (valueCm.toDouble() / GRID_CM).roundToInt() * GRID_CM

    /** The rectangle with its position and size snapped to the grid, and each side at least [MIN_SIDE_CM]. */
    fun snapped(rect: ZoneRect): ZoneRect = ZoneRect(
        xCm = snap(rect.xCm).coerceAtLeast(0),
        yCm = snap(rect.yCm).coerceAtLeast(0),
        widthCm = snap(rect.widthCm).coerceAtLeast(MIN_SIDE_CM),
        heightCm = snap(rect.heightCm).coerceAtLeast(MIN_SIDE_CM),
    )

    /** Moves by one grid step in each direction given, never past the top or left edge of the canvas. */
    fun nudged(rect: ZoneRect, stepsRight: Int, stepsDown: Int): ZoneRect =
        rect.copy(
            xCm = (rect.xCm + stepsRight * GRID_CM).coerceAtLeast(0),
            yCm = (rect.yCm + stepsDown * GRID_CM).coerceAtLeast(0),
        )

    /** Grows or shrinks by one grid step on the right and bottom edges, never below [MIN_SIDE_CM]. */
    fun resizedBySteps(rect: ZoneRect, widerSteps: Int, tallerSteps: Int): ZoneRect =
        rect.copy(
            widthCm = (rect.widthCm + widerSteps * GRID_CM).coerceAtLeast(MIN_SIDE_CM),
            heightCm = (rect.heightCm + tallerSteps * GRID_CM).coerceAtLeast(MIN_SIDE_CM),
        )

    /**
     * Where a new zone goes: the first spot in a tidy grid of rows that keeps [GAP_CM] clear of every other zone. With
     * no zones yet, that is the top left.
     */
    fun placeNew(existing: List<ZoneRect>, widthCm: Int = DEFAULT_WIDTH_CM, heightCm: Int = DEFAULT_HEIGHT_CM): ZoneRect {
        val cellWidth = DEFAULT_WIDTH_CM + GAP_CM
        val cellHeight = DEFAULT_HEIGHT_CM + GAP_CM
        var index = 0
        while (true) {
            val candidate = ZoneRect((index % ZONES_PER_ROW) * cellWidth, (index / ZONES_PER_ROW) * cellHeight, widthCm, heightCm)
            val padded = ZoneRect(candidate.xCm - GAP_CM / 2, candidate.yCm - GAP_CM / 2, widthCm + GAP_CM, heightCm + GAP_CM)
            if (existing.none { it.overlaps(padded) }) return candidate
            index++
        }
    }

    /** The smallest rectangle around every zone, or null when there are none. */
    fun bounds(rects: List<ZoneRect>): ZoneRect? {
        if (rects.isEmpty()) return null
        val left = rects.minOf { it.xCm }
        val top = rects.minOf { it.yCm }
        return ZoneRect(left, top, rects.maxOf { it.rightCm } - left, rects.maxOf { it.bottomCm } - top)
    }
}
