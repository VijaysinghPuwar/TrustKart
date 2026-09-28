#!/usr/bin/env python3
"""
Moves products that have no official image out of catalog-data/sources/*.json into catalog-data/pending/*.json,
so the storefront never shows an "Image unavailable" card. Nothing is deleted: a parked product keeps its full
researched record and returns to the catalog by moving it back once an official image is sourced.

    python3 scripts/catalog/park_imageless.py            # dry run: list what would move
    python3 scripts/catalog/park_imageless.py --apply    # move them
"""
import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SOURCES = ROOT / "catalog-data/sources"
PENDING = ROOT / "catalog-data/pending"


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", action="store_true")
    ap.add_argument("--exclude", help="comma-separated source names to leave untouched")
    args = ap.parse_args()
    skip = set(args.exclude.split(",")) if args.exclude else set()
    total = 0
    for f in sorted(SOURCES.glob("*.json")):
        if f.stem in skip:
            continue
        data = json.loads(f.read_text())
        keep = [p for p in data["products"] if p.get("image")]
        park = [p for p in data["products"] if not p.get("image")]
        if not park:
            continue
        total += len(park)
        print(f"{f.stem}: {len(park)} -> pending ({', '.join(p['slug'] for p in park)})")
        if args.apply:
            PENDING.mkdir(parents=True, exist_ok=True)
            target = PENDING / f.name
            existing = json.loads(target.read_text())["products"] if target.exists() else []
            known = {p["sku"] for p in existing}
            merged = existing + [p for p in park if p["sku"] not in known]
            target.write_text(json.dumps({"meta": {"notes": "Researched products parked until an official, "
                                                            "professional product image can be sourced."},
                                          "products": merged}, indent=1, ensure_ascii=False) + "\n")
            data["products"] = keep
            f.write_text(json.dumps(data, indent=1, ensure_ascii=False) + "\n")
    print(f"\n{total} product(s) {'moved' if args.apply else 'would move'} to catalog-data/pending/")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
