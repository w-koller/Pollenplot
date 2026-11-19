# What the identification steps score

Measured by `FusionEvalTest` in `:domain` on about 1,029 research-grade iNaturalist photos from Australia of known
species (357 of plants, 72 of frogs, and 75 of each other animal group and of fungi), with the answers of the models
the app ships as `tools/models/eval_fusion.py` recorded them with accurate pack 3, and since 18 November 2026 with
pack 4 (below). Each photo is taken through the steps the
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

**Pack 4** is pack 3 with its encoder's 37 8-bit layers computed in 8-bit integers (ONNX Runtime's accuracy level 4),
which made a look 2.4 times as fast on a Snapdragon SM8850 phone (63 ms against 151 ms; `accuracy-research.md`).
Nothing else changed. `level4_pack.py` scored pack 3's own evaluation photos with both packs, as the app scores a
photo (Model pack run 44167763710):

| | All, first | All, in five | Plants, first | Plants, in five | Log share, all |
|---|---|---|---|---|---|
| Pack 3, BioCAP's answer | 51.4% | 77.6% | 48.2% | 75.9% | −2.376 |
| Pack 4, BioCAP's answer | 51.6% | 77.9% | 48.5% | 76.2% | −2.383 |

The two packs' vectors of a photo had a cosine of at least 0.998, and the flower answer changed for 1 of 714 plant
photos. `eval_fusion.py` chooses its photos with the pack's number as the seed, so pack 4's recorded answers are on
another 1,022 photos (353 of plants, 72 of fungi) and are not comparable with pack 3's table above. With pack 4, the
app's suggestions by group, which are `FusionEvalTest`'s baseline:

| Group | Photos | First | In five |
|---|---|---|---|
| all | 1,022 | 56.3% | 81.1% |
| plant | 353 | 55.5% | 80.2% |
| bird | 75 | 65.3% | 82.7% |
| insect | 75 | 64.0% | 93.3% |
| spider | 75 | 52.0% | 77.3% |
| reptile | 75 | 46.7% | 76.0% |
| frog | 72 | 36.1% | 59.7% |
| mammal | 75 | 50.7% | 82.7% |
| fungus | 72 | 76.4% | 93.1% |
| fish | 75 | 53.3% | 86.7% |
| mollusc | 75 | 65.3% | 82.7% |

On these photos three looks as one brought the species into the first five for 22 plant photos and out of it for 6,
and the genus was given for 123 photos and right in 104, where the first species was right in 48.

**BioCLIP 2 pack 1** (`export_bioclip2.py`, Model pack run 44186039158, offered for high-end phones) is
BioCLIP 2's image encoder in 8-bit weights at accuracy level 4 with its text vectors alone, no prototypes. On pack
4's evaluation photos, each scored as the app scores a photo:

| | All, first | All, in five | Plants, first | Plants, in five | Log share, all |
|---|---|---|---|---|---|
| BioCAP pack 4 | 50.6% | 77.8% | 47.1% | 74.0% | −2.378 |
| BioCLIP 2 pack 1 | 65.3% | 88.7% | 55.5% | 84.6% | −1.399 |

It named more first in every group, from 1.4 points for frogs to 32 for spiders, and was better at the group of the
closest species overall (98.6% against 96.6%), though slightly worse for plants (97.2% against 98.3%). Its flower
band answers 63.3% of plants, 90.0% of them right. With `eval_fusion.py --seed 4` its answers are on the 1,022 photos
of pack 4's table above (`eval_outputs_bioclip2.json.gz`, `BioClip2FusionEvalTest`): the app's suggestions named
68.2% first and 89.2% in five against 56.3% and 81.1%, and plants 57.5% and 81.9% against 55.5% and 80.2%. That
plant gain of 2 points against 8.4 on the other photos shows how much one set of 353 plant photos can move. The words
held: "Likely" was right for 586 of 702, "Possibly" 97 of 243 and "Unsure" 14 of 77, and the genus was given for 144
photos and right in 122. Three looks gained less than with BioCAP (8 plants into the first five, 4 out).

What the measurements decided (with pack 3, unless it says otherwise):

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

What could be done next, ranked by the gain expected for the cost, with how to measure each and what BioCLIP 2 would
take on a phone, is in `accuracy-research.md`.
## Plant problems pack

`disease_head.py` fits a multinomial logistic regression on BioCAP's vector of a photo's middle square
(`photos.phone_square`, as `AccurateModels.middleVector` prepares it on the phone), with one class for each problem
it has enough photos of and one for a healthy plant. The photos are PlantDoc's (CC BY 4.0), PlantVillage's
(CC BY-SA 3.0, weighted at a quarter because they are laboratory photos on a plain background) and research-grade
iNaturalist observations under CC0 or CC BY, of the pathogens on their hosts and of common Australian garden plants
as healthy plants (`disease_photos.py`). Each class weighs the same in fitting. Photos are split by observer as
`photos.held_for` splits them; PlantDoc and PlantVillage have no observers, so a hash of each file's path stands in.

The regularisation and the temperature are chosen on the tuning photos (the lowest log loss). The two levels the app
shows are chosen on the tuning photos taken in the field (iNaturalist and PlantDoc) alone, because the laboratory
photos are far easier: `yes_bp`, the lowest share at which 85% of first answers are right, and never below half the
belief ("likely", and "No problem found" for a healthy answer), and `maybe_bp`, the lowest at which 35% of the chips
shown hold the right problem and at most 5% of healthy field photos are given a chip ("possible"). Those proportions
are Pollenplot's choice. Problems the catalogue does not hold, such as early
blight and septoria leaf spot, are learned as classes so their photos are not called something the app knows, and
`DiseaseCall` never offers them.

Held-out top-1, top-3, the expected calibration error before and after the temperature, and how often healthy garden
plants are called healthy are written to the run's `evaluation.md` and the release notes.

Pack 1 (Model pack run 44401441095, published as `disease-pack-1`, 264,025 bytes) has 11 classes, fitted on 9,127
photos, tuned on 3,179 and judged on 3,115. C = 100 and the temperature is 0.95. "Likely" starts at 5000 bp and
"possible" at 1000 bp.

| Held-out photos | Photos | Top-1 | Top-3 |
|---|---:|---:|---:|
| iNaturalist | 425 | 97.2% | 99.5% |
| PlantDoc | 340 | 87.1% | 98.2% |
| PlantVillage | 2,350 | 98.6% | 100.0% |
| All | 3,115 | 97.1% | 99.7% |

The overall figure is mostly PlantVillage's laboratory photos, so the field photos are the ones to judge it by. Of the
765 field photos, 99.3% reach "likely" or "No problem found", and 93.0% of those are right; 93.5% of the problem
photos have the right problem among the chips, and 3.3% of the healthy ones are given a chip. Healthy garden plants
from iNaturalist are called "No problem found" 97.1% of the time. The expected calibration error is 0.9% before the
temperature and 0.5% after. Downy mildew, anthracnose and most pests have too few photos to be a class, so the pack
never suggests them.

