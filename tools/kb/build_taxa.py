#!/usr/bin/env python3
"""Writes app/src/main/assets/kb/taxa.tsv: the genus, family and common name of every plant the models can name.

The accurate packs name thousands of plants, and the knowledge base describes a few hundred of them. For the rest the
app still knows the genus and family from this table, so a plant named by BioCAP or BioCLIP 2 gets the facts its genus
and family records hold, and its common name. The table comes from tools/models/species_index.tsv, whose taxonomy is
GBIF's backbone (or iNaturalist's where GBIF does not know the name yet).

Run it again after the species index changes:

    python3 tools/kb/build_taxa.py
"""
import csv
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[2]
INDEX = ROOT / "tools/models/species_index.tsv"
OUT = ROOT / "app/src/main/assets/kb/taxa.tsv"
RANKS = ["kingdom", "phylum", "class", "order", "family", "genus", "species"]


def main() -> None:
    rows = []
    with INDEX.open(newline="") as handle:
        for row in csv.DictReader(handle, delimiter="\t"):
            if row["group"] != "plant":
                continue
            taxonomy = row["taxonomy"].split(" ")
            if len(taxonomy) < 6:
                continue
            family, genus = taxonomy[RANKS.index("family")], taxonomy[RANKS.index("genus")]
            aliases = [a for a in row["aliases"].split("|") if a and a != row["name"]]
            common = row["common"].strip()
            rows.append((row["name"], genus, family, common[:1].upper() + common[1:], "|".join(aliases)))
    rows.sort()
    with OUT.open("w", newline="") as handle:
        handle.write("# name\tgenus\tfamily\tcommon\taliases. Made by tools/kb/build_taxa.py from tools/models/species_index.tsv.\n")
        for row in rows:
            handle.write("\t".join(field.replace("\t", " ") for field in row) + "\n")
    print(f"{len(rows)} plants written to {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
