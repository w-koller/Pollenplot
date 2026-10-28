package io.github.wkoller.pollenplot.domain.assistant

import org.junit.Assert.assertEquals
import org.junit.Test

class GardenAssistantGuardTest {
    private fun assertKind(kind: QuestionKind, vararg questions: String) =
        questions.forEach { assertEquals(it, kind, GardenAssistantGuard.classify(it)) }

    @Test
    fun `bites and stings are emergencies first`() = assertKind(
        QuestionKind.BITE_OR_STING,
        "My son was stung by a bee and his face is swelling",
        "I've been bitten by a spider in the shed",
        "The dog got bitten by a snake!",
        "She can't breathe after a wasp sting",
    )

    @Test
    fun `something eaten by a person or a pet is poisoning`() = assertKind(
        QuestionKind.POISONING,
        "My dog ate some snail pellets",
        "My toddler chewed a leaf of the oleander",
        "I got sap in my eyes from the euphorbia",
        "The cat might have eaten a lily",
    )

    @Test
    fun `whether to eat something is never left to an identification`() = assertKind(
        QuestionKind.EATING,
        "Is this mushroom edible?",
        "Can I eat the berries?",
        "Are these safe to eat?",
        "Could we cook with the leaves?",
        "Is it OK to eat the fruit?",
    )

    @Test
    fun `chemical amounts go to the label`() = assertKind(
        QuestionKind.CHEMICAL_DOSE,
        "How much glyphosate should I mix per litre?",
        "What rate of oxalic acid for my hive?",
        "How many ml of neem oil in a spray bottle?",
    )

    @Test
    fun `signs of notifiable bee pests go to the hotline`() = assertKind(
        QuestionKind.BEE_OR_EXOTIC_PEST,
        "I think I saw varroa mites on my bees",
        "My brood has sunken cappings and smells bad",
        "Is this an Asian honey bee?",
    )

    @Test
    fun `handling a dangerous animal is not the model's call`() = assertKind(
        QuestionKind.HANDLING,
        "How do I catch the snake in my shed?",
        "Is it safe to pick up a blue tongue?",
        "Can I move the redback from the letterbox?",
        "How do I get rid of the wasp nest?",
    )

    @Test
    fun `injured wildlife goes to a rescue group`() = assertKind(
        QuestionKind.INJURED_WILDLIFE,
        "I found an injured possum",
        "A baby bird fell out of its nest",
        "There's a bat caught in netting",
    )

    @Test
    fun `everyday garden questions reach the model`() = assertKind(
        QuestionKind.GENERAL,
        "When does the grevillea flower?",
        "Which plants attract blue-banded bees?",
        "Do birds eat lantana berries?",
        "What eats aphids?",
        "Is lantana poisonous to dogs?",
        "How much water does a lemon tree need?",
        "How much fertiliser should I give my citrus?",
        "Should I spray the aphids?",
        "Something has eaten my lettuces",
        "Why are the tomato leaves going yellow?",
    )
}
