"""Writes pack 4: pack 3 with its encoder's 8-bit layers computed in 8-bit integers (ONNX Runtime's accuracy level 4).

Pack 3's encoder stores its weights in 8 bits (`MatMulNBits`) and multiplies them in floating point. At accuracy
level 4 ONNX Runtime also rounds each block of a layer's input to 8-bit integers, which made a look 2.4 times as fast
on a phone and named the evaluation photos as well (eval_levels.py, accuracy-research.md). The level is an attribute of
each `MatMulNBits` node, so this sets it and changes nothing else: the weights, text vectors, prototypes, flower
vectors and their thresholds are pack 3's.

Then it scores the evaluation and tuning photos (photos.held_for, as add_prototypes.py chose them) with pack 3 and
pack 4 as the app scores a photo, and checks that the flower answers (FlowerVectors, with pack 3's thresholds) of the
plant photos barely change. It refuses to write the pack if pack 4 names clearly fewer photos or forecasts clearly
worse.

It needs no PyTorch: `pip install onnxruntime==1.31.0 onnx numpy pillow`. It runs in GitHub Actions
(.github/workflows/models.yml, build "level4").
"""
import argparse
import csv
import json
import os
import zipfile

import numpy as np
import onnx

from add_prototypes import measure, table
from pack import Pack, halves, look_vector, pinned_pack, sha256_of
from photos import EVALUATION, FUSION_PLAN, TUNING, group_of, held_out_photos

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
INDEX = os.path.join(ROOT, "tools", "models", "species_index.tsv")

PACK_VERSION = 4
BASE_PACK = 3
LEVEL = 4
# Pack 4 is refused if it names this much less of all photos first, or forecasts this much worse (mean log share).
WORST_TOP1_LOSS = 0.005
WORST_LOG_SHARE_LOSS = 0.02


def at_level(encoder, level):
    """The encoder with every MatMulNBits node's accuracy_level set to [level], and how many nodes were set."""
    model = onnx.load_from_string(encoder)
    nodes = [node for node in model.graph.node if node.op_type == "MatMulNBits"]
    assert nodes, "The encoder has no MatMulNBits nodes"
    for node in nodes:
        kept = [a for a in node.attribute if a.name != "accuracy_level"]
        del node.attribute[:]
        node.attribute.extend(kept + [onnx.helper.make_attribute("accuracy_level", level)])
    onnx.checker.check_model(model)
    return model.SerializeToString(), len(nodes)


def flower_answer(vector, flowers, logit_scale, yes_bp, no_bp):
    """What the app says of flowers for a vector (FlowerVectors): 1 in flower, -1 not, 0 unsure."""
    belief = round(10_000 / (1 + np.exp(-logit_scale * float(vector @ (flowers[0] - flowers[1])))))
    return 1 if belief >= yes_bp else -1 if belief <= no_bp else 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", required=True)
    parser.add_argument("--eval-photos", type=int, default=3, help="Photos of each species in each of tuning and evaluation")
    args = parser.parse_args()
    os.makedirs(args.out, exist_ok=True)

    files = pinned_pack()
    old = Pack(files)
    assert old.manifest["pack"] == BASE_PACK, f"gradle.properties pins pack {old.manifest['pack']}, not {BASE_PACK}"
    encoder, nodes = at_level(files["biocap-visual.onnx"], LEVEL)
    out = dict(files)
    out["biocap-visual.onnx"] = encoder
    new = Pack(out)
    print(f"Set accuracy level {LEVEL} on {nodes} MatMulNBits nodes")

    rows = list(csv.DictReader(open(INDEX), delimiter="\t"))
    assert [row["name"] for row in old.rows] == [row["name"] for row in rows], "The pack's species differ from tools/models/species_index.tsv"
    groups = np.array([group_of(row) for row in rows])
    flowers = halves(files["flowers.f16"], old.dimensions) if "flowers.f16" in files else None

    # The same photos add_prototypes.py chose for pack 3, so the numbers compare with its evaluation.md.
    sets = held_out_photos(rows, FUSION_PLAN, args.eval_photos, seed=BASE_PACK, sets=(EVALUATION, TUNING))
    results = {}
    flower_changes = flower_plants = 0
    cosines = []
    for name, photos in sets.items():
        looked = {"old": [], "new": []}
        for truth, image in photos:
            a, b = look_vector(old, image, groups[truth]), look_vector(new, image, groups[truth])
            looked["old"].append((truth, a))
            looked["new"].append((truth, b))
            cosines.append(float(a @ b / np.linalg.norm(a) / np.linalg.norm(b)))
            if flowers is not None and groups[truth] == "plant":
                about = old.manifest["flowers"]
                answers = [flower_answer(v, flowers, old.logit_scale, about["yes_bp"], about["no_bp"]) for v in (a, b)]
                flower_plants += 1
                flower_changes += answers[0] != answers[1]
        results[name] = {
            f"Pack {BASE_PACK}": measure(old.scorer, looked["old"], groups, old.logit_scale),
            f"Pack {PACK_VERSION}": measure(new.scorer, looked["new"], groups, new.logit_scale),
        }
        print(name, json.dumps({k: v["all"] for k, v in results[name].items()}))

    before, after = (results[EVALUATION][f"Pack {v}"]["all"] for v in (BASE_PACK, PACK_VERSION))
    assert after["top1"] >= before["top1"] - WORST_TOP1_LOSS, f"Pack {PACK_VERSION} names fewer photos first: {after} against {before}"
    assert after["log_share"] >= before["log_share"] - WORST_LOG_SHARE_LOSS, f"Pack {PACK_VERSION} forecasts worse: {after} against {before}"

    manifest = json.loads(files["manifest.json"])
    del out["manifest.json"]
    manifest["pack"] = PACK_VERSION
    properties = dict(
        line.strip().split("=", 1) for line in open(os.path.join(ROOT, "gradle.properties")) if "=" in line and not line.startswith("#")
    )
    manifest["base"] = {"pack": BASE_PACK, "sha256": properties["pollenplot.biocap.sha256"]}
    manifest["accuracy_level"] = LEVEL
    manifest["level_evaluation"] = {
        "groups": results[EVALUATION],
        "tuning": results[TUNING],
        "cosine_min": round(min(cosines), 4),
        "cosine_mean": round(float(np.mean(cosines)), 4),
        "flower_answers_changed": flower_changes,
        "flower_plants": flower_plants,
    }
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
        f.write(f"BioCAP pack {PACK_VERSION}: pack {BASE_PACK} with its encoder's {nodes} 8-bit layers computed in 8-bit ")
        f.write(f"integers (ONNX Runtime's accuracy level {LEVEL}), which made a look 2.4 times as fast on a phone. ")
        f.write("Everything else is pack 3's.\n\n")
        f.write("Photos are research-grade iNaturalist photos from Australia by observers whose photos made no prototype, ")
        f.write("the same as pack 3's evaluation, scored as the app scores a photo of one plant or animal, among the species ")
        f.write("of its group.\n\nOn the evaluation photos:\n\n")
        order = sorted(results[EVALUATION][f"Pack {BASE_PACK}"], key=lambda key: (key != "all", key))
        for key in order:
            f.write(f"**{key}**\n\n")
            table(f, results[EVALUATION], list(results[EVALUATION]), key=key)
            f.write("\n")
        f.write("On the tuning photos, all groups:\n\n")
        table(f, results[TUNING], list(results[TUNING]))
        f.write(f"\nThe two packs' vectors of the same photo had a cosine of at least {min(cosines):.4f} ")
        f.write(f"(mean {np.mean(cosines):.4f}). ")
        if flowers is not None:
            f.write(f"The flower answer (in flower, not, or unsure, with pack {BASE_PACK}'s thresholds) changed for ")
            f.write(f"{flower_changes} of {flower_plants} plant photos.\n")
    print(open(os.path.join(args.out, "evaluation.md")).read())
    print(f"Wrote {path}: {os.path.getsize(path)} bytes")


if __name__ == "__main__":
    main()
