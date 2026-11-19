"""Builds the BioCLIP 2 identification pack (imageomics/bioclip-2, MIT), offered beside BioCAP's for high-end phones.

BioCLIP 2 is a ViT-L/14, about four and a half times BioCAP's work for each look. With its text vectors alone it
named more plants than BioCAP's pack with prototypes (eval_levels.py), and at ONNX Runtime's accuracy level 4 a look
took 305 ms on a Snapdragon SM8850 phone (accuracy-research.md). The pack holds what BioCAP's does, in the same form,
so the app reads either:

- bioclip2-visual.onnx: the image encoder with 8-bit weights in blocks of 128 (`MatMulNBits`), computed at accuracy
  level 4, the form those measurements chose. It takes the same 224 x 224 input as BioCAP, normalised the same way.
- species.f16 and species.tsv: one unit text vector for each species in tools/models/species_index.tsv, with the
  prompt BioCAP's pack uses, as little-endian 16-bit floats.
- flowers.f16 and its thresholds in the manifest, chosen on iNaturalist photos annotated for flowers, as
  export_biocap.py chooses BioCAP's.
- manifest.json, with "encoder" naming the encoder's file.

It has no prototypes yet: making them means encoding the 70,780 prototype photos again with a model four times as slow.

The evaluation scores pack 4's own evaluation photos (photos.held_for, the photos level4_pack.py used) with this pack
and with pack 4, each as the app scores a photo: three looks as one for a plant, one look for an animal, among the
species of its group. It also checks the export against PyTorch on the fixtures, and writes reference.json, the
vectors for the fixtures that ml's BioClip2 test compares with the phone's preparation.

This needs PyTorch, so it runs in GitHub Actions (.github/workflows/models.yml, build "bioclip2").
"""
import argparse
import csv
import json
import os
import zipfile

import numpy as np
import onnx
import onnxruntime as ort
import open_clip
import torch
from huggingface_hub import HfApi
from onnxruntime.quantization.matmul_nbits_quantizer import MatMulNBitsQuantizer  # Needs onnx_ir.
from PIL import Image

from add_prototypes import measure, table
from export_biocap import (
    FIXTURES, FLOWER_PROMPTS_NO, FLOWER_PROMPTS_YES, MEAN, PHENOLOGY_FLOWERS, PHENOLOGY_NEITHER, STD, Visual,
    flower_band, flower_bp, flower_photos, pixels_of, prompt, sha256,
)
from pack import Pack, look_vector, pinned_pack, sha256_of
from photos import EVALUATION, FUSION_PLAN, SIZE, TUNING, group_of, held_out_photos, phone_square

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
INDEX = os.path.join(ROOT, "tools", "models", "species_index.tsv")

REPO = "imageomics/bioclip-2"
# The revision the phone benchmark and eval_levels.py measured (Model pack runs 44086488819 and 44100095336).
REVISION = "2957b322090f9cb17ae72c71981c7218a28d81e0"
PACK_VERSION = 1
ENCODER = "bioclip2-visual.onnx"
LEVEL = 4
# The BioCAP pack it is measured against, and whose evaluation photos it uses.
BIOCAP_PACK = 4


def load_model():
    # open_clip loads a hub model's current revision, so it must still be the one measured.
    current = HfApi().model_info(REPO).sha
    assert current == REVISION, f"{REPO} is now at {current}, not {REVISION}; please measure the new revision first"
    model, _, preprocess = open_clip.create_model_and_transforms(f"hf-hub:{REPO}")
    model.eval()
    normalise = next(t for t in preprocess.transforms if hasattr(t, "mean"))
    assert np.allclose(normalise.mean, MEAN, atol=1e-4) and np.allclose(normalise.std, STD, atol=1e-4), "BioCLIP 2 normalises photos differently"
    return model, open_clip.get_tokenizer(f"hf-hub:{REPO}")


def export(model, out):
    """The image encoder in 8-bit weights at accuracy level 4, and its cosines with PyTorch on the fixtures."""
    fp32_path = os.path.join(out, "bioclip2-visual-fp32.onnx")
    torch.onnx.export(
        Visual(model), torch.zeros(1, 3, SIZE, SIZE), fp32_path, input_names=["pixels"], output_names=["embedding"],
        dynamic_axes={"pixels": {0: "batch"}, "embedding": {0: "batch"}}, opset_version=17, do_constant_folding=True,
        dynamo=False,
    )
    onnx.checker.check_model(fp32_path)
    path = os.path.join(out, ENCODER)
    quantizer = MatMulNBitsQuantizer(onnx.load(fp32_path), bits=8, block_size=128, is_symmetric=True, accuracy_level=LEVEL)
    quantizer.process()
    quantizer.model.save_model_to_file(path, use_external_data_format=False)
    os.remove(fp32_path)
    return path


def text_vectors(model, tokenizer, texts):
    vectors = []
    for start in range(0, len(texts), 128):
        vectors.append(torch.nn.functional.normalize(model.encode_text(tokenizer(texts[start:start + 128])), dim=-1).numpy())
    return np.concatenate(vectors).astype(np.float32)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", required=True)
    parser.add_argument("--eval-photos", type=int, default=3, help="Photos of each species in the evaluation")
    parser.add_argument("--flower-photos", type=int, default=300, help="Photos in flower, and as many not, in the flower evaluation")
    args = parser.parse_args()
    os.makedirs(args.out, exist_ok=True)
    torch.set_grad_enabled(False)
    # Without gradients, attention takes a fused fast path that has no ONNX form; the ordinary path exports.
    torch.backends.mha.set_fastpath_enabled(False)

    model, tokenizer = load_model()
    rows = list(csv.DictReader(open(INDEX), delimiter="\t"))
    print(f"{len(rows)} species in the index")
    encoder_path = export(model, args.out)
    options = ort.SessionOptions()
    options.log_severity_level = 3
    phone = ort.InferenceSession(encoder_path, options, providers=["CPUExecutionProvider"])

    def embed(image):
        return phone.run(None, {"pixels": pixels_of(image)})[0][0]

    species = text_vectors(model, tokenizer, [prompt(row["taxonomy"], row["common"]) for row in rows])
    species.astype("<f2").tofile(os.path.join(args.out, "species.f16"))
    species16 = species.astype(np.float16).astype(np.float32)
    with open(os.path.join(args.out, "species.tsv"), "w", newline="") as f:
        writer = csv.writer(f, delimiter="\t", lineterminator="\n")
        writer.writerow(["name", "common", "aliases", "group"])
        for row in rows:
            writer.writerow([row["name"], row["common"], row["aliases"], group_of(row)])

    # The export against PyTorch, and the reference vectors for the app's tests, on the 224 x 224 fixtures.
    references, cosines = [], []
    for name in sorted(os.listdir(FIXTURES)):
        if name.endswith(".png"):
            image = Image.open(os.path.join(FIXTURES, name)).convert("RGB")
            vector = embed(image)
            torch_vector = torch.nn.functional.normalize(model.encode_image(torch.from_numpy(pixels_of(image))), dim=-1).numpy()[0]
            cosines.append(float(vector @ torch_vector))
            references.append({"file": name, "vector": [round(float(x), 6) for x in vector], "best": rows[int(np.argmax(species16 @ vector))]["name"]})
    print(f"Cosine with PyTorch on the fixtures: {cosines}")
    assert min(cosines) >= 0.99, f"The export differs from PyTorch: {cosines}"
    with open(os.path.join(args.out, "reference.json"), "w") as f:
        json.dump(references, f, indent=1)

    # The flower vectors and the band in which the model answers, as export_biocap.py chooses BioCAP's.
    logit_scale = float(model.logit_scale.exp())
    flowers = np.stack([
        torch.nn.functional.normalize(torch.from_numpy(text_vectors(model, tokenizer, prompts)).mean(dim=0), dim=-1).numpy()
        for prompts in (FLOWER_PROMPTS_YES, FLOWER_PROMPTS_NO)
    ]).astype(np.float32)
    flowers.astype("<f2").tofile(os.path.join(args.out, "flowers.f16"))
    flowers16 = flowers.astype(np.float16).astype(np.float32)
    beliefs, truths = [], []
    for value, truth in ((PHENOLOGY_FLOWERS, True), (PHENOLOGY_NEITHER, False)):
        # The same photos BioCAP's thresholds were chosen on (export_biocap.py's seed, its pack 2).
        for image in flower_photos(value, args.flower_photos, seed=2):
            beliefs.append(flower_bp(embed(phone_square(image)), flowers16, logit_scale))
            truths.append(truth)
    yes_bp, no_bp, flower_accuracy, answered = flower_band(beliefs, truths)
    even = [(b >= 5_000) == t for b, t in zip(beliefs, truths)]
    flower_results = {
        "photos_in_flower": truths.count(True),
        "photos_not_in_flower": truths.count(False),
        "accuracy_at_even": round(sum(even) / max(1, len(even)), 4),
        "accuracy_answered": flower_accuracy,
        "answered": answered,
    }
    print(json.dumps({"flowers": flower_results, "yes_bp": yes_bp, "no_bp": no_bp}, indent=1))
    del model

    files = [ENCODER, "species.f16", "species.tsv", "flowers.f16"]
    manifest = {
        "format": 1,
        "pack": PACK_VERSION,
        "model": f"{REPO}@{REVISION}",
        "licence": "MIT",
        "encoder": ENCODER,
        "accuracy_level": LEVEL,
        "image_size": SIZE,
        "mean": MEAN,
        "std": STD,
        "preparation": "the middle square, halved while at least 448 across, then scaled to 224 x 224 with bilinear filtering",
        "input": "pixels, float32, [batch, 3, 224, 224]",
        "output": f"embedding, float32, [batch, {species.shape[1]}], unit length",
        "embedding_dim": int(species.shape[1]),
        "species": len(rows),
        "prompt": "a photo of {taxonomy} with common name {common}.",
        "logit_scale": logit_scale,
        "flowers": {"prompts_yes": FLOWER_PROMPTS_YES, "prompts_no": FLOWER_PROMPTS_NO, "yes_bp": yes_bp, "no_bp": no_bp, "evaluation": flower_results},
        "export_cosine_min": round(min(cosines), 4),
        "versions": {"torch": torch.__version__, "open_clip": open_clip.__version__, "onnx": onnx.__version__, "onnxruntime": ort.__version__},
    }

    # Pack 4's evaluation photos, scored with both packs as the app scores a photo.
    contents = {name: open(os.path.join(args.out, name), "rb").read() for name in files}
    biocap = Pack(pinned_pack())
    assert biocap.manifest["pack"] == BIOCAP_PACK, f"gradle.properties pins pack {biocap.manifest['pack']}, not {BIOCAP_PACK}"
    bioclip = Pack({**contents, "manifest.json": json.dumps(manifest).encode()})
    groups = np.array([group_of(row) for row in rows])
    # held_out_photos as level4_pack.py called it (pack 3's seed, both sets), so the photos are the same.
    photos = held_out_photos(rows, FUSION_PLAN, args.eval_photos, seed=3, sets=(EVALUATION, TUNING))[EVALUATION]
    looked = {"BioCAP pack 4": [], "BioCLIP 2": []}
    for truth, image in photos:
        looked["BioCAP pack 4"].append((truth, look_vector(biocap, image, groups[truth])))
        looked["BioCLIP 2"].append((truth, look_vector(bioclip, image, groups[truth])))
    results = {
        "BioCAP pack 4": measure(biocap.scorer, looked["BioCAP pack 4"], groups, biocap.logit_scale),
        "BioCLIP 2": measure(bioclip.scorer, looked["BioCLIP 2"], groups, bioclip.logit_scale),
    }
    print(json.dumps({k: v["all"] for k, v in results.items()}))
    manifest["evaluation"] = results
    manifest["files"] = {name: sha256_of(data) for name, data in sorted(contents.items())}
    with open(os.path.join(args.out, "manifest.json"), "w") as f:
        json.dump(manifest, f, indent=1)

    pack = os.path.join(args.out, f"bioclip2-pack-{PACK_VERSION}.zip")
    with zipfile.ZipFile(pack, "w", compression=zipfile.ZIP_STORED) as z:
        for name in files + ["manifest.json"]:
            z.write(os.path.join(args.out, name), name)
    with open(pack + ".sha256", "w") as f:
        f.write(f"{sha256(pack)}  {os.path.basename(pack)}\n")
    with open(os.path.join(args.out, "evaluation.md"), "w") as f:
        f.write(f"BioCLIP 2 pack {PACK_VERSION}: {len(rows)} species, model {REPO}@{REVISION[:7]}, the image encoder in 8-bit ")
        f.write(f"weights at ONNX Runtime's accuracy level {LEVEL}, text vectors alone (no prototypes yet).\n\n")
        f.write("Photos are the evaluation photos of BioCAP's pack 4 (research-grade iNaturalist photos from Australia by observers ")
        f.write("whose photos made no prototype), scored as the app scores a photo of one plant or animal, among the species of its ")
        f.write("group. Group is how often the closest species of all was of the right group; log share is the mean log of the share ")
        f.write("given to the right species, higher being a better forecast.\n\n")
        order = sorted(results["BioCLIP 2"], key=lambda key: (key != "all", key != "plant", key))
        for key in order:
            f.write(f"**{key}**\n\n")
            table(f, results, list(results), key=key)
            f.write("\n")
        f.write(f"The export's vectors of the fixtures had a cosine of at least {min(cosines):.4f} with PyTorch's.\n\n")
        f.write(f"In flower: {flower_results['photos_in_flower']} plants marked as in flower and {flower_results['photos_not_in_flower']} ")
        f.write(f"marked as having neither flowers nor fruit. Split at an even belief, {flower_results['accuracy_at_even']:.1%} were right. ")
        if flower_accuracy is None:
            f.write("No band was right often enough, so the model does not answer and the app goes by season and colour.\n")
        else:
            f.write(f"It answers yes from {yes_bp} and no up to {no_bp} basis points: {answered:.1%} of the photos, {flower_accuracy:.1%} of them right.\n")
    print(open(os.path.join(args.out, "evaluation.md")).read())
    print(f"Wrote {pack}: {os.path.getsize(pack)} bytes")


if __name__ == "__main__":
    main()
