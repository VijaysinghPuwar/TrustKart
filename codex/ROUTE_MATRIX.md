# Route inspection matrix

27 route cases were loaded against real local APIs. Signed-in fixture coverage includes a purchase, wishlist item and fictional checkout address; the saved-address page was inspected in its empty state; guest shopping is separately covered by E2E tests. Search/deals/category/curated share SearchPage but each route was visited. Receipt includes tracking. No admin UI, separate tracking route, review page or standalone 500 page exists in the router. ErrorBoundary source was reviewed; an exhaustive injected-failure UI campaign was not performed.

Every route was captured at 390,768,1024,1440,1920 widths. Desktop/tablet/mobile contact sheets were visually reviewed; additional captures/DOM checks cover all 14 requested sizes for core routes. “Loads” refers to rendered route content, not merely the SPA HTTP 200 response. Console capture was limited by one harness restart; see notes below.

| Route | Loads | UI | Responsive | Console errors | Major finding |
|---|---|---|---|---|---|
| `/` | PASS | PASS, inspected base state | See responsive matrix | No captured page error; coverage limited | Shared mobile bell: UX-001 |
| `/search` | PASS | ISSUE | See responsive matrix | No captured page error; coverage limited | A11Y-003 in recent suggestions |
| `/deals` | PASS | PASS, inspected base state | See responsive matrix | No captured page error; coverage limited | Shared mobile bell: UX-001 |
| `/c/:category` | PASS | PASS, inspected base state | See responsive matrix | No captured page error; coverage limited | Shared mobile bell: UX-001 |
| `/collections/:collection` | PASS | PASS, inspected base state | See responsive matrix | No captured page error; coverage limited | Shared mobile bell: UX-001 |
| `/p/:slug` | PASS | ISSUE | See responsive matrix | No captured page error; coverage limited | UX-002 when comparison tray is open |
| `/compare` | PASS | PASS, inspected base state | See responsive matrix | No captured page error; coverage limited | Shared mobile bell: UX-001 |
| `/cart` | PASS | PASS, inspected base state | See responsive matrix | No captured page error; coverage limited | Shared mobile bell: UX-001 |
| `/checkout` | PASS | PASS, inspected base state | See responsive matrix | No captured page error; coverage limited | Shared mobile bell: UX-001 |
| `/wallet` | PASS | PASS, inspected base state | See responsive matrix | No captured page error; coverage limited | Shared mobile bell: UX-001 |
| `/collection` | PASS | PASS, inspected base state | See responsive matrix | No captured page error; coverage limited | Shared mobile bell: UX-001 |
| `/wishlist` | PASS | PASS, inspected base state | See responsive matrix | No captured page error; coverage limited | Shared mobile bell: UX-001 |
| `/rankings` | PASS | ISSUE | See responsive matrix | No captured page error; coverage limited | A11Y-001, A11Y-002 |
| `/signin` | PASS | PASS, inspected base state | See responsive matrix | No captured page error; coverage limited | Shared mobile bell: UX-001 |
| `/signup` | PASS | PASS, inspected base state | See responsive matrix | No captured page error; coverage limited | Shared mobile bell: UX-001 |
| `/account` | PASS | ISSUE | See responsive matrix | No captured page error; coverage limited | UX-003 at 768px |
| `/account/purchases` | PASS | PASS, inspected base state | See responsive matrix | No captured page error; coverage limited | Shared mobile bell: UX-001 |
| `/account/purchases/:id` | PASS | ISSUE | See responsive matrix | No captured page error; coverage limited | A11Y-001 |
| `/account/notifications` | PASS | ISSUE | See responsive matrix | No captured page error; coverage limited | UX-004 after 30 records |
| `/account/rankings` | PASS | PASS, inspected base state | See responsive matrix | No captured page error; coverage limited | Shared mobile bell: UX-001 |
| `/account/security` | PASS | PASS, inspected base state | See responsive matrix | No captured page error; coverage limited | Shared mobile bell: UX-001 |
| `/account/addresses` | PASS | PASS, inspected base state | See responsive matrix | No captured page error; coverage limited | Shared mobile bell: UX-001 |
| `/about` | PASS | PASS, inspected base state | See responsive matrix | No captured page error; coverage limited | Shared mobile bell: UX-001 |
| `/about/project` | PASS | PASS, inspected base state | See responsive matrix | No captured page error; coverage limited | Shared mobile bell: UX-001 |
| `/about/privacy` | PASS | PASS, inspected base state | See responsive matrix | No captured page error; coverage limited | Shared mobile bell: UX-001 |
| `/about/credits` | PASS | PASS, inspected base state | See responsive matrix | No captured page error; coverage limited | Shared mobile bell: UX-001 |
| `* (404)` | PASS | PASS, inspected base state | See responsive matrix | No captured page error; coverage limited | Shared mobile bell: UX-001 |

`evidence/route-matrix.json` records actual URLs, headings, viewport, DOM size, overflow and broken-image checks for all 207 cases. `browser-api-responses.json` captures responses in the resumed run. Four bounded `networkidle` waits timed out; subsequent captures rendered route headings/content, and no failed API status was recorded in that resumed capture. These are instrumentation timing limitations, not proven route failures. The first harness stopped on an unbounded networkidle wait and was resumed; do not interpret missing early console records as proof of a clean console in every state. The separate 30 cold performance runs recorded no page errors and no duplicate API requests.

All inspected base pages had headings, no root horizontal overflow and no detected failed product image after loading. Overlay clipping and fixed-layer collisions are separate checks and did fail; a passing root overflow assertion alone is insufficient.
