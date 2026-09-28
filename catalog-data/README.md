# TrustKart catalog data

Researched product data for the TrustKart catalog, kept apart from application code.

```
catalog-data/sources/<name>.json   raw researched records, one file per brand group / domain (edit these)
scripts/catalog/build.py           validates sources, normalizes images, emits seed data + provenance docs
backend/src/main/resources/catalog/products/<name>.json   generated seed files (do not edit)
backend/src/main/resources/catalog/images.json            generated image records (do not edit)
backend/src/main/resources/catalog/categories.json        category/spec extensions merged into demo/categories.json
frontend/public/images/catalog/                           generated WebP images (do not edit)
```

`DemoCatalogSeeder` loads `demo/*` first, then everything under `catalog/`. Seeding is idempotent: products are keyed
by SKU, so re-running inserts only missing products and never creates duplicates.

## Commands

```bash
python3 scripts/catalog/build.py --check --only apple     # validate one source file (fast, offline)
python3 scripts/catalog/build.py --images --only apple    # also download + normalize its images
python3 scripts/catalog/build.py                          # full build: validate everything, images, emit
python3 scripts/catalog/build.py --check-links            # full build + fetch every source URL
```

## Source file format

```jsonc
{
  "meta": { "notes": "What this file covers, research date, anything a reviewer should know." },
  "products": [
    {
      "sku": "TK-APPLE-IPHONE17PRO",            // TrustKart id: TK-<BRAND>-<MODEL>, A-Z0-9 and dashes, <= 40 chars
      "slug": "apple-iphone-17-pro",            // brand-first, lowercase, unique, stable forever
      "name": "Apple iPhone 17 Pro",            // brand + official model name
      "brand": "Apple",                         // brand exactly as the manufacturer writes it
      "category": "smartphones",                // a LEAF category slug (see below)
      "price": "1099.00",                       // USD reference price (string)
      "priceBasis": "starting-at",              // msrp | starting-at | estimate
      "priceSource": "https://www.apple.com/shop/buy-iphone/iphone-17-pro",  // required unless estimate
      "priceAsOf": "2026-09-28",                // required unless estimate
      "compareAt": null,                        // optional; must be above price (only for a real official promo)
      "releaseYear": 2025,
      "summary": "One sentence, <= 300 chars, factual.",
      "description": "2-4 factual sentences from the official page. No invented claims.",
      "keywords": "space separated extra search words",
      "specs": { "chip": "A19 Pro", "displayIn": 6.3 },       // keys must exist for the category; types must match
      "variants": [ { "label": "256 GB", "price": "1099.00" }, { "label": "512 GB", "price": "1299.00" } ],
      "colors": ["Silver", "Deep Blue", "Cosmic Orange"],
      "collections": ["latest-iphones", "apple-ecosystem"],
      "featured": false,
      "warranty": 12,
      "sources": {
        "product": "https://www.apple.com/iphone-17-pro/",          // official product page
        "specs": "https://www.apple.com/iphone-17-pro/specs/",      // official spec page (if different)
        "retrievedAt": "2026-09-28"
      },
      "image": {
        "url": "https://www.apple.com/newsroom/images/....jpg",    // direct official asset URL
        "page": "https://www.apple.com/newsroom/2025/09/....",      // page that publishes the asset
        "sourceType": "manufacturer-press",       // manufacturer-press | manufacturer-media-library |
                                                  // manufacturer-product-page | wikimedia-commons | project-render
        "rights": "Apple Newsroom image; Apple permits use for news/editorial coverage. No general license verified.",
        "alt": "iPhone 17 Pro in Cosmic Orange, back view showing the triple camera plateau",
        "match": "EXACT",                         // EXACT (this model) | PRODUCT_LINE (identical design, other config)
        "allowLowRes": false,                     // true only when the maker publishes nothing >= 480px (floor 320px)
        "darkBackground": false                   // true only for a clean official DARK studio render (no scene, no text)
      }
    }
  ]
}
```

### Rules

- **Research, don't remember.** Every product, spec and price must be read from the official manufacturer page at
  research time. If a spec isn't published, leave it out. Never guess. When the only honest price is a guess, use
  `"priceBasis": "estimate"`.
- **Current products only.** Don't add discontinued models as current.
- **Variants, not duplicates.** One product per model (or per meaningfully different model size or chip tier, such as
  iPhone 17 Pro vs Pro Max). Storage, colour and RAM options go in `variants` / `colors`, which render as the
  *Configurations* and *Colors* specs. The schema has no variant table yet (see `docs/CATALOG_EXPANSION_NOTES.md`).
- **Spec values describe the base configuration** whose price is shown (for example the smallest storage).
- **Images:** official manufacturer imagery only (press/media kits first, then official product-page assets). No
  retailer images (Amazon, Best Buy, Newegg and similar are rejected by the validator), no watermarks, no people,
  hands, rooms, desks or store shelves, and no packaging. Clean white, light grey or transparent background. The
  image must show the correct model. Record `rights` truthfully: say "not verified" when you don't know. Never
  invent a license.
- Don't bypass site protections. If an asset can't be fetched with a normal request, skip it and leave `image` out.

## Categories and spec keys

Run `python3 scripts/catalog/build.py --check` on a file with a bad key and the error lists the allowed keys. The
tree comes from `demo/categories.json` merged with `catalog/categories.json`.

## Collections (merchandising tags)

`latest-iphones`, `apple-ecosystem`, `galaxy-flagships`, `samsung-ecosystem`, `pixel-google`, `premium-laptops`,
`developer-setup`, `developer-favorites`, `gaming-monsters`, `ultimate-gaming-setup`, `dream-gaming`, `creator-workstations`,
`dream-gpus`, `ai-lab`, `ai-workstations`, `enterprise-servers`, `enterprise-lab`, `servers-workstations`,
`homelab-starter`, `homelab-essentials`, `oled-displays`, `high-end-storage`, `networking-lab`, `security-lab`,
`small-business`, `deals`.
