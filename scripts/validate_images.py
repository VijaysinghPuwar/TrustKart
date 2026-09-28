#!/usr/bin/env python3
"""
Deterministic catalog image validation (no AI). Run from the repo root:

    python3 scripts/validate_images.py            # human-readable summary, exit 1 on errors
    python3 scripts/validate_images.py --json     # machine-readable report

Checks every product in backend/src/main/resources/demo/products.json against demo/images.json:
  - an image record exists (or the product is explicitly marked as using the TrustKart fallback)
  - both files exist under frontend/public, are non-empty and really are WebP
  - the large image is at least MIN_LARGE px on its long side, the small one at least MIN_SMALL
  - recorded width/height match the actual file
  - alt text is present and not a generic placeholder
  - provenance (source page, author, license) is recorded
  - the same image file is not reused for unrelated products
Errors fail CI. Warnings (e.g. non-exact model match) are reported for human review.
"""
import hashlib
import json
import struct
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PRODUCTS = ROOT / "backend/src/main/resources/demo/products.json"
IMAGES = ROOT / "backend/src/main/resources/demo/images.json"
PUBLIC = ROOT / "frontend/public"
MIN_LARGE = 600
MIN_SMALL = 300
GENERIC_ALT = {"image", "photo", "product", "product image", "picture"}


def webp_size(path: Path):
    """Returns (width, height) of a WebP file, or None if it isn't one. Supports VP8, VP8L and VP8X."""
    data = path.read_bytes()[:40]
    if len(data) < 30 or data[:4] != b"RIFF" or data[8:12] != b"WEBP":
        return None
    chunk = data[12:16]
    if chunk == b"VP8 ":
        w, h = struct.unpack("<HH", data[26:30])
        return w & 0x3FFF, h & 0x3FFF
    if chunk == b"VP8L":
        b = data[21:25]
        w = 1 + (((b[1] & 0x3F) << 8) | b[0])
        h = 1 + (((b[3] & 0x0F) << 10) | (b[2] << 2) | ((b[1] & 0xC0) >> 6))
        return w, h
    if chunk == b"VP8X":
        w = 1 + int.from_bytes(data[24:27], "little")
        h = 1 + int.from_bytes(data[27:30], "little")
        return w, h
    return None


def main() -> int:
    products = json.loads(PRODUCTS.read_text())["products"]
    images = json.loads(IMAGES.read_text())
    errors, warnings, report = [], [], []
    by_hash = {}

    for p in products:
        slug = p["slug"]
        img = images.get(slug)
        row = {"slug": slug, "brand": p["brand"], "name": p["name"]}
        if img is None:
            if p.get("imageFallback"):
                row["status"] = "fallback"
            else:
                errors.append(f"{slug}: no image record and not marked imageFallback")
                row["status"] = "missing"
            report.append(row)
            continue
        row.update(match=img.get("match"), source=img.get("filePage"), license=img.get("license"), author=img.get("author"))
        for key, minimum in (("large", MIN_LARGE), ("small", MIN_SMALL)):
            path = PUBLIC / img[key].lstrip("/")
            if not path.is_file() or path.stat().st_size == 0:
                errors.append(f"{slug}: {key} file missing or empty ({img[key]})")
                continue
            size = webp_size(path)
            if size is None:
                errors.append(f"{slug}: {key} is not a valid WebP ({img[key]})")
                continue
            if max(size) < minimum:
                errors.append(f"{slug}: {key} is only {size[0]}x{size[1]} (min {minimum}px long side)")
            if key == "large":
                row.update(width=size[0], height=size[1], bytes=path.stat().st_size)
                if (img.get("width"), img.get("height")) != size:
                    errors.append(f"{slug}: recorded size {img.get('width')}x{img.get('height')} != actual {size[0]}x{size[1]}")
                digest = hashlib.sha256(path.read_bytes()).hexdigest()
                by_hash.setdefault(digest, []).append(slug)
        alt = (img.get("alt") or "").strip()
        if len(alt) < 8 or alt.lower() in GENERIC_ALT:
            errors.append(f"{slug}: missing or generic alt text")
        for field in ("filePage", "author", "license"):
            if not img.get(field):
                errors.append(f"{slug}: provenance field '{field}' is empty")
        if img.get("match") not in ("EXACT", "RENDER"):
            warnings.append(f"{slug}: image match is {img.get('match')}, review for accuracy")
        row["status"] = "ok"
        report.append(row)

    for digest, slugs in by_hash.items():
        if len(slugs) > 1:
            errors.append(f"same image file used for unrelated products: {', '.join(slugs)}")

    if "--json" in sys.argv:
        print(json.dumps({"errors": errors, "warnings": warnings, "products": report}, indent=1))
    else:
        exact = sum(1 for r in report if r.get("match") == "EXACT")
        renders = sum(1 for r in report if r.get("match") == "RENDER")
        print(f"{len(products)} products checked: {exact} exact-model photos, {renders} TrustKart renders, "
              f"{sum(1 for r in report if r['status'] == 'fallback')} fallbacks, {len(warnings)} warnings, {len(errors)} errors")
        for e in errors:
            print("ERROR  " + e)
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
