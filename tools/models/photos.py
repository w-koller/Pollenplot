"""The evaluation photos, the phone's preparation of a photo and its looks at one, shared by the scripts that build
and evaluate the models.

It needs only Pillow, so a script that runs the shipped models does not need PyTorch.
"""
import io
import json
import random
import time
import urllib.parse
import urllib.request

from PIL import Image

SIZE = 224
USER_AGENT = "Pollenplot model pack builder (https://github.com/w-koller/Pollenplot)"


def phone_square(image):
    """The phone's preparation (Bitmaps.squarePixels and smoothScale in the app): the middle square, halved while it
    is at least twice 224 across, which averages each 2 x 2 block, then scaled to 224 x 224 with bilinear filtering."""
    image = image.convert("RGB")
    side = min(image.size)
    left = (image.width - side) // 2
    top = (image.height - side) // 2
    square = image.crop((left, top, left + side, top + side))
    while square.width >= SIZE * 2:
        square = square.resize((square.width // 2, square.height // 2), Image.BOX)
    return square if square.size == (SIZE, SIZE) else square.resize((SIZE, SIZE), Image.BILINEAR)


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


# How many species of each group the pack's own evaluation photographs: plants most, as most photos show plants.
EVAL_PLAN = {"plant": 120, "bird": 25, "insect": 25, "spider": 8, "reptile": 8, "frog": 8, "mammal": 8, "fungus": 8}

# The fusion evaluation gives animals as many species each as birds and insects, fish and molluscs included, so each
# group's numbers mean something.
FUSION_PLAN = {**EVAL_PLAN, **{group: 25 for group in ("spider", "reptile", "frog", "mammal", "fungus", "fish", "mollusc")}}

# Observers are divided by their iNaturalist number so no photo, and no photographer's other photos of the same plant,
# is both learned from and judged with: one observer in HELD_OUT is kept for evaluation, one for tuning, and the
# species' prototypes are made from the photos of the rest (prototype_photos.py).
HELD_OUT = 5
EVALUATION = "evaluation"
TUNING = "tuning"


def held_for(observer):
    """The set an observer's photos are kept for, or None when they may be learned from."""
    return {0: EVALUATION, 1: TUNING}.get(int(observer) % HELD_OUT)


# FastPlantIdentifier.CENTRE in the app: the middle 70% of each side, in basis points.
CENTRE = (1_500, 1_500, 8_500, 8_500)
FULL = 10_000


def centre_square(width, height, share_bp=7_000):
    """BoxBp.centreSquare in the app: the square in the middle, a share of the shorter side across, in pixels."""
    side = min(width, height) * share_bp // FULL
    left = (width - side) // 2
    top = (height - side) // 2
    return left, top, left + side, top + side


def square_around(box, width, height, padding_bp=1_000):
    """ThumbnailMath.squareAround in the app: the square around a box with a tenth of its longer side added, kept
    inside the image."""
    left, right = box[0] * width // FULL, box[2] * width // FULL
    top, bottom = box[1] * height // FULL, box[3] * height // FULL
    longer = max(right - left, bottom - top)
    size = max(1, min(longer + 2 * longer * padding_bp // FULL, min(width, height)))
    x = min(max((left + right) // 2 - size // 2, 0), width - size)
    y = min(max((top + bottom) // 2 - size // 2, 0), height - size)
    return x, y, x + size, y + size


def looks(width, height):
    """The three squares BioCAP looks at in a photo of one plant (AccurateModels.lookAtOne in the app): around the
    middle box, the whole middle square, and the middle 40%. An animal gets the first alone."""
    return {
        "centre": square_around(CENTRE, width, height),
        "middle": centre_square(width, height, FULL),
        "tight": centre_square(width, height, 4_000),
    }


def group_of(row):
    return row.get("group") or "plant"


def held_out_photos(rows, plan, per_species, seed, sets=(EVALUATION,)):
    """Research-grade Australian iNaturalist photos with open licences, for species chosen from each group's most
    observed, by the observers kept for each of [sets]: for each set, a list of (species row, photo), the photos with
    the most votes first."""
    random.seed(seed)
    chosen = []
    for group, species_count in plan.items():
        observed = [i for i, row in enumerate(rows) if group_of(row) == group and int(row["observations"]) > 0]
        if observed:
            chosen += random.sample(observed[: species_count * 3], min(species_count, len(observed[: species_count * 3])))
    photos = {name: [] for name in sets}
    for index in chosen:
        species = rows[index]["taxonomy"].split()
        name = f"{species[5]} {species[6]}"
        # One page of the most voted is asked for, as only some of its observers are kept for each set.
        query = urllib.parse.urlencode({
            "taxon_name": name, "quality_grade": "research", "photos": "true", "place_id": 6744,
            "photo_license": "cc0,cc-by,cc-by-nc", "per_page": 200, "order_by": "votes",
        })
        try:
            data = json.loads(fetch(f"https://api.inaturalist.org/v1/observations?{query}"))
        except Exception as error:
            print(f"  skipped {name}: {error}")
            continue
        taken = {name: 0 for name in sets}
        for observation in data.get("results", []):
            kept = held_for((observation.get("user") or {}).get("id") or 2)
            if kept not in taken or taken[kept] >= per_species or not observation.get("photos"):
                continue
            url = observation["photos"][0]["url"].replace("square", "medium")
            try:
                photos[kept].append((index, Image.open(io.BytesIO(fetch(url))).convert("RGB")))
                taken[kept] += 1
            except Exception:
                continue
        time.sleep(1.0)
    return photos


def evaluation_photos(rows, plan, per_species, seed):
    """The photos an evaluation judges with: those of the observers kept for evaluation."""
    return held_out_photos(rows, plan, per_species, seed)[EVALUATION]
