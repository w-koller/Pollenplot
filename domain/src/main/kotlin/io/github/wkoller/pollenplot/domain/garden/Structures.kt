package io.github.wkoller.pollenplot.domain.garden

/** Something built in the yard that the Plot shows so the zones can be placed around it. It holds no plants. */
enum class StructureKind(val widthCm: Int, val heightCm: Int, val shape: StructureShape = StructureShape.RECTANGLE) {
    HOUSE(1200, 1000),
    SHED(300, 250),
    GARAGE(600, 600),
    GREENHOUSE(300, 250),
    POOL(800, 400, StructureShape.OVAL),
    AVIARY(300, 200),
    PEN(400, 300),
    DECK(400, 300),
    PAVING(400, 300),
    DRIVEWAY(300, 800),
    WATER_TANK(200, 200, StructureShape.OVAL),
    COMPOST(150, 150),
    OTHER(300, 300),
}

/** How a structure is drawn inside its rectangle: filling it, or as the oval that fits it, for a round pool or tank. */
enum class StructureShape { RECTANGLE, OVAL }

/**
 * A structure where it lies in the yard, on the same grid as the zones. A new one takes its kind's usual size and
 * shape, which are Pollenplot's starting points and are changed to fit.
 */
data class Structure(
    val id: String,
    val name: String,
    val kind: StructureKind,
    val shape: StructureShape = kind.shape,
    val rect: ZoneRect,
    val notes: String = "",
) {
    init {
        require(name.isNotBlank()) { "A structure needs a name" }
    }
}
