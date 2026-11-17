"""Exports the image encoders the phone benchmark times: BioCAP and BioCLIP 2, each in 8 bits as the pack ships BioCAP.

The phone runs BioCAP's encoder with its weights in 8 bits (`MatMulNBits`, in blocks of 128) and its activations as
floats. Whether BioCLIP 2 (imageomics/bioclip-2, MIT, a ViT-L/14) could take its place depends first on how long one
look takes on a phone, which no runner can say. So this writes, for each model, the same 8-bit form, and a second one
whose `MatMulNBits` nodes ask for `accuracy_level` 4, at which ONNX Runtime multiplies in 8-bit integers on Arm:
faster, but its activations are rounded in blocks, so it must be evaluated before it is used.

For each form it checks the export against the model in PyTorch, as the cosine between their vectors for the fixture
images and for a few evaluation photos, and times one look here. It builds no pack: the phone benchmark
(EncoderBenchmarkActivity in the debug build, through run_benchmark.sh) loads these files and times them. accuracy-research.md says what
the numbers decide.

Files written to --out:
- biocap-visual-8bit.onnx, biocap-visual-8bit-level4.onnx
- bioclip2-visual-8bit.onnx, bioclip2-visual-8bit-level4.onnx
- encoders.json and encoders.md: each file's size, SHA-256, model and revision, cosines and the seconds a look took here.

This needs PyTorch and about 6 GB of disk, so it runs in GitHub Actions (.github/workflows/models.yml, build:
benchmark).
"""
import argparse
import csv
import hashlib
import json
import os
import time

import numpy as np
import onnx
import onnxruntime as ort
import open_clip
import torch
from huggingface_hub import HfApi
from onnxruntime.quantization.matmul_nbits_quantizer import MatMulNBitsQuantizer  # Needs onnx_ir.
from PIL import Image

from eval_candidate import CANDIDATE, INDEX
from export_biocap import FIXTURES, MEAN, REPO, REVISION, STD, Visual, load_model, pixels_of
from photos import SIZE, evaluation_photos, phone_square

# A few plant photos are enough to show that an export matches PyTorch; the evaluation proper comes later.
CHECK_PLAN = {"plant": 24}


def export(model, out, stem):
    """The encoder as ONNX in 32-bit floats, then in 8-bit weights at the default accuracy level and at level 4."""
    fp32_path = os.path.join(out, f"{stem}-visual-fp32.onnx")
    torch.onnx.export(
        Visual(model), torch.zeros(1, 3, SIZE, SIZE), fp32_path, input_names=["pixels"], output_names=["embedding"],
        dynamic_axes={"pixels": {0: "batch"}, "embedding": {0: "batch"}}, opset_version=17, do_constant_folding=True,
        dynamo=False,
    )
    onnx.checker.check_model(fp32_path)
    paths = {}
    for suffix, level in (("", None), ("-level4", 4)):
        path = os.path.join(out, f"{stem}-visual-8bit{suffix}.onnx")
        quantizer = MatMulNBitsQuantizer(onnx.load(fp32_path), bits=8, block_size=128, is_symmetric=True, accuracy_level=level)
        quantizer.process()
        quantizer.model.save_model_to_file(path, use_external_data_format=False)
        paths[path] = level or 0
    os.remove(fp32_path)
    return paths


def sha256(path):
    digest = hashlib.sha256()
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def checked(name, revision, model, paths, images):
    """Each exported form's cosine with PyTorch on [images], and the seconds one look takes here."""
    reference = np.concatenate([
        torch.nn.functional.normalize(model.encode_image(torch.from_numpy(pixels_of(image))), dim=-1).numpy() for image in images
    ])
    options = ort.SessionOptions()
    options.log_severity_level = 3
    options.intra_op_num_threads = 4
    results = []
    for path, level in paths.items():
        session = ort.InferenceSession(path, options, providers=["CPUExecutionProvider"])
        vectors = np.concatenate([session.run(None, {"pixels": pixels_of(image)})[0] for image in images])
        cosines = np.sum(vectors * reference, axis=1)
        seconds = []
        for image in images[:10]:
            started = time.perf_counter()
            session.run(None, {"pixels": pixels_of(image)})
            seconds.append(time.perf_counter() - started)
        results.append({
            "file": os.path.basename(path),
            "model": name,
            "revision": revision,
            "accuracy_level": level,
            "bytes": os.path.getsize(path),
            "sha256": sha256(path),
            "dimensions": int(vectors.shape[1]),
            "input": session.get_inputs()[0].name,
            "cosine_min": round(float(cosines.min()), 4),
            "cosine_mean": round(float(cosines.mean()), 4),
            "seconds_a_look_here": round(float(np.median(seconds)), 3),
        })
        print(results[-1])
    return results


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", required=True)
    args = parser.parse_args()
    os.makedirs(args.out, exist_ok=True)
    torch.set_grad_enabled(False)
    # Without gradients, attention takes a fused fast path that has no ONNX form; the ordinary path exports.
    torch.backends.mha.set_fastpath_enabled(False)

    rows = list(csv.DictReader(open(INDEX), delimiter="\t"))
    images = [Image.open(os.path.join(FIXTURES, name)).convert("RGB") for name in sorted(os.listdir(FIXTURES)) if name.endswith(".png")]
    images += [phone_square(image) for _, image in evaluation_photos(rows, CHECK_PLAN, 1, seed=11)]
    print(f"{len(images)} images to check against PyTorch")

    results = []
    model, _, _ = load_model()
    results += checked("BioCAP", f"{REPO}@{REVISION}", model, export(model, args.out, "biocap"), images)
    del model

    revision = HfApi().model_info(CANDIDATE).sha
    larger, _, preprocess = open_clip.create_model_and_transforms(f"hf-hub:{CANDIDATE}")
    larger.eval()
    # The phone prepares a photo with BioCAP's normalisation; the candidate must have been trained with the same.
    normalise = next(t for t in preprocess.transforms if hasattr(t, "mean"))
    assert np.allclose(normalise.mean, MEAN, atol=1e-4) and np.allclose(normalise.std, STD, atol=1e-4), "The candidate normalises photos differently"
    results += checked("BioCLIP 2", f"{CANDIDATE}@{revision}", larger, export(larger, args.out, "bioclip2"), images)

    with open(os.path.join(args.out, "encoders.json"), "w") as f:
        json.dump(results, f, indent=1)
    with open(os.path.join(args.out, "encoders.md"), "w") as f:
        f.write(f"Each form's vectors against PyTorch's on {len(images)} images (3 fixtures and plant photos), and one look here with four threads.\n\n")
        f.write("| File | Model | Accuracy level | MB | Dimensions | Cosine, lowest | Cosine, mean | Seconds a look, here |\n|---|---|---|---|---|---|---|---|\n")
        for r in results:
            f.write(f"| {r['file']} | {r['model']} | {r['accuracy_level']} | {r['bytes'] / 1e6:.0f} | {r['dimensions']} | {r['cosine_min']} | {r['cosine_mean']} | {r['seconds_a_look_here']} |\n")
    print(open(os.path.join(args.out, "encoders.md")).read())
    # An export that does not match PyTorch would time a different model. Level 4 rounds its activations, so it is
    # only reported here, and judged by the full evaluation if its speed makes it worth one.
    for r in results:
        if r["accuracy_level"] == 0:
            assert r["cosine_min"] >= 0.99, f"{r['file']} differs from PyTorch: cosine {r['cosine_min']}"


if __name__ == "__main__":
    main()
