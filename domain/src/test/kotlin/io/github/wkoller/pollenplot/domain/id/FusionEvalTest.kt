package io.github.wkoller.pollenplot.domain.id

import io.github.wkoller.pollenplot.domain.plants.KnowledgeBase
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Measures the steps from the models' answers to the app's suggestions on Australian photos of known species
 * (see [FusionEval]). The numbers below are what those steps scored when they were last changed; a change that
 * lowers one fails here, and a change that raises one should raise the number with it. The photos are recorded
 * again by running the Fusion evaluation workflow, which is needed after a change to a model or to how the phone
 * looks at a photo.
 */
class FusionEvalTest {
    private val eval = FusionEval.load()

    /** The app's suggestions with the accurate pack: BioCAP's look at the photo, through [CandidateFusion]. */
    private fun withThePack(photo: EvalPhoto) = eval.asTheApp(photo)

    private fun report(title: String, scores: Map<String, EvalScore>) =
        println(title + "\n" + scores.entries.joinToString("\n") { "  ${it.key}: ${it.value}" })

    @Test
    fun `with the accurate pack the app names at least as many photos as when it was last measured`() {
        val scores = eval.measure(::withThePack)
        report("With the accurate pack", scores)
        for ((group, baseline) in WITH_THE_PACK) {
            val score = scores.getValue(group)
            assertTrue("$group: ${score.top1Bp} bp first, below the baseline of ${baseline.first}", score.top1Bp >= baseline.first)
            assertTrue("$group: ${score.top5Bp} bp in the first five, below the baseline of ${baseline.second}", score.top5Bp >= baseline.second)
        }
    }

    @Test
    fun `three looks as one name more plants than one look, and no more animals`() {
        fun wonAndLost(photos: List<EvalPhoto>): Pair<Int, Int> {
            fun inFive(photo: EvalPhoto, look: String) = eval.asTheApp(photo, eval.accurate(photo, look)).take(5).any { eval.names(it, photo) }
            val pairs = photos.map { inFive(it, FusionEval.CENTRE) to inFive(it, FusionEval.ALL_LOOKS) }
            return pairs.count { !it.first && it.second } to pairs.count { it.first && !it.second }
        }
        val plants = wonAndLost(eval.photos.filter { it.group == FusionEval.PLANT })
        val animals = wonAndLost(eval.photos.filter { it.group != FusionEval.PLANT })
        println("Three looks against one, species among the first five: plants won ${plants.first} and lost ${plants.second}; animals won ${animals.first} and lost ${animals.second}")
        assertTrue("For plants, three looks win at least twice as often as they lose", plants.first >= plants.second * 2)
        // If animals come to gain as plants do, they should get the three looks too.
        assertTrue("For animals, three looks win about as often as they lose", animals.first < animals.second * 2)
    }

    @Test
    fun `the quick models rarely know the species, which is why BioCAP's suggestions stand alone`() {
        report("Without the accurate pack", eval.measure(eval::withoutThePack))
        // How often the species is among the quick model's ten labels for either look at the photo, first or not.
        val known = eval.photos.filter { it.aiyCentre != null }.groupBy { it.group }.mapValues { (group, photos) ->
            photos.count { photo ->
                (photo.aiyCentre!!.labels + photo.aiyWhole!!.labels).any { eval.names(Candidate(it.label, eval.nameOf(group)(it.label), 0, emptySet()), photo) }
            } to photos.size
        }
        println("The species is among the quick model's labels: " + known.map { (group, count) -> "$group ${count.first} of ${count.second}" })
        // A quick model that came to know half the plants or insects would be worth combining with BioCAP again.
        for (group in listOf(FusionEval.PLANT, "insect")) {
            val (among, photos) = known.getValue(group)
            assertTrue("The quick $group model now has the species among its labels in $among of $photos photos; measure combining it with BioCAP again", among * 2 < photos)
        }
        // When the quick model's first name is BioCAP's first name too, BioCAP is no surer than its own "Likely".
        val agreeing = eval.photos.mapNotNull { photo ->
            val first = withThePack(photo).firstOrNull() ?: return@mapNotNull null
            val quick = eval.quick(photo)?.firstOrNull()?.label ?: return@mapNotNull null
            if (KnowledgeBase.normalise(eval.nameOf(photo.group)(quick) ?: quick) == FoundMerge.key(first)) eval.names(first, photo) else null
        }
        println("Both models put the same name first in ${agreeing.size} photos, and it was right in ${agreeing.count { it }}")
    }

    @Test
    fun `the words for how sure a suggestion is are honest`() {
        val firsts = eval.photos.mapNotNull { photo -> withThePack(photo).firstOrNull()?.let { Confidence.of(it.scoreBp) to eval.names(it, photo) } }
        val right = Confidence.entries.associateWith { word -> firsts.filter { it.first == word }.let { list -> list.count { it.second } to list.size } }
        println("First suggestions that were right, by their word: " + right.map { (word, count) -> "$word ${count.first} of ${count.second}" })
        fun share(word: Confidence) = right.getValue(word).let { (hits, all) -> hits * BoxBp.FULL / all }
        assertTrue("Likely is right at least two times in three", share(Confidence.HIGH) >= 6_667)
        assertTrue("Each word is right more often than the one below it", share(Confidence.HIGH) > share(Confidence.MEDIUM) && share(Confidence.MEDIUM) > share(Confidence.LOW))
    }

    @Test
    fun `where the genus is given it is right far more often than the first species`() {
        fun genus(name: String) = name.substringBefore(' ').lowercase()
        var given = 0
        var genusRight = 0
        var speciesRight = 0
        for (photo in eval.photos) {
            val suggestions = withThePack(photo)
            val lead = GenusCall.of(suggestions) ?: continue
            given++
            if ((listOf(photo.species) + photo.also).any { genus(it) == lead.genus.lowercase() }) genusRight++
            if (eval.names(suggestions.first(), photo)) speciesRight++
        }
        println("The genus was given for $given of ${eval.photos.size} photos: it was right in $genusRight, and the first species in $speciesRight")
        assertTrue("The genus is given often enough to matter", given * 10 >= eval.photos.size)
        assertTrue("It is right at least three times in four", genusRight * 4 >= given * 3)
        assertTrue(genusRight > speciesRight * 3 / 2)
    }

    private companion object {
        /**
         * Top 1 and top 5 in basis points, for all the photos and for the groups with the most at stake, when the
         * steps were last changed: accurate pack 3, whose species have prototypes, on photos by observers whose
         * photos made no prototype. BioCAP's suggestions stand in place of the quick models', from three looks as
         * one for a plant.
         */
        val WITH_THE_PACK = mapOf(
            FusionEval.ALL to (5_050 to 7_750),
            FusionEval.PLANT to (4_700 to 7_500),
            "bird" to (6_250 to 8_900),
            "insect" to (6_500 to 8_900),
        )
    }
}
