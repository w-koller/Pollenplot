"""Records what each of the phone's models says about a set of Australian photos, for the fusion evaluation.

`CandidateFusion` combines the quick AIY models with BioCAP, and its weights were chosen by hand. This runs the models
the app ships (the AIY classifiers the build downloads, and the published accurate pack) over research-grade
iNaturalist photos of known species, by observers whose photos made none of the pack's prototypes
(photos.held_for), the way the phone looks at a photo of one plant or one animal, and writes each model's answers to
eval_outputs.json.gz. `FusionEvalTest` in `:domain` reads that file, fuses the answers as the app
does and fails when top 1 or top 5 falls below its baseline, so a change to the fusion is measured, not guessed.

The file holds names and numbers only, no photos. For each photo:
- `species` and `group`: what it shows, as the species index names it, and `also`, the index's other names for it.
- `aiy`: the quick model of its group, if it has one, on the square in the middle (`centre`) and on the whole photo
  (`whole`): up to ten labels with their scores in basis points, and each look's score for "none of these".
- `biocap`: the 20 closest species of its group with their scores as the pack scores them (pack.Scorer: cosine
  similarity to the text vector, and what a prototype adds from pack 3), for three looks: `centre` (the square
  around the middle box), `middle` (the whole middle square) and `tight` (the middle 40%, where a small animal or one
  flower is), and for `all`, the mean of the three looks' vectors, which is how the app looks at a photo of one plant
  (AccurateModels.lookAtOne). An animal gets the `centre` look alone.
- `groups`: the groups of the 20 closest species of any group for the `centre` look, with their similarities.
Names are numbers into `names`, to keep the file small.

Photos here are iNaturalist's medium size, about 500 pixels across, smaller than a phone's. The models look at
224 x 224, so little is lost, and it matches the pack's own evaluation (export_biocap.py).

It needs no PyTorch: `pip install onnxruntime==1.31.0 numpy pillow ai-edge-litert`. It runs in GitHub Actions
(.github/workflows/eval.yml), because the models are not kept on a development machine.
"""
import argparse
import csv
import gzip
import io
import json
import os
import re
import tarfile

import numpy as np
from PIL import Image

from pack import Pack, closest, pinned_pack, unzip, verified
from photos import FULL, FUSION_PLAN, SIZE, centre_square, evaluation_photos, group_of, looks

try:
    from ai_edge_litert.interpreter import Interpreter
except ImportError:  # The older name of the same runtime.
    from tflite_runtime.interpreter import Interpreter

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
INDEX = os.path.join(ROOT, "tools", "models", "species_index.tsv")
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets", "ml")

# The groups with a quick model, as AiyModel.of in the app.
AIY_KIND = {"plant": "plants", "bird": "birds", "insect": "insects"}

AIY_RESULTS = 10


def aiy_models():
    """The AIY classifiers the build downloads, by the URLs and checksums in app/build.gradle.kts."""
    gradle = open(os.path.join(ROOT, "app", "build.gradle.kts")).read()
    models = {}
    for kind in AIY_KIND.values():
        block = re.search(rf'"downloadAiy{kind.capitalize()}"\) \{{(.*?)\n\}}', gradle, re.S).group(1)
        url = re.search(r'url\.set\("([^"]+)"\)', block).group(1)
        checksum = re.search(r'sha256\.set\("([0-9a-f]{64})"\)', block).group(1)
        with tarfile.open(fileobj=io.BytesIO(verified(url, checksum)), mode="r:gz") as archive:
            model = archive.extractfile(next(m for m in archive.getmembers() if m.name.endswith("3.tflite"))).read()
        labels = [line.rstrip("\n") for line in open(os.path.join(ASSETS, f"aiy_{kind}_v1_labels.txt"))]
        interpreter = Interpreter(model_content=model)
        interpreter.allocate_tensors()
        models[kind] = (interpreter, labels)
    return models


def aiy_look(model, image, box):
    """One of the quick models on a part of a photo, as AiyClassifier.classify: the part scaled to 224 x 224 whatever
    its shape, the labels with their scores in basis points of 256ths, and the last class as the background."""
    interpreter, labels = model
    pixels = np.asarray(image.crop(box).resize((SIZE, SIZE), Image.BILINEAR), dtype=np.uint8)[None, ...]
    interpreter.set_tensor(interpreter.get_input_details()[0]["index"], pixels)
    interpreter.invoke()
    scores = interpreter.get_tensor(interpreter.get_output_details()[0]["index"])[0].astype(np.int64) * FULL // 256
    background = len(labels) - 1
    order = [int(i) for i in np.argsort(-scores, kind="stable") if i != background and scores[i] > 0][:AIY_RESULTS]
    return [(labels[i], int(scores[i])) for i in order], int(scores[background])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", required=True)
    parser.add_argument("--pack", help="A pack's zip to record in place of the published one, to judge a pack before it is published")
    parser.add_argument("--eval-photos", type=int, default=3, help="Photos of each species")
    args = parser.parse_args()
    os.makedirs(args.out, exist_ok=True)

    rows = list(csv.DictReader(open(INDEX), delimiter="\t"))
    pack = Pack(unzip(open(args.pack, "rb").read()) if args.pack else pinned_pack())
    manifest = pack.manifest
    assert [row["name"] for row in pack.rows] == [row["name"] for row in rows], "The pack's species differ from tools/models/species_index.tsv"
    groups = np.array([group_of(row) for row in rows])
    quick = aiy_models()

    names = {}

    def number(name):
        return names.setdefault(name, len(names))

    def near(vector, group):
        """The closest species of a group, or of all, with their scores as the pack scores them."""
        return [(i, round(score, 5)) for i, score in closest(pack.scorer(vector), None if group is None else groups == group)]

    photos = evaluation_photos(rows, FUSION_PLAN, args.eval_photos, seed=manifest["pack"])
    print(f"{len(photos)} photos")
    records = []
    tally = {}
    for truth, image in photos:
        row = rows[truth]
        group = group_of(row)
        width, height = image.size
        squares = looks(width, height)
        vectors = dict(zip(squares, pack.encode([image.crop(box) for box in squares.values()])))
        # The three looks as one: the mean of their vectors, which is scored as one look's vector is.
        vectors["all"] = np.mean([vectors[look] for look in squares], axis=0)
        record = {
            "species": number(row["name"]),
            "also": [number(alias) for alias in row["aliases"].split("|") if alias],
            "group": group,
            "biocap": {look: [[number(rows[i]["name"]), s] for i, s in near(vector, group)] for look, vector in vectors.items()},
            "groups": [[str(groups[i]), s] for i, s in near(vectors["centre"], None)],
        }
        kind = AIY_KIND.get(group)
        if kind:
            centre, centre_background = aiy_look(quick[kind], image, centre_square(width, height))
            whole, whole_background = aiy_look(quick[kind], image, (0, 0, width, height))
            record["aiy"] = {
                "centre": [[number(label), score] for label, score in centre],
                "centreBackground": centre_background,
                "whole": [[number(label), score] for label, score in whole],
                "wholeBackground": whole_background,
            }
        records.append(record)
        # A first reading, BioCAP alone on the look the app uses, printed so a run can be checked at a glance.
        ranked = [i for i, _ in record["biocap"]["all" if group == "plant" else "centre"]]
        counts = tally.setdefault(group, [0, 0, 0])
        counts[0] += 1
        counts[1] += int(ranked[0] == record["species"])
        counts[2] += int(record["species"] in ranked[:5])

    by_name = sorted(names, key=names.get)
    output = {
        "pack": manifest["pack"],
        "model": manifest["model"],
        "logitScale": manifest["logit_scale"],
        "plan": FUSION_PLAN,
        "photosPerSpecies": args.eval_photos,
        "names": by_name,
        "photos": records,
    }
    path = os.path.join(args.out, "eval_outputs.json.gz")
    # No timestamp in the gzip header, so the same answers give the same file.
    with open(path, "wb") as raw, gzip.GzipFile(fileobj=raw, mode="wb", mtime=0) as f:
        f.write(json.dumps(output, separators=(",", ":")).encode())
    with open(os.path.join(args.out, "eval_outputs.md"), "w") as f:
        f.write(f"{len(records)} photos, pack {manifest['pack']}. BioCAP alone, on the look the app gives a photo of one plant or animal:\n\n")
        f.write("| Group | Photos | Top 1 | Top 5 |\n|---|---|---|---|\n")
        for group, (n, top1, top5) in sorted(tally.items(), key=lambda item: -item[1][0]):
            f.write(f"| {group} | {n} | {top1 / n:.1%} | {top5 / n:.1%} |\n")
        total = [sum(counts[i] for counts in tally.values()) for i in range(3)]
        f.write(f"| all | {total[0]} | {total[1] / max(1, total[0]):.1%} | {total[2] / max(1, total[0]):.1%} |\n")
    print(open(os.path.join(args.out, "eval_outputs.md")).read())
    print(f"Wrote {path}: {os.path.getsize(path)} bytes")


if __name__ == "__main__":
    main()
