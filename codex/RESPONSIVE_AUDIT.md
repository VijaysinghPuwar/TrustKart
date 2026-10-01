# Responsive audit

207 route/viewport observations, all 14 requested sizes represented. All 27 route cases were inspected at 390×844, 768×1024, 1024×768, 1440×900 and 1920×1080. Eight core flows (home, search, product, compare, cart, checkout, receipt/tracking, rankings) additionally cover the other nine requested sizes. Test harness: Chromium production build, dark theme, synthetic data; these are viewport simulations, not physical-device testing.

| Route | 390 | 768 | 1024 | 1440 | 1920 |
|---|---|---|---|---|---|
| `/` | ISSUE UX-001 | PASS | PASS | PASS | PASS |
| `/search` | ISSUE UX-001 | PASS | PASS | PASS | PASS |
| `/deals` | ISSUE UX-001 | PASS | PASS | PASS | PASS |
| `/c/:category` | ISSUE UX-001 | PASS | PASS | PASS | PASS |
| `/collections/:collection` | ISSUE UX-001 | PASS | PASS | PASS | PASS |
| `/p/:slug` | ISSUE UX-001 / UX-002 | PASS | PASS | PASS | PASS |
| `/compare` | ISSUE UX-001 | PASS | PASS | PASS | PASS |
| `/cart` | ISSUE UX-001 | PASS | PASS | PASS | PASS |
| `/checkout` | ISSUE UX-001 | PASS | PASS | PASS | PASS |
| `/wallet` | ISSUE UX-001 | PASS | PASS | PASS | PASS |
| `/collection` | ISSUE UX-001 | PASS | PASS | PASS | PASS |
| `/wishlist` | ISSUE UX-001 | PASS | PASS | PASS | PASS |
| `/rankings` | ISSUE UX-001 | PASS | PASS | PASS | PASS |
| `/signin` | ISSUE UX-001 | PASS | PASS | PASS | PASS |
| `/signup` | ISSUE UX-001 | PASS | PASS | PASS | PASS |
| `/account` | ISSUE UX-001 | ISSUE UX-003 | PASS | PASS | PASS |
| `/account/purchases` | ISSUE UX-001 | PASS | PASS | PASS | PASS |
| `/account/purchases/:id` | ISSUE UX-001 | PASS | PASS | PASS | PASS |
| `/account/notifications` | ISSUE UX-001 | PASS | PASS | PASS | PASS |
| `/account/rankings` | ISSUE UX-001 | PASS | PASS | PASS | PASS |
| `/account/security` | ISSUE UX-001 | PASS | PASS | PASS | PASS |
| `/account/addresses` | ISSUE UX-001 | PASS | PASS | PASS | PASS |
| `/about` | ISSUE UX-001 | PASS | PASS | PASS | PASS |
| `/about/project` | ISSUE UX-001 | PASS | PASS | PASS | PASS |
| `/about/privacy` | ISSUE UX-001 | PASS | PASS | PASS | PASS |
| `/about/credits` | ISSUE UX-001 | PASS | PASS | PASS | PASS |
| `* (404)` | ISSUE UX-001 | PASS | PASS | PASS | PASS |

PASS denotes the inspected base layout: heading/content present, no root overflow, visually usable stacking. It does not certify all values, overlays, keyboard behavior or every intermediate width. UX-001 was reproduced in the shared header at 320/390 and applies to routes using that header; it was not reopened independently on every route. UX-002 is conditional on an open compare tray. Notifications pagination and link/tab accessibility are recorded separately rather than being mislabeled as viewport defects.

## Exact size coverage

| Viewport | Coverage |
|---|---|
| 320×568 | Eight core flows, screenshots including cart/checkout/tracking; notification overlay interaction |
| 360×800 | Eight core flows, DOM/layout measurements |
| 375×812 | Eight core flows, DOM/layout measurements |
| 390×844 | All 27 routes, screenshots, axe and selected keyboard/overlay flows |
| 430×932 | Eight core flows, DOM/layout measurements |
| 600×960 | Eight core flows, DOM/layout measurements |
| 768×1024 | All 27 routes, screenshots and tablet visual review |
| 820×1180 | Eight core flows, DOM/layout measurements |
| 1024×768 | All 27 routes, screenshots/layout checks |
| 1024×1366 | Eight core flows, DOM/layout measurements |
| 1280×800 | Eight core flows, DOM/layout measurements |
| 1440×900 | All 27 routes, screenshots, axe and desktop visual review |
| 1728×1117 | Eight core flows, DOM/layout measurements |
| 1920×1080 | All 27 routes, screenshots/layout checks |

Non-core routes at the additional nine widths: **NOT TESTED**. No missing-size result is inferred from a neighboring breakpoint.

## Verified issues and strengths

[UX-001](OPTIMIZATION_FINDINGS.md#ux-001) clips the mobile notification panel by 34px; [UX-002](OPTIMIZATION_FINDINGS.md#ux-002) covers 45px of the mobile product action bar; [UX-003](OPTIMIZATION_FINDINGS.md#ux-003) hides important tablet summary values. The screenshots and measured rectangles make each reproducible.

Mobile shelves intentionally scroll horizontally within their own regions; this is not root-page overflow. Mobile filters settle within the viewport (bottom=844 at height 844), and Escape returns focus to Filters. An early animation-frame measurement appeared outside the viewport; the settled confirmation disproved that as a persistent defect. Wallet dialog Escape also works after settling.

Desktop maximum content widths and whitespace remain coherent with the retail design. Tablet product/search grids remain useful; the account summary is the concrete exception. No evidence supports replacing the design system, hiding half the mobile catalog, or shrinking all typography. Physical soft-keyboard behavior, safe-area hardware, browser zoom across all routes and Safari/Firefox were not comprehensively tested.

Evidence: [route data](evidence/route-matrix.json), [interaction data](evidence/interactions.json), [settled confirmation](evidence/ui-confirm.json), and `screenshots/contact-{390,768,1440}-*.jpg`.
