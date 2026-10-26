"""Builds the accurate identification pack from BioCAP (imageomics/biocap, MIT).

The pack holds:
- biocap-visual.onnx: BioCAP's image encoder, quantised to 8 bits, which turns a 224 x 224 photo into a unit vector.
- species.f16: one unit vector for each species in tools/models/species_index.tsv, from BioCAP's text encoder, as
  little-endian 16-bit floats, one row of 512 per species, in the order of species.tsv.
- species.tsv: that index, so the app can name what a vector matches.
- manifest.json: how to prepare a photo, the prompt used, each file's SHA-256 and the evaluation results.

It also writes reference.json: the vectors for the 224 x 224 test images in tools/models/fixtures/, which the app's
tests compare with its own preprocessing and model run.

This needs PyTorch and about 3 GB of disk, so it runs in GitHub Actions (.github/workflows/models.yml).
"""
import argparse
import csv
import hashlib
import io
import json
import os
import random
import time
import urllib.parse
import urllib.request
import zipfile

import numpy as np
import onnx
import onnxruntime as ort
import open_clip
import torch
from huggingface_hub import hf_hub_download
from onnxruntime.quantization import QuantType, quantize_dynamic
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
INDEX = os.path.join(ROOT, "tools", "models", "species_index.tsv")
FIXTURES = os.path.join(ROOT, "tools", "models", "fixtures")

REPO = "imageomics/biocap"
REVISION = "4e544d1d08781b6afee77bd7c1a85d04bd567d1b"
PACK_VERSION = 1
SIZE = 224
MEAN = [0.48145466, 0.4578275, 0.40821073]
STD = [0.26862954, 0.26130258, 0.27577711]
USER_AGENT = "Pollenplot model pack builder (https://github.com/w-koller/Pollenplot)"


def prompt(taxonomy, common):
    """BioCLIP's taxonomic and common name text, which BioCAP was trained with."""
    return f"a photo of {taxonomy} with common name {common}." if common else f"a photo of {taxonomy}."


def load_model():
    weights = hf_hub_download(REPO, "open_clip_model.safetensors", revision=REVISION)
    model, _, preprocess = open_clip.create_model_and_transforms("ViT-B-16", pretrained=weights)
    model.eval()
    return model, preprocess, open_clip.get_tokenizer("ViT-B-16")


class Visual(torch.nn.Module):
    """The image encoder with its vector made unit length, as the app compares unit vectors."""

    def __init__(self, model):
        super().__init__()
        self.model = model

    def forward(self, pixels):
        return torch.nn.functional.normalize(self.model.encode_image(pixels), dim=-1)


def pixels_of(image):
    """A 224 x 224 RGB image as the model's input: channels first, scaled to 0..1 and normalised."""
    array = np.asarray(image.convert("RGB"), dtype=np.float32) / 255.0
    array = (array - np.array(MEAN, dtype=np.float32)) / np.array(STD, dtype=np.float32)
    return array.transpose(2, 0, 1)[None, ...]


def centre_crop(image, resample):
    """The phone's preparation: the shorter side scaled to 224, then the middle 224 x 224."""
    width, height = image.size
    scale = SIZE / min(width, height)
    resized = image.convert("RGB").resize((max(SIZE, round(width * scale)), max(SIZE, round(height * scale))), resample)
    left = (resized.width - SIZE) // 2
    top = (resized.height - SIZE) // 2
    return resized.crop((left, top, left + SIZE, top + SIZE))


def sha256(path):
    digest = hashlib.sha256()
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def fetch(url, retries=5):
    for attempt in range(retries):
        try:
            request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
            with urllib.request.urlopen(request, timeout=60) as response:
                return response.read()
        except Exception:
            if attempt == retries - 1:
                raise
            time.sleep(3 * (attempt + 1))


def evaluation_photos(rows, species_count, per_species, seed):
    """Research-grade iNaturalist photos with open licences, for species chosen from the index's most observed."""
    random.seed(seed)
    observed = [i for i, row in enumerate(rows) if int(row["observations"]) > 0]
    chosen = random.sample(observed[: species_count * 3], species_count)
    photos = []
    for index in chosen:
        species = rows[index]["taxonomy"].split()
        name = f"{species[5]} {species[6]}"
        query = urllib.parse.urlencode({
            "taxon_name": name, "quality_grade": "research", "photos": "true", "place_id": 6744,
            "photo_license": "cc0,cc-by,cc-by-nc", "per_page": per_species, "order_by": "votes",
        })
        try:
            data = json.loads(fetch(f"https://api.inaturalist.org/v1/observations?{query}"))
        except Exception as error:
            print(f"  skipped {name}: {error}")
            continue
        for observation in data.get("results", []):
            if not observation.get("photos"):
                continue
            url = observation["photos"][0]["url"].replace("square", "medium")
            try:
                photos.append((index, Image.open(io.BytesIO(fetch(url))).convert("RGB")))
            except Exception:
                continue
        time.sleep(1.0)
    return photos


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", required=True)
    parser.add_argument("--eval-species", type=int, default=120)
    parser.add_argument("--eval-photos", type=int, default=3)
    args = parser.parse_args()
    os.makedirs(args.out, exist_ok=True)
    torch.set_grad_enabled(False)
    # Without gradients, attention takes a fused fast path that has no ONNX form; the ordinary path exports.
    torch.backends.mha.set_fastpath_enabled(False)

    model, preprocess, tokenizer = load_model()
    rows = list(csv.DictReader(open(INDEX), delimiter="\t"))
    print(f"{len(rows)} species in the index")

    # The image encoder, exported, then quantised to 8 bits for the phone.
    fp32_path = os.path.join(args.out, "biocap-visual-fp32.onnx")
    visual = Visual(model)
    torch.onnx.export(
        visual, torch.zeros(1, 3, SIZE, SIZE), fp32_path, input_names=["pixels"], output_names=["embedding"],
        dynamic_axes={"pixels": {0: "batch"}, "embedding": {0: "batch"}}, opset_version=17, do_constant_folding=True,
        dynamo=False,
    )
    onnx.checker.check_model(fp32_path)
    int8_path = os.path.join(args.out, "biocap-visual.onnx")
    quantize_dynamic(fp32_path, int8_path, weight_type=QuantType.QInt8, op_types_to_quantize=["MatMul", "Gemm"])
    options = ort.SessionOptions()
    options.log_severity_level = 3
    fp32 = ort.InferenceSession(fp32_path, options, providers=["CPUExecutionProvider"])
    int8 = ort.InferenceSession(int8_path, options, providers=["CPUExecutionProvider"])

    def embed(session, image):
        return session.run(None, {"pixels": pixels_of(image)})[0][0]

    # The species' text vectors, in the index's order.
    texts = [prompt(row["taxonomy"], row["common"]) for row in rows]
    vectors = []
    for start in range(0, len(texts), 256):
        tokens = tokenizer(texts[start:start + 256])
        vectors.append(torch.nn.functional.normalize(model.encode_text(tokens), dim=-1).numpy())
    species = np.concatenate(vectors).astype(np.float32)
    species_path = os.path.join(args.out, "species.f16")
    species.astype("<f2").tofile(species_path)
    species16 = species.astype(np.float16).astype(np.float32)

    # Evaluation: the PyTorch model with BioCAP's own preparation, then the 8-bit model as the phone prepares photos.
    photos = evaluation_photos(rows, args.eval_species, args.eval_photos, seed=PACK_VERSION)
    results = {"photos": len(photos)}
    counts = {key: 0 for key in ("torch_top1", "torch_top5", "int8_top1", "int8_top5", "agree_top1")}
    cosines = []
    for truth, image in photos:
        reference = torch.nn.functional.normalize(model.encode_image(preprocess(image)[None, ...]), dim=-1).numpy()[0]
        phone = embed(int8, centre_crop(image, Image.BILINEAR))
        torch_rank = np.argsort(-(species @ reference))[:5]
        int8_rank = np.argsort(-(species16 @ phone))[:5]
        counts["torch_top1"] += int(torch_rank[0] == truth)
        counts["torch_top5"] += int(truth in torch_rank)
        counts["int8_top1"] += int(int8_rank[0] == truth)
        counts["int8_top5"] += int(truth in int8_rank)
        counts["agree_top1"] += int(int8_rank[0] == torch_rank[0])
        cosines.append(float(reference @ phone))
    for key, value in counts.items():
        results[key] = round(value / max(1, len(photos)), 4)
    results["cosine_mean"] = round(float(np.mean(cosines)), 4) if cosines else None
    results["cosine_min"] = round(float(np.min(cosines)), 4) if cosines else None
    print(json.dumps(results, indent=1))

    # Reference vectors for the app's tests: 224 x 224 images, so only the model run and normalisation are compared.
    references = []
    for name in sorted(os.listdir(FIXTURES)):
        if name.endswith(".png"):
            image = Image.open(os.path.join(FIXTURES, name)).convert("RGB")
            assert image.size == (SIZE, SIZE), f"{name} must be {SIZE} x {SIZE}"
            vector = embed(int8, image)
            best = int(np.argmax(species16 @ vector))
            references.append({"file": name, "vector": [round(float(x), 6) for x in vector], "best": rows[best]["name"]})
    with open(os.path.join(args.out, "reference.json"), "w") as f:
        json.dump(references, f, indent=1)

    with open(os.path.join(args.out, "species.tsv"), "w", newline="") as f:
        writer = csv.writer(f, delimiter="\t", lineterminator="\n")
        writer.writerow(["name", "common", "aliases"])
        for row in rows:
            writer.writerow([row["name"], row["common"], row["aliases"]])

    files = ["biocap-visual.onnx", "species.f16", "species.tsv"]
    manifest = {
        "format": 1,
        "pack": PACK_VERSION,
        "model": f"{REPO}@{REVISION}",
        "licence": "MIT",
        "image_size": SIZE,
        "mean": MEAN,
        "std": STD,
        "preparation": "shorter side scaled to 224 with bilinear filtering, then the middle 224 x 224",
        "input": "pixels, float32, [batch, 3, 224, 224]",
        "output": "embedding, float32, [batch, 512], unit length",
        "embedding_dim": int(species.shape[1]),
        "species": len(rows),
        "prompt": "a photo of {taxonomy} with common name {common}.",
        "logit_scale": float(model.logit_scale.exp()),
        "files": {name: sha256(os.path.join(args.out, name)) for name in files},
        "evaluation": results,
        "versions": {"torch": torch.__version__, "open_clip": open_clip.__version__, "onnx": onnx.__version__, "onnxruntime": ort.__version__},
    }
    with open(os.path.join(args.out, "manifest.json"), "w") as f:
        json.dump(manifest, f, indent=1)

    pack = os.path.join(args.out, f"biocap-pack-{PACK_VERSION}.zip")
    with zipfile.ZipFile(pack, "w", compression=zipfile.ZIP_STORED) as z:
        for name in files + ["manifest.json"]:
            z.write(os.path.join(args.out, name), name)
    with open(pack + ".sha256", "w") as f:
        f.write(f"{sha256(pack)}  {os.path.basename(pack)}\n")
    with open(os.path.join(args.out, "evaluation.md"), "w") as f:
        f.write(f"BioCAP pack {PACK_VERSION}: {len(rows)} species, model {REPO}@{REVISION[:7]}.\n\n")
        f.write(f"Evaluated on {results['photos']} iNaturalist research-grade photos from Australia.\n\n")
        f.write("| | Top 1 | Top 5 |\n|---|---|---|\n")
        f.write(f"| PyTorch, BioCAP's preparation | {results['torch_top1']:.1%} | {results['torch_top5']:.1%} |\n")
        f.write(f"| 8-bit ONNX, the phone's preparation | {results['int8_top1']:.1%} | {results['int8_top5']:.1%} |\n\n")
        f.write(f"The two agree on the best match for {results['agree_top1']:.1%} of photos; their vectors have a mean ")
        f.write(f"cosine similarity of {results['cosine_mean']} (lowest {results['cosine_min']}).\n")
    print(f"Wrote {pack}")


if __name__ == "__main__":
    main()
