package io.github.wkoller.pollenplot.domain.assistant

import io.github.wkoller.pollenplot.domain.rhythms.BeeGroup
import io.github.wkoller.pollenplot.domain.season.Season
import io.github.wkoller.pollenplot.domain.wildlife.LifeGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.Month

class AskStartersTest {
    private val today = LocalDate.of(2026, 10, 28)
    private val empty = GardenPulse(today, Season.SPRING)

    @Test
    fun `an empty garden is still offered questions for the season`() {
        val all = AskStarters.forGarden(empty)
        assertEquals(listOf(StarterKind.SEASON_BEES, StarterKind.SEASON_BIRDS, StarterKind.SEASON_JOBS, StarterKind.FROGS), all.map { it.kind })
        assertTrue(all.take(3).all { it.season == Season.SPRING })
        assertEquals(3, AskStarters.page(all, 0).size)
        assertTrue("Four questions are more than one page", AskStarters.hasMore(all))
    }

    @Test
    fun `what is pressing comes first, then what is happening, then the season`() {
        val pulse = empty.copy(
            hotDays = listOf(PulseHotDay(today.plusDays(2), 365)),
            followUps = listOf(PulseFollowUp("Scale", "lemon", "Mum's lemon", today)),
            visitors = listOf(PulseVisitor("blue-banded bee", LifeGroup.INSECT, "s1", "lav", "Lavender")),
            inFlower = listOf(PulsePlant("lav", "Lavender", "Herb Spiral")),
            gaps = listOf(PulseGap(BeeGroup.STINGLESS, Month.JUNE)),
        )
        val all = AskStarters.forGarden(pulse)
        assertEquals(
            listOf(StarterKind.HOT_DAY, StarterKind.FOLLOW_UP, StarterKind.VISITOR_ON_PLANT, StarterKind.IN_FLOWER_ZONE, StarterKind.GAP),
            all.take(5).map { it.kind },
        )
        assertEquals(today.plusDays(2), all[0].date)
        assertEquals("The problem, then its plant", listOf("Scale", "Mum's lemon"), all[1].names)
        assertEquals("A follow-up is about its plant", listOf("lemon"), all[1].about)
        assertEquals("A visitor is about its sighting and the plant it was on", listOf("s1", "lav"), all[2].about)
        assertEquals(BeeGroup.STINGLESS, all[4].group)
        assertEquals(Month.JUNE, all[4].month)
    }

    @Test
    fun `flowers are asked about in the zone with the most of them`() {
        val pulse = empty.copy(
            inFlower = listOf(
                PulsePlant("a", "Grevillea", "Front Native Strip"),
                PulsePlant("b", "Lavender", "Herb Spiral"),
                PulsePlant("c", "Bottlebrush", "Front Native Strip"),
            ),
        )
        val flowers = AskStarters.forGarden(pulse).first()
        assertEquals(StarterKind.IN_FLOWER_ZONE, flowers.kind)
        assertEquals(listOf("Front Native Strip"), flowers.names)
        assertEquals(listOf("a", "c"), flowers.about)
        val noZones = AskStarters.forGarden(empty.copy(inFlower = listOf(PulsePlant("a", "Grevillea", null)))).first()
        assertEquals(StarterKind.IN_FLOWER, noZones.kind)
    }

    @Test
    fun `a garden that feeds a group of bees nothing asks what to plant, and a fungus is asked about as one`() {
        val all = AskStarters.forGarden(
            empty.copy(
                gaps = listOf(PulseGap(BeeGroup.SOLITARY, null)),
                visitors = listOf(PulseVisitor("fly agaric", LifeGroup.FUNGUS, "s2", "lemon", "Mum's lemon")),
            ),
        )
        assertTrue(all.any { it.kind == StarterKind.UNFED && it.group == BeeGroup.SOLITARY })
        assertEquals(StarterKind.FUNGUS, all.first().kind)
    }

    @Test
    fun `more ideas pages round the questions`() {
        val all = (0 until 5).map { Starter(StarterKind.FROGS, listOf("$it")) }
        assertEquals(listOf("0", "1", "2"), AskStarters.page(all, 0).map { it.names.single() })
        assertEquals(listOf("3", "4", "0"), AskStarters.page(all, 1).map { it.names.single() })
        assertEquals(listOf("1", "2", "3"), AskStarters.page(all, 2).map { it.names.single() })
        val three = all.take(3)
        assertFalse(AskStarters.hasMore(three))
        assertEquals(three, AskStarters.page(three, 4))
    }

    @Test
    fun `a plant and an animal each get their own questions`() {
        assertEquals(4, AskStarters.forPlant("Mum's lemon").size)
        assertTrue(AskStarters.forPlant("Mum's lemon").all { it.names == listOf("Mum's lemon") })
        assertEquals(3, AskStarters.forAnimal("blue-banded bee").size)
    }

    @Test
    fun `follow-ups cover each record the answer drew on before asking more about one`() {
        val plant = CitedRecord("p1", RecordKind.PLANT, "Lavender", "")
        val zone = CitedRecord("z1", RecordKind.ZONE, "Herb Spiral", "")
        val sighting = CitedRecord("s1", RecordKind.SIGHTING, "Blue-banded bee", "")
        val all = AskFollowUps.after(listOf(plant, zone, sighting)).take(AskFollowUps.SHOWN)
        assertEquals(listOf(StarterKind.PLANT_BEES, StarterKind.ZONE_BEES, StarterKind.ANIMAL_NEEDS), all.map { it.kind })
        assertEquals(listOf(listOf("p1"), listOf("z1"), listOf("s1")), all.map { it.about })
        assertEquals(listOf(StarterKind.PLANT_BEES, StarterKind.PLANT_FLOWERING), AskFollowUps.after(listOf(plant, plant)).map { it.kind })
        assertTrue(AskFollowUps.after(emptyList()).isEmpty())
    }

    @Test
    fun `an answer that cites nothing is followed by the records it was given, then the questions not asked yet`() {
        val plant = CitedRecord("p1", RecordKind.PLANT, "Lavender", "")
        val zone = CitedRecord("z1", RecordKind.ZONE, "Herb Spiral", "")
        val frogs = Starter(StarterKind.FROGS)
        val birds = Starter(StarterKind.SEASON_BIRDS, season = Season.SPRING)
        val all = AskFollowUps.after(emptyList(), considered = listOf(plant, zone), fallback = listOf(frogs, birds))
        assertEquals(
            listOf(StarterKind.PLANT_BEES, StarterKind.ZONE_BEES, StarterKind.PLANT_FLOWERING, StarterKind.FROGS, StarterKind.SEASON_BIRDS),
            all.map { it.kind },
        )
        // A record both cited and given is asked about once, the cited one first.
        val cited = AskFollowUps.after(listOf(zone), considered = listOf(plant, zone))
        assertEquals(listOf(StarterKind.ZONE_BEES, StarterKind.PLANT_BEES, StarterKind.PLANT_FLOWERING), cited.map { it.kind })
        // With nothing given at all, the questions not asked yet still follow.
        assertEquals(listOf(frogs, birds), AskFollowUps.after(emptyList(), fallback = listOf(frogs, birds, frogs)))
    }

    @Test
    fun `the greeting follows the time of day`() {
        assertEquals(PartOfDay.MORNING, PartOfDay.of(8))
        assertEquals(PartOfDay.AFTERNOON, PartOfDay.of(13))
        assertEquals(PartOfDay.EVENING, PartOfDay.of(20))
        assertEquals(PartOfDay.EVENING, PartOfDay.of(2))
    }

    @Test
    fun `the model is told what the garden is doing now`() {
        val lines = GardenPrompts.pulse(
            empty.copy(
                inFlower = listOf(PulsePlant("a", "Grevillea", "Front Native Strip")),
                visitors = listOf(PulseVisitor("blue-banded bee", LifeGroup.INSECT, "s1", "lav", "Lavender")),
                gaps = listOf(PulseGap(BeeGroup.STINGLESS, Month.JUNE, listOf("Leptospermum"))),
                hotDays = listOf(PulseHotDay(LocalDate.of(2026, 10, 30), 362)),
                followUps = listOf(PulseFollowUp("Scale", "lemon", "Mum's lemon", today)),
                honeyHives = 2,
                stinglessHives = 1,
            ),
        )
        assertEquals(
            listOf(
                "Today is Wednesday 28 October 2026, in spring.",
                "In flower this month: Grevillea in Front Native Strip.",
                "Seen in the garden for the first time this year, in the last month: blue-banded bee on Lavender.",
                "Nothing in the garden flowers for stingless bees in June. Plants that would: Leptospermum.",
                "Hot days ahead for the stingless bee hives: Friday 30 October 2026, 36 °C.",
                "Problems due a second look: Scale on Mum's lemon.",
                "Hives kept: 2 honey bee hives and 1 stingless bee hive.",
            ),
            lines,
        )
        val prompt = GardenPrompts.garden("1 zone", emptyList(), "Which bees visit the lavender?", listOf("Lavender flowers in: October"))
        assertTrue(prompt, "Facts about what the question is about:\n- Lavender flowers in: October" in prompt)
    }
}

class MidSentenceTest {
    @Test
    fun `a name loses its capital mid-sentence unless it starts with a place, a people or a person`() {
        assertEquals("blue-banded bee", MidSentence.name("Blue-banded bee"))
        assertEquals("scale", MidSentence.name("Scale"))
        assertEquals("rainbow lorikeet", MidSentence.name("Rainbow lorikeet"))
        assertEquals("Australian magpie", MidSentence.name("Australian magpie"))
        assertEquals("Queensland fruit fly", MidSentence.name("Queensland fruit fly"))
        assertEquals("Lewin's honeyeater", MidSentence.name("Lewin's honeyeater"))
        assertEquals("already lower", MidSentence.name("already lower"))
    }
}
