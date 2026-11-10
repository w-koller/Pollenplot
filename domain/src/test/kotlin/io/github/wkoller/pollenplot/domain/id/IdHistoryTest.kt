package io.github.wkoller.pollenplot.domain.id

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class IdHistoryTest {
    private fun suggestions(vararg named: Pair<String, Int>) = named.map { (name, score) -> Candidate(name, name, score, setOf(IdSource.BIOCAP)) }

    private val hakeaFirst = suggestions("Hakea laurina" to 5_200, "Grevillea robusta" to 2_100, "Banksia serrata" to 900)

    @Test
    fun `nothing decided leaves the suggestions as they are`() {
        assertSame(hakeaFirst, IdHistory.NONE.apply(hakeaFirst))
    }

    @Test
    fun `a name corrected once comes first the next time`() {
        val history = IdHistory(listOf(DecidedFind("Hakea laurina", "Grevillea robusta", "Grevillea robusta")))
        val applied = history.apply(hakeaFirst)
        assertEquals(listOf("Grevillea robusta", "Hakea laurina", "Banksia serrata"), applied.map { it.label })
        assertEquals("It takes the score of the name it replaces", 5_200, applied.first().scoreBp)
        assertEquals(setOf(IdSource.BIOCAP, IdSource.USER), applied.first().sources)
    }

    @Test
    fun `a correction the models never suggested is added`() {
        val history = IdHistory(listOf(DecidedFind("hakea laurina", "My neighbour's bottlebrush")))
        val applied = history.apply(hakeaFirst, nameOf = { null })
        assertEquals("My neighbour's bottlebrush", applied.first().label)
        assertEquals(null, applied.first().speciesName)
        assertEquals(setOf(IdSource.USER), applied.first().sources)
        assertEquals("The models' suggestions follow it", listOf("Hakea laurina", "Grevillea robusta"), applied.drop(1).map { it.label })
    }

    @Test
    fun `a typed name the knowledge base knows is read as its species`() {
        val history = IdHistory(listOf(DecidedFind("Hakea laurina", "silky oak")))
        val applied = history.apply(hakeaFirst, nameOf = { if (it == "silky oak") "Grevillea robusta" else null })
        assertEquals("Grevillea robusta", applied.first().speciesName)
    }

    @Test
    fun `a name accepted as often as it was corrected stays first`() {
        val history = IdHistory(
            listOf(
                DecidedFind("Hakea laurina", "Grevillea robusta", "Grevillea robusta"),
                DecidedFind("Hakea laurina", "Hakea laurina", "Hakea laurina"),
            ),
        )
        assertEquals("Hakea laurina", history.apply(hakeaFirst).first().label)
    }

    @Test
    fun `the correction made most often wins`() {
        val history = IdHistory(
            listOf(
                DecidedFind("Hakea laurina", "Banksia serrata", "Banksia serrata"),
                DecidedFind("Hakea laurina", "Grevillea robusta", "Grevillea robusta"),
                DecidedFind("Hakea laurina", "Grevillea robusta", "Grevillea robusta"),
            ),
        )
        assertEquals("Grevillea robusta", history.apply(hakeaFirst).first().label)
    }

    @Test
    fun `a correction for another name changes nothing`() {
        val history = IdHistory(listOf(DecidedFind("Acacia pycnantha", "Grevillea robusta", "Grevillea robusta")))
        assertEquals(hakeaFirst, history.apply(hakeaFirst))
    }

    @Test
    fun `a name turned down twice and never accepted loses half its score`() {
        val twice = IdHistory(listOf(DecidedFind("Hakea laurina", null), DecidedFind("Hakea laurina", null)))
        val applied = twice.apply(hakeaFirst)
        assertEquals(listOf("Hakea laurina" to 2_600, "Grevillea robusta" to 2_100), applied.take(2).map { it.label to it.scoreBp })

        val once = IdHistory(listOf(DecidedFind("Hakea laurina", null)))
        assertEquals(5_200, once.apply(hakeaFirst).first().scoreBp)

        val alsoAccepted = IdHistory(listOf(DecidedFind("Hakea laurina", null), DecidedFind("Hakea laurina", null), DecidedFind("Hakea laurina", "Hakea laurina")))
        assertEquals(5_200, alsoAccepted.apply(hakeaFirst).first().scoreBp)
    }

    @Test
    fun `a halved name can fall behind the next one`() {
        val history = IdHistory(listOf(DecidedFind("Hakea laurina", null), DecidedFind("Hakea laurina", null)))
        val close = suggestions("Hakea laurina" to 4_000, "Grevillea robusta" to 2_100)
        assertEquals(listOf("Grevillea robusta", "Hakea laurina"), history.apply(close).map { it.label })
    }
}
