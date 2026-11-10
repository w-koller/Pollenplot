"""The evaluation photos and the phone's preparation of a photo, shared by the scripts that evaluate the models.

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


# How many species of each group the evaluation photographs: plants most, as most photos show plants.
EVAL_PLAN = {"plant": 120, "bird": 25, "insect": 25, "spider": 8, "reptile": 8, "frog": 8, "mammal": 8, "fungus": 8}


def group_of(row):
    return row.get("group") or "plant"


def evaluation_photos(rows, plan, per_species, seed):
    """Research-grade iNaturalist photos with open licences, for species chosen from each group's most observed."""
    random.seed(seed)
    chosen = []
    for group, species_count in plan.items():
        observed = [i for i, row in enumerate(rows) if group_of(row) == group and int(row["observations"]) > 0]
        if observed:
            chosen += random.sample(observed[: species_count * 3], min(species_count, len(observed[: species_count * 3])))
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
