"""The accurate pack as the phone reads it, shared by the scripts that add to a pack and evaluate one.

It needs numpy and ONNX Runtime, not PyTorch.
"""
import csv
import hashlib
import io
import json
import os
import zipfile

import numpy as np
import onnxruntime as ort

from photos import fetch, phone_square

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

# The species a look weighs, as AccurateModels.CONSIDERED in the app.
CONSIDERED = 20


def sha256_of(data):
    return hashlib.sha256(data).hexdigest()


def verified(url, expected):
    data = fetch(url)
    assert sha256_of(data) == expected, f"{url} has SHA-256 {sha256_of(data)}, not {expected}"
    return data


def unzip(data):
    with zipfile.ZipFile(io.BytesIO(data)) as z:
        return {name: z.read(name) for name in z.namelist()}


def pinned_pack():
    """The published pack the app downloads, pinned in gradle.properties: its files by name."""
    properties = dict(
        line.strip().split("=", 1) for line in open(os.path.join(ROOT, "gradle.properties")) if "=" in line and not line.startswith("#")
    )
    return unzip(verified(properties["pollenplot.biocap.url"], properties["pollenplot.biocap.sha256"]))


def halves(data, columns):
    """Little-endian 16-bit floats as rows of 32-bit ones, the numbers the phone reads."""
    return np.frombuffer(data, dtype="<f2").astype(np.float32).reshape(-1, columns)


class Pack:
    """A pack's files, opened as the phone opens them: the encoder, the species and how a photo's vector scores them."""

    def __init__(self, files):
        self.files = files
        self.manifest = json.loads(files["manifest.json"])
        self.rows = list(csv.DictReader(io.StringIO(files["species.tsv"].decode()), delimiter="\t"))
        self.dimensions = self.manifest["embedding_dim"]
        self.logit_scale = self.manifest["logit_scale"]
        self.species = halves(files["species.f16"], self.dimensions)
        about = self.manifest.get("prototypes")
        self.scorer = Scorer(self.species, halves(files[about["file"]], self.dimensions), about["alpha"], about["beta"]) if about else Scorer(self.species)
        options = ort.SessionOptions()
        options.log_severity_level = 3
        self.encoder = ort.InferenceSession(files["biocap-visual.onnx"], options, providers=["CPUExecutionProvider"])
        self.mean = np.array(self.manifest["mean"], dtype=np.float32)
        self.std = np.array(self.manifest["std"], dtype=np.float32)

    def pixels(self, image):
        """A square part of a photo as the encoder's input, prepared as the phone does (phone_square)."""
        array = (np.asarray(phone_square(image), dtype=np.float32) / 255.0 - self.mean) / self.std
        return array.transpose(2, 0, 1)

    def encode(self, images):
        """The unit vectors of square images, one row each."""
        batch = np.stack([self.pixels(image) for image in images])
        try:
            return self.encoder.run(None, {"pixels": batch})[0]
        except Exception:
            # A form of the encoder that takes one photo at a time.
            return np.concatenate([self.encoder.run(None, {"pixels": batch[i:i + 1]})[0] for i in range(len(batch))])


class Scorer:
    """How close a photo's vector is to each species, as SpeciesIndex.nearest in the app works it out.

    Without prototypes it is the cosine similarity to the species' text vector. With them, a species whose prototype
    (the mean of BioCAP's vectors of its photos) is close to the photo gains alpha * exp(-beta * (1 - closeness)),
    which is next to nothing until the photo looks much like the species' own photos. A species without a prototype
    has a row of zeros and gains nothing. This is the cache term of Tip-Adapter (Zhang and others, 2022), with one
    prototype for each species in place of every photo.
    """

    def __init__(self, species, prototypes=None, alpha=0.0, beta=0.0):
        self.species = species
        self.prototypes = prototypes
        self.alpha = np.float32(alpha)
        self.beta = np.float32(beta)
        self.has = prototypes.any(axis=1) if prototypes is not None else None

    def __call__(self, vector):
        scores = self.species @ vector
        if self.prototypes is None or self.alpha == 0:
            return scores
        near = self.prototypes @ vector
        return scores + np.where(self.has, self.alpha * np.exp(-self.beta * (1 - near)), 0).astype(np.float32)


def closest(scores, mask=None, count=CONSIDERED):
    """The rows with the highest scores among those [mask] lets through, best first, with their scores."""
    if mask is not None:
        scores = np.where(mask, scores, -np.inf)
    return [(int(i), float(scores[i])) for i in np.argsort(-scores, kind="stable")[:count] if np.isfinite(scores[i])]


def shares(scores, logit_scale):
    """Scores, best first, as shares of BioCAP's belief (AccurateRegions.shares in the app)."""
    scores = np.asarray(scores, dtype=np.float64)
    weights = np.exp(logit_scale * (scores - scores.max()))
    return weights / weights.sum()


def look_vector(pack, image, group):
    """The vector the app scores for a photo of one plant or animal: the mean of the three looks for a plant
    (AccurateModels.lookAtOne), and the look at the middle for an animal."""
    from photos import looks

    squares = looks(*image.size)
    if group != "plant":
        return pack.encode([image.crop(squares["centre"])])[0]
    return pack.encode([image.crop(box) for box in squares.values()]).mean(axis=0)
