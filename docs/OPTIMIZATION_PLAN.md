# Optimization plan

Written 2026-09-29 after a measured baseline (Phase 0). Every problem below was observed in the running app or
found in the code; numbers come from `docs/PERFORMANCE_AUDIT.md`. Priorities: **P0** critical, **P1** high,
**P2** medium, **P3** low.

## Baseline

- Production build: 567 KB of JavaScript in 51 files (190 KB gzipped); entry chunk 279 KB (85 KB gzipped);
  CSS 66 KB (19 KB gzipped). Every route except Home and 404 is already lazy-loaded.
- Lighthouse (mobile, simulated slow 4G): Home 77 / Product 72 / Search 65 performance; accessibility 99 to 100;
  best practices 100; SEO 92 (missing `robots.txt`).
- Layout shift: Product CLS 0.39 (phone), Search 0.42 (Lighthouse), Rankings 0.26, Category 0.12.
- Home on a phone downloads 82 requests and 850 KB before the visitor scrolls.
- API: fast locally (under 20 ms), compressed with Brotli in production by Render, but the unread-count poll runs
  20 to 25 SQL statements including writes every 60 seconds for every open tab.
- Backend: 2.6 s startup locally, about 190 s on Render's shared CPU; 384 MB RSS locally. Docker image 458 MB,
  multi-stage, non-root, layered jar.

## UI/UX Issues

| # | Issue | Where | Priority |
|---|---|---|---|
| U1 | Footer is visible while a lazy route loads, then jumps: the main source of layout shift on every lazy page | `RootLayout.tsx` | P0 |
| U2 | Home has no `h1`; heading styles vary (5 different h1 treatments) | `HomePage.tsx`, pages | P1 |
| U3 | Hard-coded hex colours in search duplicate design tokens | `SmartSearch.tsx` | P2 |
| U4 | Wallet balance in the header changes width when it loads, shifting the search box | `SiteHeader.tsx` | P2 |

## Mobile Issues

| # | Issue | Where | Priority |
|---|---|---|---|
| M1 | Home collection tiles are two columns of 2x2 mini-grids on phones: names truncated to "Apple iPh...", prices overlap | `HomePage.tsx`, `CollectionTile.tsx` | P0 |
| M2 | Checkout overflows by 13 px at 320 px (line prices cannot shrink) | `CheckoutPage.tsx` | P0 |
| M3 | Wishlist "new list" form overflows by 4 px at 320 px | `WishlistPage.tsx` | P1 |
| M4 | Header uses about 190 px on phones because "Hello, name / Account" wraps onto its own row | `SiteHeader.tsx` | P1 |
| M5 | Tap targets under 40 px: hero dots 24 px, hero pause 28 px, wishlist heart 36 px, quantity steppers, 16 px compare checkbox, 32 px small buttons, 20 px footer links | several | P1 |
| M6 | Product page: mobile add-to-cart bar can sit under the compare tray | `ProductPage.tsx`, `CompareTray.tsx` | P2 |

## Tablet Issues

No layout breakage at 600, 768, 820 or 1024 px (automated overflow scan plus screenshots). Search and category
grids use 3 columns at 768 px and read well. Nothing blocking; tap-target fixes (M5) also apply.

## Desktop Issues

Content is capped at a sensible width at 1440 to 1920 px and grids stay balanced. No P0/P1 desktop problems were
found; layout shift (U1) is the main desktop issue.

## Accessibility Issues

| # | Issue | Priority |
|---|---|---|
| A1 | Visible label and accessible name differ on the account link (Lighthouse `label-content-name-mismatch`) | P1 |
| A2 | Product spec table uses a fake caption (`table-fake-caption`); search page heading order skips a level | P2 |
| A3 | Processing overlay has no focus management; a button nested inside a listbox option in search | P2 |
| A4 | Disabled "Proceed to checkout" link can still be activated from the keyboard | P2 |

## Frontend Performance

| # | Issue | Priority |
|---|---|---|
| F1 | Product pages fetch the checkout quote (17 SQL statements and a wallet row lock) even though the Buy-now dialog is closed, and refetch it after every add-to-cart | P0 |
| F2 | Unread-count polls every 60 s for every visitor, including guests with no shopper | P0 |
| F3 | Search suggestions request fires on every page load with an empty query; no minimum length | P1 |
| F4 | Home LCP waits for JS download, then the home API call, then the hero image (5.3 s simulated) | P1 |
| F5 | Global retry (8 attempts) also applies to background polling | P2 |
| F6 | Header search placeholder rotates every 3.5 s, re-rendering the header on every page | P3 |

## Images

Already good: WebP at 400 and 800 px (average 9 KB and 30 KB), `srcset`/`sizes`, intrinsic dimensions, lazy loading
below the fold, one-shot error fallback. Remaining: collection page thumbnails lack dimensions, a few thumbnails
(orders, checkout, notifications) have no lazy loading or error fallback (P2). AVIF was considered and deferred:
catalog images are owned by the catalog pipeline and WebP is already small.

## API

| # | Issue | Priority |
|---|---|---|
| API1 | `/notifications/unread-count` does a read-write sync (inserts, deletes, 3 uncached ranking queries) on every poll | P0 |
| API2 | `/catalog/home` runs 20 statements (one per collection tile in a loop) and sends 64 KB with a duplicated hero and the whole category tree | P1 |
| API3 | Cart and quote resolve options with one product lookup per line (N+1) | P1 |
| API4 | GET `/wallet` and GET `/checkout/quote` take `SELECT ... FOR UPDATE` on the wallet row | P1 |
| API5 | Facets run one query per filterable spec (16 statements for laptops) | P2 |

## Backend

| # | Issue | Priority |
|---|---|---|
| B1 | Category tree is rebuilt from the database on every catalog request | P1 |
| B2 | Rate-limit and revocation Redis calls happen inside database transactions | P2 |
| B3 | Unused `spring-boot-starter-cache` dependency and some dead repository methods | P3 |

## Database

| # | Issue | Priority |
|---|---|---|
| D1 | No index leads with `product_collection.product_id` (used for a product's collection tags) | P2 |
| D2 | Indexes never used by any query: `product_specs_gin` (`jsonb_path_ops` cannot serve `->>`), `product_name_trgm` | P3 (documented, not dropped) |
| D3 | `login_event` and `user_session` have no retention job | P3 |

## Redis

Redis is used for rate limits, session revocation and OAuth state only, all with TTLs; it fails open. Nothing to
add: the data that is hot (catalog, leaderboard) is small and cheaper to cache in process. Documented in
`docs/PERFORMANCE.md`.

## Docker

Already multi-stage, non-root, layered, Alpine JRE. No repo-root `.dockerignore` (not needed: build context is
`backend/`). Nothing P0/P1.

## Testing

Baseline tests are green (134 backend, 11 frontend unit, 14 Playwright). Add: responsive overflow check
(`scripts/responsive-audit.mjs`), page-weight measurement (`scripts/measure.mjs`), a query-count regression test
for the unread-count path, and a Playwright check that Home, Product and Search have no horizontal overflow at
390/768/1440.

## Priorities

1. P0: U1, M1, M2, F1, F2 plus API1 (the notification poll), then re-measure.
2. P1: M3, M4, M5, U2, A1, F3, F4, API2, API3, API4, B1.
3. P2/P3 as time allows, each documented if deferred.

## Status (2026-09-29)

Done: U1, U2 (home h1), U4, M1, M2, M3, M4, M5 (to 36 to 40 px), A1, A2, A4, F1, F2, F3, F4 (early data and banner
preload), F5 (poll retries), API1, API2, API3, API4, B1, D1, plus the banner changing height between slides (found
during the pass). Measured results: [PERFORMANCE_AUDIT.md](PERFORMANCE_AUDIT.md).

Deferred, with reasons in the audit's "Remaining issues": U3 (token clean-up in search colours), M6, A3, F6, API5,
B2, B3, D2, D3, a 600 px image variant, and server rendering for the home page.
