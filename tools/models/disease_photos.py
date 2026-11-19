"""Chooses the photos the plant problems pack is trained and judged on.

The pack is a classifier on BioCAP's image vector with one class for each problem key in
app/src/main/assets/kb/problems.json that has photos, and one for a healthy plant. The photos come from:

- PlantDoc (Singh and others, 2020; CC BY 4.0), photos of leaves found on the web and labelled by the authors;
- PlantVillage (Mohanty, Hughes and Salathé, 2016; CC BY-SA 3.0), leaves photographed on a plain background in a
  laboratory, weighted down because garden photos look nothing like them; and
- research-grade iNaturalist observations of the pathogens themselves, which show the problem on its host, and of
  common Australian garden plants as healthy plants, first photo only, CC0 or CC BY.

Each dataset's licence is read from its repository and must be one of OPEN_LICENCES, or the script stops. Each photo
is kept for evaluation, for tuning or for learning by its observer (photos.held_for), so no photographer's photos are
both learned from and judged with. The datasets have no observers, so a photo's file path stands in for one.

It needs only Python. It runs in GitHub Actions (.github/workflows/models.yml).
"""
import argparse
import csv
import hashlib
import json
import os
import random
import time
import urllib.parse

from photos import USER_AGENT, held_for

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
PROBLEMS = os.path.join(ROOT, "app", "src", "main", "assets", "kb", "problems.json")
INDEX = os.path.join(ROOT, "tools", "models", "species_index.tsv")

HEALTHY = "healthy"

# The licences a dataset may carry (SPDX), so the pack can be shared with credits.
OPEN_LICENCES = {"CC0-1.0", "CC-BY-4.0", "CC-BY-SA-3.0", "MIT"}

PLANTDOC = "pratikkayal/PlantDoc-Dataset"
PLANTVILLAGE = "spMohanty/PlantVillage-Dataset"

# Repositories whose licence GitHub does not detect, with the licence their own files state. PlantVillage's
# README_HF.md and its Hugging Face card (huggingface.co/datasets/mohanty/PlantVillage) give "license: cc-by-sa-3.0".
DECLARED = {PLANTVILLAGE: ("CC-BY-SA-3.0", "README_HF.md")}

# How much a photo from each source counts in training. PlantVillage's laboratory photos count for a quarter.
WEIGHTS = {"plantdoc": 1.0, "plantvillage": 0.25, "inaturalist": 1.0}

# PlantDoc's folders by the problem key they show. A key the catalogue does not hold is still learned, so its photos
# are not called something else, and the app never offers it.
PLANTDOC_CLASSES = {
    "Apple leaf": HEALTHY, "Bell_pepper leaf": HEALTHY, "Blueberry leaf": HEALTHY, "Cherry leaf": HEALTHY,
    "Peach leaf": HEALTHY, "Raspberry leaf": HEALTHY, "Soyabean leaf": HEALTHY, "Strawberry leaf": HEALTHY,
    "Tomato leaf": HEALTHY, "grape leaf": HEALTHY,
    "Tomato Early blight leaf": "early-blight", "Potato leaf early blight": "early-blight",
    "Tomato leaf late blight": "late-blight", "Potato leaf late blight": "late-blight",
    "Tomato Septoria leaf spot": "septoria-leaf-spot",
    "Squash Powdery mildew leaf": "powdery-mildew",
    "Apple rust leaf": "rust", "Corn rust leaf": "rust",
    "Tomato two spotted spider mites leaf": "two-spotted-mite",
}

# PlantVillage's folders (raw/color) by the problem key they show.
PLANTVILLAGE_CLASSES = {
    "Apple___healthy": HEALTHY, "Blueberry___healthy": HEALTHY, "Cherry_(including_sour)___healthy": HEALTHY,
    "Corn_(maize)___healthy": HEALTHY, "Grape___healthy": HEALTHY, "Peach___healthy": HEALTHY,
    "Pepper,_bell___healthy": HEALTHY, "Potato___healthy": HEALTHY, "Raspberry___healthy": HEALTHY,
    "Soybean___healthy": HEALTHY, "Strawberry___healthy": HEALTHY, "Tomato___healthy": HEALTHY,
    "Tomato___Early_blight": "early-blight", "Potato___Early_blight": "early-blight",
    "Tomato___Late_blight": "late-blight", "Potato___Late_blight": "late-blight",
    "Tomato___Septoria_leaf_spot": "septoria-leaf-spot",
    "Squash___Powdery_mildew": "powdery-mildew", "Cherry_(including_sour)___Powdery_mildew": "powdery-mildew",
    "Apple___Cedar_apple_rust": "rust", "Corn_(maize)___Common_rust_": "rust",
    "Tomato___Spider_mites Two-spotted_spider_mite": "two-spotted-mite",
}

# The iNaturalist taxa whose observations show each problem. A name above species takes in the taxa beneath it.
INATURALIST_TAXA = {
    "powdery-mildew": ["Erysiphaceae"],
    "downy-mildew": ["Peronosporaceae"],
    "myrtle-rust": ["Austropuccinia psidii"],
    "rust": ["Pucciniales"],
    "botrytis": ["Botrytis cinerea"],
    "peach-leaf-curl": ["Taphrina deformans"],
    "citrus-scab": ["Elsinoe fawcettii"],
    "late-blight": ["Phytophthora infestans"],
    "early-blight": ["Alternaria solani"],
    "septoria-leaf-spot": ["Septoria lycopersici"],
    "anthracnose": ["Colletotrichum"],
    "sooty-mould": ["Capnodiaceae"],
    "black-spot-of-rose": ["Diplocarpon rosae"],
}

# Rust's own iNaturalist photos leave out myrtle rust, which is a class of its own.
EXCLUDE = {"rust": "Austropuccinia psidii"}

AUSTRALIA = 6744


def github(path, token):
    """A GitHub API answer, with the workflow's token when there is one."""
    import urllib.request

    headers = {"User-Agent": USER_AGENT, "Accept": "application/vnd.github+json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    for attempt in range(5):
        try:
            with urllib.request.urlopen(urllib.request.Request(f"https://api.github.com/{path}", headers=headers), timeout=60) as response:
                return json.loads(response.read())
        except Exception:
            if attempt == 4:
                raise
            time.sleep(3 * (attempt + 1))


def inaturalist(query):
    from photos import fetch

    return json.loads(fetch("https://api.inaturalist.org/v1/observations?" + urllib.parse.urlencode(query)))


def licence_of(repo, token):
    """The repository's licence as GitHub reports it, or as its own files declare it (DECLARED), which must be open.
    A declared licence is checked again in the file that states it, so a change there stops the build."""
    if repo in DECLARED:
        spdx, file = DECLARED[repo]
        from photos import fetch

        text = fetch(f"https://raw.githubusercontent.com/{repo}/HEAD/{file}").decode("utf-8", "replace").lower()
        assert f"license: {spdx.lower()}" in text, f"{repo}'s {file} no longer says license: {spdx.lower()}"
        return spdx
    spdx = (github(f"repos/{repo}/license", token).get("license") or {}).get("spdx_id")
    assert spdx in OPEN_LICENCES, f"{repo} is licensed {spdx}, which is not one the pack may use"
    return spdx


def tree(repo, sha, token):
    """The entries of one folder of a repository, by name."""
    data = github(f"repos/{repo}/git/trees/{sha}", token)
    assert not data.get("truncated"), f"The listing of {repo} {sha} was cut short"
    return {entry["path"]: entry for entry in data["tree"]}


def stand_in_observer(source, path):
    """A number in place of the observer for a dataset's photo, from its file name, so photos split the same way on
    every run."""
    return str(int(hashlib.sha256(f"{source}/{path}".encode()).hexdigest()[:12], 16))


def dataset_photos(source, repo, root, classes, per_class, rng, token):
    """Up to [per_class] photos of each of the dataset's classes, as rows."""
    commit = github(f"repos/{repo}/commits/HEAD", token)["sha"]
    licence = licence_of(repo, token)
    folder = tree(repo, commit, token)
    for part in root:
        folder = tree(repo, folder[part]["sha"], token)
    rows = []
    for name, key in sorted(classes.items()):
        if name not in folder:
            print(f"  {source}: no folder {name}")
            continue
        files = sorted(path for path, entry in tree(repo, folder[name]["sha"], token).items()
                       if entry["type"] == "blob" and path.lower().endswith((".jpg", ".jpeg", ".png")))
        rng.shuffle(files)
        for path in files[:per_class]:
            full = "/".join([*root, name, path])
            rows.append({
                "source": source, "key": key, "label": name,
                "url": f"https://raw.githubusercontent.com/{repo}/{commit}/{urllib.parse.quote(full)}",
                "observer": stand_in_observer(source, full), "licence": licence,
                "credit": f"{repo} at {commit[:12]}", "weight": WEIGHTS[source],
            })
    print(f"  {source}: {len(rows)} photos, {licence}, commit {commit[:12]}")
    return rows


def inaturalist_photos(key, taxa, per_class, place=None, exclude=None):
    """Research-grade observations of [taxa] with a CC0 or CC BY first photo, spread over observers."""
    by_observer = {}
    for taxon in taxa:
        for page in range(1, 6):
            query = {
                "taxon_name": taxon, "quality_grade": "research", "photos": "true", "photo_license": "cc0,cc-by",
                "per_page": 200, "page": page, "order_by": "id",
            }
            if place:
                query["place_id"] = place
            try:
                data = inaturalist(query)
            except Exception as error:
                print(f"  skipped {taxon} page {page}: {error}")
                break
            results = data.get("results", [])
            for observation in results:
                name = ((observation.get("taxon") or {}).get("name") or "")
                if exclude and name.startswith(exclude):
                    continue
                photo = (observation.get("photos") or [None])[0]
                user = (observation.get("user") or {}).get("id")
                if not photo or not user or (photo.get("license_code") or "").lower() not in ("cc0", "cc-by"):
                    continue
                by_observer.setdefault(str(user), []).append({
                    "source": "inaturalist", "key": key, "label": name,
                    "url": photo["url"].replace("square", "medium"), "observer": str(user),
                    "licence": photo["license_code"].upper(), "credit": photo.get("attribution", ""),
                    "weight": WEIGHTS["inaturalist"],
                })
            time.sleep(1.0)
            # Enough to choose from: three times the photos wanted.
            if len(results) < 200 or sum(len(found) for found in by_observer.values()) >= per_class * 3:
                break
    # One from each observer in turn, so no one garden fills a class.
    queues = [by_observer[o] for o in sorted(by_observer, key=int)]
    rows = []
    while queues and len(rows) < per_class:
        for queue in queues:
            if len(rows) < per_class:
                rows.append(queue.pop(0))
        queues = [q for q in queues if q]
    return rows


def healthy_garden_plants(count, per_species, rng):
    """Photos of common Australian garden plants from the species index, as healthy plants."""
    species = [row for row in csv.DictReader(open(INDEX), delimiter="\t") if (row.get("group") or "plant") == "plant" and int(row.get("observations") or 0) > 0]
    species.sort(key=lambda row: -int(row["observations"]))
    chosen = rng.sample(species[: count * 3], min(count, len(species)))
    rows = []
    for row in chosen:
        rows += inaturalist_photos(HEALTHY, [row["name"]], per_species, place=AUSTRALIA)
    return rows


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", required=True)
    parser.add_argument("--per-class", type=int, default=600, help="The most photos of a class from each source")
    parser.add_argument("--garden-species", type=int, default=80, help="Garden plants photographed as healthy")
    parser.add_argument("--seed", type=int, default=1)
    args = parser.parse_args()
    os.makedirs(args.out, exist_ok=True)
    token = os.environ.get("GITHUB_TOKEN", "")
    rng = random.Random(args.seed)
    keys = {problem["key"] for problem in json.load(open(PROBLEMS))} | {HEALTHY}

    rows = []
    rows += dataset_photos("plantdoc", PLANTDOC, ["train"], PLANTDOC_CLASSES, args.per_class, rng, token)
    rows += dataset_photos("plantdoc", PLANTDOC, ["test"], PLANTDOC_CLASSES, args.per_class, rng, token)
    rows += dataset_photos("plantvillage", PLANTVILLAGE, ["raw", "color"], PLANTVILLAGE_CLASSES, args.per_class, rng, token)
    for key, taxa in sorted(INATURALIST_TAXA.items()):
        found = inaturalist_photos(key, taxa, args.per_class, exclude=EXCLUDE.get(key))
        print(f"  inaturalist {key}: {len(found)} photos" + ("" if key in keys else " (not in the catalogue)"))
        rows += found
    rows += healthy_garden_plants(args.garden_species, 12, rng)

    for row in rows:
        row["held_for"] = held_for(row["observer"]) or "learning"
    path = os.path.join(args.out, "disease_photos.tsv")
    fields = ["source", "key", "label", "url", "observer", "held_for", "licence", "credit", "weight"]
    with open(path, "w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fields, delimiter="\t", lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)
    counts = {}
    for row in rows:
        tally = counts.setdefault(row["key"], {})
        tally[row["held_for"]] = tally.get(row["held_for"], 0) + 1
    for key in sorted(counts):
        print(f"  {key}: " + ", ".join(f"{n} {s}" for s, n in sorted(counts[key].items())))
    print(f"Wrote {len(rows)} photos to {path}")


if __name__ == "__main__":
    main()
