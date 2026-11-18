"""Measures whether ONNX Runtime's 8-bit integer arithmetic (accuracy level 4) names species as well as the encoders
the phone runs now.

`MatMulNBits` at accuracy level 4 rounds each block of a layer's input to 8-bit integers before multiplying, which
made BioCAP 2.4 times as fast and BioCLIP 2 2.2 times as fast on a phone (accuracy-research.md). Rounding the
activations of a transformer has cost about seven points of top-1 before (export_biocap.py), so speed alone does not
settle it. This takes the encoders export_candidate.py wrote (a Model pack run with build "benchmark") and scores the
evaluation photos (photos.held_for, the same choice as eval_fusion.py) with each:

- BioCAP: the published pack's own encoder, the same export at level 0, and at level 4, each scored as the pack scores
  a photo (pack.Scorer, text vectors and prototypes).
- BioCLIP 2: the export at level 0 and at level 4, scored by its species' text vectors alone (it has no prototypes
  yet), made here in PyTorch as eval_candidate.py makes them.

A plant is scored with three looks as one (AccurateModels.lookAtOne) and with the centre look alone, because a faster
encoder could let BioCLIP 2 keep three looks. For each pair of the same model it counts the photos each named first
that the other did not, and the cosine between their vectors.

Files written to --out: levels.json and levels.md. It needs PyTorch only for BioCLIP 2's text vectors, so it runs in
GitHub Actions (.github/workflows/models.yml, build "levels").
"""
import argparse
import csv
import json
import os

import numpy as np
import onnxruntime as ort

from pack import Pack, Scorer, pinned_pack
from photos import FUSION_PLAN, evaluation_photos, group_of, looks

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
INDEX = os.path.join(ROOT, "tools", "models", "species_index.tsv")


def session(path):
    options = ort.SessionOptions()
    options.log_severity_level = 3
    return ort.InferenceSession(path, options, providers=["CPUExecutionProvider"])


def bioclip2_text(rows, revision):
    """BioCLIP 2's unit text vector for each species, as eval_candidate.py makes them, stored as the phone would."""
    import open_clip
    import torch

    from export_biocap import prompt

    from huggingface_hub import HfApi

    torch.set_grad_enabled(False)
    # open_clip loads the model's current revision; it must be the one the encoders were exported from.
    current = HfApi().model_info("imageomics/bioclip-2").sha
    assert revision is None or current == revision, f"BioCLIP 2 is now at {current}, but the encoders came from {revision}"
    model, _, _ = open_clip.create_model_and_transforms("hf-hub:imageomics/bioclip-2")
    model.eval()
    tokenizer = open_clip.get_tokenizer("hf-hub:imageomics/bioclip-2")
    texts = [prompt(row["taxonomy"], row["common"]) for row in rows]
    vectors = np.concatenate([
        torch.nn.functional.normalize(model.encode_text(tokenizer(texts[start:start + 128])), dim=-1).numpy() for start in range(0, len(texts), 128)
    ])
    return vectors.astype(np.float16).astype(np.float32)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--encoders", required=True, help="The folder holding export_candidate.py's .onnx files and encoders.json")
    parser.add_argument("--out", required=True)
    parser.add_argument("--eval-photos", type=int, default=3, help="Photos of each species")
    args = parser.parse_args()
    os.makedirs(args.out, exist_ok=True)

    rows = list(csv.DictReader(open(INDEX), delimiter="\t"))
    groups = np.array([group_of(row) for row in rows])
    pack = Pack(pinned_pack())
    assert [row["name"] for row in pack.rows] == [row["name"] for row in rows], "The pack's species differ from tools/models/species_index.tsv"
    about = {r["file"]: r for r in json.load(open(os.path.join(args.encoders, "encoders.json")))} if os.path.exists(os.path.join(args.encoders, "encoders.json")) else {}
    revision = next((r["revision"].split("@")[1] for r in about.values() if r["model"] == "BioCLIP 2"), None)

    def file(name):
        return os.path.join(args.encoders, name)

    # name: (model family, encoder session, scorer)
    bioclip2 = Scorer(bioclip2_text(rows, revision))
    encoders = {
        "BioCAP, the pack's own": ("BioCAP", pack.encoder, pack.scorer),
        "BioCAP, level 0": ("BioCAP", session(file("biocap-visual-8bit.onnx")), pack.scorer),
        "BioCAP, level 4": ("BioCAP", session(file("biocap-visual-8bit-level4.onnx")), pack.scorer),
        "BioCLIP 2, level 0": ("BioCLIP 2", session(file("bioclip2-visual-8bit.onnx")), bioclip2),
        "BioCLIP 2, level 4": ("BioCLIP 2", session(file("bioclip2-visual-8bit-level4.onnx")), bioclip2),
    }

    photos = evaluation_photos(rows, FUSION_PLAN, args.eval_photos, seed=pack.manifest["pack"])
    print(f"{len(photos)} photos")
    # For each encoder and each way of looking, the rank of the right species for each photo (99 when not in five).
    ranks = {name: {"three": [], "one": []} for name in encoders}
    vectors = {name: [] for name in encoders}
    truths = []
    for number, (truth, image) in enumerate(photos):
        group = group_of(rows[truth])
        truths.append(group)
        squares = looks(*image.size)
        boxes = list(squares.values()) if group == "plant" else [squares["centre"]]
        pixels = np.stack([pack.pixels(image.crop(box)) for box in boxes])
        mask = groups == group
        for name, (_, encoder, scorer) in encoders.items():
            looked = np.concatenate([encoder.run(None, {"pixels": pixels[i:i + 1]})[0] for i in range(len(pixels))])
            vectors[name].append(looked[0])
            for way, vector in (("three", looked.mean(axis=0)), ("one", looked[0])):
                scores = np.where(mask, scorer(vector), -np.inf)
                top = np.argsort(-scores, kind="stable")[:5]
                ranks[name][way].append(int(np.where(top == truth)[0][0]) if truth in top else 99)
        if number % 100 == 0:
            print(f"  {number} photos scored")

    truths = np.array(truths)
    plants = truths == "plant"
    results = {"photos": len(photos), "plants": int(plants.sum()), "encoders": {}, "pairs": {}}
    for name in encoders:
        entry = {}
        for way in ("three", "one"):
            r = np.array(ranks[name][way])
            entry[way] = {
                "all_top1": round(float(np.mean(r == 0)), 4), "all_top5": round(float(np.mean(r < 5)), 4),
                "plant_top1": round(float(np.mean(r[plants] == 0)), 4), "plant_top5": round(float(np.mean(r[plants] < 5)), 4),
            }
        results["encoders"][name] = entry
    for model in ("BioCAP", "BioCLIP 2"):
        base, fast = f"{model}, level 0", f"{model}, level 4"
        a, b = np.array(ranks[base]["three"]), np.array(ranks[fast]["three"])
        cosines = np.sum(np.array(vectors[base]) * np.array(vectors[fast]), axis=1)
        results["pairs"][model] = {
            "level4_right_level0_wrong": int(np.sum((b == 0) & (a != 0))),
            "level0_right_level4_wrong": int(np.sum((a == 0) & (b != 0))),
            "level4_in_five_level0_not": int(np.sum((b < 5) & (a >= 5))),
            "level0_in_five_level4_not": int(np.sum((a < 5) & (b >= 5))),
            "cosine_min": round(float(cosines.min()), 4),
            "cosine_mean": round(float(cosines.mean()), 4),
        }

    with open(os.path.join(args.out, "levels.json"), "w") as f:
        json.dump(results, f, indent=1)
    with open(os.path.join(args.out, "levels.md"), "w") as f:
        f.write(f"{len(photos)} evaluation photos, {int(plants.sum())} of them plants. BioCAP is scored with the pack's text vectors ")
        f.write("and prototypes, BioCLIP 2 with its text vectors alone. \"Three looks\" is how the app looks at a plant; an animal ")
        f.write("always gets one look, so the two ways differ only for plants.\n\n")
        f.write("| Encoder | Looks | All, first | All, in five | Plants, first | Plants, in five |\n|---|---|---|---|---|---|\n")
        for name, entry in results["encoders"].items():
            for way, label in (("three", "three for a plant"), ("one", "one")):
                e = entry[way]
                f.write(f"| {name} | {label} | {e['all_top1']:.1%} | {e['all_top5']:.1%} | {e['plant_top1']:.1%} | {e['plant_top5']:.1%} |\n")
        f.write("\nLevel 4 against level 0 of the same model, with the app's looks, on all photos:\n\n")
        f.write("| Model | First only at level 4 | First only at level 0 | In five only at level 4 | In five only at level 0 | Cosine, lowest | Cosine, mean |\n")
        f.write("|---|---|---|---|---|---|---|\n")
        for model, p in results["pairs"].items():
            f.write(f"| {model} | {p['level4_right_level0_wrong']} | {p['level0_right_level4_wrong']} | {p['level4_in_five_level0_not']} | ")
            f.write(f"{p['level0_in_five_level4_not']} | {p['cosine_min']} | {p['cosine_mean']} |\n")
    print(open(os.path.join(args.out, "levels.md")).read())


if __name__ == "__main__":
    main()
