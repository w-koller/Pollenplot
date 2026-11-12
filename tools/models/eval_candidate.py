"""Measures a larger model against BioCAP, to say whether a pack built on it would be worth its size.

The candidate is BioCLIP 2 (imageomics/bioclip-2, MIT), a ViT-L/14 where BioCAP is a ViT-B/16. Both are run in
PyTorch as published, on the evaluation photos (photos.held_for), each photo prepared and looked at as the phone
does (three looks as one for a plant, one for an animal) and named among the species of its group by the species' text
vectors. It also times one look on this machine's processor, for the ratio between the models: a phone is slower than
this machine, but by about the same factor for both.

It builds nothing. A candidate that names clearly more photos would next be exported and made smaller as
export_biocap.py does for BioCAP, and timed on a phone.

This needs PyTorch, so it runs in GitHub Actions (.github/workflows/models.yml).
"""
import argparse
import csv
import json
import os
import time

import numpy as np
import open_clip
import torch
from huggingface_hub import HfApi

from export_biocap import MEAN, REPO, REVISION, STD, load_model, prompt
from photos import FUSION_PLAN, evaluation_photos, group_of, looks, phone_square

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
INDEX = os.path.join(ROOT, "tools", "models", "species_index.tsv")
CANDIDATE = "imageomics/bioclip-2"


def pixels_of(image):
    array = (np.asarray(phone_square(image), dtype=np.float32) / 255.0 - np.array(MEAN, dtype=np.float32)) / np.array(STD, dtype=np.float32)
    return array.transpose(2, 0, 1)


def measured(name, model, tokenizer, rows, groups, photos):
    """How a model names the photos, by group, with its size and the seconds one look takes."""
    texts = [prompt(row["taxonomy"], row["common"]) for row in rows]
    species = np.concatenate([
        torch.nn.functional.normalize(model.encode_text(tokenizer(texts[start:start + 128])), dim=-1).numpy() for start in range(0, len(texts), 128)
    ]).astype(np.float16).astype(np.float32)
    tallies = {}
    seconds = []
    for truth, image in photos:
        squares = looks(*image.size)
        boxes = list(squares.values()) if groups[truth] == "plant" else [squares["centre"]]
        batch = torch.from_numpy(np.stack([pixels_of(image.crop(box)) for box in boxes]))
        started = time.perf_counter()
        vectors = torch.nn.functional.normalize(model.encode_image(batch), dim=-1).numpy()
        seconds.append((time.perf_counter() - started) / len(boxes))
        scores = np.where(groups == groups[truth], species @ vectors.mean(axis=0), -np.inf)
        ranked = np.argsort(-scores)[:5]
        result = np.array([1, ranked[0] == truth, truth in ranked])
        for key in ("all", str(groups[truth])):
            tallies[key] = tallies.get(key, 0) + result
    visual = sum(p.numel() for p in model.visual.parameters())
    return {
        "model": name,
        "image_encoder_parameters": visual,
        "megabytes_at_8_bits": round(visual / 1e6),
        "seconds_a_look": round(float(np.median(seconds)), 3),
        "groups": {key: {"photos": int(n), "top1": round(top1 / n, 4), "top5": round(top5 / n, 4)} for key, (n, top1, top5) in tallies.items()},
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", required=True)
    parser.add_argument("--eval-photos", type=int, default=3)
    args = parser.parse_args()
    os.makedirs(args.out, exist_ok=True)
    torch.set_grad_enabled(False)
    rows = list(csv.DictReader(open(INDEX), delimiter="\t"))
    groups = np.array([group_of(row) for row in rows])
    photos = evaluation_photos(rows, FUSION_PLAN, args.eval_photos, seed=3)
    print(f"{len(photos)} photos")

    model, _, tokenizer = load_model()
    results = [measured(f"BioCAP ({REPO}@{REVISION[:7]})", model, tokenizer, rows, groups, photos)]
    del model
    revision = HfApi().model_info(CANDIDATE).sha
    larger, _, preprocess = open_clip.create_model_and_transforms(f"hf-hub:{CANDIDATE}")
    larger.eval()
    # The phone's preparation uses BioCAP's normalisation; the candidate must have been trained with the same.
    normalise = next(t for t in preprocess.transforms if hasattr(t, "mean"))
    assert np.allclose(normalise.mean, MEAN, atol=1e-4) and np.allclose(normalise.std, STD, atol=1e-4), "The candidate normalises photos differently"
    results.append(measured(f"BioCLIP 2 ({CANDIDATE}@{revision[:7]})", larger, open_clip.get_tokenizer(f"hf-hub:{CANDIDATE}"), rows, groups, photos))

    with open(os.path.join(args.out, "candidate.json"), "w") as f:
        json.dump(results, f, indent=1)
    with open(os.path.join(args.out, "candidate.md"), "w") as f:
        f.write(f"{len(photos)} evaluation photos, each model in PyTorch as published, with the phone's preparation and looks, ")
        f.write("named among the species of its group by the species' text vectors.\n\n")
        f.write("| Model | Image encoder | Seconds a look, here | All, first | All, in five | Plants, first | Plants, in five |\n|---|---|---|---|---|---|---|\n")
        for r in results:
            everything, plants = r["groups"]["all"], r["groups"]["plant"]
            f.write(f"| {r['model']} | about {r['megabytes_at_8_bits']} MB at 8 bits | {r['seconds_a_look']} | {everything['top1']:.1%} | {everything['top5']:.1%} | {plants['top1']:.1%} | {plants['top5']:.1%} |\n")
        f.write("\nBy group, first and in five:\n\n| Group | Photos | " + " | ".join(r["model"].split(" (")[0] for r in results) + " |\n|---|---|" + "---|" * len(results) + "\n")
        for key in sorted(results[0]["groups"], key=lambda k: (k != "all", -results[0]["groups"][k]["photos"])):
            cells = " | ".join(f"{r['groups'][key]['top1']:.1%}, {r['groups'][key]['top5']:.1%}" for r in results)
            f.write(f"| {key} | {results[0]['groups'][key]['photos']} | {cells} |\n")
    print(open(os.path.join(args.out, "candidate.md")).read())


if __name__ == "__main__":
    main()
