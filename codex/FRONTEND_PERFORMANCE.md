# Frontend performance

Production build, lint, typecheck, format and unit tests passed. Existing route-level lazy loading already separates non-home pages; Security Center, comparison, rankings, checkout and account code have separate chunks. No admin UI exists to accidentally include in the home bundle. Seven production dependencies were checked against source/config usage; no unused production package or clearly wasteful whole-library import was established. Production npm audit reported zero vulnerabilities at audit time; this does not cover all dev dependencies or Maven CVEs.

## Bundle measurements

| Build output | Raw bytes | Local gzip bytes |
|---|---:|---:|
| All 51 JS chunks (not all initially loaded) | 583,779 | 195,623 |
| `index-CfVJ0_lk.js` | 287,589 | 87,340 |
| `lib-DPLC5VWN.js` | 96,363 | 31,285 |
| `useQuery-CcxDsmI-.js` | 22,742 | 7,511 |
| `CheckoutPage-CFxsqj-v.js` | 16,919 | 5,378 |
| `ProductPage-DhTVAvuQ.js` | 10,980 | 4,002 |
| `SearchPage-BiPrhDPz.js` | 10,704 | 3,889 |
| `RankingsPage-GbiXAawk.js` | 10,618 | 3,406 |
| `keys-Cwlibffg.js` | 10,376 | 3,855 |
| `ReceiptPage-DG5iVclz.js` | 9,512 | 3,248 |
| `jsx-runtime-Dk72oS4N.js` | 8,774 | 3,346 |
| `index-BrzICM7x.css` | 68,981 | 19,831 |

Raw and gzip bytes above come from `evidence/bundle.json`; gzip was calculated locally and may differ slightly from Vite's compression reporting. The initial browser load imports shared chunks too: desktop home transferred **145,007 bytes of JavaScript** and **20,230 bytes of CSS**, including response overhead as counted by CDP. The entry chunk's compressed size alone is not “initial JS.”

## Measured local loads

30 cold browser loads: three runs per route/profile; table values are medians. Desktop: 1440×900, DPR 1, no throttling. Mobile: 390×844, DPR 3, 4× CPU throttle, configured 60ms network latency and approximately 9Mbps download/2Mbps upload. Cache disabled, production Vite preview with same-origin proxy to a local warm Java server. Metrics were sampled after network idle plus one second, then the page was scrolled to collect lazy assets. Guest empty cart was used for performance; populated cart/checkout were covered in functional QA.

| Page / profile | Initial requests | Initial KiB | After-scroll KiB | LCP ms | FCP ms | TTFB ms | Observed CLS | DOM elements | JS heap MiB |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| home / desktop | 83 | 948.2 | 948.2 | 568 | 68 | 1.2 | 0.000997 | 1393 | 5.4 |
| home / mobile | 48 | 1031.0 | 1373.6 | 884 | 444 | 1.0 | 0.000000 | 1401 | 6.0 |
| search / desktop | 51 | 530.1 | 530.1 | 408 | 52 | 1.1 | 0.000000 | 1171 | 5.3 |
| search / mobile | 37 | 691.2 | 1131.3 | 1084 | 420 | 1.0 | 0.005249 | 1179 | 6.1 |
| product / desktop | 38 | 354.4 | 354.4 | 396 | 52 | 1.1 | 0.000000 | 902 | 4.9 |
| product / mobile | 30 | 297.2 | 404.3 | 892 | 416 | 1.0 | 0.000000 | 910 | 4.9 |
| cart / desktop | 23 | 231.0 | 231.0 | 364 | 52 | 1.1 | 0.000000 | 535 | 4.1 |
| cart / mobile | 23 | 231.0 | 231.0 | 692 | 412 | 1.1 | 0.000000 | 543 | 4.1 |
| rankings / desktop | 26 | 235.8 | 235.8 | 348 | 48 | 1.0 | 0.000000 | 711 | 4.5 |
| rankings / mobile | 26 | 235.8 | 235.8 | 692 | 408 | 1.0 | 0.000000 | 719 | 4.5 |

These are laboratory observations on this Mac, not production percentiles or a guarantee for phones. TTFB is the document navigation responseStart measurement, not end-to-end API latency. CLS is observed load-time shift accumulation, not a field session measurement. LCP candidate on home was the hero product image; desktop first sample was a 400px source rendered at 360px. Image loading—not a large video or embedding client—dominates transfer. No page errors or duplicate API URLs were captured across these 30 loads.

Lighthouse Performance score: **Not measured**. Lighthouse Accessibility score: **Not measured**. INP: **Not measured**. A cached Lighthouse binary was unavailable; no score was fabricated. React DevTools profiling/commit counts: **Not measured**. CDP JS heap/script/layout metrics are in `performance.json`, but they do not prove a React rerender problem or a memory leak. No blanket memoization recommendation follows from them.

## React, data and timers

QueryClient is created once with useState. Query keys are centralized; default staleTime is 30 seconds and default window-focus refetch is off. Home/category hooks provide longer freshness where appropriate. GET helpers pass AbortSignal; smart suggestions use a 150ms debounce and a minimum query length. Rapid macbook typing made one request; ArrowDown/Enter and Escape worked. Preserve this behavior.

Cart mutations apply the returned server cart through setQueryData and invalidate quotes; wallet/order mutations invalidate their relevant families. No whole-app cart reload, wallet interval or per-order tracking polling was found. There is no measured reason to add optimistic financial state complexity. Query retries are bounded (up to eight transient retries with delay capped at 15 seconds, designed for cold API startup), and 4xx responses are excluded. Notification count overrides retry to one. Failure-screen usability during a real hosted cold start remains unmeasured.

Two repeating UI timers were found: search placeholder rotation at 3.5 seconds and hero rotation at 7 seconds. Source includes cleanup; hero pauses for interaction/reduced motion. Shared theme/compare/drawer contexts can rerender consumers, but no expensive measured interaction justified React.memo everywhere. Measured DOM sizes (~500–1,400 elements in these performance routes) do not alone justify virtualization. A very large acquired collection was not stress-tested.

## Actionable change

[FE-001](OPTIMIZATION_FINDINGS.md#fe-001) removes proven unused homepage payload and backend tile work. [UX-004](OPTIMIZATION_FINDINGS.md#ux-004) adds bounded notification navigation. UI overlay fixes should retain existing shared providers and query ownership. See IMAGE_ASSET_AUDIT.md and API_NETWORK_AUDIT.md for transfer details.
