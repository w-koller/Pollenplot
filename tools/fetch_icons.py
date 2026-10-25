"""Downloads Material Symbols Rounded icons and writes them as Android vector drawables.

Usage: python3 tools/fetch_icons.py yard local_florist:fill water_drop

Each name becomes app/src/main/res/drawable/ic_<name>.xml. A name ending in ":fill" fetches the filled
style and writes ic_<name>_filled.xml, which the navigation bar uses for the selected tab. Material
Symbols are published under the Apache License 2.0, and each file names the icon it came from.
"""
import os
import re
import sys
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app", "src", "main", "res", "drawable")
URL = "https://fonts.gstatic.com/s/i/short-term/release/materialsymbolsrounded/{name}/{style}/24px.svg"

TEMPLATE = """<?xml version="1.0" encoding="utf-8"?>
<!-- Material Symbols Rounded "{name}"{style_note}, Apache License 2.0. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="{width}"
    android:viewportHeight="{height}">
    <group android:translateY="{shift}">
{paths}
    </group>
</vector>
"""


def fetch(name: str, filled: bool) -> str:
    style = "fill1" if filled else "default"
    with urllib.request.urlopen(URL.format(name=name, style=style), timeout=30) as response:
        svg = response.read().decode("utf-8")
    view_box = re.search(r'viewBox="([-\d.]+) ([-\d.]+) ([\d.]+) ([\d.]+)"', svg)
    if not view_box:
        raise SystemExit(f"{name}: the icon has no viewBox")
    min_x, min_y, width, height = view_box.groups()
    if float(min_x) != 0:
        raise SystemExit(f"{name}: a viewBox that starts away from x = 0 is not supported")
    paths = re.findall(r'<path[^>]*\sd="([^"]+)"', svg)
    if not paths:
        raise SystemExit(f"{name}: the icon has no paths")
    lines = "\n".join(f'        <path android:fillColor="#FF000000" android:pathData="{d}" />' for d in paths)
    shift = -float(min_y)
    return TEMPLATE.format(
        name=name,
        style_note=", filled" if filled else "",
        width=width,
        height=height,
        shift=int(shift) if shift.is_integer() else shift,
        paths=lines,
    )


def main(names: list[str]) -> None:
    if not names:
        raise SystemExit(__doc__)
    os.makedirs(OUT, exist_ok=True)
    for entry in names:
        name, _, style = entry.partition(":")
        filled = style == "fill"
        file_name = f"ic_{name}{'_filled' if filled else ''}.xml"
        with open(os.path.join(OUT, file_name), "w", encoding="utf-8") as f:
            f.write(fetch(name, filled))
        print(f"Wrote {file_name}")


if __name__ == "__main__":
    main(sys.argv[1:])
