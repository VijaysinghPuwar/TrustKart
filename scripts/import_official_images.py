#!/usr/bin/env python3
"""
Imports manufacturer product photos into the catalog.

Input: one or more manifest JSON files, each a list of
  {"slug", "file", "imageUrl", "pageUrl", "brand", "match", "alt", "notes"}
where "file" is relative to the manifest's directory.

Each photo is flattened onto white, trimmed to the product, padded to a square with an even margin and written as
WebP at 800 and 400 px. images.json records the manufacturer page as the source. Usage rights for manufacturer images
are NOT verified by this script: they are recorded as "Manufacturer product image (usage rights not verified)" so the
status is visible in docs and on the credits page.

  python3 scripts/import_official_images.py manifest1.json [manifest2.json ...]
"""
from __future__ import annotations

import json
import subprocess
import sys
import tempfile
from pathlib import Path

from PIL import Image, ImageChops

ROOT = Path(__file__).resolve().parents[1]
IMAGES = ROOT / "backend/src/main/resources/demo/images.json"
CREDITS = ROOT / "frontend/public/credits.json"
OUT = ROOT / "frontend/public/images/products"
LICENSE = "Manufacturer product image (usage rights not verified)"
MARGIN = 0.06  # empty space around the product, as a fraction of the square's side
MIN_SIDE = 600


def normalize(src: Path) -> Image.Image:
    img = Image.open(src)
    img.load()
    if img.mode in ("RGBA", "LA", "P"):
        img = img.convert("RGBA")
        white = Image.new("RGBA", img.size, (255, 255, 255, 255))
        img = Image.alpha_composite(white, img)
    img = img.convert("RGB")
    # Trim the near-white border so every product sits at the same scale.
    diff = ImageChops.difference(img, Image.new("RGB", img.size, (255, 255, 255)))
    bbox = diff.convert("L").point(lambda v: 255 if v > 12 else 0).getbbox()
    if bbox:
        img = img.crop(bbox)
    w, h = img.size
    side = int(max(w, h) / (1 - 2 * MARGIN))
    canvas = Image.new("RGB", (side, side), (255, 255, 255))
    canvas.paste(img, ((side - w) // 2, (side - h) // 2))
    return canvas


def main(manifests: list[str]) -> None:
    images = json.loads(IMAGES.read_text())
    credits = json.loads(CREDITS.read_text())
    catalog = set(images)
    done, skipped = [], []
    with tempfile.TemporaryDirectory() as tmp:
        for m in manifests:
            base = Path(m).resolve().parent
            for e in json.loads(Path(m).read_text()):
                slug = e["slug"]
                if not e.get("file"):
                    skipped.append(f"{slug}: {e.get('notes', 'not sourced')}")
                    continue
                if slug not in catalog:
                    skipped.append(f"{slug}: not in catalog")
                    continue
                square = normalize(base / e["file"])
                if square.width < MIN_SIDE:
                    skipped.append(f"{slug}: source too small ({square.width}px)")
                    continue
                large = square.resize((800, 800), Image.LANCZOS) if square.width >= 800 else square
                png = Path(tmp) / f"{slug}.png"
                large.save(png)
                subprocess.run(["cwebp", "-quiet", "-q", "88", "-m", "6", str(png), "-o",
                                str(OUT / f"{slug}-800.webp")], check=True)
                subprocess.run(["cwebp", "-quiet", "-q", "88", "-m", "6", "-resize", "400", "400", str(png),
                                "-o", str(OUT / f"{slug}-400.webp")], check=True)
                images[slug] = {
                    "large": f"/images/products/{slug}-800.webp",
                    "small": f"/images/products/{slug}-400.webp",
                    "width": large.width,
                    "height": large.height,
                    "alt": e["alt"],
                    "match": e["match"],
                    "author": e["brand"],
                    "license": LICENSE,
                    "licenseUrl": e["pageUrl"],
                    "filePage": e["pageUrl"],
                }
                credits = [c for c in credits if c["file"] != slug]
                credits.append({"file": slug, "title": e["alt"], "source": e["pageUrl"], "author": e["brand"],
                                "license": LICENSE, "licenseUrl": e["pageUrl"],
                                "match": e["match"].lower().replace("_", " ")})
                done.append(slug)
    IMAGES.write_text(json.dumps(images, indent=1, ensure_ascii=False) + "\n")
    CREDITS.write_text(json.dumps(credits))
    print(f"imported {len(done)}")
    for s in skipped:
        print("SKIPPED " + s)


if __name__ == "__main__":
    main(sys.argv[1:])
