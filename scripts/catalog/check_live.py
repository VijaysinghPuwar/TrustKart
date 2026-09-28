#!/usr/bin/env python3
"""
End-to-end availability check against a running TrustKart (API + static files through the frontend origin):
every seeded catalog product must open (HTTP 200), have an image, and both image sizes must load as WebP.
Parked products (catalog/parked.json) must NOT be visible.

    python3 scripts/catalog/check_live.py http://localhost:5183
"""
import concurrent.futures
import json
import sys
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "backend/src/main/resources"


def get(url: str):
    try:
        with urllib.request.urlopen(urllib.request.Request(url, headers={"Accept": "*/*"}), timeout=30) as r:
            return r.status, r.headers.get("Content-Type", ""), r.read()
    except urllib.error.HTTPError as e:
        return e.code, "", b""
    except Exception as e:  # noqa: BLE001
        return 0, str(e), b""


def main() -> int:
    base = sys.argv[1].rstrip("/") if len(sys.argv) > 1 else "http://localhost:5183"
    products = [p for f in sorted((RES / "catalog/products").glob("*.json"))
                for p in json.loads(f.read_text())["products"]]
    parked_skus = set(json.loads((RES / "catalog/parked.json").read_text())["skus"]) if (RES / "catalog/parked.json").exists() else set()
    errors = []

    def check(p):
        status, _, body = get(f"{base}/api/v1/catalog/products/{p['slug']}")
        if status != 200:
            return [f"{p['slug']}: product page HTTP {status}"]
        detail = json.loads(body)
        img = detail["product"].get("image")
        if not img:
            return [f"{p['slug']}: no image"]
        out = []
        for key in ("small", "large"):
            s, ctype, data = get(base + img[key])
            if s != 200 or len(data) < 500 or not (data[:4] == b"RIFF" and data[8:12] == b"WEBP"):
                out.append(f"{p['slug']}: {key} image {img[key]} -> HTTP {s} {ctype} {len(data)} bytes")
        if p["options"] and not detail.get("options"):
            out.append(f"{p['slug']}: options missing from API")
        for g in detail.get("options") or []:
            for v in g["values"]:
                if v.get("image"):
                    s, _, data = get(base + v["image"]["large"])
                    if s != 200 or data[8:12] != b"WEBP":
                        out.append(f"{p['slug']}: {g['name']} {v['label']!r} image HTTP {s}")
        return out

    with concurrent.futures.ThreadPoolExecutor(max_workers=16) as pool:
        for problems in pool.map(check, products):
            errors.extend(problems)

    total_status, _, body = get(f"{base}/api/v1/catalog/products?size=1")
    total = json.loads(body)["totalItems"] if total_status == 200 else "?"

    # Every card on the storefront (catalog + demo products): image present and loadable; nothing parked visible.
    cards, page = [], 0
    while True:
        s, _, body = get(f"{base}/api/v1/catalog/products?size=48&page={page}")
        if s != 200:
            errors.append(f"listing page {page}: HTTP {s}")
            break
        data = json.loads(body)
        cards += data["items"]
        page += 1
        if page >= data["totalPages"]:
            break

    def check_card(c):
        if c["sku"] in parked_skus:
            return [f"{c['slug']}: parked product is visible"]
        img = c.get("image")
        if not img:
            return [f"{c['slug']}: card has no image"]
        s, _, data = get(base + img["small"])
        return [] if s == 200 and data[8:12] == b"WEBP" else [f"{c['slug']}: card image {img['small']} HTTP {s}"]

    with concurrent.futures.ThreadPoolExecutor(max_workers=16) as pool:
        for problems in pool.map(check_card, cards):
            errors.extend(problems)
    print(f"storefront cards checked: {len(cards)}")
    for e in errors:
        print("FAIL", e)
    print(f"\nchecked {len(products)} catalog products ({len(parked_skus)} parked); storefront total {total}; "
          f"{len(errors)} problem(s)")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
