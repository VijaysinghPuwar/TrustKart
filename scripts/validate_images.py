#!/usr/bin/env python3
"""
Deterministic catalog image validation (no AI). Run from the repo root:

    python3 scripts/validate_images.py            # human-readable summary, exit 1 on errors
    python3 scripts/validate_images.py --json     # machine-readable report

Checks every product the seeder loads (demo/products.json plus every catalog/products/*.json file) against the
image records it would use (demo/images.json, overridden per slug by catalog/images.json):
  - slugs and SKUs are unique across all files
  - an image record exists (or the product is explicitly marked as using the TrustKart fallback, or is parked
    in catalog/parked.json, which hides it until an official image is sourced)
  - both files exist under frontend/public, are non-empty and really are WebP
  - the large image is at least MIN_LARGE px on its long side, the small one at least MIN_SMALL
  - recorded width/height match the actual file
  - alt text is present and not a generic placeholder
  - provenance (source page, author, license) is recorded
  - every option (variant) image exists, is a WebP of the recorded size and has real alt text
  - the same image file is not reused for unrelated demo products. The researched catalog legitimately shares a
    manufacturer photo between SKUs of one product line, so those groups are counted for review, not failed.
Errors fail CI. Warnings (e.g. non-exact model match) are reported for human review.
"""
import hashlib
import json
import struct
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RESOURCES = ROOT / "backend/src/main/resources"
PRODUCTS = RESOURCES / "demo/products.json"
IMAGES = RESOURCES / "demo/images.json"
CATALOG_PRODUCTS = RESOURCES / "catalog/products"
CATALOG_IMAGES = RESOURCES / "catalog/images.json"
CATALOG_PARKED = RESOURCES / "catalog/parked.json"
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


def load_catalog():
    """Products and image records exactly as DemoCatalogSeeder merges them. Returns (products, images, demo slugs, parked)."""
    demo = json.loads(PRODUCTS.read_text())["products"]
    products = list(demo)
    for path in sorted(CATALOG_PRODUCTS.glob("*.json")):
        products.extend(json.loads(path.read_text())["products"])
    images = json.loads(IMAGES.read_text())
    if CATALOG_IMAGES.is_file():
        images.update(json.loads(CATALOG_IMAGES.read_text()))
    parked = set(json.loads(CATALOG_PARKED.read_text())["skus"]) if CATALOG_PARKED.is_file() else set()
    return products, images, {p["slug"] for p in demo}, parked


def check_file(label, url, minimum, recorded, errors):
    """Validates one image file; returns its (width, height) or None."""
    path = PUBLIC / url.lstrip("/")
    if not path.is_file() or path.stat().st_size == 0:
        errors.append(f"{label} file missing or empty ({url})")
        return None
    size = webp_size(path)
    if size is None:
        errors.append(f"{label} is not a valid WebP ({url})")
        return None
    if max(size) < minimum:
        errors.append(f"{label} is only {size[0]}x{size[1]} (min {minimum}px long side)")
    if recorded is not None and recorded != size:
        errors.append(f"{label} recorded size {recorded[0]}x{recorded[1]} != actual {size[0]}x{size[1]}")
    return size


def generic_alt(alt) -> bool:
    alt = (alt or "").strip()
    return len(alt) < 8 or alt.lower() in GENERIC_ALT


def main() -> int:
    products, images, demo_slugs, parked = load_catalog()
    errors, warnings, report = [], [], []
    by_hash = {}
    option_images = 0

    for field in ("slug", "sku"):
        seen = {}
        for p in products:
            seen[p[field]] = seen.get(p[field], 0) + 1
        errors.extend(f"{field} '{v}' appears {n} times" for v, n in seen.items() if n > 1)

    for p in products:
        slug = p["slug"]
        for group in p.get("options") or []:
            for value in group["values"]:
                vimg = value.get("image")
                if not vimg:
                    continue
                option_images += 1
                label = f"{slug} option '{group['name']}: {value['label']}'"
                check_file(label + " large", vimg["large"], MIN_LARGE, (vimg.get("width"), vimg.get("height")), errors)
                check_file(label + " small", vimg["small"], MIN_SMALL, None, errors)
                if generic_alt(vimg.get("alt")):
                    errors.append(f"{label}: missing or generic alt text")
        img = images.get(slug)
        row = {"slug": slug, "brand": p["brand"], "name": p["name"]}
        if img is None:
            if p.get("imageFallback"):
                row["status"] = "fallback"
            elif p["sku"] in parked:
                row["status"] = "parked"
            else:
                errors.append(f"{slug}: no image record and not marked imageFallback")
                row["status"] = "missing"
            report.append(row)
            continue
        row.update(match=img.get("match"), source=img.get("filePage"), license=img.get("license"), author=img.get("author"))
        large = check_file(f"{slug}: large", img["large"], MIN_LARGE, (img.get("width"), img.get("height")), errors)
        check_file(f"{slug}: small", img["small"], MIN_SMALL, None, errors)
        if large is not None:
            path = PUBLIC / img["large"].lstrip("/")
            row.update(width=large[0], height=large[1], bytes=path.stat().st_size)
            by_hash.setdefault(hashlib.sha256(path.read_bytes()).hexdigest(), []).append(slug)
        if generic_alt(img.get("alt")):
            errors.append(f"{slug}: missing or generic alt text")
        for field in ("filePage", "author", "license"):
            if not img.get(field):
                errors.append(f"{slug}: provenance field '{field}' is empty")
        if img.get("match") not in ("EXACT", "RENDER"):
            warnings.append(f"{slug}: image match is {img.get('match')}, review for accuracy")
        row["status"] = "ok"
        report.append(row)

    shared_groups = 0
    for digest, slugs in by_hash.items():
        demo = [s for s in slugs if s in demo_slugs]
        if len(demo) > 1:
            errors.append(f"same image file used for unrelated products: {', '.join(demo)}")
        if len(slugs) > 1:
            shared_groups += 1

    if "--json" in sys.argv:
        print(json.dumps({"errors": errors, "warnings": warnings, "products": report}, indent=1))
    else:
        exact = sum(1 for r in report if r.get("match") == "EXACT")
        renders = sum(1 for r in report if r.get("match") == "RENDER")
        print(f"{len(products)} products checked: {exact} exact-model photos, {renders} TrustKart renders, "
              f"{sum(1 for r in report if r['status'] == 'fallback')} fallbacks, "
              f"{sum(1 for r in report if r['status'] == 'parked')} parked, {option_images} option images, "
              f"{shared_groups} photos shared within the catalog, {len(warnings)} warnings, {len(errors)} errors")
        for e in errors:
            print("ERROR  " + e)
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
