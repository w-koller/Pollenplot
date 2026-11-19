package io.github.wkoller.pollenplot.domain.id

/**
 * [FusionEvalTest] on BioCLIP 2's answers (bioclip2-pack-1, the pack for high-end phones), recorded by eval_fusion.py
 * with `--seed 4` on the photos of BioCAP's pack 4, so the two baselines are on the same 1,022 photos. The words for
 * how sure a suggestion is, the genus and three looks were chosen with BioCAP, and must hold for BioCLIP 2 too.
 */
class BioClip2FusionEvalTest : FusionEvalTest() {
    override val resource get() = "eval_outputs_bioclip2.json.gz"

    override val baseline get() = WITH_BIOCLIP2

    private companion object {
        /** Top 1 and top 5 in basis points with bioclip2-pack-1, text vectors alone, when it was published. */
        val WITH_BIOCLIP2 = mapOf(
            FusionEval.ALL to (6_800 to 8_900),
            FusionEval.PLANT to (5_750 to 8_150),
            "bird" to (7_700 to 9_850),
            "insect" to (9_050 to 9_850),
        )
    }
}
