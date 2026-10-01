# API and network audit

Twenty-one endpoints were each sampled five times with request-correlated SQL logging on the isolated warm stack. These medians include local HTTP handling and diagnostic logging; they are not production load-test percentiles. Bodies are decoded JSON bytes, while browser transfer numbers include protocol/response overhead. Long receipt UUIDs identify synthetic local orders only.

| Endpoint (under /api/v1) | Median ms | JSON body bytes | SQL statements/request |
|---|---:|---:|---:|
| `/catalog/home` | 2.81 | 66,115 | 0 |
| `/catalog/categories` | 2.10 | 12,257 | 0 |
| `/catalog/products?size=24` | 10.37 | 16,942 | 2 |
| `/catalog/products/apple-iphone-18-pro` | 9.54 | 10,135 | 6 |
| `/catalog/categories/laptops/facets` | 11.71 | 7,103 | 11 |
| `/search?q=macbook&size=24` | 10.62 | 17,188 | 2 |
| `/search/suggest?q=macbook` | 5.30 | 3,035 | 2 |
| `/cart` | 5.14 | 862 | 4 |
| `/wallet` | 2.77 | 86 | 2 |
| `/checkout/quote` | 4.84 | 390 | 5 |
| `/purchases?size=10` | 4.53 | 425 | 3 |
| `/purchases/b44416ca-23c0-4256-a112-c4b306d128ee` | 3.80 | 2,452 | 2 |
| `/collection` | 7.51 | 13,886 | 13 |
| `/wishlist` | 4.01 | 870 | 4 |
| `/notifications/unread-count` | 7.09 | 11 | 8 |
| `/notifications?size=30` | 8.41 | 892 | 10 |
| `/leaderboards/monthly` | 1.62 | 934 | 0 |
| `/leaderboards/all-time` | 1.67 | 983 | 0 |
| `/leaderboards/me` | 3.43 | 288 | 3 |
| `/me` | 3.21 | 343 | 4 |
| `/me/sessions` | 2.92 | 204 | 1 |

Slowest median among tested endpoints: laptop facets, 11.71ms. A larger query count does not automatically mean unacceptable latency: the detail and collection endpoints use deliberate separate projections too. Wishlist query growth and notification write churn were separately reproduced at increasing data sizes and are findings.

## Initial home requests and payload

Cold desktop home issued 83 total requests, including **seven API reads**: catalog/home, catalog/categories, cart, wallet, me, notifications/unread-count and wishlist/ids. Initial transferred bytes were ~948.2KiB; image traffic was 672,784 bytes, fonts 48,577, scripts 145,007, CSS 20,230 and Fetch 83,287 in the representative desktop run. No duplicate API URL was captured in the 30 measured cold route loads. Recently viewed state can add a lookup request; it was empty in the cold home fixture.

The home JSON body was 66,115 bytes; list page size 24 was 16,942 bytes, and product detail 10,135 bytes. Cards are summaries with card-required pricing/stock/image data rather than full descriptions/reviews/options. Home returns 11 collection tiles despite rendering at most six: [FE-001](OPTIMIZATION_FINDINGS.md#fe-001). Category data also appears in home plus the independent header categories response (12,257 bytes); this is an overlap worth considering when designing that change, but removing the header query blindly could create a navigation waterfall on non-home routes. It is not a separate quantified fix claim.

## Search and request control

Typing m → ma → mac → macb → macbo → macbook at 60ms per character generated one suggestions call for macbook. Suggestions have 150ms debounce, cancellation and a two-character minimum. Search interpretation is local/backend rule-based and SQL-based; no embedding generation or AI request occurred. Smart mode is explicitly disabled. Preserve fast suggestions rather than introducing a large delay.

GET reads use abort signals; refresh/CSRF bootstrapping use shared promises to avoid concurrent duplicate refresh requests. Query retries are bounded and do not retry normal 4xx. No wallet polling or whole-app refetch after cart quantity changes was found. Notifications use two-minute polling; BE-003 is a write-amplification problem under that otherwise reasonable cadence.

## Correctness and bounded lists

[API-001](OPTIMIZATION_FINDINGS.md#api-001) reproduces an options=null HTTP 500. [API-002](OPTIMIZATION_FINDINGS.md#api-002) reproduces incorrect totals on an empty out-of-range page. [UX-004](OPTIMIZATION_FINDINGS.md#ux-004) covers UI access to older notification pages.

Product/search and purchase/notification APIs have server-side page bounds. Public rankings cap at monthly 50/all-time 100. Wishlists have finite list/item limits but use repeated read queries (BE-002). Collection currently returns the shopper's aggregate acquired-product collection without page controls; a large lifetime collection was not generated, so this audit makes no measured slowdown claim. Before applying virtualization or a new API contract there, generate a representative high-volume fixture and measure response/DOM size.

## Caching and compression limits

Local API responses did not contain Content-Encoding; the preview did gzip larger JS/CSS. Hosted Vercel compression was not measured, so do not claim that production JSON is uncompressed. Catalog/search public responses carry CDN/browser cache headers; personal data must remain private. Existing PublicCacheIT verifies public cookie-free versus private behavior. Catalog stale-if-error/stale-while-revalidate policy can retain older public data during upstream sleep/outage; server-side repricing protects checkout correctness. Do not weaken that check to avoid a refresh.

Evidence: `api-measurements.json`, `api-query-counts.json`, `performance.json`, payload snapshots, `interactions.json` and `bounded-query-counts.json`.
