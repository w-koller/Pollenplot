# What the identification steps score

Measured by `FusionEvalTest` in `:domain` on 1,029 research-grade iNaturalist photos from Australia of known species
(357 of plants, 72 of frogs, and 75 of each other animal group and of fungi), with the answers of the models the app
ships as `tools/models/eval_fusion.py` recorded them with accurate pack 3. Each photo is taken through the steps the
phone takes for a photo of one plant or one animal, with no boost for the garden or for records nearby. "First" is how
often the right species was the first suggestion, "in five" how often it was among the first five.

From pack 3 the photos are by observers kept for evaluation (one iNaturalist observer in five, `photos.held_for`),
none of whose photos made a prototype, so these numbers are not comparable with those recorded for pack 2 on other
photos (48.8% first and 74.3% in five overall, 41.1% and 69.7% for plants). The pack 2 row below is BioCAP's text
vectors alone on the same photos as pack 3, from the pack's own evaluation, which looks at each photo as the app does.

| | All, first | All, in five | Plants, first | Plants, in five |
|---|---|---|---|---|
| Without the accurate pack (the quick models alone) | 5.2% | 6.8% | 8.4% | 10.1% |
| Pack 2 (text vectors alone), BioCAP's answer | 45.1% | 73.5% | 38.7% | 67.8% |
| Pack 3 (with prototypes), BioCAP's answer | 51.0% | 77.5% | 47.1% | 74.2% |
| Pack 3, the app's suggestions | 50.8% | 77.9% | 47.3% | 75.6% |

The app's suggestions were recorded an hour after the pack's own evaluation, which fetched the photos again, so a few
photos differ between the last two rows.

With pack 3, the app's suggestions by group:

| Group | Photos | First | In five |
|---|---|---|---|
| plant | 357 | 47.3% | 75.6% |
| bird | 75 | 62.7% | 89.3% |
| insect | 75 | 65.3% | 89.3% |
| spider | 75 | 46.7% | 69.3% |
| reptile | 75 | 49.3% | 69.3% |
| frog | 72 | 37.5% | 61.1% |
| mammal | 75 | 50.7% | 84.0% |
| fungus | 75 | 61.3% | 90.7% |
| fish | 75 | 46.7% | 84.0% |
| mollusc | 75 | 53.3% | 74.7% |

What the measurements decided:

- **The quick models give way to BioCAP.** The species was among the quick model's ten labels, for either look at the
  photo, in 39 of 357 plant photos, 8 of 75 insect photos and 29 of 75 bird photos. Adding its labels to BioCAP's by
  weight, only where they agreed, or as a bonus was never better than BioCAP alone, and for plants the weighting in
  1.0.0-rc.4 was worse (measured with pack 2). With pack 3, when both models put the same name first (57 photos) it
  was right in 47.
- **A plant gets three looks as one.** Against one look, the species came into the first five in 21 plant photos and
  left it in 4. For animals it came in for 22 and left for 21, so an animal keeps one look.
- **The words are honest.** The first suggestion was right in 432 of 637 marked "Likely", 79 of 294 marked
  "Possibly" and 12 of 95 marked "Unsure".
- **The genus is given when the species is unsure.** It was given for 131 photos, and was right in 106 of them, where
  the first species was right in 42.
- **A filter for plants never recorded in Australia was not built.** It changed nothing without the pack, because
  the quick model does not have the right species to move up.
- **Pack 3 gives each species a prototype, weighed gently.** A prototype is the mean of BioCAP's vectors of three or
  more Australian photos of the species (7,931 of the 13,644 species have one, from 70,780 photos), and a species gains
  alpha × exp(−beta × (1 − closeness to its prototype)) on top of its text similarity. `add_prototypes.py` tried 35
  settings of alpha and beta, and five blends of the prototype into the text vector, on photos by observers kept for
  tuning, and took the best forecast (the mean log of the share given to the right species): alpha 0.1, beta 5.5.
  Stronger settings named about one photo in a hundred more and forecast worse than the text vectors alone (alpha 0.8,
  beta 10 gave −2.650 on the evaluation photos against −2.430), which would have made "Likely" right less often. The
  way of choosing was changed twice after seeing evaluation numbers, so the evaluation photos are not wholly
  independent of it; the gentle settings differ by about a point among themselves.
- **BioCLIP 2 is the candidate for the next pack.** With text vectors alone on evaluation photos it named 64.7% first
  and 88.2% in five (plants 55.5% and 84.3%), as PyTorch runs it (`eval_candidate.py`). Its image encoder would be about
  304 MB at 8 bits against 86 MB, and one look took 4.4 times as long on the runner. It has not been exported,
  quantised or timed on a phone.
