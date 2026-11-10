package io.github.wkoller.pollenplot.domain.id

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveLabelsTest {
    private fun frame(vararg named: Pair<String, Int>) = named.sortedByDescending { it.second }.map { (name, score) -> Candidate(name, name, score, setOf(IdSource.AIY_PLANTS)) }

    private fun LiveLabels.names(vararg named: Pair<String, Int>) = update(frame(*named)).map { it.label }

    @Test
    fun `a sure name shows on the first frame and an unsure one after a few`() {
        val live = LiveLabels()
        assertEquals(listOf("Grevillea robusta"), live.names("Grevillea robusta" to 7_000, "Hakea laurina" to 3_000))
        assertEquals(listOf("Grevillea robusta"), live.names("Grevillea robusta" to 7_000, "Hakea laurina" to 3_000))
        assertEquals(listOf("Grevillea robusta", "Hakea laurina"), live.names("Grevillea robusta" to 7_000, "Hakea laurina" to 3_000))
    }

    @Test
    fun `two names close together do not swap with each frame's noise`() {
        val live = LiveLabels()
        repeat(4) { live.names("Grevillea robusta" to 4_200, "Hakea laurina" to 3_900) }
        // The camera has not moved; the scores wobble either side of each other.
        val orders = (0 until 12).map { i ->
            if (i % 2 == 0) live.names("Grevillea robusta" to 3_900, "Hakea laurina" to 4_250) else live.names("Grevillea robusta" to 4_250, "Hakea laurina" to 3_900)
        }
        assertTrue("The first name stays first: $orders", orders.all { it == listOf("Grevillea robusta", "Hakea laurina") })
    }

    @Test
    fun `a name that clearly leads for two frames takes over`() {
        val live = LiveLabels()
        repeat(4) { live.names("Grevillea robusta" to 4_200, "Hakea laurina" to 3_900) }
        // The camera moves to a hakea.
        val first = (0 until 6).map { live.names("Grevillea robusta" to 800, "Hakea laurina" to 7_500).first() }
        assertEquals("Hakea laurina", first.last())
        assertTrue("It took over within a few frames: $first", first.indexOf("Hakea laurina") in 1..3)
    }

    @Test
    fun `a name near the line it shows at does not blink`() {
        val live = LiveLabels()
        repeat(6) { live.names("Grevillea robusta" to 6_000, "Hakea laurina" to 2_300) }
        // Its score wobbles across the line a single cut-off would sit on.
        val shown = (0 until 12).map { i -> "Hakea laurina" in live.names("Grevillea robusta" to 6_000, "Hakea laurina" to if (i % 2 == 0) 1_300 else 2_400) }
        assertTrue("It stays shown: $shown", shown.all { it })
        // It leaves once it is really gone.
        val after = (0 until 6).map { "Hakea laurina" in live.names("Grevillea robusta" to 6_000) }
        assertEquals(false, after.last())
    }

    @Test
    fun `the word for how sure a name is does not flip near its line`() {
        val live = LiveLabels()
        repeat(8) { live.names("Grevillea robusta" to 7_200) }
        // The score settles around the line between Likely and Possibly and wobbles across it.
        val words = (0 until 12).map { i -> Confidence.of(live.update(frame("Grevillea robusta" to if (i % 2 == 0) 5_500 else 6_400)).single().scoreBp) }
        assertTrue("It stays Likely: $words", words.all { it == Confidence.HIGH })
        val lower = (0 until 8).map { Confidence.of(live.update(frame("Grevillea robusta" to 4_000)).single().scoreBp) }
        assertEquals("A real fall changes the word", Confidence.MEDIUM, lower.last())
    }

    @Test
    fun `one frame of nothing keeps the list, and two clear it`() {
        val live = LiveLabels()
        repeat(3) { live.names("Grevillea robusta" to 7_000) }
        assertEquals(listOf("Grevillea robusta"), live.update(emptyList(), background = true).map { it.label })
        assertEquals(listOf("Grevillea robusta"), live.names("Grevillea robusta" to 7_000))
        live.update(emptyList(), background = true)
        assertEquals(emptyList<Candidate>(), live.update(emptyList(), background = true))
        // After that it starts afresh, as for a new plant.
        assertEquals(emptyList<String>(), live.names("Hakea laurina" to 3_000))
    }

    @Test
    fun `at most three names are shown, and reset forgets them`() {
        val live = LiveLabels()
        repeat(5) { live.names("A a" to 6_000, "B b" to 5_000, "C c" to 4_000, "D d" to 3_500) }
        assertEquals(listOf("A a", "B b", "C c"), live.names("A a" to 6_000, "B b" to 5_000, "C c" to 4_000, "D d" to 3_500))
        live.reset()
        assertEquals(emptyList<Candidate>(), live.update(emptyList()))
    }
}
