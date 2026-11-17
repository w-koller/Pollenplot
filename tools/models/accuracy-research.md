# How identification could be made more accurate

Findings only, written on 17 November 2026. Nothing here has been built or measured yet, and no workflow was run for
it. The starting point is pack 3 (BioCAP with prototypes), whose suggestions name 47.3% of the 357 held-out
Australian plant photos first and 75.6% in five (`evaluation.md`). Every gain below is an estimate from the papers it
cites, and each one names the measurement that would replace the estimate. `CLAUDE.md` asks that a weight, threshold
or look change only with a number from `FusionEvalTest`, and that still holds.

## The recommendation

Do these in this order. The first three are worth doing whatever happens with BioCLIP 2, because they either carry over
to a new model unchanged or are scripts that run again on its vectors.

| Order | Change | Expected gain, plants first | Cost | Carries over to BioCLIP 2 |
|---|---|---|---|---|
| 1 | A location prior from Atlas of Living Australia (ALA) records per 50 km cell | about 2 to 5 points | Medium: a data build, a pack or knowledge base file, and one fusion term | Yes, unchanged |
| 2 | Keep each prototype photo's vector, then a text-anchored linear probe and several prototypes per species | about 1 to 3 points | Low: one workflow rerun and `add_prototypes.py`; the probe needs no app change | The scripts do; they run again on its vectors |
| 3 | A phone benchmark of BioCLIP 2 (the gate for everything in the BioCLIP 2 section) | none by itself | Low: an export and a debug benchmark over `adb` | It is BioCLIP 2 |
| 4 | More photos per species | about 1 to 2 points, more on species with few photos | Low in effort, hours of Actions time | The photo list does |
| 5 | The user's confirmed photos as personal prototypes | not measurable on iNaturalist photos as they are; likely the largest for plants photographed again in one garden | Medium to high: app storage, scoring and a new simulated measurement | Vectors must be made again |
| 6 | Several photos of one plant combined | unknown until measured; likely large when the photos show different parts | Low to measure, high to build the screens | Yes, unchanged |

Number 6 is cheap to measure, and that measurement should be taken alongside number 1, because a large number there
would move it up. The BioCLIP 2 pack itself, if the benchmark passes, is the largest single gain on offer (see
below), and comes after numbers 1 and 2 so that its pack is built with the better scripts from the start.

Two things the app already does are not measured at all, and measuring them is the first step of number 1 and
number 5. `FusionEvalTest` runs every photo with no garden boost (`CandidateFusion.GARDEN_BOOST_BP`, × 1.5) and no
nearby boost (`NEARBY_BOOST_BP`, × 1.2 for a species with at least three ALA records within 10 km in ten years, from
`assets/weeds/sources.json`), and `IdHistory` has never been scored.

## 1. A location prior from ALA records

**What it is.** A photo's suggestions are multiplied by how likely each species is to be where the photo was taken,
before the photo is looked at. Mac Aodha, Cole and Perona trained such a prior on presence records alone and raised
iNat2018 top-1 from 60.2% to 72.7% (InceptionV3 at 299 pixels), and iNat2017 from 63.3% to 69.6%. Adding the date
to their prior added only 0.1 to 0.3 points. SINR (Cole et al., 2023), the line of work behind iNaturalist's
geomodel, reports the gain a prior gives an iNaturalist classifier as Δ top-1: +4.1 for the best plain grid of
counts, +5.2 for Mac Aodha's prior and up to +6.2 for SINR.

**What to expect here.** Less than those papers. Their classifiers chose among species of the whole world, and a
prior removes the other continents. Pollenplot's species index already holds only species recorded in Australia, so the
prior can only separate, say, a Western Australian banksia from a Queensland one. Inside Australia the gaps are still
wide (south-west Western Australia, the wet tropics and Tasmania share few native plants), so an estimate of 2 to
5 points on plants is reasonable. Garden plants are a known weakness: an exotic ornamental is rarely recorded on ALA
near the garden, so the prior must never push a species down much, only lift those recorded nearby. Leave the date
out at first, as Mac Aodha found it worth a fraction of a point.

**How to build it.** The plain grid is the cheap form, and SINR's own table shows it gets most of the gain (+4.1 of
+6.2). For each cell of half a degree (about 50 km), count each species' ALA records since a fixed year, through
biocache's `facets=species` search for the cell's box, as the nearby search already does for a circle. About 3,000
cells hold land, so the build is about 3,000 requests in GitHub Actions. Smooth each cell with its eight neighbours,
so a species in a poorly recorded cell is not taken as absent, and keep a log count in 2 or 3 bits. The result is a
few megabytes and could ship in the knowledge base or the pack. It works offline, unlike the nearby records, which
need the monthly sync, and it reaches the garden's cell from the coordinates the app already keeps (rounded to
about 1 km). A learned SINR is the next step only if the grid's gain is large and its gaps are measured.

**How to measure it.**

1. `eval_fusion.py` records, for each photo, its iNaturalist coordinates (`location`, rounded to 0.01 degrees) and
   whether they were obscured (`obscured` or `geoprivacy`), and the month it was observed. The tuning photos
   (`photos.held_for`) need the same, so a weight is chosen on them, not on the evaluation photos.
2. The prior is built with a leave-out. iNaturalist Australia sends its research-grade observations to ALA, so each
   evaluation photo is itself an ALA record. Build the grid without the records of the evaluation and tuning
   observers (or, more simply, without iNaturalist's records at all for the evaluation build), or the prior will have
   seen the answer.
3. `FusionEvalTest` gains a test that applies the prior and the existing nearby boost to each photo, with the
   nearby records looked up for the photo's coordinates under the same leave-out. Report first, in five, and the
   honesty of "Likely", "Possibly" and "Unsure", which a prior can make surer than it is right. Obscured photos
   are reported apart, because their coordinates are moved by up to about 20 km.
4. Then replace `NEARBY_BOOST_BP` with the prior or keep both, by the number.

## 2. Keep each photo's vector, then a probe and several prototypes

**A finding first.** The 70,780 prototype vectors were never kept. `prototype_vectors.py` writes, for each species,
only the sum of its photos' unit vectors and their count, and `add_prototypes.py` divides one by the other. A linear
probe and several prototypes per species both need each photo's own vector. So the first step is to make
`prototype_vectors.py` write each vector as well (70,780 × the encoder's dimensions × 2 bytes, about 72 MB at BioCAP's 512
dimensions), and rerun the vectors job. It need not choose the photos again: the pack already holds
`prototype_photos.tsv.gz`, and the vectors job can read that in place of the 35 GB of open data.

**A text-anchored linear probe.** A prototype is the class mean, which is SimpleShot (Wang et al., 2019) and the
nearest-mean classifier of Prototypical Networks (Snell et al., 2017). A linear probe trained on the same vectors
learns which directions separate similar species, which a mean cannot. Radford et al. (2021) found linear probes on
CLIP's features well ahead of zero-shot with enough examples, but behind it with one to four examples per class, and
these species have 3 to 16. The forms that do well with few examples start from the text vectors and stay close to
them: LP++ (Huang et al., 2024) builds the probe's weights from the text vectors and learns a blend, and WiSE-FT
(Wortsman et al., 2022) interpolates between the zero-shot and the trained weights. So:

- each species' weight is its text vector plus a learned change, with a penalty on the change's size;
- the 5,713 species without photos keep their text vector unchanged;
- the penalty and the logit scale are chosen on the tuning photos by the forecast (mean log share of the right
  species), as `add_prototypes.py` chooses alpha and beta, because a probe trained on few photos grows surer than it
  is right, and "Likely" depends on that.

Such a probe is a matrix with one row per species, the same shape as `species.f16`. `add_prototypes.py` already ships
one such form ("blend" replaces the text vectors), so a probe folded into `species.f16` needs no app change at all,
only the pack. Training is a softmax over 13,644 species on 70,780 vectors, minutes on the runner's processor.

**Several prototypes per species.** One mean is a poor summary of a species whose photos show flowers, fruit and whole
shrubs. Tip-Adapter (Zhang et al., 2022) keeps every training photo's vector and adds, for each species, the sum of
exp(−beta × (1 − closeness)) over its photos, which is the form `SpeciesIndex` already uses for one prototype, summed
over many. Every photo is about 72 MB more in the pack; up to three k-means centres for each species (fewer for a
species with few photos) is about a third of that, and the phone's cost is some 20,000 more dot products a look.
Try the full cache, three centres and the single mean on the tuning photos, and pick by the forecast.

**How to measure it.** `add_prototypes.py` already tries settings on the tuning photos and reports them on the
evaluation photos. Add the probe and the several-prototype forms to its `settings_for`, keep the forecast as the
choice, and then run `eval_fusion.py` on the pack it builds and replace `FusionEvalTest`'s baseline, as for pack 3.
Report the gain split by how many photos the species' prototype had (from `counts`), because that says whether
number 4 is worth its hours.

## 4. More photos per species

Pack 3's prototypes average about 9 photos (70,780 for 7,931 species), with a limit of 16 (`--per-species`) and none
for the 5,713 species with fewer than three (`MIN_PHOTOS`). The photos are research-grade, Australian, by observers
not held out, and only the first photo of an observation under CC0 or CC BY. Three ways to get more, cheapest first:

- Raise `--per-species` to 32 and `CANDIDATES` above 80, for the common species.
- Take an observation's second and third photos, which often show another part of the plant. They are by the same
  observer, so count them as one observer when spreading.
- Look outside Australia for species that are planted or naturalised here. Most garden plants are common on
  iNaturalist elsewhere. This needs a second region in `prototype_photos.py` and its own measurement, because a
  photo from another country shows another setting.

CC BY-NC photos should stay out. A prototype is made from them and ships in the pack, with their credits, and the
pack is published under terms that a non-commercial licence may not allow.

The gain from a prototype was 8.4 points for plants (38.7% to 47.1%, BioCAP's answer). More photos make each mean
steadier and give prototypes to more species, so 1 to 2 points overall is a reasonable estimate, more on rare
species. Measured exactly as number 2, with the split by photo count.

## 5. The user's confirmed photos as personal prototypes

**What it is.** A garden is photographed again and again, and the same grevillea looks the same each time. Each find
the user accepts or corrects becomes a labelled photo of a plant in this garden. `IdHistory` already learns from those
decisions, but only by name: when the models put a name first that the user corrected, the correction takes its
place. A personal prototype learns from the photo itself, so a find close to a plant the user has named before is
lifted towards that name, even when the models never put that name first and even for a cultivar the species
index does not hold. This is the few-shot setting of Tip-Adapter and SimpleShot, with the user's own photos as the
examples.

**What it needs.**

- The detection keeps BioCAP's vector of the find (512 numbers at 16 bits, 1 KB). The detection table does not
  keep it today, and the schema may still change in place before 1.0.0. The vectors stay on the phone and go into a
  backup with the rest.
- For each name the user has accepted or chosen, a score of the same form as the pack's prototypes, summed over the
  user's photos of it (the Tip-Adapter form, because a garden has few photos of each plant), with its own alpha and
  beta, applied after fusion where `IdHistory` is applied (`PhotoAnalysis`, the live suggestions and
  `AccuratePass`).
- `IdHistory` stays, for the finds made before a vector was kept and for animals seen once.
- A pack update changes the vectors, so the personal vectors are made again from the kept photos when the pack
  changes, or dropped where a photo is gone.

**How to measure it.** The evaluation photos come from many observers and hold no garden, so `FusionEvalTest` cannot
measure this as it is. A fair simulation uses iNaturalist observers as gardens: for each evaluation observer, fetch up
to 50 of their other research-grade plant observations within a few kilometres of the photo, take those as the
user's confirmed finds (the right species and the distractors alike), and score the evaluation photo with and
without them. Only observers held for evaluation are used, so nothing leaks into the pack. The same simulation can
then score `IdHistory`, the garden boost and the two together, none of which has been measured. Choose alpha and beta
on observers held for tuning.

## 6. Several photos of one plant combined

**What it is.** Two photos of one plant, say the flower and the leaf, are scored as one, by the mean of their vectors
as the app already does for its three looks at one photo (`AccurateModels.lookAtOne`). Three looks at one photo moved
the species into the first five in 21 plant photos and out of it in 4; separate photos of different parts should do
better than three crops of one. PlantCLEF has scored observations of several images since 2014, and Pl@ntNet's app
asks for several organs for the same reason (Affouard et al., 2017).

**How to measure it.** `held_out_photos` takes only the first photo of each observation. Record the second and third
photos of the same observations as well, as extra looks in `eval_outputs.json.gz`, and add a test that scores each
observation with one photo, then two, then three. This costs one workflow run and a test, and should be done with
number 1.

**What building it would take.** A way to say that photos show one plant: "Add another photo" on a review card, or
photos taken within a minute of each other offered as one plant. `FoundMerge` already joins two finds of one plant in
one photo, and could join two finds across photos once the user says so.

## BioCLIP 2

**What is known.** With text vectors alone, BioCLIP 2 named 55.5% of plant photos first and 84.3% in five, against
38.7% and 67.8% for BioCAP (`eval_candidate.py`, PyTorch). Its image encoder would be about 304 MB at 8 bits against
86 MB, and one look took 4.4 times as long on the runner. That ratio matches open_clip's published profile of the two
encoders at 224 pixels: about 78 billion multiply-adds for ViT-L/14 against about 18 for ViT-B/16.

Two cautions about the 55.5%. `eval_candidate.py` gives a plant three looks as one, as pack 3 does, so BioCLIP 2 with
one look will score lower and must be measured so. And it is the text vectors alone; with its own prototypes it
should gain as BioCAP did (8.4 points), though not necessarily as much.

**Licence.** The model card gives the weights the MIT licence, with no further terms, and says it was fine-tuned
from OpenCLIP's ViT-L/14 trained on LAION-2B. TreeOfLife-200M, its training data, is marked CC0 as a compilation, but
its card says the images and text keep their own licences, "mostly within the CC family": EOL and GBIF images range
from CC0 to CC BY-NC-SA, FathomNet is CC BY-NC-ND, and the GBIF occurrence snapshot is CC BY-NC. The card says
nothing about models trained on it. So shipping the weights under MIT carries the same open question about
non-commercial training images that BioCAP's weights already carry; check BioCAP's card for its training data
to confirm the position is unchanged, and seek advice if Pollenplot is ever sold. The card also names a newer
BioCLIP 2.5 with a ViT-H/14 encoder, which is about twice as large again and not a phone candidate.

**Measured on a phone (17 November 2026).** `run_benchmark.sh` on a Xiaomi 2512BPNDAG (Qualcomm SM8850, 15 GB,
Android 37), with the encoders of Model pack run 44086488819, four threads, three looks to warm up and twenty timed.
The phone stayed cool throughout (thermal status 0).

| Encoder | MB | Load | Median look | 90th percentile | Slowest |
|---|---|---|---|---|---|
| BioCAP, accuracy level 0 (as shipped) | 91 | 125 ms | 151 ms | 188 ms | 314 ms |
| BioCAP, accuracy level 4 | 91 | 208 ms | 63 ms | 70 ms | 72 ms |
| BioCLIP 2, accuracy level 0 | 317 | 509 ms | 672 ms | 787 ms | 866 ms |
| BioCLIP 2, accuracy level 4 | 317 | 758 ms | 305 ms | 366 ms | 374 ms |

- BioCLIP 2 takes 4.5 times as long as BioCAP at level 0 and 4.9 times at level 4, close to the 4.4 measured on the
  runner. On this phone a BioCLIP 2 look is well inside the 1.5 s of the ship rule, at either level.
- Level 4 is 2.2 to 2.4 times as fast on this phone's Arm processor (1.6 times on the runner's Intel one). Its
  vectors stayed within a cosine of 0.996 of PyTorch's (`encoders.md` of the same run), but whether it names species
  as well has not been measured. If it does, it is worth having for BioCAP alone, and at 305 ms BioCLIP 2 could keep
  three looks for a plant (about 0.9 s) on a phone like this one, which would change the ship rule's "one look".
- Memory, as the process's PSS: BioCAP at level 0 added about 100 MB when loaded and 130 MB at its peak; level 4
  added about 230 and 260 MB, because ONNX Runtime repacks the weights for its integer kernels. BioCLIP 2 at level 4
  added about 510 MB loaded and 540 MB at its peak. Each model ran after the last in one process, and the memory an
  earlier session freed is not always given back, so the later rows are rough (BioCLIP 2 at level 0 could not be
  read). A run with one model at a time (`--es models <file>`) gives cleaner numbers.
- This phone is one of the fastest Android phones sold. A mid-range phone with 8 GB has not been measured, and is
  the next phone to time, because the ship rule is set by the slowest phone the pack is offered on.

**Phone latency and memory, estimated.** No published benchmark of a ViT-L/14 under ONNX Runtime on an Android
phone was found, so these are estimates to be replaced by the benchmark.

- *Execution provider.* The app runs BioCAP on ONNX Runtime's default processor provider with four threads
  (`BioCapEncoder`). Its 8-bit weights are `MatMulNBits`, a contrib operator that neither the XNNPACK nor the NNAPI
  provider runs, so with either of them those nodes fall back to the processor anyway, and NNAPI's NDK API is
  deprecated from Android 15. The processor provider is the path to measure. Its `MatMulNBits` has an
  `accuracy_level` setting; level 4 computes with 8-bit integers (with KleidiAI kernels on Arm processors that have
  them), which ONNX Runtime reports as about 2.3 times as fast as level 1 in one desktop Arm measurement. Level 4
  quantises the activations in blocks, and 8-bit activations cost transformers about seven points before, so it
  needs the full evaluation, for BioCAP as well as BioCLIP 2.
- *Latency.* The work is 4.4 times BioCAP's and the weights read each look are 3.5 times as many, so a look should
  take about 4 to 4.5 times as long as BioCAP's on the same phone. BioCAP's time on a phone has never been recorded.
  If it takes 0.25 s a look on a recent flagship, BioCLIP 2 takes about 1.1 s; at 0.35 s, about 1.5 s. Mid-range
  phones are two to three times slower and will not meet the rule below.
- *Memory.* About 304 MB of weights, plus ONNX Runtime's working memory for one 224 × 224 photo, which for a
  ViT-L/14 is small (257 tokens of 1,024 numbers, attention maps of a few megabytes), plus any copy the provider
  makes when it repacks weights at load. A peak of 400 to 600 MB is a reasonable estimate. It must never be loaded
  while Gemma 4 is (`SecondOpinionWorker`), which already takes much more.
- *The pack.* Text vectors and prototypes at 768 dimensions (CLIP ViT-L/14's width; confirm from the export) are about
  21 MB each, so the download is about 350 MB against pack 3's 142 MB.

**The steps.**

1. **Export.** `models.yml`'s `build: benchmark` job runs `export_candidate.py`, which exports BioCAP's and
   BioCLIP 2's image encoders as `export_biocap.py` exports BioCAP, with `MatMulNBits` at 8 bits and block size 128,
   each at `accuracy_level` 0 and 4, and checks them against PyTorch on the fixtures and 24 plant photos. The models
   are artifacts kept for a week, one for each model, never a release.
2. **Benchmark on a phone.** `tools/models/run_benchmark.sh <run-id>` installs the debug build, pushes the four
   models to the phone one download at a time, and starts `EncoderBenchmarkActivity` (debug builds only), which times
   each of them and the installed pack's encoder with the app's settings: three looks to warm up and twenty timed,
   the median, 90th percentile and slowest look, the time to load, the memory (PSS) loaded and at its peak, and the
   phone's heat before and after. The results land in `build/benchmark/`. This is the gate: if BioCLIP 2 is over the
   time limit, stop here.
3. **Prototypes.** Make the vectors again with BioCLIP 2's encoder from the same `prototype_photos.tsv.gz`, keeping
   each photo's vector (number 2), and choose alpha and beta, or the probe, on the tuning photos.
4. **Everything else that is model-specific.** `flowers.f16` and `flowers.yes_bp` and `no_bp`, the group check
   (`AccurateRegions.GROUP_SHARE_BP`) and the genus threshold are BioCAP's and must be chosen again.
5. **Full evaluation.** The pack's own evaluation, then `eval_fusion.py` on the pack, with plants given one look, and
   `FusionEvalTest`'s baseline, honesty and genus tests on the new answers.
6. **In the app.** A second pack, offered only on phones with 8 GB of memory or more (`ActivityManager`'s
   `totalMem`, which reports a little under the number sold, so about 7.5 GB), and BioCAP's pack everywhere else.

**The ship rule.** Ship a BioCLIP 2 pack only when all of these hold:

- its suggestions name at least 5 points more plant photos first than pack 3's (47.3% now), on the same evaluation
  photos, with BioCLIP 2 given one look and pack 3 its three;
- one look takes under about 1.5 s (the median) on the slowest phone it is offered on;
- it is offered only on phones with 8 GB of memory or more;
- a photo gets one look, not three;
- `FusionEvalTest`'s honesty and genus tests pass on its answers.

**A disease head.** A classifier for plant diseases trained on BioCAP's vectors (the optional disease work in another
session) reads BioCAP's 512 numbers. BioCLIP 2's vectors are another space, likely of another size, so such a
head must be trained again for a BioCLIP 2 pack, and a phone that has both packs needs the head that matches the
encoder it runs.

## Sources

- Mac Aodha, Cole and Perona, *Presence-Only Geographical Priors for Fine-Grained Image Classification*, ICCV 2019.
  [arXiv:1906.05272](https://arxiv.org/abs/1906.05272)
- Cole et al., *Spatial Implicit Neural Representations for Global-Scale Species Mapping*, ICML 2023.
  [arXiv:2306.02564](https://arxiv.org/abs/2306.02564), code at [elijahcole/sinr](https://github.com/elijahcole/sinr)
- Radford et al., *Learning Transferable Visual Models From Natural Language Supervision*, 2021.
  [arXiv:2103.00020](https://arxiv.org/abs/2103.00020)
- Wang et al., *SimpleShot: Revisiting Nearest-Neighbor Classification for Few-Shot Learning*, 2019.
  [arXiv:1911.04623](https://arxiv.org/abs/1911.04623)
- Snell, Swersky and Zemel, *Prototypical Networks for Few-shot Learning*, NeurIPS 2017.
  [arXiv:1703.05175](https://arxiv.org/abs/1703.05175)
- Zhang et al., *Tip-Adapter: Training-free Adaption of CLIP for Few-shot Classification*, ECCV 2022.
  [arXiv:2207.09519](https://arxiv.org/abs/2207.09519)
- Huang et al., *LP++: A Surprisingly Strong Linear Probe for Few-Shot CLIP*, CVPR 2024.
  [arXiv:2404.02285](https://arxiv.org/abs/2404.02285)
- Wortsman et al., *Robust fine-tuning of zero-shot models* (WiSE-FT), CVPR 2022.
  [arXiv:2109.01903](https://arxiv.org/abs/2109.01903)
- Affouard et al., *Pl@ntNet app in the era of deep learning*, ICLR 2017 workshop.
- Gu et al., *BioCLIP 2: Emergent Properties from Scaling Hierarchical Contrastive Learning*, NeurIPS 2025.
  [arXiv:2505.23883](https://arxiv.org/abs/2505.23883); model card at
  [imageomics/bioclip-2](https://huggingface.co/imageomics/bioclip-2); data card at
  [imageomics/TreeOfLife-200M](https://huggingface.co/datasets/imageomics/TreeOfLife-200M)
- open_clip's model profile (multiply-adds of each encoder), in
  [mlfoundations/open_clip](https://github.com/mlfoundations/open_clip)
- ONNX Runtime pull request 27692 (8-bit `MatMulNBits` at accuracy level 4 on Arm),
  [microsoft/onnxruntime](https://github.com/microsoft/onnxruntime/pull/27692)
- Android's [NNAPI migration guide](https://developer.android.com/ndk/guides/neuralnetworks/migration-guide)
  (the NDK API is deprecated from Android 15)

Not reached: iNaturalist's blog post introducing its geomodel refused the request, and the full text of
Zhu, Lange and Hamilton, *Investigating Different Geo Priors for Image Classification*
([arXiv:2508.15946](https://arxiv.org/abs/2508.15946)), which studies SINR priors on iNaturalist observations,
could not be read, so neither is quoted.
