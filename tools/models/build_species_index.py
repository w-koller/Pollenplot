"""Builds the species index the accurate identifier (BioCAP) chooses from: tools/models/species_index.tsv.

The index is the plants a backyard in Australia is likely to show:
- every species in the app's knowledge base,
- every species the quick model (AIY Plants V1) knows,
- the cultivated and wild plants most often recorded in Australia on iNaturalist,
- Brisbane City Council's weed list and the national weed lists on the Atlas of Living Australia.

Each species gets its taxonomy from the GBIF backbone, which matches the taxonomy BioCAP was trained with, and a common
name from the knowledge base or iNaturalist where there is one. Every web lookup is cached in --cache, so a rerun
only asks for what is new. Run it with the standard library only:

    python3 tools/models/build_species_index.py --cache /path/to/cache
"""
import argparse
import concurrent.futures
import csv
import glob
import json
import os
import re
import sys
import threading
import time
import urllib.parse
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "tools", "models", "species_index.tsv")
AIY_LABELS = os.path.join(ROOT, "app", "src", "main", "assets", "ml", "aiy_plants_v1_labels.txt")
KB_SPECIES = os.path.join(ROOT, "app", "src", "main", "assets", "kb", "species", "*.json")

INAT_AUSTRALIA = 6744
USER_AGENT = "Pollenplot species index builder (https://github.com/w-koller/Pollenplot)"
RANKS = ["kingdom", "phylum", "class", "order", "family", "genus", "species"]


class Cache:
    """Remembers each web lookup by its URL, saved after every few hundred new answers. Safe to use from threads."""

    def __init__(self, path):
        self.path = path
        self.data = json.load(open(path)) if os.path.exists(path) else {}
        self.unsaved = 0
        self.lock = threading.Lock()

    def get(self, url, pause):
        with self.lock:
            if url in self.data:
                return self.data[url]
        for attempt in range(6):
            try:
                request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT, "Accept": "application/json"})
                with urllib.request.urlopen(request, timeout=60) as response:
                    value = json.load(response)
                break
            except Exception as error:  # Flaky DNS and rate limits: wait and try again.
                if attempt == 5:
                    raise
                print(f"  retry {attempt + 1} for {url}: {error}", file=sys.stderr)
                time.sleep(5 * (attempt + 1))
        time.sleep(pause)
        with self.lock:
            self.data[url] = value
            self.unsaved += 1
            if self.unsaved >= 200:
                self._save()
        return value

    def save(self):
        with self.lock:
            self._save()

    def _save(self):
        tmp = self.path + ".part"
        with open(tmp, "w") as f:
            json.dump(self.data, f)
        os.replace(tmp, self.path)
        self.unsaved = 0


def kb_species():
    """The knowledge base's species: name, common name and synonyms."""
    species = []
    for path in sorted(glob.glob(KB_SPECIES)):
        for item in json.load(open(path)):
            species.append((item["name"], (item.get("common") or [None])[0], item.get("synonyms") or []))
    return species


def aiy_labels():
    labels = [line.strip() for line in open(AIY_LABELS) if line.strip()]
    return [label for label in labels if label != "None"]


def inat_top(cache, captive, count):
    """The plants most observed in Australia on iNaturalist, with their Australian English common names."""
    results = []
    page = 1
    while len(results) < count:
        query = {
            "place_id": INAT_AUSTRALIA,
            "iconic_taxa": "Plantae",
            "captive": "true" if captive else "false",
            "per_page": 500,
            "page": page,
            "locale": "en-AU",
        }
        if not captive:
            query["quality_grade"] = "research"
        url = "https://api.inaturalist.org/v1/observations/species_counts?" + urllib.parse.urlencode(query)
        data = cache.get(url, pause=1.0)
        if not data["results"]:
            break
        for row in data["results"]:
            taxon = row["taxon"]
            if taxon.get("rank") == "species":
                results.append((taxon["name"], taxon.get("preferred_common_name"), row["count"]))
        page += 1
    return results[:count]


def brisbane_weeds(cache):
    names = []
    for offset in range(0, 400, 100):
        url = (
            "https://data.brisbane.qld.gov.au/api/explore/v2.1/catalog/datasets/brisbane-weeds/records?"
            + urllib.parse.urlencode({"limit": 100, "offset": offset})
        )
        for row in cache.get(url, pause=0.2)["results"]:
            for name in binomials(row.get("payload_fields_attributes_botanicalname") or ""):
                names.append((name, row.get("payload_fields_attributes_primarycommonname")))
    return names


NOT_EPITHETS = {"syn", "spp", "sp", "subsp", "ssp", "var", "cv", "agg", "aggregate", "other", "than", "and", "except"}


def binomials(field):
    """The species a weed list's botanical name field names, such as "Dyschoriste nagchana Dyschoriste depressa" or
    "Sphagneticola trilobata syn. Wedelia trilobata", as the app's WeedNames reads them."""
    words = [w for w in re.split(r"[\s,;()]+", field.replace("\u00a0", " ").replace("×", " ")) if w and w != "x"]
    found = []
    for genus, epithet in zip(words, words[1:]):
        if re.fullmatch(r"[A-Z][a-z]+", genus) and re.fullmatch(r"[a-z][a-z-]+", epithet) and epithet not in NOT_EPITHETS:
            name = f"{genus} {epithet}"
            if name not in found:
                found.append(name)
    return found


def ala_list(cache, uid):
    """The species on one of the Atlas of Living Australia's species lists."""
    url = f"https://lists-ws.ala.org.au/ws/speciesListItems/{uid}?max=1000"
    names = []
    for item in cache.get(url, pause=0.2):
        # The list's spelling and the name the Atlas matched it to, read the way the app reads them.
        for field in (item.get("name"), item.get("scientificName")):
            names += [n for n in binomials(field or "") if n not in names]
    return names


def inat_classification(cache, name):
    """Kingdom to genus as iNaturalist has them, for a species the GBIF backbone does not know by this name yet, such
    as tea trees that Australia's census moved from Leptospermum to Gaudium. iNaturalist's plant classes and phyla
    are the GBIF backbone's, so the taxonomy reads the same way. None if iNaturalist does not know the name either."""
    url = "https://api.inaturalist.org/v1/taxa?" + urllib.parse.urlencode({"q": name, "rank": "species", "per_page": 1})
    results = cache.get(url, pause=1.0)["results"]
    if not results or results[0]["name"] != name:
        return None
    taxon = results[0]
    ancestors = {}
    ids = taxon["ancestor_ids"]
    for start in range(0, len(ids), 30):
        url = "https://api.inaturalist.org/v1/taxa/" + ",".join(str(i) for i in ids[start:start + 30])
        for ancestor in cache.get(url, pause=1.0)["results"]:
            ancestors[ancestor["rank"]] = ancestor["name"]
    if not all(ancestors.get(rank) for rank in RANKS[:-1]):
        return None
    return {**{rank: ancestors[rank] for rank in RANKS[:-1]}, "species": name, "key": f"inat:{taxon['id']}"}


def gbif_match(cache, name):
    url = "https://api.gbif.org/v1/species/match?" + urllib.parse.urlencode({"name": name, "kingdom": "Plantae", "strict": "true"})
    return cache.get(url, pause=0.05)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cache", required=True, help="A JSON file for the web lookups, kept between runs")
    parser.add_argument("--inat-cultivated", type=int, default=3000)
    parser.add_argument("--inat-wild", type=int, default=5000)
    args = parser.parse_args()
    cache = Cache(args.cache)

    # Each candidate: the name a source uses, a common name if the source has one, and where it came from.
    candidates = []
    kb_names = {}
    for name, common, synonyms in kb_species():
        candidates.append((name, common, "kb"))
        kb_names[name] = name
        for synonym in synonyms:
            kb_names[synonym] = name
    for label in aiy_labels():
        candidates.append((label, None, "aiy"))
    print("iNaturalist, cultivated", file=sys.stderr)
    inat_counts = {}
    for name, common, count in inat_top(cache, captive=True, count=args.inat_cultivated):
        candidates.append((name, common, "inat_cultivated"))
        inat_counts[name] = inat_counts.get(name, 0) + count
    print("iNaturalist, wild", file=sys.stderr)
    for name, common, count in inat_top(cache, captive=False, count=args.inat_wild):
        candidates.append((name, common, "inat_wild"))
        inat_counts[name] = inat_counts.get(name, 0) + count
    print("Brisbane weeds", file=sys.stderr)
    for name, common in brisbane_weeds(cache):
        candidates.append((name, common, "brisbane_weed"))
    print("National weed lists", file=sys.stderr)
    for uid in ("dr823", "dr35447"):  # Weeds of National Significance; the National Alert List for Environmental Weeds.
        for name in ala_list(cache, uid):
            candidates.append((name, None, "national_weed"))

    # One entry for each species the GBIF backbone accepts, so synonyms from different sources become one entry.
    entries = {}
    unmatched = []
    names = sorted({c[0] for c in candidates})
    print(f"GBIF taxonomy for {len(names)} names", file=sys.stderr)
    matches = {}
    # A few lookups at a time: one at a time, GBIF takes hours for this many names.
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
        for i, (name, match) in enumerate(zip(names, pool.map(lambda n: gbif_match(cache, n), names))):
            if i % 500 == 0:
                print(f"  {i}", file=sys.stderr, flush=True)
            if match.get("matchType") in ("EXACT", "FUZZY") and match.get("kingdom") == "Plantae" and match.get("species") and all(match.get(r) for r in RANKS):
                matches[name] = {**match, "key": f"gbif:{match.get('speciesKey', match['usageKey'])}"}
            else:
                unmatched.append(name)
    # Names iNaturalist gave that the backbone does not have yet are classified as iNaturalist has them.
    inat_names = {c[0] for c in candidates if c[2].startswith("inat_")}
    for name in [n for n in unmatched if n in inat_names]:
        match = inat_classification(cache, name)
        if match:
            matches[name] = match
            unmatched.remove(name)
    cache.save()

    for name, common, source in candidates:
        match = matches.get(name)
        if match is None:
            continue
        key = match["key"]
        entry = entries.setdefault(key, {
            "taxonomy": " ".join(match[r] if r != "species" else match["species"].split()[1] for r in RANKS),
            "gbif": match["species"],
            "names": [],
            "common": None,
            "sources": set(),
            "kb": None,
            "observations": 0,
        })
        if name not in entry["names"]:
            entry["names"].append(name)
        entry["sources"].add(source)
        if name in kb_names:
            entry["kb"] = kb_names[name]
        # The knowledge base's common name comes first, then iNaturalist's Australian English name.
        if common and (entry["common"] is None or source == "kb"):
            entry["common"] = common
        entry["observations"] = max(entry["observations"], inat_counts.get(name, 0))

    # The name the app shows: the knowledge base's, then the first source's. Two GBIF species can reach the same
    # knowledge base entry, one through a synonym GBIF keeps apart; the entry the knowledge base names keeps the name.
    claimed = {}
    for key, entry in entries.items():
        if entry["kb"] and (entry["kb"] in entry["names"] or entry["kb"] not in claimed):
            claimed[entry["kb"]] = key
    rows = []
    for key, entry in entries.items():
        label = entry["kb"] if entry["kb"] and claimed.get(entry["kb"]) == key else next(n for n in entry["names"] if n != entry["kb"])
        rows.append([
            label,
            entry["taxonomy"],
            entry["common"] or "",
            ",".join(sorted(entry["sources"])),
            "|".join(n for n in entry["names"] if n != label),
            str(entry["observations"]),
            str(key),
        ])
    rows.sort(key=lambda r: (-int(r[5]), r[0]))
    with open(OUT, "w", newline="") as f:
        writer = csv.writer(f, delimiter="\t", lineterminator="\n")
        writer.writerow(["name", "taxonomy", "common", "sources", "aliases", "observations", "taxon"])
        writer.writerows(rows)
    print(f"Wrote {len(rows)} species to {os.path.relpath(OUT, ROOT)}; {len(unmatched)} names had no GBIF species match.", file=sys.stderr)
    with open(os.path.join(os.path.dirname(args.cache), "unmatched_names.txt"), "w") as f:
        f.write("\n".join(unmatched) + "\n")


if __name__ == "__main__":
    main()
