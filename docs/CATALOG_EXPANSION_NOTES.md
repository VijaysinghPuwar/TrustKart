# Catalog expansion: integration notes

Written for whoever merges this branch alongside the main TrustKart work.

- **Branch / worktree:** `trustkart-catalog-expansion`, worktree `apps/TrustKart-catalog`, branched from `50bbee9`.
  It was built in parallel with the uncommitted `trustkart-rebuild` work and never touched that checkout.
- **Result:** 113 → **905** products (792 new), 88 new-catalog brands, 18 departments (7 new), 689 new product images.
- **Tests:** full backend suite, 106 tests, 0 failures. The frontend build passes. The seed was checked visually against
  an isolated stack: Postgres on :5443, Redis on :6390, API on :8090, Vite on :5183.

## What was added

| Area | Files |
|---|---|
| Researched source data (edit these) | `catalog-data/sources/*.json` (17 files, one per brand group) |
| Build + validation pipeline | `scripts/catalog/build.py`, `catalog-data/README.md` |
| Generated seed data (do not edit) | `backend/src/main/resources/catalog/products/*.json`, `catalog/images.json` |
| Category/spec extensions | `backend/src/main/resources/catalog/categories.json` |
| Images (generated) | `frontend/public/images/catalog/<slug>-{800,400}.webp` |
| Provenance | `docs/CATALOG_SOURCES.md`, `docs/CATALOG_ASSET_SOURCES.md`, `catalog-data/product-image-sources.json` |

`demo/categories.json`, `demo/products.json` and `demo/images.json` are **unchanged**, and so are the 113 existing
products and their images.

## Code changes outside data

| File | Change | Conflict risk |
|---|---|---|
| `catalog/seed/DemoCatalogSeeder.java` | Loads `catalog/categories.json` (grafted onto the demo tree in memory), every `catalog/products/*.json` and `catalog/images.json`. Brands are looked up **by slug** (fixes a crash: `CORSAIR` vs `Corsair` collided on `brand_slug_key`). A product that already exists and has no image gets its seed image. Otherwise still insert-only. | **High.** The main checkout also has uncommitted edits to this file. Keep both sides' changes. |
| `catalog/seed/SeedModel.java` | `CategoryExtensionFile`, `CategoryExtension`, `CategorySeed.extendedWith/withChildren` | Low |
| `catalog/infra/BrandRepository.java` | `findBySlug` | Low |
| `search/application/QueryInterpreter.java` | Search phrases for the new departments (phone, tv, earbuds, drone, console...), plus guards so "raid controller" and "nintendo switch" aren't misread as gaming controllers or network switches | Low |
| `frontend/src/features/search/SearchPage.tsx` | Titles for the new collection tags | Low (main has not modified it) |
| `CatalogApiIT`, `QueryInterpreterTest` | Assertions no longer pin demo-era counts. Adds a department-seeding test and phrase-collision tests. | Low |

No migration. The schema is unchanged: variants are expressed as specs (below).

## Schema assumptions / things the main terminal should know

1. **No variant table.** Storage, colour and RAM options are rendered as the `configurations` spec
   ("256 GB – $1,199 · 512 GB – $1,399") and the `colors` spec. The price shown is the base configuration. A real
   `product_variant` table (plus product-page selector and cart line variant) is the natural follow-up, but it touches
   the product page and cart, so it was left to the main terminal.
2. **18 departments.** The header category bar overflows at 1440 px ("Watches & Wearables" is clipped). The header
   belongs to the main terminal. Consider a "More" menu, or showing only the top N departments.
3. **Image credit text.** In this branch's older `ProductPage.tsx`, manufacturer images show "via Wikimedia Commons".
   Main's uncommitted `ProductPage.tsx` already branches on `sourceUrl`, so this is fixed on merge. Keep main's version.
4. **Category tiles.** `features/home/categoryImages.json` has no entries for the 7 new departments, so their tiles
   render without a photo.
5. **Existing 113 products' images** are the main terminal's (renders/Commons). This branch provides an optional
   hook, `catalog-data/demo-image-overrides/*.json`, for official photos of existing products. It is empty.
6. **Prices:** 528 `msrp`, 184 `starting-at`, 80 `estimate`. Estimates are quote-only enterprise hardware, or brands
   with no published US price. Every estimate is labelled in `docs/CATALOG_SOURCES.md`. None is presented as a live price.
7. **Image rights.** New images are official manufacturer store or press images. Their `rights` field says
   "not verified". None is claimed as licensed. Review before any public or commercial deployment.

## Integration steps

1. Merge `trustkart-catalog-expansion` after main commits its work. Resolve `DemoCatalogSeeder.java` by keeping main's
   changes plus: the `withExtensions` / `catalogProducts` / `backfillImage` methods, the catalog reads in `seed()`, and
   brand lookup by slug.
2. `python3 scripts/catalog/build.py`. It must print `0 errors`.
3. `./mvnw verify` (all 106 tests).
4. Restart with seeding enabled. Existing databases just gain the new categories and products.

## Known gaps (next session)

- **Lenovo:** no source file. The agent hit the usage limit before writing it.
- **Thin coverage:** components (20), security keys (Yubico researched, not added), Canon and Insta360 cameras,
  Kingston, be quiet!, Fractal, Eaton/Tripp Lite, and HP (36 products; the agent was cut short).
- **103 products without images:**
  - All Razer items (only 500 px studio assets exist).
  - Supermicro, APC and Cisco (their sites block scripted downloads).
  - About half of NVIDIA/AMD/Intel.
  - Several TVs (screen art on tinted backgrounds).
  - Full list in `docs/CATALOG_ASSET_SOURCES.md`.
- `catalog-data/build/report.json` has per-brand and per-category counts after each build.
