package io.github.wkoller.pollenplot.domain.id

import io.github.wkoller.pollenplot.domain.plants.AuState
import io.github.wkoller.pollenplot.domain.wildlife.Animal
import io.github.wkoller.pollenplot.domain.wildlife.FaunaBase
import io.github.wkoller.pollenplot.domain.wildlife.LifeGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReviewPickTest {
    private val lemon = Candidate("Citrus limon", "Citrus limon", 8_000, setOf(IdSource.AIY_PLANTS))
    private val unknownPlant = Candidate("Odd plantus", null, 6_000, setOf(IdSource.AIY_PLANTS))
    private val magpie = Candidate("Gymnorhina tibicen", "Gymnorhina tibicen", 9_000, setOf(IdSource.AIY_BIRDS), common = "Magpie")
    private val stranger = Candidate("Rarus avis", "Rarus avis", 5_000, setOf(IdSource.AIY_BIRDS), common = "Rare bird")

    private val fauna = FaunaBase(
        listOf(
            Animal(
                "Gymnorhina tibicen", listOf("Australian magpie"), LifeGroup.BIRD, "Artamidae", setOf(AuState.QLD), emptySet(),
                emptyList(), null, null, null, null, emptyList(), "", emptyList(),
            ),
        ),
    )

    @Test
    fun `the top suggestion is added unless another is chosen`() {
        assertEquals(ReviewPick.Plant("Citrus limon", null), ReviewPick.plant(listOf(lemon, unknownPlant)))
        assertEquals(ReviewPick.Plant(null, "Odd plantus"), ReviewPick.plant(listOf(lemon, unknownPlant), index = 1))
    }

    @Test
    fun `a species the user searched for wins over the suggestions`() {
        assertEquals(ReviewPick.Plant("Grevillea robusta", null), ReviewPick.plant(listOf(lemon), picked = "Grevillea robusta"))
        assertEquals(ReviewPick.Plant("Grevillea robusta", null), ReviewPick.plant(emptyList(), picked = "Grevillea robusta"))
    }

    @Test
    fun `nothing is added without a suggestion or a choice`() {
        assertNull(ReviewPick.plant(emptyList()))
        assertNull(ReviewPick.animal(emptyList(), fauna))
    }

    @Test
    fun `an animal the knowledge base has is named as the knowledge base names it`() {
        assertEquals(ReviewPick.Animal("Gymnorhina tibicen", "Australian magpie"), ReviewPick.animal(listOf(magpie), fauna))
    }

    @Test
    fun `an animal the knowledge base lacks keeps the model's common and scientific names`() {
        assertEquals(ReviewPick.Animal("Rarus avis", "Rare bird"), ReviewPick.animal(listOf(stranger), fauna))
        assertEquals(ReviewPick.Animal("Rarus avis", "Rare bird"), ReviewPick.animal(listOf(stranger), fauna = null))
    }

    @Test
    fun `a name the user typed for an unknown animal is only its label`() {
        assertEquals(ReviewPick.Animal(null, "Blue bird"), ReviewPick.animal(listOf(magpie), fauna, picked = "Blue bird"))
        assertEquals(ReviewPick.Animal("Gymnorhina tibicen", "Australian magpie"), ReviewPick.animal(emptyList(), fauna, picked = "gymnorhina  tibicen"))
    }
}
