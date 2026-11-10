# What the identification steps score

Measured by `FusionEvalTest` in `:domain` on 1,035 research-grade iNaturalist photos from Australia of known species
(360 of plants, 75 of each animal group and of fungi), with the answers of the models the app ships as
`tools/models/eval_fusion.py` recorded them with accurate pack 2. Each photo is taken through the steps the phone
takes for a photo of one plant or one animal, with no boost for the garden or for records nearby. "First" is how
often the right species was the first suggestion, "in five" how often it was among the first five.

| | All, first | All, in five | Plants, first | Plants, in five |
|---|---|---|---|---|
| Without the accurate pack (the quick models alone) | 4.6% | 6.7% | 6.9% | 8.1% |
| With the pack, as 1.0.0-rc.4 combined the models | 47.8% | 70.8% | 37.2% | 59.7% |
| With the pack, now | 48.8% | 74.3% | 41.1% | 69.7% |

The row for 1.0.0-rc.4 is from the first recording of the photos; the others are from the second, which fetched the
photos again, so a few photos differ between them.

With the pack, now, by group:

| Group | Photos | First | In five |
|---|---|---|---|
| plant | 360 | 41.1% | 69.7% |
| bird | 75 | 69.3% | 90.7% |
| insect | 75 | 48.0% | 74.7% |
| spider | 75 | 56.0% | 64.0% |
| reptile | 75 | 44.0% | 73.3% |
| frog | 75 | 21.3% | 40.0% |
| mammal | 75 | 48.0% | 88.0% |
| fungus | 75 | 66.7% | 93.3% |
| fish | 75 | 57.3% | 80.0% |
| mollusc | 75 | 65.3% | 86.7% |

What the measurements decided:

- **The quick models give way to BioCAP.** The species was among the quick model's ten labels, for either look at the
  photo, in 32 of 360 plant photos, 5 of 75 insect photos and 40 of 75 bird photos. Adding its labels to BioCAP's by
  weight, only where they agreed, or as a bonus was never better than BioCAP alone, and for plants the weighting in
  1.0.0-rc.4 was worse. When both models put the same name first (56 photos) it was right in 41, no more often than
  BioCAP's own "Likely".
- **A plant gets three looks as one.** Against one look, the species came into the first five in 21 plant photos and
  left it in 8. For animals it came in for 17 and left for 16, so an animal keeps one look.
- **The words are honest.** The first suggestion was right in 368 of 517 marked "Likely", 117 of 390 marked
  "Possibly" and 20 of 125 marked "Unsure".
- **The genus is given when the species is unsure.** It was given for 175 photos, and was right in 139 of them, where
  the first species was right in 61.
- **A filter for plants never recorded in Australia was not built.** It changed nothing without the pack, because
  the quick model does not have the right species to move up.
