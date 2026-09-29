# Performance audit

Measured 2026-09-29 on a MacBook (Apple M5) against the local stack: the production frontend build served by
`vite preview`, the Spring Boot API on port 8080 and PostgreSQL 17 in Docker, with the full 1,836-product catalog.
Every number below was measured; nothing is estimated. Where a number did not change it is still listed, so the
table is complete rather than flattering.

How to reproduce:

| What | Command |
|---|---|
| Page weight and Web Vitals | `cd frontend && npm run build && npx vite preview --port 4173 & node scripts/measure.mjs http://localhost:4173 out.json` |
| Responsive scan (13 widths, 20 routes) | `node scripts/responsive-audit.mjs http://localhost:4173 [screenshot-dir]` |
| Lighthouse | `npx lighthouse@12 http://localhost:4173/ --only-categories=performance,accessibility,best-practices,seo` (default mobile preset) |
| SQL statements per request | local Postgres with `log_statement = 'all'` for the duration of the test, counting `statement`/`execute` lines per request (includes `BEGIN`/`COMMIT`) |

`measure.mjs` loads each route in a fresh browser with a cold cache, three times, and reports medians. The phone
profile is 390x844 with 4x CPU slowdown and a ~9 Mbps / 60 ms network; the desktop profile is 1440x900
unthrottled.

## Lighthouse (mobile preset, simulated slow 4G)

Same page and settings before and after; the "after" column is identical in two consecutive runs (±0.2 s LCP).

| Page | Performance | LCP | CLS | Accessibility | Best practices | SEO |
|---|---|---|---|---|---|---|
| Home | 77 → **80** | 5.3 s → **4.7 s** | 0 → 0 | 100 → 100 | 100 → 100 | 92 → **100** |
| Product | 72 → **90** | 3.2 s → 3.1 s | 0.375 → **0** | 100 → 100 | 100 → 100 | 92 → **100** |
| Search | 65 → **87** | 4.0 s → **3.6 s** | 0.421 → **0.001** | 99 → **100** | 100 → 100 | 92 → **100** |

Total blocking time stayed between 0 and 20 ms throughout. First contentful paint is 2.4 to 2.5 s on this preset
before and after; it is bound by downloading the CSS and JavaScript over the simulated slow 4G link (see Remaining
issues).

## Layout shift (CLS, measured in Chromium)

| Page | Phone before → after | Desktop before → after |
|---|---|---|
| Home | 0 → 0 | 0.014 → 0.001 |
| Search | 0.059 → 0.005 | 0.123 → **0** |
| Category | 0.028 → 0.005 | 0.111 → **0** |
| Deals | 0.046 → 0.005 | 0.100 → **0** |
| Product | **0.391 → 0** | **0.317 → 0** |
| Rankings | 0 → 0 | 0.168 → **0** |
| Wallet / Sign-in | 0 → 0 | 0.006 / 0.019 → **0** |

Cause of most of it: while a lazily loaded page was arriving, the footer sat inside the viewport under a small
spinner and then jumped down (0.10 to 0.43 per page). Smaller causes: the home banner placeholder was shorter than
the banner, the banner changed height between slides, and the search "Understood as" row appeared late.

## Page weight and requests (median of 3 cold loads)

"Load" is what downloads before the visitor scrolls; "total" includes scrolling to the bottom.

| Page | Requests | KB on load | KB total | Images | API calls |
|---|---|---|---|---|---|
| Home, phone | 82 → **72** | 851 → 1,030 | 1,513 → **1,373** | 58 → **49** | 8 → **7** |
| Home, desktop | 84 → 83 | 947 → 947 | 947 → 947 | 60 → 60 | 8 → **7** |
| Search, phone | 51 → 51 | 610 → 611 | 1,199 → 1,201 | 25 → 25 | 8 → 8 |
| Product, phone | 38 → **36** | 297 → 297 | 404 → 404 | 8 → 8 | 9 → **7** |
| Product, desktop | 40 → **38** | 355 → 354 | 355 → 354 | 10 → 10 | 9 → **7** |
| Cart, Wallet, Rankings, Compare, Sign-in | each −1 | ±1 | ±1 | same | each −1 |

- Home on a phone downloads **140 KB less in total** (9 fewer images), but more of it arrives before scrolling,
  because the collection tiles became one swipeable row and so sit higher on the page.
- The product page stopped fetching a checkout quote in the background (see API below).
- Every page stopped firing an empty-query search suggestion request.

A note on LCP for text-only pages (cart, wallet, rankings, sign-in): the baseline reported 48 to 52 ms on desktop
because the largest thing painted was the footer, visible under the loading spinner. With the footer now below the
fold, LCP measures the page's real content, about 350 ms (desktop) and 700 ms (phone). That is the honest number,
not a slowdown: the content arrives when it did before.

## Frontend bundle

Unchanged in size, as expected: this pass removed requests and layout work, not features.

| | Before | After |
|---|---|---|
| JavaScript (51 files) | 567.5 KB raw / 190.4 KB gzip | 569.8 KB raw / 190.9 KB gzip |
| Entry chunk | 278.7 KB / 84.7 KB gzip | 280.5 KB / 85.2 KB gzip |
| CSS | 65.7 KB / 19.0 KB gzip | 67.4 KB / 19.4 KB gzip |
| Production build time | 4.0 s | 2.2 s (warm) |

Every route except Home and 404 was already a lazy chunk; icons are imported per icon; all seven runtime
dependencies are used.

## API and database (SQL statements per request, including BEGIN/COMMIT)

| Request | Before | After | What changed |
|---|---|---|---|
| `GET /catalog/home` | 20 | **1** (cached) | Built once per minute in memory, dropped when the catalog is reseeded |
| `GET /catalog/categories` | 6 | **1** (cached) | Same, five-minute lifetime |
| `GET /notifications/unread-count` (every poll) | 20 to 25, with writes | **13, read-only** (8 queries) | Inserts only missing milestones; ranks read from the leaderboard cache; last month's ranking runs only for shoppers who spent last month |
| `GET /cart` (5 lines) | 14 | **9** | Option pricing loads all products in one query instead of one per line |
| `GET /checkout/quote` (5 lines) | 17 | **10** | Same, and no wallet row lock for a preview |
| `GET /wallet` | 9 | **7** | Reads the wallet without `SELECT ... FOR UPDATE` |
| Product page, total | 9 API calls | 7 | The closed "Buy now" dialog no longer fetches a quote |

Response times stayed under 55 ms for every endpoint locally. In production Render compresses responses with
Brotli (verified: `content-encoding: br` on `/api/v1/catalog/home`), so local sizes are raw JSON.

Notification polling also changed on the client: every 2 minutes instead of 60 seconds, only while the tab is
visible (or in the background if the shopper enabled browser alerts, which previously never fired), and one retry
instead of eight.

## Backend

| | Before | After |
|---|---|---|
| Startup (local) | 2.6 s | 2.6 s |
| Catalog seeding on a boot with unchanged data (local) | 26 s | **0.07 s** (skipped by fingerprint) |
| Catalog seeding on Render when products change | 11 min (8:51 to 9:02 PM deploy) | **2.5 min** (9:08 to 9:10 PM deploy) |
| Memory, local (RSS / heap used / metaspace) | 384 MB / 151 MB / 125 MB | not re-measured |
| Test suite | 134 tests pass | 134 tests pass |

## Docker

The image was already multi-stage (JDK Alpine build, JRE Alpine runtime), non-root, and uses Spring Boot's layered
jar. Size 458 MB, of which 94 MB are application layers. No change was needed.

## Responsive scan

`scripts/responsive-audit.mjs` checks 20 routes at 320, 360, 375, 390, 430, 600, 768, 820, 1024, 1280, 1440, 1728
and 1920 px as a signed-in shopper with orders and a cart.

| | Before | After |
|---|---|---|
| Routes with horizontal page scroll | 2 (checkout at 320 px: 13 px; wishlist at 320 px: 4 px) | **0** |
| Phone header height (320 to 430 px) | 218 px (logo, then account row, then search) | **166 px** (one row plus search) |
| Home banner height across slides at 768 px | 449 to 569 px | **360 px, all slides** |
| Smallest tap targets on phones | 24 px (banner dots), 28 px, 32 px buttons | 32x40 px dots, 36 to 40 px controls |

## Remaining issues

- **Home LCP on slow 4G is still 4.7 s.** The banner image can't start downloading until the home data arrives;
  both now start during page load, not after the app boots, but the first paint itself waits for 85 KB of gzipped
  entry JavaScript and 19 KB of CSS. Next step would be server-rendering or statically generating the home page.
- **Phone product cards use the 800 px image** on 3x screens (about 38 KB each). A 600 px variant would cut that
  by roughly a third without visible loss; it belongs in the catalog image pipeline.
- **Facets** still run one query per filterable spec (16 for laptops). Cached HTTP responses hide most of it.
- **Render cold starts** (free plan) take about 3 minutes; the keep-warm workflow and the "starting up" notice
  cover it.
