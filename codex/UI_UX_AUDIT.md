# UI/UX audit

Visual review covered the actual production UI, not only JSX: home, four search/listing entry points, product/options, compare, cart, checkout, wallet, collection, wishlist, rankings, authentication, seven account views, about/privacy/project/credits and 404. [ROUTE_MATRIX.md](ROUTE_MATRIX.md) enumerates the 27 route cases; full-page screenshots and contact sheets are retained.

The premium dark shopping identity is already coherent: prominent product photography, blue hero/brand accents, warm cart actions, consistent image wells and dense but legible retail hierarchy. Product/card styles are shared. A token-based theme and shared Button/Field/Dialog components exist; arbitrary-value classes alone did not establish a meaningful performance problem. No redesign or broad token rewrite is recommended.

## Actual friction

- [UX-001](OPTIMIZATION_FINDINGS.md#ux-001): notification preview clips off the left edge on phones.
- [UX-002](OPTIMIZATION_FINDINGS.md#ux-002): comparison tray obstructs the sticky product purchase action.
- [UX-003](OPTIMIZATION_FINDINGS.md#ux-003): tablet account summary truncates wallet mode and money.
- [UX-004](OPTIMIZATION_FINDINGS.md#ux-004): older retained notifications have no full-page navigation.
- [A11Y-003](OPTIMIZATION_FINDINGS.md#a11y-003): recent-search deletion is unavailable through the keyboard.

[A11Y-001](OPTIMIZATION_FINDINGS.md#a11y-001) and [A11Y-002](OPTIMIZATION_FINDINGS.md#a11y-002) cover link discoverability and leaderboard tab behavior. These are concrete interaction problems; do not treat personal preferences about whitespace, dark colors or shelf density as bugs.

## Working flows worth preserving

The existing shopping E2E suite completed search → add to cart → address checkout → confirmation → collection at desktop and phone sizes. Wallet funding, comparison and rankings opt-in/purchase flows passed. Product options and server quotes preserve authoritative totals. Signed-in account/security screens rendered with synthetic records; the saved-address empty state was inspected. Guest and empty-cart views were inspected separately. Search typing produced one request for rapidly entered “macbook,” keyboard result selection/submission worked, and Escape closed suggestions.

Filter and wallet dialogs close with Escape and restore their triggers after the animation settles. Collection/category rows intentionally use swipe/scroll affordances on phones. Tracking communicates timestamp-derived stages; no fake sub-second progress animation was needed. Existing image fallbacks and error components are useful; do not remove them to reduce DOM counts.

## Scope limits

All implemented route cases were visited, but this is not every catalog product, option combination, possible error, notification age or authentication-provider state. The malformed quote error was reproduced through HTTP, not a naturally generated browser link. Google external consent, real assistive technology, all light-theme screens, production cold starts and a complete failure-injection campaign were not performed. There is no admin UI to visually review; protected admin APIs were probed.
