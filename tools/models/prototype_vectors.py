"""Turns the photos prototype_photos.py chose into BioCAP vectors, one share of the species at a time.

Each photo is fetched from iNaturalist's open data at its medium size, prepared as the phone prepares a photo (the
middle square, phone_square) and given to the image encoder of the base pack, which is the encoder the phone runs.
For each species it writes the sum of its photos' unit vectors and how many there were; add_prototypes.py makes the
prototypes from all the shares. It also writes the photos it used, for the pack's attribution.

It needs no PyTorch: `pip install onnxruntime==1.31.0 numpy pillow`. It runs in GitHub Actions
(.github/workflows/models.yml), several shares side by side.
"""
import argparse
import csv
import io
import os
from concurrent.futures import ThreadPoolExecutor

import numpy as np
from PIL import Image

from add_prototypes import base_pack
from photos import fetch
from pack import Pack

BUCKET = "https://inaturalist-open-data.s3.amazonaws.com"
CHUNK = 256
BATCH = 16


def photo_of(row):
    """A chosen photo, or None when it cannot be fetched or read."""
    try:
        return Image.open(io.BytesIO(fetch(f"{BUCKET}/photos/{row['photo']}/medium.{row['extension']}", retries=3))).convert("RGB")
    except Exception as error:
        print(f"  skipped photo {row['photo']}: {error}")
        return None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--photos", required=True, help="prototype_photos.tsv")
    parser.add_argument("--out", required=True)
    parser.add_argument("--share", type=int, default=0)
    parser.add_argument("--shares", type=int, default=1)
    parser.add_argument("--limit", type=int, default=0, help="Stop after this many photos, to try the script")
    args = parser.parse_args()
    os.makedirs(args.out, exist_ok=True)
    pack = Pack(base_pack())
    chosen = [row for row in csv.DictReader(open(args.photos), delimiter="\t") if int(row["row"]) % args.shares == args.share]
    if args.limit:
        chosen = chosen[: args.limit]
    print(f"{len(chosen)} photos in share {args.share} of {args.shares}")
    sums = {}
    counts = {}
    used = []
    with ThreadPoolExecutor(max_workers=16) as pool:
        for start in range(0, len(chosen), CHUNK):
            chunk = chosen[start:start + CHUNK]
            fetched = [(row, image) for row, image in zip(chunk, pool.map(photo_of, chunk)) if image is not None]
            for at in range(0, len(fetched), BATCH):
                batch = fetched[at:at + BATCH]
                for (row, _), vector in zip(batch, pack.encode([image for _, image in batch])):
                    species = int(row["row"])
                    sums[species] = sums.get(species, 0) + vector.astype(np.float64)
                    counts[species] = counts.get(species, 0) + 1
                    used.append(row)
            print(f"  {min(start + CHUNK, len(chosen))} of {len(chosen)}", flush=True)
    species = sorted(sums)
    np.savez(
        os.path.join(args.out, f"vectors-{args.share}.npz"),
        rows=np.array(species, dtype=np.int64),
        sums=np.array([sums[s] for s in species], dtype=np.float32).reshape(len(species), pack.dimensions),
        counts=np.array([counts[s] for s in species], dtype=np.int64),
    )
    with open(os.path.join(args.out, f"used-{args.share}.tsv"), "w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=list(used[0]) if used else ["row"], delimiter="\t", lineterminator="\n")
        writer.writeheader()
        writer.writerows(used)
    print(f"{len(used)} photos of {len(species)} species")


if __name__ == "__main__":
    main()
