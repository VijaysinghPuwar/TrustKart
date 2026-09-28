#!/usr/bin/env python3
"""
TrustKart catalog pipeline: researched source data -> validated, normalized Spring Boot seed data.

    catalog-data/sources/<name>.json      raw researched records (hand/agent authored, with provenance)
            |  python3 scripts/catalog/build.py
            v
    backend/src/main/resources/catalog/products/<name>.json   seed files read by DemoCatalogSeeder
    backend/src/main/resources/catalog/images.json            image records (same shape as demo/images.json)
    frontend/public/images/catalog/<slug>-{800,400}.webp       normalized product images
    catalog-data/product-image-sources.json                   machine-readable image provenance
    docs/CATALOG_SOURCES.md, docs/CATALOG_ASSET_SOURCES.md    human-readable provenance
    catalog-data/build/report.json                            counts and review lists

Usage:
    python3 scripts/catalog/build.py --check [--only apple,samsung]   validate sources only (no network, no writes)
    python3 scripts/catalog/build.py --images --only apple            fetch + normalize images for some sources
    python3 scripts/catalog/build.py                                  full build (validate, images, emit everything)
    python3 scripts/catalog/build.py --check-links                    also HEAD-check every source URL
    python3 scripts/catalog/build.py --categories                     list leaf categories and spec keys (T/N/B)

Exit code 1 when any error is found. The format is documented in catalog-data/README.md.
"""
from __future__ import annotations

import argparse
import concurrent.futures
import hashlib
import io
import json
import re
import sys
import urllib.error
import urllib.request
import zlib
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SOURCES = ROOT / "catalog-data/sources"
# Official images for products that live in demo/products.json (replace their earlier Commons photos / renders).
DEMO_OVERRIDES = ROOT / "catalog-data/demo-image-overrides"  # *.json, each {"images": [{slug, image, retrievedAt}]}
CACHE = ROOT / "catalog-data/.cache"
BUILD = ROOT / "catalog-data/build"
RES = ROOT / "backend/src/main/resources"
DEMO_CATEGORIES = RES / "demo/categories.json"
DEMO_PRODUCTS = RES / "demo/products.json"
CATALOG_CATEGORIES = RES / "catalog/categories.json"
OUT_PRODUCTS = RES / "catalog/products"
OUT_IMAGES = RES / "catalog/images.json"
PUBLIC = ROOT / "frontend/public"
IMG_DIR = PUBLIC / "images/catalog"

SKU_RE = re.compile(r"^TK-[A-Z0-9]+(-[A-Z0-9]+)*$")
SLUG_RE = re.compile(r"^[a-z0-9]+(-[a-z0-9]+)*$")
TAG_RE = SLUG_RE
PRICE_RE = re.compile(r"^\d+(\.\d{1,2})?$")
DATE_RE = re.compile(r"^\d{4}-\d{2}-\d{2}$")
PRICE_BASES = {"msrp", "starting-at", "estimate"}
SOURCE_TYPES = {"manufacturer-press", "manufacturer-product-page", "manufacturer-media-library", "wikimedia-commons",
                "project-render"}
MATCHES = {"EXACT", "PRODUCT_LINE"}
REQUIRED = {"sku", "slug", "name", "brand", "category", "price", "priceBasis", "summary", "description", "specs", "sources"}
OPTIONAL = {"compareAt", "priceSource", "priceAsOf", "priceNote", "releaseYear", "keywords", "collections", "featured",
            "warranty", "stock", "variants", "colors", "image", "notes"}
IMAGE_FIELDS = {"url", "page", "sourceType", "rights", "alt", "match", "credit", "licenseUrl"}
UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15"

CANVAS = 800
CONTENT = 720          # product occupies at most this many px of the 800 canvas (consistent margins)
MIN_SOURCE_PX = 480    # error below this long side (would be visibly upscaled)
WARN_SOURCE_PX = 700


class Issues:
    def __init__(self):
        self.errors: list[str] = []
        self.warnings: list[str] = []

    def err(self, where: str, msg: str):
        self.errors.append(f"{where}: {msg}")

    def warn(self, where: str, msg: str):
        self.warnings.append(f"{where}: {msg}")


# ----------------------------------------------------------------------------------------------------------------------
# Categories: demo tree + catalog extensions, merged exactly the way DemoCatalogSeeder merges them.
# ----------------------------------------------------------------------------------------------------------------------

def merged_categories() -> list[dict]:
    tree = json.loads(DEMO_CATEGORIES.read_text())["categories"]
    ext = json.loads(CATALOG_CATEGORIES.read_text())

    def find(nodes, slug):
        for n in nodes:
            if n["slug"] == slug:
                return n
            hit = find(n.get("children") or [], slug)
            if hit:
                return hit
        return None

    for e in ext.get("extensions", []):
        node = find(tree, e["extend"])
        if node is None:
            raise SystemExit(f"catalog/categories.json extends unknown category {e['extend']}")
        node.setdefault("specs", []).extend(e.get("specs") or [])
        node.setdefault("children", []).extend(e.get("children") or [])
    tree.extend(ext.get("categories", []))
    return tree


def category_index(tree):
    """slug -> {"name", "path", "leaf", "specs": {key: type}}"""
    index = {}

    def walk(nodes, inherited, path, path_slugs):
        for n in nodes:
            specs = dict(inherited)
            for s in n.get("specs") or []:
                specs[s["key"]] = s["type"]
            children = n.get("children") or []
            if n["slug"] in index:
                raise SystemExit(f"duplicate category slug {n['slug']}")
            top = path_slugs[0] if path_slugs else n["slug"]
            index[n["slug"]] = {"name": n["name"], "path": path + [n["name"]], "leaf": not children, "specs": specs,
                                "top": top}
            walk(children, specs, path + [n["name"]], path_slugs + [n["slug"]])

    def walk_wrapper(nodes, inherited, path, path_slugs=None):
        return walk(nodes, inherited, path, path_slugs or [])

    walk_wrapper(tree, {}, [])
    return index


# ----------------------------------------------------------------------------------------------------------------------
# Validation
# ----------------------------------------------------------------------------------------------------------------------

def load_sources(only: set[str] | None):
    files = sorted(SOURCES.glob("*.json"))
    out = {}
    for f in files:
        if only and f.stem not in only:
            continue
        try:
            data = json.loads(f.read_text())
        except json.JSONDecodeError as e:
            raise SystemExit(f"{f.name}: invalid JSON: {e}")
        out[f.stem] = data
    return out


def slugify(name: str) -> str:
    """Same rule as DemoCatalogSeeder.slugify."""
    import unicodedata
    ascii_name = "".join(c for c in unicodedata.normalize("NFD", name) if not unicodedata.combining(c))
    return re.sub(r"(^-|-$)", "", re.sub(r"[^a-z0-9]+", "-", ascii_name.lower()))


def spec_ok(kind: str, value) -> bool:
    if kind == "TEXT":
        return isinstance(value, str) and value.strip() != ""
    if kind == "NUMBER":
        return isinstance(value, (int, float)) and not isinstance(value, bool)
    if kind == "BOOLEAN":
        return isinstance(value, bool)
    return False


def money_str(p: str) -> str:
    v = float(p)
    return f"${v:,.0f}" if v == int(v) else f"${v:,.2f}"


# ----------------------------------------------------------------------------------------------------------------------
# Facet normalization: filterable TEXT specs become consistent values (matching the demo catalog's formats) so a filter
# shows "3840x2160" once instead of five spellings. The original wording moves to a non-filterable detail spec.
# ----------------------------------------------------------------------------------------------------------------------

NAMED_RES = [(r"\b8k\b", "7680x4320"), (r"\b6k\b", "6144x3456"), (r"\b5k2k\b", "5120x2160"), (r"\b5k\b", "5120x2880"),
             (r"\b4k\b|2160p|\buhd\b", "3840x2160"), (r"\bqhd\b|1440p", "2560x1440"), (r"1080p|full hd|\bfhd\b", "1920x1080")]


def norm_resolution(v: str):
    m = re.search(r"(\d{3,5})\s*[x×]\s*(\d{3,5})", v)
    if m:
        return f"{m.group(1)}x{m.group(2)}"
    low = v.lower()
    for pat, res in NAMED_RES:
        if re.search(pat, low):
            return res
    return v


def norm_panel(v: str):
    low = v.lower()
    rules = [("micro rgb", "Micro RGB"), ("rgb mini", "RGB Mini LED"), ("rgb miniled", "RGB Mini LED"),
             ("true rgb", "RGB LED"), ("qd-oled", "QD-OLED"), ("qd oled", "QD-OLED"), ("woled", "OLED"), ("oled", "OLED"),
             ("ips black", "IPS Black"), ("mini led", "Mini LED"), ("mini-led", "Mini LED"), ("miniled", "Mini LED"),
             ("ips", "IPS"), ("va", "VA"), ("lcd", "LED LCD"), ("qled", "QLED")]
    for key, label in rules:
        if re.search(r"\b" + re.escape(key) + r"\b", low) or (key in low and " " in key):
            if label == "Mini LED" and "ips" in low:
                return "IPS Mini LED"
            return label
    return v


def norm_socket(v: str):
    return re.sub(r"^FC", "", v.replace(" ", ""))


def norm_technology(v: str):
    low = v.lower()
    if "thermal transfer" in low:
        return "Thermal transfer"
    if "thermal" in low and "inkjet" not in low:
        return "Direct thermal"
    if "laser" in low or " led" in low:
        return "Color laser" if "color" in low or "colour" in low else "Laser"
    if "inkjet" in low or "ink" in low:
        return "Inkjet (supertank)" if ("tank" in low or "ecotank" in low) else "Inkjet"
    return v


def norm_os(v: str):
    if v.lower().startswith("none"):
        return "None (bring your own)"
    v = re.split(r"\s*[(,;]|\s+at launch", v)[0].strip()
    return v


def norm_wifi(v: str):
    m = re.search(r"wi-?fi\s*(\d+e?)", v, re.I)
    if m:
        return "Wi-Fi " + m.group(1).upper().replace("E", "E")
    return v


def norm_layout(v: str):
    low = v.lower()
    for key, label in [("tenkeyless", "TKL"), ("tkl", "TKL"), ("96%", "96%"), ("75%", "75%"), ("65%", "65%"),
                       ("60%", "60%"), ("full", "Full size"), ("compact", "Compact")]:
        if key in low:
            return label
    return v


def norm_gpu_chipset(v: str):
    return re.sub(r"^(NVIDIA|AMD|Intel)\s+", "", v)


def norm_connectivity(v: str):
    low = v.lower()
    parts = []
    if "wi-fi" in low or "wifi" in low:
        parts.append("Wi-Fi")
    if "bluetooth" in low:
        parts.append("Bluetooth")
    if not parts or any(k in low for k in ("usb", "wired", "xlr", "3.5", "hdmi", "optical", "thunderbolt")):
        if not parts:
            parts.append("Wired")
    return " + ".join(parts)


def norm_platform(v: str):
    low = v.lower()
    fams = [("xbox", "Xbox"), ("ps5", "PlayStation 5"), ("playstation", "PlayStation 5"),
            ("switch 2", "Nintendo Switch 2"), ("switch", "Nintendo Switch"), ("steamos", "SteamOS"),
            ("steam", "SteamOS"),
            ("windows", "PC (Windows)"), ("pc", "PC (Windows)"), ("android", "Mobile"), ("ios", "Mobile")]
    found = []
    for key, label in fams:
        if re.search(r"\b" + re.escape(key) + r"\b", low) and label not in found:
            found.append(label)
    if not found:
        return v
    return found[0]  # primary platform; the full list stays in the "Works with" detail spec


def norm_mount(v: str):
    low = v.lower()
    for key, label in [("e-mount", "Sony E"), ("sony e", "Sony E"), ("nikon z", "Nikon Z"), ("fujifilm x", "FUJIFILM X"),
                       ("fujifilm g", "FUJIFILM G"), ("l-mount", "L-Mount"), ("micro four thirds", "Micro Four Thirds"),
                       ("rf", "Canon RF"), ("fixed", "Fixed lens")]:
        if key in low:
            return label
    return None  # not a lens mount (gimbal, magnetic clip...): drop from the lens-mount facet


# key -> (normalizer, detail key or None, departments it applies to or None for all)
FACETS = {
    "resolution": (norm_resolution, None, {"monitors", "tvs"}),
    "panel": (norm_panel, "panelDetails", {"monitors", "tvs"}),
    "socket": (norm_socket, None, None),
    "technology": (norm_technology, None, {"printers"}),
    "os": (norm_os, None, None),
    "wifiStandard": (norm_wifi, "wifiDetails", {"networking"}),
    "layout": (norm_layout, None, {"peripherals"}),
    "chipset": (norm_gpu_chipset, None, {"components"}),
    "connectivity": (norm_connectivity, "connectionDetails", {"audio"}),
    "platform": (norm_platform, "compatibility", {"gaming"}),
    "mount": (norm_mount, None, {"cameras-drones"}),
}


def normalize_facets(specs: dict, cat: dict) -> dict:
    out = dict(specs)
    for key, (fn, detail, departments) in FACETS.items():
        value = out.get(key)
        if not isinstance(value, str) or (departments and cat["top"] not in departments):
            continue
        clean = fn(value)
        if clean is None:
            out.pop(key)
            continue
        out[key] = clean
        if detail and clean != value and detail in cat["specs"] and detail not in out:
            out[detail] = value
    return out


def normalize_specs(p: dict, cat: dict, where: str, issues: Issues) -> dict:
    specs = normalize_facets(dict(p.get("specs") or {}), cat)
    if p.get("variants"):
        if "configurations" not in cat["specs"]:
            issues.err(where, f"variants given but category {p['category']} has no 'configurations' spec")
        elif "configurations" in specs:
            issues.err(where, "give either variants or specs.configurations, not both")
        else:
            parts = []
            for v in p["variants"]:
                if not isinstance(v, dict) or not v.get("label"):
                    issues.err(where, "each variant needs a label")
                    continue
                extra = set(v) - {"label", "price"}
                if extra:
                    issues.err(where, f"unknown variant fields {sorted(extra)}")
                if v.get("price") is not None:
                    if not PRICE_RE.match(str(v["price"])):
                        issues.err(where, f"bad variant price {v['price']!r}")
                        continue
                    parts.append(f"{v['label']} – {money_str(v['price'])}")
                else:
                    parts.append(v["label"])
            specs["configurations"] = " · ".join(parts)
    if p.get("colors"):
        if "colors" not in cat["specs"]:
            issues.err(where, f"colors given but category {p['category']} has no 'colors' spec")
        elif not all(isinstance(c, str) and c.strip() for c in p["colors"]):
            issues.err(where, "colors must be non-empty strings")
        else:
            specs["colors"] = ", ".join(p["colors"])
    return specs


def load_overrides(demo_products: list[dict], issues: Issues) -> list[dict]:
    """Pseudo-products (slug/sku/name/brand from demo/products.json + image) so they share the image path."""
    by_slug = {p["slug"]: p for p in demo_products}
    out, seen = [], set()
    entries = []
    for f in sorted(DEMO_OVERRIDES.glob("*.json")):
        try:
            entries += [(f.stem, i, o) for i, o in enumerate(json.loads(f.read_text()).get("images", []))]
        except json.JSONDecodeError as e:
            issues.err(f"demo-image-overrides/{f.name}", f"invalid JSON: {e}")
    for fname, i, o in entries:
        where = f"demo-image-overrides/{fname}[{i}] {o.get('slug', '?')}"
        if o.get("slug") in seen:
            issues.err(where, "slug overridden twice")
        seen.add(o.get("slug"))
        demo = by_slug.get(o.get("slug"))
        if demo is None:
            issues.err(where, "slug is not in demo/products.json")
            continue
        if set(o) - {"slug", "image", "retrievedAt"}:
            issues.err(where, f"unknown fields {sorted(set(o) - {'slug', 'image', 'retrievedAt'})}")
        img = o.get("image") or {}
        for f in ("url", "page", "sourceType", "rights", "alt", "match"):
            if not img.get(f):
                issues.err(where, f"image.{f} required")
        if img.get("sourceType") == "project-render":
            issues.err(where, "overrides must be real product imagery")
        if RETAILER_HOSTS.search(str(img.get("url", ""))) or RETAILER_HOSTS.search(str(img.get("page", ""))):
            issues.err(where, "retailer-hosted images are not allowed")
        out.append({"sku": demo["sku"], "slug": demo["slug"], "name": demo["name"], "brand": demo["brand"],
                    "image": img, "sources": {"retrievedAt": o.get("retrievedAt")}})
    return out


def validate(sources: dict, index: dict, issues: Issues, demo_products: list[dict]):
    seen_sku = {p["sku"]: "demo/products.json" for p in demo_products}
    seen_slug = {p["slug"]: "demo/products.json" for p in demo_products}
    demo_names = {p["name"].lower(): p["slug"] for p in demo_products}
    seen_name: dict[str, str] = {}
    brand_by_slug = {slugify(p["brand"]): p["brand"] for p in demo_products}
    seen_image_url: dict[str, str] = {}
    normalized = {}

    for name, data in sources.items():
        if set(data) - {"meta", "products"}:
            issues.err(name, f"top-level keys must be meta/products, got {sorted(data)}")
        products = data.get("products") or []
        out = []
        for i, p in enumerate(products):
            where = f"{name}[{i}] {p.get('sku', '?')}"
            missing = REQUIRED - set(p)
            unknown = set(p) - REQUIRED - OPTIONAL
            if missing:
                issues.err(where, f"missing fields {sorted(missing)}")
                continue
            if unknown:
                issues.err(where, f"unknown fields {sorted(unknown)}")

            sku, slug = p["sku"], p["slug"]
            if not SKU_RE.match(sku) or len(sku) > 40:
                issues.err(where, "SKU must match TK-XXX-YYY (A-Z0-9) and be <= 40 chars")
            if not SLUG_RE.match(slug) or len(slug) > 120:
                issues.err(where, "bad slug")
            if sku in seen_sku:
                issues.err(where, f"duplicate SKU (also in {seen_sku[sku]})")
            if slug in seen_slug:
                issues.err(where, f"duplicate slug (also in {seen_slug[slug]})")
            seen_sku[sku] = seen_slug[slug] = name
            lname = p["name"].strip().lower()
            if lname in seen_name or lname in demo_names:
                issues.err(where, f"duplicate product name {p['name']!r}")
            seen_name[lname] = name

            for field, limit in (("name", 200), ("summary", 300)):
                if not isinstance(p[field], str) or not p[field].strip():
                    issues.err(where, f"{field} is empty")
                elif len(p[field]) > limit:
                    issues.err(where, f"{field} longer than {limit}")
            if not isinstance(p["brand"], str) or not p["brand"].strip() or len(p["brand"]) > 120:
                issues.err(where, "missing brand")
            else:
                canonical = brand_by_slug.setdefault(slugify(p["brand"]), p["brand"])
                if canonical != p["brand"]:
                    issues.err(where, f"brand {p['brand']!r} must be spelled {canonical!r} (same brand slug)")
            if not isinstance(p["description"], str) or len(p["description"]) < 60:
                issues.err(where, "description too short (< 60 chars)")

            cat = index.get(p["category"])
            if cat is None:
                issues.err(where, f"unknown category {p['category']!r}")
                continue
            if not cat["leaf"]:
                issues.err(where, f"category {p['category']!r} is not a leaf category")

            price = str(p["price"])
            if not PRICE_RE.match(price) or float(price) <= 0:
                issues.err(where, f"invalid price {p['price']!r}")
            elif float(price) > 5_000_000:
                issues.err(where, "price above sanity cap")
            if p.get("compareAt") is not None:
                ca = str(p["compareAt"])
                if not PRICE_RE.match(ca) or (PRICE_RE.match(price) and float(ca) <= float(price)):
                    issues.err(where, "compareAt must be a price above price")
            if p["priceBasis"] not in PRICE_BASES:
                issues.err(where, f"priceBasis must be one of {sorted(PRICE_BASES)}")
            elif p["priceBasis"] != "estimate":
                if not str(p.get("priceSource", "")).startswith("https://"):
                    issues.err(where, "official price needs an https priceSource")
                if not DATE_RE.match(str(p.get("priceAsOf", ""))):
                    issues.err(where, "official price needs priceAsOf (YYYY-MM-DD)")

            src = p["sources"]
            if not isinstance(src, dict) or not str(src.get("product", "")).startswith("https://"):
                issues.err(where, "sources.product must be an https URL")
            else:
                if set(src) - {"product", "specs", "retrievedAt", "notes"}:
                    issues.err(where, f"unknown sources fields {sorted(set(src) - {'product', 'specs', 'retrievedAt', 'notes'})}")
                if not DATE_RE.match(str(src.get("retrievedAt", ""))):
                    issues.err(where, "sources.retrievedAt (YYYY-MM-DD) required")
                if src.get("specs") and not str(src["specs"]).startswith("https://"):
                    issues.err(where, "sources.specs must be an https URL")

            for tag in p.get("collections") or []:
                if not TAG_RE.match(tag) or len(tag) > 60:
                    issues.err(where, f"bad collection tag {tag!r}")
            if p.get("warranty") is not None and not (isinstance(p["warranty"], int) and 0 <= p["warranty"] <= 240):
                issues.err(where, "warranty must be 0-240 months")
            if p.get("stock") is not None and not (isinstance(p["stock"], list) and len(p["stock"]) == 2
                                                   and all(isinstance(x, int) and x >= 0 for x in p["stock"])):
                issues.err(where, "stock must be [available, lowStockThreshold]")

            if not isinstance(p["specs"], dict) or not p["specs"]:
                issues.err(where, "specs must be a non-empty object")
                continue
            specs = normalize_specs(p, cat, where, issues)
            for key, value in specs.items():
                kind = cat["specs"].get(key)
                if kind is None:
                    issues.err(where, f"spec {key!r} is not defined for {p['category']} "
                                      f"(allowed: {', '.join(sorted(cat['specs']))})")
                elif not spec_ok(kind, value):
                    issues.err(where, f"spec {key!r} must be {kind}, got {value!r}")
            if len(specs) < 3:
                issues.warn(where, "fewer than 3 specs")

            img = p.get("image")
            if img is None:
                issues.warn(where, "no image (storefront shows fallback)")
            else:
                bad = set(img) - IMAGE_FIELDS
                if bad:
                    issues.err(where, f"unknown image fields {sorted(bad)}")
                for f in ("url", "page", "sourceType", "rights", "alt", "match"):
                    if not img.get(f):
                        issues.err(where, f"image.{f} required")
                if img.get("sourceType") and img["sourceType"] not in SOURCE_TYPES:
                    issues.err(where, f"image.sourceType must be one of {sorted(SOURCE_TYPES)}")
                if img.get("match") and img["match"] not in MATCHES:
                    issues.err(where, f"image.match must be one of {sorted(MATCHES)}")
                alt = str(img.get("alt", ""))
                if alt and (len(alt) < 15 or len(alt) > 300):
                    issues.err(where, "image.alt must be 15-300 chars")
                if img.get("page") and len(img["page"]) > 500:
                    issues.err(where, "image.page longer than 500 chars")
                u = img.get("url", "")
                if u and not (u.startswith("https://") or u.startswith("local:")):
                    issues.err(where, "image.url must be https:// or local:<path>")
                if u in seen_image_url:
                    issues.warn(where, f"image URL also used by {seen_image_url[u]}")
                seen_image_url[u] = sku
                if RETAILER_HOSTS.search(u) or RETAILER_HOSTS.search(str(img.get("page", ""))):
                    issues.err(where, "retailer-hosted images are not allowed")

            out.append({**p, "_specs": specs})
        normalized[name] = out
    return normalized


RETAILER_HOSTS = re.compile(r"//([a-z0-9-]+\.)*(amazon|media-amazon|bestbuy|microcenter|ebay|ebayimg|newegg|walmart|"
                            r"target|bhphotovideo|costco|aliexpress|temu)\.", re.I)


# ----------------------------------------------------------------------------------------------------------------------
# Images
# ----------------------------------------------------------------------------------------------------------------------

def fetch(url: str) -> bytes:
    if url.startswith("local:"):
        return (ROOT / url[len("local:"):]).read_bytes()
    raw = CACHE / "raw" / hashlib.sha1(url.encode()).hexdigest()
    if raw.exists() and raw.stat().st_size > 0:
        return raw.read_bytes()
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "image/avif,image/webp,image/png,image/jpeg,*/*"})
    with urllib.request.urlopen(req, timeout=40) as r:
        data = r.read()
    raw.parent.mkdir(parents=True, exist_ok=True)
    raw.write_bytes(data)
    return data


def normalize_image(data: bytes):
    """Flatten onto white, lift near-white studio backgrounds to pure white, trim, center on a square canvas."""
    from PIL import Image, ImageChops, ImageOps, ImageStat

    im = Image.open(io.BytesIO(data))
    im = ImageOps.exif_transpose(im)
    if im.mode in ("P", "LA", "L") or "transparency" in im.info:
        im = im.convert("RGBA")
    if im.mode == "RGBA":
        bg = Image.new("RGBA", im.size, (255, 255, 255, 255))
        bg.alpha_composite(im)
        im = bg
    im = im.convert("RGB")
    w, h = im.size
    notes = []

    # Background detection from the four corner patches. Corners (not the whole border) so that product shots
    # cropped edge-to-edge, where the product touches a side, still read as studio white. 3 of 4 must agree.
    b = max(3, int(min(w, h) * 0.03))
    corners = [im.crop((0, 0, b, b)), im.crop((w - b, 0, w, b)), im.crop((0, h - b, b, h)), im.crop((w - b, h - b, w, h))]
    stats = [ImageStat.Stat(c) for c in corners]
    clean = [st for st in stats if max(st.stddev) < 6 and min(st.mean) >= 228]
    uniform = len(clean) >= 3
    ref = clean if clean else stats
    bgc = [sum(st.mean[c] for st in ref) / len(ref) for c in range(3)]
    bright = min(bgc) >= 228
    if not (uniform and bright):
        notes.append("non-white background")
    elif min(bgc) < 254:
        # Scale channels so the studio background becomes pure white (<= ~11% lift, invisible on the product).
        lut = []
        for c in range(3):
            k = 255.0 / max(bgc[c], 1)
            lut.extend(min(255, int(round(v * k))) for v in range(256))
        im = im.point(lut)

    if uniform and bright:
        diff = ImageChops.difference(im, Image.new("RGB", im.size, (255, 255, 255))).convert("L")
        mask = diff.point(lambda v: 255 if v > 14 else 0)
        box = mask.getbbox()
        if box:
            pad = 2
            box = (max(0, box[0] - pad), max(0, box[1] - pad), min(w, box[2] + pad), min(h, box[3] + pad))
            im = im.crop(box)
    cw, ch = im.size
    long_side = max(cw, ch)
    scale = min(CONTENT / cw, CONTENT / ch)
    if scale > 1:
        scale = min(scale, 1.6)  # never upscale beyond 1.6x
    nw, nh = max(1, round(cw * scale)), max(1, round(ch * scale))
    im = im.resize((nw, nh), Image.LANCZOS)
    fill = (255, 255, 255) if (uniform and bright) else tuple(int(x) for x in bgc)
    canvas = Image.new("RGB", (CANVAS, CANVAS), fill)
    canvas.paste(im, ((CANVAS - nw) // 2, (CANVAS - nh) // 2))
    return canvas, long_side, notes


def process_image(p: dict):
    """Returns (record, issues list). Idempotent: skips work when the processed sidecar matches the source URL."""
    from PIL import Image

    img = p["image"]
    slug = p["slug"]
    large = IMG_DIR / f"{slug}-800.webp"
    small = IMG_DIR / f"{slug}-400.webp"
    side = CACHE / "processed" / f"{slug}.json"
    probs = []
    if large.exists() and small.exists() and side.exists():
        meta = json.loads(side.read_text())
        if meta.get("url") == img["url"]:
            return meta, probs
    try:
        data = fetch(img["url"])
        canvas, long_side, notes = normalize_image(data)
    except (urllib.error.URLError, OSError, ValueError, TimeoutError) as e:
        return None, [("err", f"image download/decode failed: {e}")]
    if long_side < MIN_SOURCE_PX:
        probs.append(("err", f"source image too small ({long_side}px long side)"))
        return None, probs
    if long_side < WARN_SOURCE_PX:
        probs.append(("warn", f"low source resolution ({long_side}px)"))
    for n in notes:
        probs.append(("warn", n))
    IMG_DIR.mkdir(parents=True, exist_ok=True)
    canvas.save(large, "WEBP", quality=88, method=6)
    canvas.resize((400, 400), Image.LANCZOS).save(small, "WEBP", quality=86, method=6)
    meta = {"url": img["url"], "sourcePx": long_side, "notes": notes,
            "sha256": hashlib.sha256(large.read_bytes()).hexdigest()}
    side.parent.mkdir(parents=True, exist_ok=True)
    side.write_text(json.dumps(meta))
    return meta, probs


def run_images(normalized: dict, issues: Issues, overrides: list[dict] = ()):
    items = [p for ps in normalized.values() for p in ps if p.get("image")] + list(overrides)
    results = {}
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
        futs = {pool.submit(process_image, p): p for p in items}
        for fut in concurrent.futures.as_completed(futs):
            p = futs[fut]
            meta, probs = fut.result()
            for level, msg in probs:
                (issues.err if level == "err" else issues.warn)(f"{p['sku']} image", msg)
            if meta:
                results[p["slug"]] = meta
    by_hash: dict[str, str] = {}
    for slug, meta in sorted(results.items()):
        h = meta.get("sha256")
        if h in by_hash:
            issues.warn(slug, f"identical processed image as {by_hash[h]}")
        by_hash[h] = slug
    return results


# ----------------------------------------------------------------------------------------------------------------------
# Emit
# ----------------------------------------------------------------------------------------------------------------------

def stock_for(p: dict) -> list[int]:
    if p.get("stock"):
        return p["stock"]
    h = zlib.crc32(p["sku"].encode())
    price = float(p["price"])
    if price >= 20000:
        return [2 + h % 6, 2]
    if price >= 3000:
        return [4 + h % 14, 3]
    return [8 + h % 52, 5]


STORAGE_RE = re.compile(r"^\d+(\.\d+)?\s?(GB|TB)$", re.I)


def option_group_name(labels: list[str]) -> str:
    """Names a priced option group from its labels; "Configuration" when no narrower name is clearly right."""
    if all(STORAGE_RE.match(l.strip()) or re.match(r"^\s*\d+(\.\d+)?\s?(GB|TB)\b[^,;]{0,25}$", l, re.I)
           for l in labels):
        return "Storage"
    sizes = [re.search(r'(\d+(\.\d+)?)\s?(\"|”|-inch|inch| in\b|mm)', l.lower()) for l in labels]
    if all(sizes) and len({m.group(1) for m in sizes}) == len(labels):
        return "Size"
    if all("pack" in l.lower() for l in labels):
        return "Pack"
    return "Configuration"


def derive_options(p: dict) -> list[dict]:
    """Selectable options from researched variants/colors. Only officially priced variants become choices; the
    default always costs exactly the listed price (a 'Configuration shown' choice is added when the specs describe
    a configuration that isn't one of the variants)."""
    groups = []
    base = float(p["price"])
    priced = [v for v in (p.get("variants") or []) if v.get("price")]
    if priced:
        values = [{"label": v["label"], "price": f"{float(v['price']):.2f}"} for v in priced]
        if not any(abs(float(v["price"]) - base) < 0.005 for v in values):
            values.insert(0, {"label": "Configuration shown", "price": f"{base:.2f}"})
        seen, unique = set(), []
        for v in values:
            if v["label"] not in seen:
                seen.add(v["label"])
                unique.append(v)
        for v in unique:
            if abs(float(v["price"]) - base) < 0.005:
                v["default"] = True
                break
        if len(unique) >= 2:
            groups.append({"name": option_group_name([v["label"] for v in unique if v["label"] != "Configuration shown"]
                                                     or ["x"]), "values": unique})
    colors = [c for c in (p.get("colors") or []) if isinstance(c, str) and c.strip()]
    colors = list(dict.fromkeys(colors))
    if len(colors) >= 2:
        groups.append({"name": "Color", "values": [{"label": c, **({"default": True} if i == 0 else {})}
                                                   for i, c in enumerate(colors)]})
    return groups


def seed_record(p: dict) -> dict:
    return {
        "sku": p["sku"], "slug": p["slug"], "name": p["name"], "brand": p["brand"], "category": p["category"],
        "price": f"{float(p['price']):.2f}",
        "compareAt": None if p.get("compareAt") is None else f"{float(p['compareAt']):.2f}",
        "warranty": p.get("warranty", 12), "stock": stock_for(p), "featured": bool(p.get("featured", False)),
        "backorder": False, "discontinued": False, "collections": p.get("collections") or [],
        "summary": p["summary"], "description": p["description"], "keywords": p.get("keywords", ""),
        "specs": p["_specs"],
        "options": derive_options(p),
    }


def image_record(p: dict) -> dict:
    img = p["image"]
    credit = img.get("credit") or p["brand"]
    license_label = {
        "manufacturer-press": "Manufacturer product image (press kit)",
        "manufacturer-media-library": "Manufacturer product image (media library)",
        "manufacturer-product-page": "Manufacturer product image",
        "wikimedia-commons": img.get("rights", "Wikimedia Commons"),
        "project-render": "TrustKart render",
    }[img["sourceType"]]
    return {
        "large": f"/images/catalog/{p['slug']}-800.webp", "small": f"/images/catalog/{p['slug']}-400.webp",
        "width": CANVAS, "height": CANVAS, "alt": img["alt"], "match": img["match"], "author": credit,
        "license": license_label, "licenseUrl": img.get("licenseUrl") or img["page"], "filePage": img["page"],
    }


def md_escape(s) -> str:
    return str(s if s is not None else "").replace("|", "\\|").replace("\n", " ")


def emit(normalized: dict, sources: dict, image_meta: dict, index: dict, issues: Issues, overrides: list[dict]):
    OUT_PRODUCTS.mkdir(parents=True, exist_ok=True)
    for old in OUT_PRODUCTS.glob("*.json"):
        if old.stem not in normalized:
            old.unlink()
    images = {}
    img_sources = []
    for name, ps in normalized.items():
        (OUT_PRODUCTS / f"{name}.json").write_text(
            json.dumps({"products": [seed_record(p) for p in ps]}, indent=1, ensure_ascii=False) + "\n")
        for p in ps:
            if p.get("image") and p["slug"] in image_meta:
                images[p["slug"]] = image_record(p)
                img = p["image"]
                img_sources.append({
                    "productId": p["sku"], "slug": p["slug"], "productName": p["name"], "brand": p["brand"],
                    "sourcePage": img["page"], "assetUrl": img["url"],
                    "localAsset": f"frontend/public/images/catalog/{p['slug']}-800.webp",
                    "sourceType": img["sourceType"], "match": img["match"],
                    "retrievedAt": p["sources"].get("retrievedAt"), "rights": img["rights"],
                    "sourcePx": image_meta[p["slug"]].get("sourcePx"),
                })
    for o in overrides:
        if o["slug"] in image_meta:
            images[o["slug"]] = image_record(o)
            img = o["image"]
            img_sources.append({
                "productId": o["sku"], "slug": o["slug"], "productName": o["name"], "brand": o["brand"],
                "sourcePage": img["page"], "assetUrl": img["url"],
                "localAsset": f"frontend/public/images/catalog/{o['slug']}-800.webp", "sourceType": img["sourceType"],
                "match": img["match"], "retrievedAt": o["sources"].get("retrievedAt"), "rights": img["rights"],
                "sourcePx": image_meta[o["slug"]].get("sourcePx"), "replacesDemoImage": True,
            })
    OUT_IMAGES.write_text(json.dumps(dict(sorted(images.items())), indent=1, ensure_ascii=False) + "\n")
    (ROOT / "catalog-data/product-image-sources.json").write_text(
        json.dumps(sorted(img_sources, key=lambda r: r["productId"]), indent=1, ensure_ascii=False) + "\n")

    # Stale processed images (product removed or renamed) are deleted so the public folder mirrors the catalog.
    keep = {f"{s}-800.webp" for s in images} | {f"{s}-400.webp" for s in images}
    # Never delete images belonging to source files left out of this build (e.g. still being researched).
    for f in SOURCES.glob("*.json"):
        if f.stem not in normalized:
            for m in re.finditer(r'"slug":\s*"([a-z0-9-]+)"', f.read_text()):
                keep |= {f"{m.group(1)}-800.webp", f"{m.group(1)}-400.webp"}
    for f in IMG_DIR.glob("*.webp"):
        if f.name not in keep:
            f.unlink()

    all_products = [p for ps in normalized.values() for p in ps]
    today = date.today().isoformat()

    lines = ["# Catalog sources", "",
             "Generated by `python3 scripts/catalog/build.py` from `catalog-data/sources/*.json`. Do not edit by hand.", "",
             "Every product records the official page its details were read from. Prices are TrustKart **virtual** "
             "reference prices, not live retail prices:", "",
             "- **msrp**: manufacturer's published list price for the configuration described.",
             "- **starting-at**: manufacturer's published starting price; the configurations spec lists the options.",
             "- **estimate**: no public manufacturer price (quote-only enterprise hardware, or price not published). "
             "The number is a TrustKart simulation value and must not be read as a quote.", "",
             f"Built {today}. {len(all_products)} products from {len(normalized)} source files.", ""]
    for name, ps in normalized.items():
        meta = sources[name].get("meta") or {}
        lines += [f"## {name} ({len(ps)})", ""]
        if meta.get("notes"):
            lines += [md_escape(meta["notes"]), ""]
        lines += ["| SKU (TrustKart) | Product | Category | Price | Basis | Price as of | Official page | Spec source | Retrieved |",
                  "|---|---|---|---|---|---|---|---|---|"]
        for p in ps:
            s = p["sources"]
            lines.append("| " + " | ".join(md_escape(x) for x in [
                f"`{p['sku']}`", p["name"], p["category"], money_str(p["price"]), p["priceBasis"],
                p.get("priceAsOf", ""), f"[link]({s['product']})",
                f"[link]({s['specs']})" if s.get("specs") else "same", s.get("retrievedAt", "")]) + " |")
        lines.append("")
    lines += ["SKUs beginning `TK-` are TrustKart identifiers, not manufacturer part numbers.", ""]
    (ROOT / "docs/CATALOG_SOURCES.md").write_text("\n".join(lines))

    rows = ["# Catalog image sources", "",
            "Generated by `python3 scripts/catalog/build.py`. Machine-readable copy: "
            "`catalog-data/product-image-sources.json`.", "",
            "Images were downloaded once from the manufacturer page or asset listed, flattened onto white, trimmed, "
            "centered on an 800x800 canvas and saved as WebP (800 and 400 px). Nothing is hotlinked.", "",
            "**Usage rights.** Manufacturer product and press images are published by the brand to depict its "
            "products, but their terms generally allow editorial or reseller use and do not grant a general license. "
            "TrustKart is a non-commercial portfolio simulation that sells nothing. The `Rights` column records what is "
            "actually known; nothing here should be read as a granted license. Review before any public or "
            "commercial deployment.", "",
            "| Product | Brand | Type | Match | Source page | Asset | Rights / notes |", "|---|---|---|---|---|---|---|"]
    for r in sorted(img_sources, key=lambda r: (r["brand"], r["productName"])):
        rows.append("| " + " | ".join(md_escape(x) for x in [
            r["productName"], r["brand"], r["sourceType"], r["match"], f"[page]({r['sourcePage']})",
            f"[asset]({r['assetUrl']})" if r["assetUrl"].startswith("https://") else r["assetUrl"], r["rights"]]) + " |")
    missing = [p for p in all_products if p["slug"] not in images]
    rows += ["", f"## Products without a catalog image ({len(missing)})", "",
             "These render the storefront's neutral fallback until an image is sourced.", ""]
    rows += [f"- `{p['sku']}` {p['name']}" for p in missing]
    (ROOT / "docs/CATALOG_ASSET_SOURCES.md").write_text("\n".join(rows) + "\n")

    def count_by(fn):
        out = {}
        for p in all_products:
            k = fn(p)
            out[k] = out.get(k, 0) + 1
        return dict(sorted(out.items(), key=lambda kv: (-kv[1], kv[0])))

    top = {slug: info["path"][0] for slug, info in index.items()}
    BUILD.mkdir(parents=True, exist_ok=True)
    report = {
        "builtAt": today, "products": len(all_products), "images": len(images),
        "brands": len({p["brand"] for p in all_products}),
        "byBrand": count_by(lambda p: p["brand"]), "byCategory": count_by(lambda p: p["category"]),
        "byDepartment": count_by(lambda p: top[p["category"]]), "bySource": {n: len(ps) for n, ps in normalized.items()},
        "byPriceBasis": count_by(lambda p: p["priceBasis"]),
        "imageSourceTypes": count_by(lambda p: p["image"]["sourceType"] if p["slug"] in images else "none"),
        "missingImages": [p["sku"] for p in missing],
        "imageWarnings": [w for w in issues.warnings if " image:" in w or "identical processed" in w],
    }
    (BUILD / "report.json").write_text(json.dumps(report, indent=1, ensure_ascii=False) + "\n")
    return report


def check_links(normalized: dict, issues: Issues):
    urls = {}
    for ps in normalized.values():
        for p in ps:
            for u in {p["sources"].get("product"), p["sources"].get("specs"), p.get("priceSource")}:
                if u:
                    urls.setdefault(u, p["sku"])

    def head(u):
        req = urllib.request.Request(u, headers={"User-Agent": UA}, method="GET")
        try:
            with urllib.request.urlopen(req, timeout=25) as r:
                return r.status
        except urllib.error.HTTPError as e:
            return e.code
        except Exception as e:  # noqa: BLE001 - report any network failure
            return str(e)[:60]

    with concurrent.futures.ThreadPoolExecutor(max_workers=12) as pool:
        for u, status in zip(urls, pool.map(head, urls)):
            if status != 200:
                # Many manufacturer sites reject scripted requests (403) even though the page is valid in a browser.
                issues.warn(f"{urls[u]} link", f"{status} {u}")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true", help="validate only")
    ap.add_argument("--images", action="store_true", help="validate + process images, no emit")
    ap.add_argument("--only", help="comma-separated source names")
    ap.add_argument("--exclude", help="comma-separated source names left out of a full build (e.g. files still being researched)")
    ap.add_argument("--check-links", action="store_true")
    ap.add_argument("--quiet-warnings", action="store_true")
    ap.add_argument("--categories", action="store_true", help="print leaf categories and their spec keys")
    args = ap.parse_args()
    if args.categories:
        for slug, info in category_index(merged_categories()).items():
            if info["leaf"]:
                keys = ", ".join(f"{k}:{t[0]}" for k, t in info["specs"].items())
                print(f"{slug:26} {' > '.join(info['path'])}\n{'':26} {keys}")
        return 0
    only = set(args.only.split(",")) if args.only else None
    if only and not (args.check or args.images):
        print("--only is only allowed with --check or --images (a full build must see every source)", file=sys.stderr)
        return 2

    index = category_index(merged_categories())
    demo_products = json.loads(DEMO_PRODUCTS.read_text())["products"]
    issues = Issues()
    sources = load_sources(only)
    excluded = set(args.exclude.split(",")) if args.exclude else set()
    for name in excluded:
        sources.pop(name, None)
    if only and set(only) - set(sources) - {"demo-image-overrides"}:
        print(f"unknown source(s): {sorted(set(only) - set(sources))}", file=sys.stderr)
        return 2
    # Cross-file duplicate detection needs every file even when validating a subset.
    all_sources = load_sources(None) if only else sources
    if not only:
        all_sources = {k: v for k, v in all_sources.items() if k not in excluded}
    normalized_all = validate(all_sources, index, issues, demo_products)
    normalized = {k: v for k, v in normalized_all.items() if k in sources}
    if only:
        scoped = tuple(f"{n}[" for n in only) + (("demo-image-overrides",) if "demo-image-overrides" in only else ())
        issues.errors = [e for e in issues.errors if e.startswith(scoped) or any(
            p["sku"] in e for ps in normalized.values() for p in ps)]
        issues.warnings = [w for w in issues.warnings if w.startswith(scoped)]

    overrides = load_overrides(demo_products, issues) if (not only or "demo-image-overrides" in only) else []
    report = None
    if not args.check:
        image_meta = run_images(normalized, issues, overrides)
        if not args.images:
            if args.check_links:
                check_links(normalized, issues)
            report = emit(normalized, all_sources, image_meta, index, issues, overrides)

    count = sum(len(v) for v in normalized.values())
    if not args.quiet_warnings:
        for w in issues.warnings:
            print("WARN ", w)
    for e in issues.errors:
        print("ERROR", e)
    print(f"\n{count} products in {len(normalized)} source file(s): {len(issues.errors)} errors, "
          f"{len(issues.warnings)} warnings")
    if report:
        print(f"emitted {report['products']} products, {report['images']} images, {report['brands']} brands")
    return 1 if issues.errors else 0


if __name__ == "__main__":
    sys.exit(main())
