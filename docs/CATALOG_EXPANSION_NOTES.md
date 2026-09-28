# Catalog expansion notes

Built on `trustkart-catalog-expansion` (worktree `apps/TrustKart-catalog`) alongside the main
`trustkart-rebuild` work, and merged into `trustkart-rebuild` in several steps (latest `a7bb8f3`), which is
pushed as PR #1.

## Result

| | |
|---|---|
| Storefront products | 113 → **1,192** (1,079 researched catalog products + the original 113) |
| Brands (catalog) | 106 |
| Departments | 11 → **18** (Phones, Watches & Wearables, Audio, TVs, Cameras & Drones, Gaming, Smart Home) |
| Images | every visible product has an official manufacturer photo (1,079 catalog images); 0 "Image unavailable" |
| Products with selectable options | 311 (storage, size, pack, configuration, color), with server-resolved prices |
| Prices | 675 `msrp`, 208 `starting-at`, 196 `estimate` (quote-only enterprise gear or no published US price) |
| Parked | 44 researched products with no obtainable official image, hidden (see below) |
| Tests | 122 backend tests pass on the merged tree; `scripts/validate_images.py` 0 errors; `scripts/catalog/check_live.py` 0 problems on 5173 |

Largest departments: Networking 296, Laptops 106, Servers 102, PC Components 101, Peripherals 86, Storage 72.

## How the catalog is built

| Path | Role |
|---|---|
| `catalog-data/sources/*.json` | Researched records with provenance: official product/spec URL, price basis and date, image source and rights. Edit these. |
| `catalog-data/pending/*.json` | Researched products parked until an official image exists |
| `scripts/catalog/build.py` | Validates, normalizes facets, derives options, downloads and normalizes images, emits seed data and docs |
| `scripts/catalog/park_imageless.py` | Moves imageless products to `pending/` |
| `scripts/catalog/check_live.py` | End-to-end check against a running site: every product page, every image, no parked product visible |
| `backend/src/main/resources/catalog/` | Generated seed files: `products/*.json`, `images.json`, `categories.json` (extensions), `parked.json` |
| `frontend/public/images/catalog/` | Generated 800/400 px WebP images, plus `categories/` department tiles |
| `docs/CATALOG_SOURCES.md`, `docs/CATALOG_ASSET_SOURCES.md`, `catalog-data/product-image-sources.json` | Provenance, generated |

Typical loop: edit sources, run `python3 scripts/catalog/build.py` (it must report 0 errors), restart the backend,
then run `python3 scripts/catalog/check_live.py http://localhost:5173`.

## Behaviour worth knowing

- **Seeding** (`DemoCatalogSeeder`):
  - Insert-only by SKU.
  - On restart, existing catalog products pick up new images and options.
  - SKUs in `catalog/parked.json` are set to DRAFT, which hides them from search, product pages and counts.
  - A parked product returns once it's back in the seeds.
  - Brands are looked up by slug, which fixed a crash from brand names differing only in case (`CORSAIR`/`Corsair`).
- **Options** (V9 `product.options`):
  - At most one priced group per product, and its default equals `product.price`.
  - `CatalogService.resolveOptions()` is the only source of option prices.
  - The cart/checkout side (V10) is the main terminal's.
- **Facets:** filterable text specs (resolution, panel, socket, OS, Wi-Fi, layout, platform, mount, printer technology, audio connectivity) are normalized at build time to the demo catalog's formats. The original wording is kept in a detail spec.
- **Images:**
  - Official manufacturer sources only, never retailer or reseller CDNs.
  - Flattened onto white, trimmed and centered.
  - Two flagged exceptions, set per image after a visual check:
    - `allowLowRes`: the maker publishes nothing ≥480 px (Razer's 500 px studio PNGs).
    - `darkBackground`: official dark studio renders (NVIDIA RTX PRO / DGX).
  - The download retries once as `curl` when a CDN rejects browser-like agents (cisco.com, i.dell.com, supermicro.com). No other protection is worked around.
- **Rights:** manufacturer images are recorded as "rights not verified" and never claimed as licensed. Review before any public or commercial deployment.

## Parked (44) — why

- **Sites block all automated access:** Cisco, APC/Schneider, HPE.
- **No clean single-product official image:**
  - Valve Steam Machine, Controller and Frame (beige scenes)
  - Hisense UR9 and U7, Sony BRAVIA 7 II (text on screen art)
  - Meta Quest 3S (expiring signed URLs)
  - FortiGate 90G, Micron and Solidigm enterprise SSDs, several Intel NICs, Xeon 698X, Gaudi 3 PCIe
  - a few Sysracks, Toshiba and KIOXIA drives, MSI/Acer desktops, Sonos Ace Ultra, Shure SM7dB
- Full list: `catalog-data/pending/`.

## Known gaps / next steps

- **Brands still thin or missing:**
  - Kingston, be quiet!, Fractal and ASRock (blocked or no US pricing)
  - Juniper, HPE Aruba, Arista and Gigabyte servers (blocked)
  - Canon and Insta360 cameras, Tripp Lite / Eaton, Monoprice
- **Lenovo:** covered by the networking batches (ThinkSystem servers, ThinkCentre Tiny), but it still has no consumer-laptop source file.
- **Web search:** research agents share a 200-web-search budget per session.
