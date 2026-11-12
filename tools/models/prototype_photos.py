"""Chooses the photos the species' prototypes are made from.

A prototype is the mean of BioCAP's vectors of a species' photos, which the pack holds beside the species' text
vector. This reads iNaturalist's open data (https://github.com/inaturalist/inaturalist-open-data), which lists every
observation and photo, and writes, for each species of tools/models/species_index.tsv, up to a number of photos that
are:

- of a research-grade observation in Australia (a box around the mainland and Tasmania),
- the first photo of its observation, under CC0 or CC BY, so a pack made from them can be shared with attribution,
- by an observer not kept for evaluation or tuning (photos.held_for), and
- spread over as many observers as the species has, so no one garden or camera fills a prototype.

The two large files are about 14 GB and 21 GB, read as streams and never kept, so this needs little disk. It needs
only Python and the shell's curl, gzip and awk. It runs in GitHub Actions (.github/workflows/models.yml).
"""
import argparse
import csv
import os
import random
import subprocess

from photos import group_of, held_for

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
INDEX = os.path.join(ROOT, "tools", "models", "species_index.tsv")
BUCKET = "https://inaturalist-open-data.s3.amazonaws.com"

# iNaturalist's numbers for the kingdoms, which tell a plant's name from the same name given to an animal.
KINGDOMS = {"plant": "47126", "fungus": "47170"}
ANIMALS = "1"

# South, north, west and east of the mainland and Tasmania, in degrees.
AUSTRALIA = (-44.0, -10.0, 112.0, 154.0)

# The observations of each species that photos are looked for, chosen at random from all it has.
CANDIDATES = 80

OBSERVATIONS = (
    'NR > 1 && $7 == "research" && $3 != "" && $3 + 0 >= %s && $3 + 0 <= %s && $4 + 0 >= %s && $4 + 0 <= %s '
    '{ print $1 "\\t" $2 "\\t" $6 }' % AUSTRALIA
)
PHOTOS = (
    'NR == FNR { want[$1]; next } '
    '($3 in want) && $9 == 0 && (toupper($6) == "CC0" || toupper($6) == "CC-BY") '
    '{ print $2 "\\t" $3 "\\t" $4 "\\t" $5 "\\t" toupper($6) }'
)


def stream(name, awk=None, before=""):
    """One of the open data's tables, row by row, through [awk] when given, with [before] as a file awk reads first."""
    command = f"curl -sSfL {BUCKET}/{name}.csv.gz | gzip -dc"
    if awk:
        command += f" | awk -F'\\t' '{awk}' {before} -"
    process = subprocess.Popen(["bash", "-o", "pipefail", "-c", command], stdout=subprocess.PIPE, text=True, encoding="utf-8", errors="replace")
    for line in process.stdout:
        yield line.rstrip("\n").split("\t")
    assert process.wait() == 0, f"Reading {name} failed"


def taxa_of(rows, taxa):
    """iNaturalist's taxon numbers for the index's species, and for the subspecies and varieties under them: each
    number with its row of the index. A name counts only in the species' own kingdom."""
    wanted = {}
    for i, row in enumerate(rows):
        for name in [row["name"]] + [alias for alias in row["aliases"].split("|") if alias]:
            wanted.setdefault(name.lower(), []).append(i)
    taxa = [taxon for taxon in taxa if len(taxon) >= 5 and taxon[0].isdigit()]
    found = {}
    for number, ancestry, _, rank, name, *_ in taxa:
        if rank not in ("species", "hybrid"):
            continue
        ancestors = ancestry.split("/")
        for i in wanted.get(name.lower(), []):
            if KINGDOMS.get(group_of(rows[i]), ANIMALS) in ancestors:
                found.setdefault(number, i)
                break
    under = {}
    for number, ancestry, *_ in taxa:
        if number in found:
            continue
        for ancestor in reversed(ancestry.split("/")):
            if ancestor in found:
                under[number] = found[ancestor]
                break
    return {**found, **under}


def spread(photos, count, rng):
    """Up to [count] of a species' photos, taking one from each observer in turn."""
    by_observer = {}
    for photo in photos:
        by_observer.setdefault(photo["observer"], []).append(photo)
    queues = list(by_observer.values())
    rng.shuffle(queues)
    chosen = []
    while queues and len(chosen) < count:
        for queue in queues:
            if len(chosen) < count:
                chosen.append(queue.pop())
        queues = [queue for queue in queues if queue]
    return chosen


def choose(rows, taxa, observations, photos_for, per_species, seed):
    """The chosen photos, from the tables' rows. [photos_for] gives the photo rows of a set of observations."""
    rng = random.Random(seed)
    species_of = taxa_of(rows, taxa)
    print(f"{len(set(species_of.values()))} of {len(rows)} species are in iNaturalist's taxa")
    candidates = {}
    seen = {}
    for uuid, observer, taxon in observations:
        row = species_of.get(taxon)
        if row is None or not observer.isdigit() or held_for(observer) is not None:
            continue
        seen[row] = seen.get(row, 0) + 1
        pool = candidates.setdefault(row, [])
        if len(pool) < CANDIDATES:
            pool.append(uuid)
        else:
            # A reservoir, so each of the species' observations is as likely to be kept as any other.
            place = rng.randrange(seen[row])
            if place < CANDIDATES:
                pool[place] = uuid
    row_of = {uuid: row for row, pool in candidates.items() for uuid in pool}
    print(f"{sum(seen.values())} observations of {len(seen)} species in Australia, {len(row_of)} looked at for photos")
    found = {}
    for photo, uuid, observer, extension, licence in photos_for(row_of):
        if uuid in row_of and extension.lower() in ("jpg", "jpeg", "png"):
            found.setdefault(row_of[uuid], []).append({"photo": photo, "observer": observer, "extension": extension, "licence": licence})
    chosen = []
    for row in sorted(found):
        # Sorted first, so the choice does not depend on the order the table gave them in.
        for photo in spread(sorted(found[row], key=lambda p: int(p["photo"])), per_species, rng):
            chosen.append({"row": row, "name": rows[row]["name"], **photo})
    return chosen


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", required=True)
    parser.add_argument("--per-species", type=int, default=16, help="The most photos of each species")
    parser.add_argument("--seed", type=int, default=3)
    args = parser.parse_args()
    os.makedirs(args.out, exist_ok=True)
    rows = list(csv.DictReader(open(INDEX), delimiter="\t"))

    def photos_for(row_of):
        wanted = os.path.join(args.out, "observations.txt")
        with open(wanted, "w") as f:
            f.writelines(uuid + "\n" for uuid in row_of)
        yield from stream("photos", PHOTOS, before=wanted)
        os.remove(wanted)

    chosen = choose(rows, stream("taxa"), stream("observations", OBSERVATIONS), photos_for, args.per_species, args.seed)
    observers = {photo["observer"] for photo in chosen}
    names = {number: (login, name) for number, login, name, *_ in (line + ["", ""] for line in stream("observers")) if number in observers}
    path = os.path.join(args.out, "prototype_photos.tsv")
    with open(path, "w", newline="") as f:
        writer = csv.writer(f, delimiter="\t", lineterminator="\n")
        writer.writerow(["row", "name", "photo", "extension", "licence", "observer", "login", "observer_name"])
        for photo in chosen:
            login, name = names.get(photo["observer"], ("", ""))
            writer.writerow([photo["row"], photo["name"], photo["photo"], photo["extension"], photo["licence"], photo["observer"], login, name])
    counts = {}
    for photo in chosen:
        counts[photo["row"]] = counts.get(photo["row"], 0) + 1
    by_group = {}
    for row, count in counts.items():
        tally = by_group.setdefault(group_of(rows[row]), [0, 0])
        tally[0] += 1
        tally[1] += count
    print(f"{len(chosen)} photos of {len(counts)} species, {sum(1 for c in counts.values() if c >= 3)} with three or more")
    for group, (species, photos) in sorted(by_group.items(), key=lambda item: -item[1][1]):
        print(f"  {group}: {photos} photos of {species} species")
    print(f"Wrote {path}")


if __name__ == "__main__":
    main()
