"""Trains, evaluates and writes the plant problems pack: a classifier on BioCAP's image vector.

The pack adds no encoder. The phone encodes the middle square of a photo with the accurate pack's encoder
(photos.phone_square), and this pack's head turns the vector into a share for each problem and for a healthy plant:
softmax((W v + b) / T), as DiseaseHead in :ml works it out. It is multinomial logistic regression, fitted on the
photos of the observers kept for learning, with its regularisation and temperature T chosen on the tuning observers'
photos and every number reported from the evaluation observers' photos (photos.held_for).

    vectors  turns a share of disease_photos.tsv's photos into vectors with the pinned accurate pack's encoder;
    fit      fits the head from every share's vectors and writes disease-pack-N.zip and evaluation.md;
    fixture  writes tools/models/fixtures/disease_head_reference.json, a small head and its answers, which
             DiseaseHeadTest checks the phone's arithmetic against.

It needs `pip install onnxruntime==1.31.0 numpy pillow scikit-learn`, and no PyTorch. It runs in GitHub Actions
(.github/workflows/models.yml).
"""
import argparse
import csv
import glob
import hashlib
import io
import json
import os
import zipfile
from concurrent.futures import ThreadPoolExecutor

import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
HEALTHY = "healthy"

# A problem becomes a class only with this many photos to learn from, and some to tune and judge with.
MIN_LEARNING = 30
MIN_HELD = 5

# The levels the app shows a suggestion at, chosen on the tuning photos taken in the field (iNaturalist and PlantDoc),
# because PlantVillage's single leaves on plain backgrounds are far easier than a phone's photo of a garden and made
# every level look safe in pack 1. "Likely" (and "No problem found" for a healthy answer) needs LIKELY_PRECISION of
# the field photos at or above yes_bp to have the right class first, and never starts below LIKELY_FLOOR_BP, half the
# belief. A "possible" chip needs POSSIBLE_PRECISION of the chips shown at or above maybe_bp to be right, and may be
# given to at most HEALTHY_CHIP_RATE of the healthy field photos. All of these are Pollenplot's choice.
LIKELY_PRECISION = 0.85
POSSIBLE_PRECISION = 0.35
HEALTHY_CHIP_RATE = 0.05
LIKELY_FLOOR_BP = 5_000
FLOOR_BP = 1_000
FIELD = ("inaturalist", "plantdoc")
SHOWN = 3

REGULARISATION = (1.0, 3.0, 10.0, 30.0, 100.0, 300.0)
TEMPERATURES = np.round(np.arange(0.3, 5.01, 0.05), 2)
CHUNK = 256
BATCH = 16


def properties():
    return dict(line.strip().split("=", 1) for line in open(os.path.join(ROOT, "gradle.properties")) if "=" in line and not line.startswith("#"))


def sha256_of(data):
    return hashlib.sha256(data).hexdigest()


def as_stored(values):
    """Numbers as the pack holds them, in 16-bit floats, and as the phone reads them back."""
    return np.asarray(values, dtype=np.float32).astype("<f2").astype(np.float32)


def head_shares(weights, bias, temperature, vectors):
    """Each class's share for each vector: softmax((W v + b) / T) in 32-bit floats, as DiseaseHead.shares."""
    vectors = np.atleast_2d(np.asarray(vectors, dtype=np.float32))
    logits = (vectors @ weights.T + bias) / np.float32(temperature)
    logits -= logits.max(axis=1, keepdims=True)
    weighted = np.exp(logits)
    return weighted / weighted.sum(axis=1, keepdims=True)


# --- vectors -------------------------------------------------------------------------------------------------------

def vectors(args):
    from PIL import Image

    from pack import Pack, pinned_pack
    from photos import fetch

    rows = list(csv.DictReader(open(args.photos), delimiter="\t"))
    chosen = [i for i in range(len(rows)) if i % args.shares == args.share]
    if args.limit:
        chosen = chosen[: args.limit]
    print(f"{len(chosen)} photos in share {args.share} of {args.shares}")
    pack = Pack(pinned_pack())

    def photo(i):
        try:
            return Image.open(io.BytesIO(fetch(rows[i]["url"], retries=3))).convert("RGB")
        except Exception as error:
            print(f"  skipped photo {i}: {error}")
            return None

    done, found = [], []
    with ThreadPoolExecutor(max_workers=16) as pool:
        for start in range(0, len(chosen), CHUNK):
            chunk = chosen[start:start + CHUNK]
            fetched = [(i, image) for i, image in zip(chunk, pool.map(photo, chunk)) if image is not None]
            for at in range(0, len(fetched), BATCH):
                batch = fetched[at:at + BATCH]
                # Pack.encode prepares each photo as the phone does: its middle square, phone_square.
                for (i, _), vector in zip(batch, pack.encode([image for _, image in batch])):
                    done.append(i)
                    found.append(vector)
            print(f"  {min(start + CHUNK, len(chosen))} of {len(chosen)}", flush=True)
    os.makedirs(args.out, exist_ok=True)
    np.savez(
        os.path.join(args.out, f"disease-vectors-{args.share}.npz"),
        rows=np.array(done, dtype=np.int64),
        vectors=np.array(found, dtype=np.float32).reshape(len(found), pack.dimensions),
    )
    print(f"{len(done)} vectors")


# --- fit -----------------------------------------------------------------------------------------------------------

def load(args):
    rows = list(csv.DictReader(open(args.photos), delimiter="\t"))
    index, found = [], []
    for path in sorted(glob.glob(os.path.join(args.vectors, "disease-vectors-*.npz"))):
        data = np.load(path)
        index += data["rows"].tolist()
        found.append(data["vectors"])
    assert index, f"No vectors in {args.vectors}"
    return [rows[i] for i in index], np.concatenate(found).astype(np.float32)


def balanced_weights(rows, labels, classes):
    """Each photo's source weight, scaled so every class weighs the same in all."""
    weights = np.array([float(row["weight"]) for row in rows])
    totals = np.array([weights[labels == c].sum() for c in range(len(classes))])
    return weights * (totals.mean() / totals[labels])


def nll(shares, labels):
    return float(-np.mean(np.log(np.maximum(shares[np.arange(len(labels)), labels], 1e-12))))


def calibration_error(shares, labels, bins=10):
    """Expected calibration error of the first answer: the gap between belief and accuracy, over ten bins of belief."""
    top = shares.max(axis=1)
    right = shares.argmax(axis=1) == labels
    edges = np.linspace(0, 1, bins + 1)
    error = 0.0
    for low, high in zip(edges[:-1], edges[1:]):
        inside = (top > low) & (top <= high)
        if inside.any():
            error += inside.mean() * abs(top[inside].mean() - right[inside].mean())
    return float(error)


def top_k(shares, labels, k):
    order = np.argsort(-shares, axis=1)[:, :k]
    return float(np.mean([labels[i] in order[i] for i in range(len(labels))])) if len(labels) else float("nan")


def chips_for(row, healthy, yes_bp, bp):
    """The chips the app shows for one photo's shares at maybe_bp = bp: none when it says "No problem found"."""
    if row.argmax() == healthy and row.max() * 10_000 >= yes_bp:
        return []
    return [c for c in np.argsort(-row) if c != healthy and row[c] * 10_000 >= bp][:SHOWN]


def thresholds(shares, labels, healthy):
    """yes_bp and maybe_bp from the field tuning photos (see LIKELY_PRECISION, POSSIBLE_PRECISION and HEALTHY_CHIP_RATE)."""
    top = shares.max(axis=1)
    first = shares.argmax(axis=1)
    yes_bp = 9_750
    for bp in range(LIKELY_FLOOR_BP, 10_000, 250):
        at = top * 10_000 >= bp
        if at.sum() >= 10 and (first[at] == labels[at]).mean() >= LIKELY_PRECISION:
            yes_bp = bp
            break
    maybe_bp = yes_bp
    well = np.flatnonzero(labels == healthy)
    for bp in range(FLOOR_BP, yes_bp, 250):
        chips = [chips_for(shares[i], healthy, yes_bp, bp) for i in range(len(labels))]
        shown = sum(len(c) for c in chips)
        right = sum(labels[i] in c for i, c in enumerate(chips))
        alarms = np.mean([bool(chips[i]) for i in well]) if len(well) else 0.0
        if shown >= 10 and right / shown >= POSSIBLE_PRECISION and alarms <= HEALTHY_CHIP_RATE:
            maybe_bp = bp
            break
    return yes_bp, maybe_bp


def field_report(shares, labels, healthy, yes_bp, maybe_bp):
    """How the levels do on field photos: how often a "likely" or "No problem found" is right, and how many get one."""
    top = shares.max(axis=1)
    first = shares.argmax(axis=1)
    sure = top * 10_000 >= yes_bp
    chips = [chips_for(shares[i], healthy, yes_bp, maybe_bp) for i in range(len(labels))]
    sick = labels != healthy
    return {
        "photos": int(len(labels)),
        "sure": float(sure.mean()) if len(labels) else None,
        "sure_right": float((first[sure] == labels[sure]).mean()) if sure.any() else None,
        "sick_right_chip": float(np.mean([labels[i] in chips[i] for i in np.flatnonzero(sick)])) if sick.any() else None,
        "healthy_given_a_chip": float(np.mean([bool(chips[i]) for i in np.flatnonzero(~sick)])) if (~sick).any() else None,
    }


def fit(args):
    from sklearn.linear_model import LogisticRegression

    rows, found = load(args)
    problems = {p["key"]: p for p in json.load(open(os.path.join(ROOT, "app", "src", "main", "assets", "kb", "problems.json")))}
    sets = np.array([row["held_for"] for row in rows])
    keys = np.array([row["key"] for row in rows])
    classes = [HEALTHY] + sorted(
        key for key in set(keys) - {HEALTHY}
        if (keys[sets == "learning"] == key).sum() >= MIN_LEARNING
        and (keys[sets == "tuning"] == key).sum() >= MIN_HELD and (keys[sets == "evaluation"] == key).sum() >= MIN_HELD
    )
    # Problems the catalogue does not hold are classes too, so a photo of one is not pushed into a catalogued class
    # it merely looks like; the app never offers them (DiseaseCall.of leaves out keys missing from the catalogue).
    left_out = sorted(set(problems) - set(classes))
    uncatalogued = sorted(set(classes) - set(problems) - {HEALTHY})
    keep = np.isin(keys, classes)
    rows = [row for row, k in zip(rows, keep) if k]
    found, keys, sets = found[keep], keys[keep], sets[keep]
    labels = np.array([classes.index(k) for k in keys])
    split = {name: sets == name for name in ("learning", "tuning", "evaluation")}
    print(f"{len(classes)} classes; " + ", ".join(f"{n} {name}" for name, n in ((s, m.sum()) for s, m in split.items())))

    learn = split["learning"]
    weights = balanced_weights([r for r, m in zip(rows, learn) if m], labels[learn], classes)
    tune = split["tuning"]
    best = None
    for c in REGULARISATION:
        model = LogisticRegression(C=c, max_iter=3000)
        model.fit(found[learn], labels[learn], sample_weight=weights)
        loss = nll(model.predict_proba(found[tune]), labels[tune])
        print(f"  C={c}: tuning log loss {loss:.4f}")
        if best is None or loss < best[0]:
            best = (loss, c, model)
    _, c, model = best
    w = as_stored(model.coef_)
    b = as_stored(model.intercept_)
    temperature = min(TEMPERATURES, key=lambda t: nll(head_shares(w, b, t, found[tune]), labels[tune]))
    tuned = head_shares(w, b, temperature, found[tune])
    healthy = classes.index(HEALTHY)
    sources = np.array([row["source"] for row in rows])
    field = np.isin(sources, FIELD)
    yes_bp, maybe_bp = thresholds(tuned[field[tune]], labels[tune][field[tune]], healthy)

    judge = split["evaluation"]
    raw = head_shares(w, b, 1.0, found[judge])
    shares = head_shares(w, b, temperature, found[judge])
    report = {
        "classes": classes, "left_out": left_out, "uncatalogued": uncatalogued, "C": c, "temperature": float(temperature), "yes_bp": yes_bp, "maybe_bp": maybe_bp,
        "photos": {name: int(m.sum()) for name, m in split.items()},
        "top1": top_k(shares, labels[judge], 1), "top3": top_k(shares, labels[judge], 3),
        "ece_before": calibration_error(raw, labels[judge]), "ece_after": calibration_error(shares, labels[judge]),
        "by_source": {}, "by_class": {},
    }
    for source in sorted(set(sources[judge])):
        at = sources[judge] == source
        report["by_source"][source] = {"photos": int(at.sum()), "top1": top_k(shares[at], labels[judge][at], 1), "top3": top_k(shares[at], labels[judge][at], 3)}
    for i, key in enumerate(classes):
        at = labels[judge] == i
        report["by_class"][key] = {"photos": int(at.sum()), "top1": top_k(shares[at], labels[judge][at], 1), "top3": top_k(shares[at], labels[judge][at], 3)}
    report["field"] = field_report(shares[field[judge]], labels[judge][field[judge]], healthy, yes_bp, maybe_bp)
    garden = (sources[judge] == "inaturalist") & (labels[judge] == healthy)
    report["garden_healthy_called_healthy"] = float(((shares[garden].argmax(axis=1) == healthy) & (shares[garden].max(axis=1) * 10_000 >= yes_bp)).mean()) if garden.any() else None
    report["garden_healthy_given_a_chip"] = float(np.mean([
        bool(chips_for(shares[i], healthy, yes_bp, maybe_bp)) for i in np.flatnonzero(garden)
    ])) if garden.any() else None

    write_pack(args, classes, w, b, float(temperature), yes_bp, maybe_bp, rows, learn, found[judge][:6], report)


def write_pack(args, classes, w, b, temperature, yes_bp, maybe_bp, rows, learn, references, report):
    props = properties()
    number = int(args.pack or props["pollenplot.disease.pack"])
    out = {
        "weights.f16": w.astype("<f2").tobytes(),
        "bias.f16": b.astype("<f2").tobytes(),
    }
    credits = io.StringIO()
    writer = csv.writer(credits, delimiter="\t", lineterminator="\n")
    writer.writerow(["source", "class", "licence", "credit", "url"])
    for row, used in zip(rows, learn):
        if used:
            writer.writerow([row["source"], row["key"], row["licence"], row["credit"], row["url"]])
    out["credits.tsv"] = credits.getvalue().encode()
    out["reference.json"] = json.dumps([
        {"vector": [round(float(x), 7) for x in v], "shares": [round(float(s), 7) for s in head_shares(w, b, temperature, v)[0]]}
        for v in references
    ]).encode()
    manifest = {
        "pack": number,
        "biocap_pack": int(props["pollenplot.biocap.pack"]),
        "biocap_sha256": props["pollenplot.biocap.sha256"],
        "embedding_dim": int(w.shape[1]),
        "classes": classes,
        "temperature": temperature,
        "yes_bp": yes_bp,
        "maybe_bp": maybe_bp,
        "datasets": [
            "PlantDoc (Singh and others, 2020), CC BY 4.0, github.com/pratikkayal/PlantDoc-Dataset",
            "PlantVillage (Mohanty, Hughes and Salathé, 2016), CC BY-SA 3.0, github.com/spMohanty/PlantVillage-Dataset",
            "iNaturalist observations under CC0 and CC BY, credited in credits.tsv",
        ],
        "files": {name: sha256_of(data) for name, data in sorted(out.items())},
    }
    out["manifest.json"] = json.dumps(manifest, indent=1).encode()
    os.makedirs(args.out, exist_ok=True)
    path = os.path.join(args.out, f"disease-pack-{number}.zip")
    with zipfile.ZipFile(path, "w", compression=zipfile.ZIP_DEFLATED) as z:
        for name, data in sorted(out.items()):
            z.writestr(zipfile.ZipInfo(name, date_time=(2026, 1, 1, 0, 0, 0)), data, compress_type=zipfile.ZIP_DEFLATED)
    with open(path + ".sha256", "w") as f:
        f.write(f"{sha256_of(open(path, 'rb').read())}  {os.path.basename(path)}\n")
    with open(os.path.join(args.out, "manifest.json"), "w") as f:
        f.write(out["manifest.json"].decode())
    with open(os.path.join(args.out, "report.json"), "w") as f:
        json.dump(report, f, indent=1)
    with open(os.path.join(args.out, "evaluation.md"), "w") as f:
        f.write(markdown(number, report, os.path.getsize(path)))
    print(open(os.path.join(args.out, "evaluation.md")).read())


def markdown(number, report, size):
    def pct(x):
        return "n/a" if x is None or x != x else f"{100 * x:.1f}%"

    lines = [
        f"# Plant problems pack {number}",
        "",
        f"A classifier on BioCAP's image vector with {len(report['classes'])} classes, {size:,} bytes. It was fitted on "
        f"{report['photos']['learning']:,} photos, tuned on {report['photos']['tuning']:,} and judged on "
        f"{report['photos']['evaluation']:,}, each set from different observers.",
        "",
        f"- Regularisation C = {report['C']}, temperature {report['temperature']:.2f}.",
        f"- Shown as likely from {report['yes_bp']} bp, and as possible from {report['maybe_bp']} bp.",
        f"- Held-out top-1 {pct(report['top1'])}, top-3 {pct(report['top3'])}.",
        f"- Expected calibration error {pct(report['ece_before'])} before the temperature, {pct(report['ece_after'])} after.",
        f"- Healthy garden plants from iNaturalist called \"No problem found\": {pct(report['garden_healthy_called_healthy'])}; "
        f"given at least one chip: {pct(report['garden_healthy_given_a_chip'])}.",
        f"- Field photos judged (iNaturalist and PlantDoc): {report['field']['photos']:,}. "
        f"{pct(report['field']['sure'])} reach \"likely\" or \"No problem found\", and {pct(report['field']['sure_right'])} of those are right. "
        f"{pct(report['field']['sick_right_chip'])} of the problem photos have the right problem among the chips, and "
        f"{pct(report['field']['healthy_given_a_chip'])} of the healthy ones are given a chip.",
        f"- Problems in the catalogue with too few photos to be a class (most pests have none): {', '.join(report['left_out']) or 'none'}.",
        f"- Classes the app's catalogue does not hold, learned so their photos are not called something else and "
        f"never offered: {', '.join(report['uncatalogued']) or 'none'}.",
        "",
        "| Source | Photos | Top-1 | Top-3 |",
        "|---|---:|---:|---:|",
    ]
    lines += [f"| {s} | {v['photos']} | {pct(v['top1'])} | {pct(v['top3'])} |" for s, v in report["by_source"].items()]
    lines += ["", "| Class | Photos | Top-1 | Top-3 |", "|---|---:|---:|---:|"]
    lines += [f"| {c} | {v['photos']} | {pct(v['top1'])} | {pct(v['top3'])} |" for c, v in report["by_class"].items()]
    return "\n".join(lines) + "\n"


# --- fixture -------------------------------------------------------------------------------------------------------

def fixture(args):
    """A small head with random numbers, stored as the pack stores them, and its answers for a few vectors."""
    rng = np.random.default_rng(7)
    classes, dimensions = ["healthy", "powdery-mildew", "rust", "botrytis"], 8
    w = as_stored(rng.normal(0, 4, (len(classes), dimensions)))
    b = as_stored(rng.normal(0, 1, len(classes)))
    vectors = rng.normal(0, 1, (3, dimensions)).astype(np.float32)
    vectors /= np.linalg.norm(vectors, axis=1, keepdims=True)
    temperature = 1.7
    data = {
        "classes": classes, "dimensions": dimensions, "temperature": temperature,
        "weights_f16": w.astype("<f2").tobytes().hex(), "bias_f16": b.astype("<f2").tobytes().hex(),
        "cases": [{"vector": [float(x) for x in v], "shares": [float(s) for s in head_shares(w, b, temperature, v)[0]]} for v in vectors],
    }
    path = os.path.join(ROOT, "tools", "models", "fixtures", "disease_head_reference.json")
    with open(path, "w") as f:
        json.dump(data, f, indent=1)
        f.write("\n")
    print(f"Wrote {path}")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    commands = parser.add_subparsers(dest="command", required=True)
    v = commands.add_parser("vectors")
    v.add_argument("--photos", required=True, help="disease_photos.tsv")
    v.add_argument("--out", required=True)
    v.add_argument("--share", type=int, default=0)
    v.add_argument("--shares", type=int, default=1)
    v.add_argument("--limit", type=int, default=0, help="Stop after this many photos, to try the script")
    f = commands.add_parser("fit")
    f.add_argument("--photos", required=True, help="disease_photos.tsv")
    f.add_argument("--vectors", required=True, help="The folder of every share's vectors")
    f.add_argument("--out", required=True)
    f.add_argument("--pack", default="", help="The pack's number; gradle.properties' pollenplot.disease.pack by default")
    commands.add_parser("fixture")
    args = parser.parse_args()
    {"vectors": vectors, "fit": fit, "fixture": fixture}[args.command](args)


if __name__ == "__main__":
    main()
