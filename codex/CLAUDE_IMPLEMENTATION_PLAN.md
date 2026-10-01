# TrustKart Optimization Implementation Plan

## Current Verified State

Read AUDIT_SUMMARY.md, OPTIMIZATION_FINDINGS.md and TEST_RESULTS.md before editing. Baseline is `177fb10` on `trustkart-rebuild`; origin was fetched and this branch is current with its upstream. At audit time origin/main has an identical source tree with different merge history. Verify your current commit and diff first: another coding process may have changed the project since this audit.

The production build and 169 existing executed tests passed. Sixteen independent security assertions had fifteen passes and one failure (SEC-001). The audit inspected 27 route cases, 207 route/viewport observations, all 14 requested sizes, 54 axe scans and 30 measured cold loads. There are 18 prioritized findings, not a mandate to rewrite the application. Detailed files/lines and reproducible evidence are linked below.

## Rules

1. Verify each referenced finding still exists before changing it. Record obsolete/already-fixed findings instead of reintroducing an old fix.
2. Inspect status and concurrent work. Keep unrelated edits and untracked scripts/renders. Do not reset, stash, clean or switch over active work. Create an isolated worktree if useful with the user's normal workflow.
3. Preserve premium imagery, dark retail identity, desktop/tablet/mobile usability, fast search, accessibility and useful feedback. Do not redesign unrelated areas.
4. Preserve server pricing, wallet/order/ledger atomicity, stock checks, idempotency, ownership, CSRF, rate limits and session security. PostgreSQL stays authoritative.
5. Use local/dev services and synthetic data. Audit harnesses have temporary paths; read them before reuse. Never run synthetic fixture or key-deletion probes against production.
6. Establish the relevant pre-change failure/measurement, make one focused change, then run its targeted regression plus phase checks. Keep actual before/after numbers with identical fixtures; no invented percentages.
7. Treat migrations/cache semantics separately from cosmetic changes. Use additive, reversible transitions where possible. Commit/push/deploy only under the user's authorization for that implementation session.
8. The tasks below are future work. This audit has not implemented any of them.

## P0

No P0 was demonstrated. Do not manufacture emergency changes to fill this section. If new evidence reveals critical breakage, pause the optimization sequence and assess it.

## P1 — Address security before optimization

### SEC-001: Revoked access tokens become valid after Redis denylist key loss

**Verify first:** Read [SEC-001](OPTIMIZATION_FINDINGS.md#sec-001) and reproduce it on the current branch. On an isolated stack register a disposable account, retain its access cookie, log out, replay GET /api/v1/me/sessions, delete that session’s tk:revoked-sid key, then replay the same cookie. See tools/security-check.cjs. Never flush a shared Redis.

**Files likely involved:** [backend/src/main/java/com/vijaysinghpuwar/trustkart/security/SessionRevocation.java:40](../backend/src/main/java/com/vijaysinghpuwar/trustkart/security/SessionRevocation.java); [backend/src/main/java/com/vijaysinghpuwar/trustkart/security/JwtConfig.java:1](../backend/src/main/java/com/vijaysinghpuwar/trustkart/security/JwtConfig.java).

**Exact problem:** A negative cache lookup is being used as authoritative proof of non-revocation.

**Recommended implementation:** Verify the reproduction still fails; make the missing-key path use the existing indexed session check; retain Redis positive hits only as an optimization. Handle unknown/expired sessions consistently.

**What NOT to change / UI verification:** Avoid sign-in loops or unexplained account logout; preserve valid sessions and short access/rotating refresh semantics. Recheck the affected route/interaction at the sizes specified by the canonical finding; backend-only work must produce the same visible cards, amounts and actions.

**Implementation risk:** Medium: every authenticated request is affected; database failure must have explicit fail-closed behavior.

**Pre-change test:** Capture the reproduction result and relevant evidence before editing; retain the current passing tests as the baseline.

**Post-change test:** Add integration cases for key deletion, Redis write failure then recovery, Redis outage, unknown session, logout/device revoke/password change, and valid sessions. Repeat ownership/role/CSRF tests and measure authenticated query count.

**Performance verification:** Measure authenticated request SQL/latency after the fix; the security guarantee takes precedence over saving a single indexed read.

**Security verification:** Apply the row for SEC-001 in SECURITY_OPTIMIZATION_RISKS.md; re-run affected ownership/validation/cache/session tests with real middleware enabled.

**Rollback considerations:** Do not restore the known fail-open Redis-miss behavior as a performance rollback. Retain an authoritative fallback; disable only optional caching if needed.

## Phase 1: Safe Quick Wins

### API-001: JSON null in quote options produces HTTP 500

**Verify first:** Read [API-001](OPTIMIZATION_FINDINGS.md#api-001) and reproduce it on the current branch. With a local shopper call /api/v1/checkout/quote?productId=1&options=null with normal valid session context.

**Files likely involved:** [backend/src/main/java/com/vijaysinghpuwar/trustkart/purchase/PurchaseController.java:58](../backend/src/main/java/com/vijaysinghpuwar/trustkart/purchase/PurchaseController.java).

**Exact problem:** The parser validates map length/entries before rejecting a null result.

**Recommended implementation:** Verify reproduction; add explicit parser validation before dereferencing the map, preserving key/value limits.

**What NOT to change / UI verification:** Keep actionable validation errors rather than a generic failure screen. Recheck the affected route/interaction at the sizes specified by the canonical finding; backend-only work must produce the same visible cards, amounts and actions.

**Implementation risk:** Low; distinguish empty/omitted options from explicit null.

**Pre-change test:** Capture the reproduction result and relevant evidence before editing; retain the current passing tests as the baseline.

**Post-change test:** Test absent, empty, null, array, scalar, nested/wrong-type values, too many/long options and valid priced variants; assert 400 vs 200 and no writes.

**Performance verification:** Unnecessary stack trace and potential transient-error client retries. Compare the same fixture before and after; a correctness/UI fix needs no fabricated latency target.

**Security verification:** Apply the row for API-001 in SECURITY_OPTIMIZATION_RISKS.md; re-run affected ownership/validation/cache/session tests with real middleware enabled.

**Rollback considerations:** Keep the change isolated so its application diff can be reverted without reverting unrelated work. Preserve prior data and API contracts; do not delete migration history or production user state.

### A11Y-001: Two inline links rely on color alone

**Verify first:** Read [A11Y-001](OPTIMIZATION_FINDINGS.md#a11y-001) and reproduce it on the current branch. With the audit account open rankings or a receipt in dark theme; inspect the inline links visually and run axe.

**Files likely involved:** [frontend/src/features/rankings/RankingsPage.tsx:260](../frontend/src/features/rankings/RankingsPage.tsx); [frontend/src/features/account/ReceiptPage.tsx:139](../frontend/src/features/account/ReceiptPage.tsx).

**Exact problem:** Inline anchors inherit text styling without an underline or equivalent cue.

**Recommended implementation:** Verify the two violations still exist; reuse the project’s inline-link styling rather than a new one-off color.

**What NOT to change / UI verification:** Do not underline every button or alter the premium palette globally. Recheck the affected route/interaction at the sizes specified by the canonical finding; backend-only work must produce the same visible cards, amounts and actions.

**Implementation risk:** Low; verify contrast and hover/focus in both themes.

**Pre-change test:** Capture the reproduction result and relevant evidence before editing; retain the current passing tests as the baseline.

**Post-change test:** Run axe on both routes at phone/desktop widths and visually inspect normal/hover/focus in dark/light themes.

**Performance verification:** Negligible resource effect. Compare the same fixture before and after; a correctness/UI fix needs no fabricated latency target.

**Security verification:** Apply the row for A11Y-001 in SECURITY_OPTIMIZATION_RISKS.md; re-run affected ownership/validation/cache/session tests with real middleware enabled.

**Rollback considerations:** Keep the change isolated so its application diff can be reverted without reverting unrelated work. Preserve prior data and API contracts; do not delete migration history or production user state.

## Phase 2: Responsive/UI Fixes

### UX-001: Mobile notification panel opens partly outside the viewport

**Verify first:** Read [UX-001](OPTIMIZATION_FINDINGS.md#ux-001) and reproduce it on the current branch. At 320×568 or 390×844 open the header bell. Inspect the panel’s left edge.

**Files likely involved:** [frontend/src/components/layout/NotificationBell.tsx:129](../frontend/src/components/layout/NotificationBell.tsx).

**Exact problem:** absolute right-0 anchors a nearly viewport-wide panel to a bell that is left of the cart.

**Recommended implementation:** Verify geometry before changing it; use a shared positioned overlay or a mobile-specific placement with a 12px inset and bounded height.

**What NOT to change / UI verification:** Do not replace a quick preview with a confusing full-screen interruption or lose Escape/close behavior. Recheck the affected route/interaction at the sizes specified by the canonical finding; backend-only work must produce the same visible cards, amounts and actions.

**Implementation risk:** Low to medium: sticky header, scroll and focus behavior need checks.

**Pre-change test:** Capture the reproduction result and relevant evidence before editing; retain the current passing tests as the baseline.

**Post-change test:** Open empty/populated panels at 320,390,430,768,1440; assert rect.left>=0 and right<=innerWidth; verify Escape, outside click, keyboard link activation and focus return.

**Performance verification:** No meaningful CPU/transfer effect. Compare the same fixture before and after; a correctness/UI fix needs no fabricated latency target.

**Security verification:** Apply the row for UX-001 in SECURITY_OPTIMIZATION_RISKS.md; re-run affected ownership/validation/cache/session tests with real middleware enabled.

**Rollback considerations:** Keep the change isolated so its application diff can be reverted without reverting unrelated work. Preserve prior data and API contracts; do not delete migration history or production user state.

### UX-002: Compare tray covers the mobile product purchase bar

**Verify first:** Read [UX-002](OPTIMIZATION_FINDINGS.md#ux-002) and reproduce it on the current branch. Open a product on a phone viewport, add it to comparison, then scroll until the sticky purchase bar is shown.

**Files likely involved:** [frontend/src/components/commerce/CompareTray.tsx:26](../frontend/src/components/commerce/CompareTray.tsx); [frontend/src/features/product/ProductPage.tsx:248](../frontend/src/features/product/ProductPage.tsx).

**Exact problem:** Independent fixed overlays do not reserve space for one another.

**Recommended implementation:** Verify overlap; make the tray and bar share an explicit stacking/spacing contract, including the tray’s wrapped state.

**What NOT to change / UI verification:** Do not hide comparison or remove convenient product buying controls. Recheck the affected route/interaction at the sizes specified by the canonical finding; backend-only work must produce the same visible cards, amounts and actions.

**Implementation risk:** Medium: long names, safe-area insets and tray wrapping vary in height.

**Pre-change test:** Capture the reproduction result and relevant evidence before editing; retain the current passing tests as the baseline.

**Post-change test:** Test one to four compared products, long labels, clear/remove, 320–430 widths, bottom safe-area and scroll positions; click both actions and verify no accidental purchase/navigation.

**Performance verification:** No material resource change. Compare the same fixture before and after; a correctness/UI fix needs no fabricated latency target.

**Security verification:** Apply the row for UX-002 in SECURITY_OPTIMIZATION_RISKS.md; re-run affected ownership/validation/cache/session tests with real middleware enabled.

**Rollback considerations:** Keep the change isolated so its application diff can be reverted without reverting unrelated work. Preserve prior data and API contracts; do not delete migration history or production user state.

### UX-003: Tablet account cards truncate wallet mode and monetary value

**Verify first:** Read [UX-003](OPTIMIZATION_FINDINGS.md#ux-003) and reproduce it on the current branch. At 768×1024 use an account with Unlimited mode and a six-digit collection value; open Account.

**Files likely involved:** [frontend/src/features/account/AccountOverview.tsx:45](../frontend/src/features/account/AccountOverview.tsx); [frontend/src/features/account/AccountOverview.tsx:107](../frontend/src/features/account/AccountOverview.tsx); [frontend/src/features/account/AccountLayout.tsx:1](../frontend/src/features/account/AccountLayout.tsx).

**Exact problem:** Viewport sm:grid-cols-3 ignores available content width; truncate hides essential values.

**Recommended implementation:** Verify with the same large-value fixture, then adjust summary layout breakpoints/available-width rules.

**What NOT to change / UI verification:** Avoid shrinking typography until money becomes hard to read. Recheck the affected route/interaction at the sizes specified by the canonical finding; backend-only work must produce the same visible cards, amounts and actions.

**Implementation risk:** Low: longer localized labels and very large balances need attention.

**Pre-change test:** Capture the reproduction result and relevant evidence before editing; retain the current passing tests as the baseline.

**Post-change test:** Inspect 600,768,820,1024,1440; finite/unlimited wallets, zero and large totals, 200% zoom; compare displayed amounts with the API.

**Performance verification:** No measurable resource cost. Compare the same fixture before and after; a correctness/UI fix needs no fabricated latency target.

**Security verification:** Apply the row for UX-003 in SECURITY_OPTIMIZATION_RISKS.md; re-run affected ownership/validation/cache/session tests with real middleware enabled.

**Rollback considerations:** Keep the change isolated so its application diff can be reverted without reverting unrelated work. Preserve prior data and API contracts; do not delete migration history or production user state.

### UX-004: Full notifications page has no way to reach records beyond the first 30

**Verify first:** Read [UX-004](OPTIMIZATION_FINDINGS.md#ux-004) and reproduce it on the current branch. Populate more than 30 notifications in the isolated fixture, open the full notifications page, and try to access older retained entries.

**Files likely involved:** [frontend/src/data/notifications.ts:27](../frontend/src/data/notifications.ts); [frontend/src/features/account/NotificationsPage.tsx:52](../frontend/src/features/account/NotificationsPage.tsx).

**Exact problem:** The preview and full-page hook share a fixed first-page query without pagination state.

**Recommended implementation:** Verify >30 fixture; parameterize the full-page hook, retain prefix invalidation, render count and navigation with loading/error states.

**What NOT to change / UI verification:** Maintain scroll/focus and avoid jumping to page one after every read action. Recheck the affected route/interaction at the sizes specified by the canonical finding; backend-only work must produce the same visible cards, amounts and actions.

**Implementation risk:** Medium: marking records read changes counters while navigating pages.

**Pre-change test:** Capture the reproduction result and relevant evidence before editing; retain the current passing tests as the baseline.

**Post-change test:** Test 0,1,30,31,200 entries, last-page deletion/read-all, back navigation, keyboard and 320px layout; ensure requests remain bounded and shopper-scoped.

**Performance verification:** Keep bounded requests; do not fix by loading all history on every header render. Compare the same fixture before and after; a correctness/UI fix needs no fabricated latency target.

**Security verification:** Apply the row for UX-004 in SECURITY_OPTIMIZATION_RISKS.md; re-run affected ownership/validation/cache/session tests with real middleware enabled.

**Rollback considerations:** Keep the change isolated so its application diff can be reverted without reverting unrelated work. Preserve prior data and API contracts; do not delete migration history or production user state.

## Phase 3: Frontend Performance

### FE-001: Homepage builds and transfers collection tiles that are never rendered

**Verify first:** Read [FE-001](OPTIMIZATION_FINDINGS.md#fe-001) and reproduce it on the current branch. Load a fresh homepage, compare the 11 returned tiles with rendered collection sections, then repeat with recent browsing history.

**Files likely involved:** [frontend/src/features/home/HomePage.tsx:26](../frontend/src/features/home/HomePage.tsx); [backend/src/main/java/com/vijaysinghpuwar/trustkart/catalog/application/CatalogService.java:67](../backend/src/main/java/com/vijaysinghpuwar/trustkart/catalog/application/CatalogService.java); [backend/src/main/java/com/vijaysinghpuwar/trustkart/catalog/application/CatalogService.java:380](../backend/src/main/java/com/vijaysinghpuwar/trustkart/catalog/application/CatalogService.java).

**Exact problem:** Backend HOME_TILES grew beyond the UI’s fixed maximum.

**Recommended implementation:** Verify visible ordering and payload; limit work before querying/building unused tile cards, then keep client display logic consistent.

**What NOT to change / UI verification:** Preserve recent-history tile placement, premium richness and category discoverability. Recheck the affected route/interaction at the sizes specified by the canonical finding; backend-only work must produce the same visible cards, amounts and actions.

**Implementation risk:** Low to medium: future clients/curation may depend on the larger DTO.

**Pre-change test:** Capture the reproduction result and relevant evidence before editing; retain the current passing tests as the baseline.

**Post-change test:** Compare home screenshots with/without recent history at phone/tablet/desktop, payload bytes and cache-cold query counts; verify all collection links still work.

**Performance verification:** Compare home body bytes and cache-cold tile-query count; preserve screenshots with and without recent history. No need for global memoization or extra lazy boundaries.

**Security verification:** Apply the row for FE-001 in SECURITY_OPTIMIZATION_RISKS.md; re-run affected ownership/validation/cache/session tests with real middleware enabled.

**Rollback considerations:** Keep the change isolated so its application diff can be reverted without reverting unrelated work. Preserve prior data and API contracts; do not delete migration history or production user state.

## Phase 4: Images and Network

### QA-001: CI image validation covers only the 113-product demo catalog

**Verify first:** Read [QA-001](OPTIMIZATION_FINDINGS.md#qa-001) and reproduce it on the current branch. Run python3 scripts/validate_images.py; compare its input paths/count with catalog/products/*.json and catalog/images.json.

**Files likely involved:** [scripts/validate_images.py:28](../scripts/validate_images.py); [.github/workflows/ci.yml:72](../.github/workflows/ci.yml).

**Exact problem:** Validator inputs were not expanded with the catalog.

**Recommended implementation:** Verify catalog count and merge precedence, expand the validator, and add small malformed fixtures for both source groups.

**What NOT to change / UI verification:** Do not replace legitimate premium imagery with generic fallbacks merely to pass CI. Recheck the affected route/interaction at the sizes specified by the canonical finding; backend-only work must produce the same visible cards, amounts and actions.

**Implementation risk:** Low to medium: shared manufacturer imagery and documented non-exact matches must not become false failures.

**Pre-change test:** Capture the reproduction result and relevant evidence before editing; retain the current passing tests as the baseline.

**Post-change test:** Assert known total, missing primary/variant assets, empty/generic alt/provenance and dimensions; confirm current permitted warnings remain explicit.

**Performance verification:** A modest longer CI scan trades for reliable asset checks. Compare the same fixture before and after; a correctness/UI fix needs no fabricated latency target.

**Security verification:** Apply the row for QA-001 in SECURITY_OPTIMIZATION_RISKS.md; re-run affected ownership/validation/cache/session tests with real middleware enabled.

**Rollback considerations:** Keep the change isolated so its application diff can be reverted without reverting unrelated work. Preserve prior data and API contracts; do not delete migration history or production user state.

### IMG-001: Byte-identical product images are stored under many URLs

**Verify first:** Read [IMG-001](OPTIMIZATION_FINDINGS.md#img-001) and reproduce it on the current branch. Run tools/assets.py and group public assets by SHA-256; inspect model/variant attribution before choosing candidates.

**Files likely involved:** [frontend/public/images](../frontend/public/images); [backend/src/main/resources/catalog/images.json:1](../backend/src/main/resources/catalog/images.json); [backend/src/main/resources/demo/images.json:1](../backend/src/main/resources/demo/images.json).

**Exact problem:** Product-specific generation/download paths duplicate reused manufacturer imagery.

**Recommended implementation:** Verify hashes and owners; begin with a small safe group, map shared content while preserving source metadata, and coordinate old URL retention with CDN stale policies.

**What NOT to change / UI verification:** Never merge different color/model photos or strip credits; retain compatibility for old asset URLs until caches expire. Recheck the affected route/interaction at the sizes specified by the canonical finding; backend-only work must produce the same visible cards, amounts and actions.

**Implementation risk:** Medium migration risk despite low priority: deployed cached JSON may reference old paths.

**Pre-change test:** Capture the reproduction result and relevant evidence before editing; retain the current passing tests as the baseline.

**Post-change test:** Validate all manifests/variants, compare screenshots, check old and new URLs, measure output size and actual cross-product requests; roll back manifests if any broken image appears.

**Performance verification:** Measure build/public bytes and actual repeat-image requests after canonicalization; compare rendered images at DPR 1 and 3. Do not call the 20.3MiB storage upper bound per-page savings.

**Security verification:** Apply the row for IMG-001 in SECURITY_OPTIMIZATION_RISKS.md; re-run affected ownership/validation/cache/session tests with real middleware enabled.

**Rollback considerations:** Retain old files while deployed cached manifests can reference them; roll back manifest mappings independently. Do not delete provenance.

## Phase 5: API Optimization

### API-002: Out-of-range product pages report an incorrect zero total

**Verify first:** Read [API-002](OPTIMIZATION_FINDINGS.md#api-002) and reproduce it on the current branch. Read page 0 and page 9999 with the same size/filter; compare totalItems.

**Files likely involved:** [backend/src/main/java/com/vijaysinghpuwar/trustkart/catalog/infra/ProductSearchRepository.java:102](../backend/src/main/java/com/vijaysinghpuwar/trustkart/catalog/infra/ProductSearchRepository.java).

**Exact problem:** Window-count metadata disappears with an empty result set.

**Recommended implementation:** Verify issue and decide the page contract; implement count handling alongside page-first hydration where appropriate.

**What NOT to change / UI verification:** Do not reset user filters or claim no results solely because the page is beyond the end. Recheck the affected route/interaction at the sizes specified by the canonical finding; backend-only work must produce the same visible cards, amounts and actions.

**Implementation risk:** Medium: coordinate with DB-001 and search fallback; avoid changing ranking or SQL parameterization.

**Pre-change test:** Capture the reproduction result and relevant evidence before editing; retain the current passing tests as the baseline.

**Post-change test:** Test empty catalog, genuine zero matches, last valid page, one-past-last, huge page and filter changes; verify search does not relax solely due to an out-of-range offset.

**Performance verification:** Coordinate count correctness tests with DB-001 in Phase 7. Compare SQL and latency on ordinary and empty pages; accept a bounded count query if needed for correctness.

**Security verification:** Apply the row for API-002 in SECURITY_OPTIMIZATION_RISKS.md; re-run affected ownership/validation/cache/session tests with real middleware enabled.

**Rollback considerations:** Keep the change isolated so its application diff can be reverted without reverting unrelated work. Preserve prior data and API contracts; do not delete migration history or production user state.

## Phase 6: Backend/JPA

### BE-001: Catalog requests repeatedly reload and rebuild stable category metadata

**Verify first:** Read [BE-001](OPTIMIZATION_FINDINGS.md#be-001) and reproduce it on the current branch. Request the same suggestion/listing repeatedly with SQL logging; observe the category SELECT despite unchanged catalog metadata.

**Files likely involved:** [backend/src/main/java/com/vijaysinghpuwar/trustkart/catalog/application/CatalogService.java:105](../backend/src/main/java/com/vijaysinghpuwar/trustkart/catalog/application/CatalogService.java); [backend/src/main/java/com/vijaysinghpuwar/trustkart/catalog/application/CategoryTree.java:1](../backend/src/main/java/com/vijaysinghpuwar/trustkart/catalog/application/CategoryTree.java).

**Exact problem:** Public category DTO caching and internal CategoryTree construction are separate.

**Recommended implementation:** Verify repeated SQL; cache only immutable metadata, keep price/stock/permissions outside it, and invalidate alongside existing catalog caches.

**What NOT to change / UI verification:** Avoid stale category counts/names or invalid filter choices after catalog changes. Recheck the affected route/interaction at the sizes specified by the canonical finding; backend-only work must produce the same visible cards, amounts and actions.

**Implementation risk:** Medium: invalidation ordering during catalog seeding and multiple instances matters.

**Pre-change test:** Capture the reproduction result and relevant evidence before editing; retain the current passing tests as the baseline.

**Post-change test:** Measure repeated-query SQL counts; test catalog changes, unknown slugs, descendants and concurrent reads; avoid an added Redis round trip per request.

**Performance verification:** Removes repeated category query/allocation on hot catalog paths; expected benefit medium, not measured post-change. Compare the same fixture before and after; a correctness/UI fix needs no fabricated latency target.

**Security verification:** Apply the row for BE-001 in SECURITY_OPTIMIZATION_RISKS.md; re-run affected ownership/validation/cache/session tests with real middleware enabled.

**Rollback considerations:** Keep the change isolated so its application diff can be reverted without reverting unrelated work. Preserve prior data and API contracts; do not delete migration history or production user state.

### BE-002: Wishlist reads add two SQL queries per list

**Verify first:** Read [BE-002](OPTIMIZATION_FINDINGS.md#be-002) and reproduce it on the current branch. Create three local wishlists with one item each, reading /wishlist after each creation with request-correlated SQL logging.

**Files likely involved:** [backend/src/main/java/com/vijaysinghpuwar/trustkart/wishlist/WishlistService.java:45](../backend/src/main/java/com/vijaysinghpuwar/trustkart/wishlist/WishlistService.java); [backend/src/main/java/com/vijaysinghpuwar/trustkart/wishlist/WishlistService.java:140](../backend/src/main/java/com/vijaysinghpuwar/trustkart/wishlist/WishlistService.java).

**Exact problem:** Per-list stream mapping invokes repositories inside the mapping function.

**Recommended implementation:** Verify growth; bulk-load item associations for owned list IDs and call lookupAll once for distinct products.

**What NOT to change / UI verification:** Do not reorder saved products or collapse intentionally separate lists. Recheck the affected route/interaction at the sizes specified by the canonical finding; backend-only work must produce the same visible cards, amounts and actions.

**Implementation risk:** Medium: empty lists, duplicate products across lists and deleted products must map correctly.

**Pre-change test:** Capture the reproduction result and relevant evidence before editing; retain the current passing tests as the baseline.

**Post-change test:** Test 0/1/many lists, shared products, empty/deleted products and another shopper’s IDs; confirm query count stays bounded and returned content matches.

**Performance verification:** Two extra SQL round trips per additional nonempty list; finite list/item limits constrain but do not remove this waste. Compare the same fixture before and after; a correctness/UI fix needs no fabricated latency target.

**Security verification:** Apply the row for BE-002 in SECURITY_OPTIMIZATION_RISKS.md; re-run affected ownership/validation/cache/session tests with real middleware enabled.

**Rollback considerations:** Keep the change isolated so its application diff can be reverted without reverting unrelated work. Preserve prior data and API contracts; do not delete migration history or production user state.

### BE-003: Notification retention recreates and deletes old milestones on every poll

**Verify first:** Read [BE-003](OPTIMIZATION_FINDINGS.md#be-003) and reproduce it on the current branch. Use tools/bounded-data.cjs only against an isolated fixture database. Create 60 delivered synthetic orders within the 45-day lookback; poll unread count three times.

**Files likely involved:** [backend/src/main/java/com/vijaysinghpuwar/trustkart/notification/NotificationService.java:138](../backend/src/main/java/com/vijaysinghpuwar/trustkart/notification/NotificationService.java); [backend/src/main/java/com/vijaysinghpuwar/trustkart/notification/NotificationService.java:168](../backend/src/main/java/com/vijaysinghpuwar/trustkart/notification/NotificationService.java); [backend/src/main/java/com/vijaysinghpuwar/trustkart/notification/NotificationService.java:180](../backend/src/main/java/com/vijaysinghpuwar/trustkart/notification/NotificationService.java).

**Exact problem:** Presentation retention and durable milestone-generation knowledge share the same table rows.

**Recommended implementation:** Verify threshold reproduction; specify generation/retention semantics first, then persist minimal durable dedupe/high-water state without an unbounded scheduler.

**What NOT to change / UI verification:** Do not suppress new updates, re-notify read events, or remove user notification preferences. Recheck the affected route/interaction at the sizes specified by the canonical finding; backend-only work must produce the same visible cards, amounts and actions.

**Implementation risk:** Medium to high: dedupe state and historical backfill need a careful transactional/migration design.

**Pre-change test:** Capture the reproduction result and relevant evidence before editing; retain the current passing tests as the baseline.

**Post-change test:** Repeat 60-order test and require zero INSERTs on unchanged polls; test 199/200/201, late milestones, cancellation/refund, preference changes, read state, concurrent sync and 45-day expiry.

**Performance verification:** 40 avoidable writes plus retention deletion per unchanged poll in the reproduced fixture; row/WAL churn grows with due history. Compare the same fixture before and after; a correctness/UI fix needs no fabricated latency target.

**Security verification:** Apply the row for BE-003 in SECURITY_OPTIMIZATION_RISKS.md; re-run affected ownership/validation/cache/session tests with real middleware enabled.

**Rollback considerations:** Prefer additive durable metadata if the fix needs schema changes. Preserve prior notification history, preferences and read status. Roll back code as a coordinated unit; do not drop migration records.

## Phase 7: PostgreSQL

### DB-001: Product listing hydrates primary images before limiting the page

**Verify first:** Read [DB-001](OPTIMIZATION_FINDINGS.md#db-001) and reproduce it on the current branch. Run the read-only EXPLAIN (ANALYZE, BUFFERS) listing in evidence/explain.sql against the 2,201-product audit seed.

**Files likely involved:** [backend/src/main/java/com/vijaysinghpuwar/trustkart/catalog/infra/ProductSearchRepository.java:29](../backend/src/main/java/com/vijaysinghpuwar/trustkart/catalog/infra/ProductSearchRepository.java).

**Exact problem:** A LATERAL join is placed inside the full result relation.

**Recommended implementation:** Verify current plan; build a page relation/CTE or equivalent planner-stable query, coordinate API-002, and compare returned IDs/card fields.

**What NOT to change / UI verification:** No missing/wrong thumbnail or changed product order should result. Recheck the affected route/interaction at the sizes specified by the canonical finding; backend-only work must produce the same visible cards, amounts and actions.

**Implementation risk:** Medium: relevance, price/discount sort, nulls and filter equivalence must remain exact.

**Pre-change test:** Capture the reproduction result and relevant evidence before editing; retain the current passing tests as the baseline.

**Post-change test:** EXPLAIN default and filtered/text listings; assert image-loop count tracks page size, compare counts/sorts/ties, run CatalogApiIT and browse screenshots.

**Performance verification:** Run the stored EXPLAIN on the same seed before/after. Image probe loops should approach returned page size rather than 2,201. Compare all returned IDs, counts, sorts and timing; do not require an arbitrary percentage gain.

**Security verification:** Apply the row for DB-001 in SECURITY_OPTIMIZATION_RISKS.md; re-run affected ownership/validation/cache/session tests with real middleware enabled.

**Rollback considerations:** Restore the previous query if equivalence fails, retaining a separately verified API-002 count correction. No speculative index migration is required by this finding.

## Phase 8: Redis

Re-verify SEC-001's fix under missing-key and outage/recovery cases after all backend changes. This phase has no independent cache-everything task. Keep rate limiting and OAuth state TTLs; PostgreSQL remains the authority for commerce/session revocation. Relevant files: SessionRevocation.java, JwtConfig.java and existing Redis/rate-limit configuration.

Pre-change: capture current Redis key TTL/memory and authenticated SQL behavior in isolated services. Post-change: valid sessions work; revoked/unknown sessions fail even after lost keys. UI: no login loop. Performance: measure the cost of authoritative reads, rather than skipping them. Security: retain fail-closed authorization. Rollback: remove optional cache enhancements, never restore the known missing-key bypass.

## Phase 9: Accessibility

### A11Y-002: Leaderboard tabs lack expected arrow-key navigation

**Verify first:** Read [A11Y-002](OPTIMIZATION_FINDINGS.md#a11y-002) and reproduce it on the current branch. Focus This Month, press ArrowRight/ArrowLeft, then compare Tab+Space behavior.

**Files likely involved:** [frontend/src/features/rankings/RankingsPage.tsx:307](../frontend/src/features/rankings/RankingsPage.tsx).

**Exact problem:** Missing roving tabIndex and arrow-key handler.

**Recommended implementation:** Verify behavior; implement left/right wrap and Home/End if supported, selected tab focusability and correct aria-controls/labelledby.

**What NOT to change / UI verification:** Do not unexpectedly fetch repeatedly while holding an arrow key. Recheck the affected route/interaction at the sizes specified by the canonical finding; backend-only work must produce the same visible cards, amounts and actions.

**Implementation risk:** Low; avoid stealing page-level arrow keys outside the widget.

**Pre-change test:** Capture the reproduction result and relevant evidence before editing; retain the current passing tests as the baseline.

**Post-change test:** Test Tab, Shift+Tab, Left/Right, Enter/Space and visible focus; verify selected panel and bounded query behavior.

**Performance verification:** Negligible resource effect. Compare the same fixture before and after; a correctness/UI fix needs no fabricated latency target.

**Security verification:** Apply the row for A11Y-002 in SECURITY_OPTIMIZATION_RISKS.md; re-run affected ownership/validation/cache/session tests with real middleware enabled.

**Rollback considerations:** Keep the change isolated so its application diff can be reverted without reverting unrelated work. Preserve prior data and API contracts; do not delete migration history or production user state.

### A11Y-003: Recent-search removal is pointer-only

**Verify first:** Read [A11Y-003](OPTIMIZATION_FINDINGS.md#a11y-003) and reproduce it on the current branch. Submit a search, refocus the empty search input, navigate recent suggestions using only the keyboard, and attempt to remove one.

**Files likely involved:** [frontend/src/components/search/SmartSearch.tsx:302](../frontend/src/components/search/SmartSearch.tsx).

**Exact problem:** Preventing input blur for pointer selection also removes a usable keyboard action.

**Recommended implementation:** Verify pointer-only behavior; choose a coherent combobox/history interaction rather than simply adding nested tabbable buttons.

**What NOT to change / UI verification:** Preserve existing ArrowDown/Enter selection, Escape closing, fast suggestions and focus. Recheck the affected route/interaction at the sizes specified by the canonical finding; backend-only work must produce the same visible cards, amounts and actions.

**Implementation risk:** Medium: nested controls in listbox options can confuse assistive technology.

**Pre-change test:** Capture the reproduction result and relevant evidence before editing; retain the current passing tests as the baseline.

**Post-change test:** Test recent-item deletion by keyboard and pointer, announcement/focus after deletion, last item, and Enter still navigating to the selected result.

**Performance verification:** No material resource change. Compare the same fixture before and after; a correctness/UI fix needs no fabricated latency target.

**Security verification:** Apply the row for A11Y-003 in SECURITY_OPTIMIZATION_RISKS.md; re-run affected ownership/validation/cache/session tests with real middleware enabled.

**Rollback considerations:** Keep the change isolated so its application diff can be reverted without reverting unrelated work. Preserve prior data and API contracts; do not delete migration history or production user state.

Recheck A11Y-001 from Phase 1 and UX-001/002 focus/operability after keyboard changes. Use both automated scans and manual keyboard behavior; scanner scores alone are insufficient.

## Phase 10: Docker/Deployment

### QA-002: Existing end-to-end tests are not executed by CI

**Verify first:** Read [QA-002](OPTIMIZATION_FINDINGS.md#qa-002) and reproduce it on the current branch. Read all jobs in ci.yml and compare to package.json e2e command and frontend/e2e/*.spec.ts.

**Files likely involved:** [.github/workflows/ci.yml:1](../.github/workflows/ci.yml); [frontend/playwright.config.ts:1](../frontend/playwright.config.ts); [frontend/e2e/shopping.spec.ts:1](../frontend/e2e/shopping.spec.ts).

**Exact problem:** Browser tests exist but are not wired into the workflow.

**Recommended implementation:** Verify no new CI job already covers this; start local PG/Redis/API, wait for seed/readiness, serve built frontend, run E2E and upload artifacts.

**What NOT to change / UI verification:** No product UI change is needed. Recheck the affected route/interaction at the sizes specified by the canonical finding; backend-only work must produce the same visible cards, amounts and actions.

**Implementation risk:** Medium: service readiness, seeding and test parallelism need reliable orchestration.

**Pre-change test:** Capture the reproduction result and relevant evidence before editing; retain the current passing tests as the baseline.

**Post-change test:** Run the proposed job in CI, prove a deliberately failing local test fails the job during development, remove that temporary failure, and confirm teardown; preserve secret/CSRF protections.

**Performance verification:** Record CI duration and avoid redundant stacks/workers. Existing Docker multistage runtime is sound; image size alone does not justify a base-image replacement.

**Security verification:** Apply the row for QA-002 in SECURITY_OPTIMIZATION_RISKS.md; re-run affected ownership/validation/cache/session tests with real middleware enabled.

**Rollback considerations:** Keep workflow additions in a separate diff; if orchestration flakes, repair readiness/isolation without disabling the existing build/security jobs or silently ignoring E2E failures.

No other Docker change is required by current evidence. Retain non-root JRE, layered builds, .dockerignore and current security headers. Do not copy the audit DEBUG flags or local insecure-cookie setting into production. Before any later deployment verify real environment/limits separately; this plan does not authorize a deployment.

## Phase 11: Final Regression Testing

Follow VERIFICATION_CHECKLIST.md. Run backend verify, frontend lint/typecheck/format/tests/build, expanded image validation and existing E2E on an isolated stack. Re-run the independent reproductions for all changed IDs, route/viewport screenshots, keyboard/axe, console/network and targeted SQL plans.

Required end-to-end behaviors: guest and registered search/product/cart; priced variants; wallet funding/mode; checkout with server quote and repeated idempotency key; receipt/tracking/refund; collection/wishlist; notifications after 30 and 200 records; leaderboard privacy/ranks; account/session revocation; normal-user/admin ownership boundaries. Preserve the premium visual identity.

Compare results with this audit's measured baseline using the same viewport, DPR, fixture, cache state and throttling. Record unmapped/untested states honestly. Review the final Git diff for unrelated source/config/catalog/secret changes and document each resolved ID with evidence. Do not claim all findings fixed merely because lint/build pass.
