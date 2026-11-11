"""Adds the species' prototypes to the base pack and writes the pack the app downloads.

The base pack (export_biocap.py) names a photo by the species whose text vector is closest to it. A prototype is the
mean of BioCAP's vectors of a species' own photos (prototype_photos.py, prototype_vectors.py), which says what the
species looks like to the model, where the text says only what it is called. This tries ways of using them on photos
kept for tuning, chooses one, and measures it on photos kept for evaluation, whose observers gave the prototypes
nothing (photos.held_for):

- "near": a species gains alpha * exp(-beta * (1 - closeness to its prototype)) on top of the text similarity
  (pack.Scorer), with the prototypes in prototypes.f16 beside the text vectors.
- "blend": each species' text vector is replaced by a weighted mean of it and its prototype, in species.f16.

The setting chosen is the best forecast (the mean log share given to the right species) among those that name
nearly as many tuning photos as the one that names the most, so the pack gains what the prototypes offer without
growing surer than it is right, which the words the app puts on a suggestion depend on. The strongest settings name
a few more photos and forecast clearly worse. Each photo is scored as the app scores it: three looks as one
for a plant, one look for an animal, among the species of its group.

The encoder, the flower vectors and their thresholds are the base pack's, unchanged. The pack also holds
prototype_photos.tsv.gz: each photo a prototype was made from, with its photographer and licence (CC0 or CC BY).

It needs no PyTorch: `pip install onnxruntime==1.31.0 numpy pillow`. It runs in GitHub Actions
(.github/workflows/models.yml).
"""
import argparse
import csv
import glob
import gzip
import io
import json
import os
import zipfile

import numpy as np

from pack import Pack, Scorer, closest, look_vector, sha256_of, shares, unzip, verified
from photos import EVALUATION, FUSION_PLAN, TUNING, group_of, held_out_photos

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
INDEX = os.path.join(ROOT, "tools", "models", "species_index.tsv")

PACK_VERSION = 3

# The pack export_biocap.py made, which this adds to.
BASE_PACK = 2
BASE_URL = "https://github.com/w-koller/Pollenplot/releases/download/biocap-pack-2/biocap-pack-2.zip"
BASE_SHA256 = "e696fa92258372c1ede99c10ede97e2a8521470becffa1cc224a5a41974888d8"

# A mean of fewer photos says more about one photo than about the species.
MIN_PHOTOS = 3

NEAR = [(alpha, beta) for beta in (2.0, 5.5, 10.0, 15.0, 20.0) for alpha in (0.02, 0.05, 0.1, 0.2, 0.4, 0.8, 1.6)]
BLEND = [0.1, 0.2, 0.3, 0.5, 0.7]
TEXT = "The text vectors alone"

# Settings within this of the most photos named, first and in five added together, are judged by their forecast.
NEARLY = 0.02

# The least share counted for a species outside the closest, so one miss does not decide the forecast's score.
FLOOR = 1e-4


def base_pack():
    return unzip(verified(BASE_URL, BASE_SHA256))


def unit(vectors):
    length = np.linalg.norm(vectors, axis=1, keepdims=True)
    return np.divide(vectors, length, out=np.zeros_like(vectors), where=length > 0)


def as_stored(vectors):
    """Vectors as the pack holds them, in 16-bit floats, and as the phone reads them back."""
    return vectors.astype("<f2").astype(np.float32)


def prototypes_from(directory, species, dimensions):
    """Each species' prototype from the shares' sums, a row of zeros for a species with fewer than MIN_PHOTOS photos,
    and how many photos each was made from."""
    sums = np.zeros((species, dimensions), dtype=np.float64)
    counts = np.zeros(species, dtype=np.int64)
    for path in sorted(glob.glob(os.path.join(directory, "vectors-*.npz"))):
        share = np.load(path)
        sums[share["rows"]] += share["sums"]
        counts[share["rows"]] += share["counts"]
    sums[counts < MIN_PHOTOS] = 0
    return unit(sums).astype(np.float32), counts


def settings_for(text, prototypes):
    """Every way tried of scoring a photo, by name: what the pack would hold for it, and its scorer."""
    has = prototypes.any(axis=1, keepdims=True)
    settings = {TEXT: ({"form": "text"}, Scorer(text))}
    for alpha, beta in NEAR:
        settings[f"Near, alpha {alpha:g}, beta {beta:g}"] = ({"form": "near", "alpha": alpha, "beta": beta}, Scorer(text, prototypes, alpha, beta))
    for weight in BLEND:
        blended = as_stored(np.where(has, unit((1 - weight) * text + weight * prototypes), text))
        settings[f"Blend, {weight:.0%} prototype"] = ({"form": "blend", "weight": weight, "vectors": blended}, Scorer(blended))
    return settings


def measure(scorer, photos, groups, logit_scale):
    """How a scorer names [photos], each (species row, vector): for all and for each group, the photos, how often the
    species was first, among the first five, how often the closest species of all was of its group, and the sum of the
    log shares given to the right species."""
    tallies = {}
    for truth, vector in photos:
        scores = scorer(vector)
        near = closest(scores, groups == groups[truth])
        ranked = [row for row, _ in near]
        given = dict(zip(ranked, shares([score for _, score in near], logit_scale)))
        result = np.array([
            1,
            ranked[0] == truth,
            truth in ranked[:5],
            groups[int(np.argmax(scores))] == groups[truth],
            np.log(max(float(given.get(truth, 0)), FLOOR)),
        ], dtype=np.float64)
        for key in ("all", str(groups[truth])):
            tallies[key] = tallies.get(key, 0) + result
    return {
        key: {"photos": int(n), "top1": round(top1 / n, 4), "top5": round(top5 / n, 4), "group": round(group / n, 4), "log_share": round(log / n, 4)}
        for key, (n, top1, top5, group, log) in tallies.items()
    }


def choose(tuned):
    """The best forecast among the settings whose first and in-five shares together are within NEARLY of the most,
    and no worse a forecast than the text's alone. Forecasts closer than a hundredth count as equal, and the setting
    that names more photos is taken."""
    def named(name):
        return tuned[name]["all"]["top1"] + tuned[name]["all"]["top5"]

    honest = [name for name in tuned if tuned[name]["all"]["log_share"] >= tuned[TEXT]["all"]["log_share"]]
    most = max(named(name) for name in honest)
    near = [name for name in honest if named(name) >= most - NEARLY]
    return max(near, key=lambda name: (round(tuned[name]["all"]["log_share"], 2), named(name), name == TEXT))


def table(f, results, rows, key="all"):
    f.write("| | Photos | First | In five | Group | Log share |\n|---|---|---|---|---|---|\n")
    for name in rows:
        r = results[name][key] if key else results[name]
        f.write(f"| {name} | {r['photos']} | {r['top1']:.1%} | {r['top5']:.1%} | {r['group']:.1%} | {r['log_share']:.3f} |\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--vectors", required=True, help="The folder of prototype_vectors.py's shares")
    parser.add_argument("--out", required=True)
    parser.add_argument("--eval-photos", type=int, default=3, help="Photos of each species in each of tuning and evaluation")
    args = parser.parse_args()
    os.makedirs(args.out, exist_ok=True)

    files = base_pack()
    pack = Pack(files)
    assert pack.manifest["pack"] == BASE_PACK and "prototypes" not in pack.manifest
    rows = list(csv.DictReader(open(INDEX), delimiter="\t"))
    assert [row["name"] for row in pack.rows] == [row["name"] for row in rows], "The base pack's species differ from tools/models/species_index.tsv"
    groups = np.array([group_of(row) for row in rows])
    prototypes, counts = prototypes_from(args.vectors, len(rows), pack.dimensions)
    prototypes = as_stored(prototypes)
    made = counts >= MIN_PHOTOS
    print(f"{int(made.sum())} of {len(rows)} species have a prototype, from {int(counts[made].sum())} photos")
    assert made.any(), "No prototypes were made"

    sets = held_out_photos(rows, FUSION_PLAN, args.eval_photos, seed=PACK_VERSION, sets=(EVALUATION, TUNING))
    vectors = {name: [(truth, look_vector(pack, image, groups[truth])) for truth, image in photos] for name, photos in sets.items()}
    print({name: len(photos) for name, photos in vectors.items()})

    settings = settings_for(pack.species, prototypes)
    tuned = {name: measure(scorer, vectors[TUNING], groups, pack.logit_scale) for name, (_, scorer) in settings.items()}
    judged = {name: measure(scorer, vectors[EVALUATION], groups, pack.logit_scale) for name, (_, scorer) in settings.items()}
    chosen = choose(tuned)
    about, _ = settings[chosen]
    print(f"Chosen: {chosen}")
    print(json.dumps({"tuning": {name: result["all"] for name, result in tuned.items()}, "evaluation": judged[chosen]}, indent=1))

    # The pack: the base pack's files, with the prototypes as the chosen setting uses them.
    out = dict(files)
    manifest = dict(pack.manifest)
    del out["manifest.json"]
    manifest["pack"] = PACK_VERSION
    manifest["base"] = {"pack": BASE_PACK, "sha256": BASE_SHA256}
    evaluation = {
        "tried": {name: {TUNING: tuned[name]["all"], EVALUATION: judged[name]["all"]} for name in settings},
        "chosen": chosen,
        "groups": judged[chosen],
        "groups_text": judged[TEXT],
    }
    if about["form"] == "near":
        out["prototypes.f16"] = prototypes.astype("<f2").tobytes()
        manifest["prototypes"] = {"file": "prototypes.f16", "alpha": about["alpha"], "beta": about["beta"]}
    elif about["form"] == "blend":
        out["species.f16"] = about["vectors"].astype("<f2").tobytes()
        manifest["blend"] = {"weight": about["weight"]}
    if about["form"] != "text":
        used = [row for path in sorted(glob.glob(os.path.join(args.vectors, "used-*.tsv"))) for row in csv.DictReader(open(path), delimiter="\t")]
        used = sorted((row for row in used if made[int(row["row"])]), key=lambda row: (int(row["row"]), int(row["photo"])))
        listing = io.StringIO()
        writer = csv.writer(listing, delimiter="\t", lineterminator="\n")
        writer.writerow(["species", "photo", "licence", "photographer", "address"])
        for row in used:
            who = row["observer_name"] or row["login"] or f"iNaturalist observer {row['observer']}"
            writer.writerow([row["name"], row["photo"], "CC0 1.0" if row["licence"] == "CC0" else "CC BY 4.0", who, f"https://www.inaturalist.org/photos/{row['photo']}"])
        packed = io.BytesIO()
        with gzip.GzipFile(fileobj=packed, mode="wb", mtime=0) as f:
            f.write(listing.getvalue().encode())
        out["prototype_photos.tsv.gz"] = packed.getvalue()
        manifest["prototype_photos"] = {
            "file": "prototype_photos.tsv.gz", "photos": len(used), "species": int(made.sum()), "least_photos": MIN_PHOTOS,
            "source": "iNaturalist open data, research-grade observations in Australia, CC0 and CC BY photos",
        }
    manifest["prototypes_evaluation"] = evaluation
    manifest["files"] = {name: sha256_of(data) for name, data in sorted(out.items())}
    out["manifest.json"] = json.dumps(manifest, indent=1).encode()

    path = os.path.join(args.out, f"biocap-pack-{PACK_VERSION}.zip")
    with zipfile.ZipFile(path, "w", compression=zipfile.ZIP_STORED) as z:
        for name, data in out.items():
            z.writestr(zipfile.ZipInfo(name, date_time=(2026, 1, 1, 0, 0, 0)), data)
    with open(path + ".sha256", "w") as f:
        f.write(f"{sha256_of(open(path, 'rb').read())}  {os.path.basename(path)}\n")
    with open(os.path.join(args.out, "manifest.json"), "wb") as f:
        f.write(out["manifest.json"])
    with open(os.path.join(args.out, "evaluation.md"), "w") as f:
        f.write(f"BioCAP pack {PACK_VERSION}: pack {BASE_PACK} with prototypes for {int(made.sum())} of {len(rows)} species, ")
        f.write(f"each the mean of BioCAP's vectors of {MIN_PHOTOS} or more photos, {int(counts[made].sum())} in all ")
        f.write("(research-grade iNaturalist observations in Australia, CC0 and CC BY).\n\n")
        f.write("Photos are research-grade iNaturalist photos from Australia by observers whose photos made no prototype, ")
        f.write("scored as the app scores a photo of one plant or animal, among the species of its group. First and in five ")
        f.write("are how often the right species came first and among the first five; group is how often the closest species ")
        f.write("of all was of the right group; log share is the mean log of the share given to the right species, higher ")
        f.write("being a better forecast.\n\n")
        f.write(f"The pack holds: **{chosen}**.\n\nOn the evaluation photos, by group, the text vectors alone:\n\n")
        order = sorted(judged[TEXT], key=lambda key: (key != "all", -judged[TEXT][key]["photos"]))
        table(f, {key: judged[TEXT][key] for key in order}, order, key=None)
        f.write("\nand what the pack holds:\n\n")
        table(f, {key: judged[chosen][key] for key in order}, order, key=None)
        f.write("\nEvery setting tried, on the tuning photos, which chose:\n\n")
        table(f, tuned, list(settings))
        f.write("\nand on the evaluation photos:\n\n")
        table(f, judged, list(settings))
    print(open(os.path.join(args.out, "evaluation.md")).read())
    print(f"Wrote {path}: {os.path.getsize(path)} bytes")


if __name__ == "__main__":
    main()
