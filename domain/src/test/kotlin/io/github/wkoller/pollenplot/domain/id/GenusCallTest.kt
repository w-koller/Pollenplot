package io.github.wkoller.pollenplot.domain.id

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GenusCallTest {
    private fun suggestions(vararg named: Pair<String, Int>, source: IdSource = IdSource.BIOCAP) =
        named.map { (name, score) -> Candidate(name, name, score, setOf(source)) }

    @Test
    fun `belief spread over one genus names the genus`() {
        val found = suggestions("Grevillea robusta" to 3_400, "Grevillea banksii" to 2_900, "Hakea laurina" to 1_200, "Grevillea juniperina" to 800)
        assertEquals(GenusLead("Grevillea", 7_100), GenusCall.of(found))
    }

    @Test
    fun `a likely species needs no genus`() {
        assertNull(GenusCall.of(suggestions("Grevillea robusta" to 6_400, "Grevillea banksii" to 2_900)))
    }

    @Test
    fun `belief spread over several genera names none`() {
        assertNull(GenusCall.of(suggestions("Grevillea robusta" to 3_400, "Hakea laurina" to 3_000, "Banksia serrata" to 2_500)))
        assertNull(GenusCall.of(emptyList()))
    }

    @Test
    fun `a find the user has corrected is left to their correction`() {
        val found = suggestions("My grevillea" to 3_400, source = IdSource.USER) + suggestions("Grevillea robusta" to 3_400, "Grevillea banksii" to 2_900)
        assertNull(GenusCall.of(found))
    }
}
