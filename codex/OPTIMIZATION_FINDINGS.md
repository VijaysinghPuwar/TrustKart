# TrustKart verified findings

Audit baseline: `177fb10` on `trustkart-rebuild`, inspected September 30–October 1, 2026. These are observations of that snapshot, not assumptions about a future checkout. Verify each finding still exists before changing it. Full evidence is local under `evidence/`; synthetic accounts and orders were used only on isolated local services.

**18 findings: P0 0 · P1 1 · P2 16 · P3 1.** HIGH means directly reproduced/measured, including direct inspection of CI/validator inputs. It does not mean a proposed fix is risk-free. No production measurements or field performance claims are made.

| ID | Priority | Area | Finding | User impact | Resource impact | Confidence |
|---|---|---|---|---|---|---|
| [SEC-001](OPTIMIZATION_FINDINGS.md#sec-001) | P1 | Security / Redis | Revoked access tokens become valid after Redis denylist key loss | An already-issued revoked access token can regain protected access until token expiry (configured access lifetime 10 minutes). This requires possession of that token and key loss; it is not unauthenticated account takeover. | Correctness fix may add indexed session reads; measure them rather than preserving this unsafe shortcut. | HIGH |
| [UX-001](OPTIMIZATION_FINDINGS.md#ux-001) | P2 | Responsive UI | Mobile notification panel opens partly outside the viewport | Notification text and navigation are cut off. | No meaningful CPU/transfer effect. | HIGH |
| [UX-002](OPTIMIZATION_FINDINGS.md#ux-002) | P2 | Responsive UI | Compare tray covers the mobile product purchase bar | The sticky purchase action is obstructed; an in-page action still exists above. | No material resource change. | HIGH |
| [UX-003](OPTIMIZATION_FINDINGS.md#ux-003) | P2 | Responsive UI | Tablet account cards truncate wallet mode and monetary value | Critical commerce information is ambiguous. | No measurable resource cost. | HIGH |
| [UX-004](OPTIMIZATION_FINDINGS.md#ux-004) | P2 | Navigation / pagination | Full notifications page has no way to reach records beyond the first 30 | Older order/delivery updates become inaccessible from the notification page. | Keep bounded requests; do not fix by loading all history on every header render. | HIGH |
| [A11Y-001](OPTIMIZATION_FINDINGS.md#a11y-001) | P2 | Accessibility | Two inline links rely on color alone | Reduced link discoverability for people with color-vision differences. | Negligible resource effect. | HIGH |
| [A11Y-002](OPTIMIZATION_FINDINGS.md#a11y-002) | P2 | Accessibility | Leaderboard tabs lack expected arrow-key navigation | Keyboard and screen-reader users encounter an unexpected tab interaction; the content is still reachable with Tab+Space. | Negligible resource effect. | HIGH |
| [A11Y-003](OPTIMIZATION_FINDINGS.md#a11y-003) | P2 | Accessibility | Recent-search removal is pointer-only | Keyboard users cannot selectively remove stored search history. | No material resource change. | HIGH |
| [API-001](OPTIMIZATION_FINDINGS.md#api-001) | P2 | API validation | JSON null in quote options produces HTTP 500 | Malformed links/clients receive an incorrect server error; no successful purchase or pricing bypass was demonstrated. | Unnecessary stack trace and potential transient-error client retries. | HIGH |
| [API-002](OPTIMIZATION_FINDINGS.md#api-002) | P2 | API correctness | Out-of-range product pages report an incorrect zero total | Clients can incorrectly conclude that no products match after pagination/filter changes. | A correctness-preserving count strategy may add a bounded query on empty pages. | HIGH |
| [FE-001](OPTIMIZATION_FINDINGS.md#fe-001) | P2 | Frontend / payload / backend | Homepage builds and transfers collection tiles that are never rendered | Same shopping content can arrive with less unused data. | At least 20 unused cards/15,331 JSON bytes per home payload; five avoidable product tile queries per home-cache rebuild. No percentage latency promise. | HIGH |
| [DB-001](OPTIMIZATION_FINDINGS.md#db-001) | P2 | PostgreSQL query shape | Product listing hydrates primary images before limiting the page | Potential faster browsing as catalog grows; current local latency is small. | Measured 2,201 probes for 24 returned products; expected benefit is reduced database work, not a claimed speedup percentage. | HIGH |
| [BE-001](OPTIMIZATION_FINDINGS.md#be-001) | P2 | Backend / query reuse | Catalog requests repeatedly reload and rebuild stable category metadata | Suggestion behavior can stay just as responsive and correct. | Removes repeated category query/allocation on hot catalog paths; expected benefit medium, not measured post-change. | HIGH |
| [BE-002](OPTIMIZATION_FINDINGS.md#be-002) | P2 | Backend / N+1 | Wishlist reads add two SQL queries per list | Lower latency when managing several lists without changing visible content. | Two extra SQL round trips per additional nonempty list; finite list/item limits constrain but do not remove this waste. | HIGH |
| [BE-003](OPTIMIZATION_FINDINGS.md#be-003) | P2 | Backend / persistence | Notification retention recreates and deletes old milestones on every poll | The visible list stays capped, masking repeated database work; a badge poll becomes more expensive for frequent shoppers. | 40 avoidable writes plus retention deletion per unchanged poll in the reproduced fixture; row/WAL churn grows with due history. | HIGH |
| [QA-001](OPTIMIZATION_FINDINGS.md#qa-001) | P2 | Quality coverage | CI image validation covers only the 113-product demo catalog | Future catalog regressions can evade CI; this is a coverage gap, not a claim of 2,088 broken products. | A modest longer CI scan trades for reliable asset checks. | HIGH |
| [QA-002](OPTIMIZATION_FINDINGS.md#qa-002) | P2 | Quality coverage | Existing end-to-end tests are not executed by CI | Reduces risk of breaking working commerce flows during the planned optimizations. | Adds CI compute; use one shared test stack per job, readiness checks and appropriate worker limits. | HIGH |
| [IMG-001](OPTIMIZATION_FINDINGS.md#img-001) | P3 | Asset storage | Byte-identical product images are stored under many URLs | No expected visual change; cross-product cache reuse may improve when identical URLs are shared. | About 20.3MiB redundant static storage is an upper bound, not per-page download savings; current build contains 142.6MiB of public assets. | HIGH |

## SEC-001

### Revoked access tokens become valid after Redis denylist key loss

**Priority:** P1

**Category:** Security / Redis

**Affected route/component/service:** Authenticated API after logout

**Exact files and baseline lines:** [backend/src/main/java/com/vijaysinghpuwar/trustkart/security/SessionRevocation.java:40](../backend/src/main/java/com/vijaysinghpuwar/trustkart/security/SessionRevocation.java); [backend/src/main/java/com/vijaysinghpuwar/trustkart/security/JwtConfig.java:1](../backend/src/main/java/com/vijaysinghpuwar/trustkart/security/JwtConfig.java)

**Evidence:** [security-checks.json](evidence/security-checks.json): logout 204; replay 401; delete only the synthetic audit session’s Redis revocation key; replay 200. PostgreSQL revocation remains recorded.

**How to reproduce:** On an isolated stack register a disposable account, retain its access cookie, log out, replay GET /api/v1/me/sessions, delete that session’s tk:revoked-sid key, then replay the same cookie. See tools/security-check.cjs. Never flush a shared Redis.

**Current behavior:** Redis false/missing key is treated as a live session. PostgreSQL is consulted only when Redis throws.

**Expected behavior:** Logout remains effective after eviction, data loss, or a failed denylist write followed by Redis recovery.

**User impact:** An already-issued revoked access token can regain protected access until token expiry (configured access lifetime 10 minutes). This requires possession of that token and key loss; it is not unauthenticated account takeover.

**Resource impact:** Correctness fix may add indexed session reads; measure them rather than preserving this unsafe shortcut.

**Likely root cause:** A negative cache lookup is being used as authoritative proof of non-revocation.

**Recommended change:** Consult authoritative session state on a denylist miss, or design another scheme that cannot interpret lost cache state as authorization.

**Why this is safe:** PostgreSQL already stores session revocation and is already used by the exception fallback.

**Risk of changing it:** Medium: every authenticated request is affected; database failure must have explicit fail-closed behavior.

**Potential UX regression:** Avoid sign-in loops or unexplained account logout; preserve valid sessions and short access/rotating refresh semantics.

**How Claude should implement it:** Verify the reproduction still fails; make the missing-key path use the existing indexed session check; retain Redis positive hits only as an optimization. Handle unknown/expired sessions consistently.

**How Claude should test it:** Add integration cases for key deletion, Redis write failure then recovery, Redis outage, unknown session, logout/device revoke/password change, and valid sessions. Repeat ownership/role/CSRF tests and measure authenticated query count.

**Confidence:** HIGH


## UX-001

### Mobile notification panel opens partly outside the viewport

**Priority:** P2

**Category:** Responsive UI

**Affected route/component/service:** Header notification bell

**Exact files and baseline lines:** [frontend/src/components/layout/NotificationBell.tsx:129](../frontend/src/components/layout/NotificationBell.tsx)

**Evidence:** [interactions.json](evidence/interactions.json) reports x=-34 at both 320px and 390px; [320px screenshot](screenshots/notification-open-320.png) shows clipped title/body/link.

**How to reproduce:** At 320×568 or 390×844 open the header bell. Inspect the panel’s left edge.

**Current behavior:** The panel width is viewport-bounded but its right edge is positioned relative to the bell, leaving its left side off-screen.

**Expected behavior:** The entire panel and its controls stay inside the viewport.

**User impact:** Notification text and navigation are cut off.

**Resource impact:** No meaningful CPU/transfer effect.

**Likely root cause:** absolute right-0 anchors a nearly viewport-wide panel to a bell that is left of the cart.

**Recommended change:** Use viewport-constrained positioning at phone widths; preserve the desktop anchored panel.

**Why this is safe:** A layout-only fix can retain fetching, notification state and styling.

**Risk of changing it:** Low to medium: sticky header, scroll and focus behavior need checks.

**Potential UX regression:** Do not replace a quick preview with a confusing full-screen interruption or lose Escape/close behavior.

**How Claude should implement it:** Verify geometry before changing it; use a shared positioned overlay or a mobile-specific placement with a 12px inset and bounded height.

**How Claude should test it:** Open empty/populated panels at 320,390,430,768,1440; assert rect.left>=0 and right<=innerWidth; verify Escape, outside click, keyboard link activation and focus return.

**Confidence:** HIGH


## UX-002

### Compare tray covers the mobile product purchase bar

**Priority:** P2

**Category:** Responsive UI

**Affected route/component/service:** /p/:slug with a selected comparison item

**Exact files and baseline lines:** [frontend/src/components/commerce/CompareTray.tsx:26](../frontend/src/components/commerce/CompareTray.tsx); [frontend/src/features/product/ProductPage.tsx:248](../frontend/src/features/product/ProductPage.tsx)

**Evidence:** [ui-confirm.json](evidence/ui-confirm.json): at 390×844 the tray overlaps the sticky bar by 45px and intercepts its button center; [screenshot](screenshots/compare-tray-overlap-390.png).

**How to reproduce:** Open a product on a phone viewport, add it to comparison, then scroll until the sticky purchase bar is shown.

**Current behavior:** Compare tray z-30 bottom-5 sits over the product bar z-20 bottom-0.

**Expected behavior:** Both comparison and primary purchase actions remain visible and operable.

**User impact:** The sticky purchase action is obstructed; an in-page action still exists above.

**Resource impact:** No material resource change.

**Likely root cause:** Independent fixed overlays do not reserve space for one another.

**Recommended change:** Coordinate overlay placement using the actual occupied height or a shared bottom-action layout.

**Why this is safe:** Presentation can change independently of cart/checkout authority.

**Risk of changing it:** Medium: long names, safe-area insets and tray wrapping vary in height.

**Potential UX regression:** Do not hide comparison or remove convenient product buying controls.

**How Claude should implement it:** Verify overlap; make the tray and bar share an explicit stacking/spacing contract, including the tray’s wrapped state.

**How Claude should test it:** Test one to four compared products, long labels, clear/remove, 320–430 widths, bottom safe-area and scroll positions; click both actions and verify no accidental purchase/navigation.

**Confidence:** HIGH


## UX-003

### Tablet account cards truncate wallet mode and monetary value

**Priority:** P2

**Category:** Responsive UI

**Affected route/component/service:** /account at 768px

**Exact files and baseline lines:** [frontend/src/features/account/AccountOverview.tsx:45](../frontend/src/features/account/AccountOverview.tsx); [frontend/src/features/account/AccountOverview.tsx:107](../frontend/src/features/account/AccountOverview.tsx); [frontend/src/features/account/AccountLayout.tsx:1](../frontend/src/features/account/AccountLayout.tsx)

**Evidence:** [account-768.png](screenshots/account-768.png): “∞ Unli…” and “$450,0…” in summary cards beside the account sidebar.

**How to reproduce:** At 768×1024 use an account with Unlimited mode and a six-digit collection value; open Account.

**Current behavior:** Three summary columns occupy the narrow space remaining beside a 220px sidebar; value spans truncate.

**Expected behavior:** Wallet mode and full amount can be read without guessing.

**User impact:** Critical commerce information is ambiguous.

**Resource impact:** No measurable resource cost.

**Likely root cause:** Viewport sm:grid-cols-3 ignores available content width; truncate hides essential values.

**Recommended change:** Use fewer columns at this content width, or a container-aware grid; let essential numbers remain readable.

**Why this is safe:** Retains the same information and design tokens without changing calculations.

**Risk of changing it:** Low: longer localized labels and very large balances need attention.

**Potential UX regression:** Avoid shrinking typography until money becomes hard to read.

**How Claude should implement it:** Verify with the same large-value fixture, then adjust summary layout breakpoints/available-width rules.

**How Claude should test it:** Inspect 600,768,820,1024,1440; finite/unlimited wallets, zero and large totals, 200% zoom; compare displayed amounts with the API.

**Confidence:** HIGH


## UX-004

### Full notifications page has no way to reach records beyond the first 30

**Priority:** P2

**Category:** Navigation / pagination

**Affected route/component/service:** /account/notifications

**Exact files and baseline lines:** [frontend/src/data/notifications.ts:27](../frontend/src/data/notifications.ts); [frontend/src/features/account/NotificationsPage.tsx:52](../frontend/src/features/account/NotificationsPage.tsx)

**Evidence:** [bounded-data.json](evidence/bounded-data.json): endpoint returns 30 items with totalItems=200. Hook fixes size=30 and page renders only that response with no page/load-more control.

**How to reproduce:** Populate more than 30 notifications in the isolated fixture, open the full notifications page, and try to access older retained entries.

**Current behavior:** Only the first page can be viewed although older retained records exist.

**Expected behavior:** All retained notifications are reachable through bounded pagination or incremental loading.

**User impact:** Older order/delivery updates become inaccessible from the notification page.

**Resource impact:** Keep bounded requests; do not fix by loading all history on every header render.

**Likely root cause:** The preview and full-page hook share a fixed first-page query without pagination state.

**Recommended change:** Add page-aware query keys and an accessible paging control to the full page; keep the bell preview small.

**Why this is safe:** Backend already returns page/size/totalItems.

**Risk of changing it:** Medium: marking records read changes counters while navigating pages.

**Potential UX regression:** Maintain scroll/focus and avoid jumping to page one after every read action.

**How Claude should implement it:** Verify >30 fixture; parameterize the full-page hook, retain prefix invalidation, render count and navigation with loading/error states.

**How Claude should test it:** Test 0,1,30,31,200 entries, last-page deletion/read-all, back navigation, keyboard and 320px layout; ensure requests remain bounded and shopper-scoped.

**Confidence:** HIGH


## A11Y-001

### Two inline links rely on color alone

**Priority:** P2

**Category:** Accessibility

**Affected route/component/service:** /rankings and /account/purchases/:id

**Exact files and baseline lines:** [frontend/src/features/rankings/RankingsPage.tsx:260](../frontend/src/features/rankings/RankingsPage.tsx); [frontend/src/features/account/ReceiptPage.tsx:139](../frontend/src/features/account/ReceiptPage.tsx)

**Evidence:** [accessibility.json](evidence/accessibility.json): link-in-text-block violations at 1440 and 390 for “Change” and “store policy”; axe reports surrounding-text contrast 1.23:1.

**How to reproduce:** With the audit account open rankings or a receipt in dark theme; inspect the inline links visually and run axe.

**Current behavior:** These inline links lack a persistent non-color distinction from surrounding text.

**Expected behavior:** Readers can identify links without relying solely on hue.

**User impact:** Reduced link discoverability for people with color-vision differences.

**Resource impact:** Negligible resource effect.

**Likely root cause:** Inline anchors inherit text styling without an underline or equivalent cue.

**Recommended change:** Add a consistent persistent underline/non-color treatment to these links.

**Why this is safe:** Uses existing palette and preserves destination and copy.

**Risk of changing it:** Low; verify contrast and hover/focus in both themes.

**Potential UX regression:** Do not underline every button or alter the premium palette globally.

**How Claude should implement it:** Verify the two violations still exist; reuse the project’s inline-link styling rather than a new one-off color.

**How Claude should test it:** Run axe on both routes at phone/desktop widths and visually inspect normal/hover/focus in dark/light themes.

**Confidence:** HIGH


## A11Y-002

### Leaderboard tabs lack expected arrow-key navigation

**Priority:** P2

**Category:** Accessibility

**Affected route/component/service:** /rankings period selector

**Exact files and baseline lines:** [frontend/src/features/rankings/RankingsPage.tsx:307](../frontend/src/features/rankings/RankingsPage.tsx)

**Evidence:** [interactions.json](evidence/interactions.json): ArrowRight leaves focus/selection on This Month; Tab then Space activates All Time. Both buttons use role=tab.

**How to reproduce:** Focus This Month, press ArrowRight/ArrowLeft, then compare Tab+Space behavior.

**Current behavior:** The widget exposes tab semantics but behaves as separate ordinary buttons.

**Expected behavior:** Tab enters the active tab; arrows navigate tabs, with a consistent manual/automatic activation model.

**User impact:** Keyboard and screen-reader users encounter an unexpected tab interaction; the content is still reachable with Tab+Space.

**Resource impact:** Negligible resource effect.

**Likely root cause:** Missing roving tabIndex and arrow-key handler.

**Recommended change:** Complete the ARIA tabs keyboard pattern using existing tab/panel IDs.

**Why this is safe:** No data or ranking algorithm change is required.

**Risk of changing it:** Low; avoid stealing page-level arrow keys outside the widget.

**Potential UX regression:** Do not unexpectedly fetch repeatedly while holding an arrow key.

**How Claude should implement it:** Verify behavior; implement left/right wrap and Home/End if supported, selected tab focusability and correct aria-controls/labelledby.

**How Claude should test it:** Test Tab, Shift+Tab, Left/Right, Enter/Space and visible focus; verify selected panel and bounded query behavior.

**Confidence:** HIGH


## A11Y-003

### Recent-search removal is pointer-only

**Priority:** P2

**Category:** Accessibility

**Affected route/component/service:** Smart Search recent suggestions

**Exact files and baseline lines:** [frontend/src/components/search/SmartSearch.tsx:302](../frontend/src/components/search/SmartSearch.tsx)

**Evidence:** [ui-confirm.json](evidence/ui-confirm.json) records the labeled Remove macbook button with tabIndex=-1; source binds removal only to onMouseDown and the input keyboard handler provides no equivalent removal command.

**How to reproduce:** Submit a search, refocus the empty search input, navigate recent suggestions using only the keyboard, and attempt to remove one.

**Current behavior:** A mouse user can remove a recent item, but the button cannot be tabbed to or activated from the combobox keyboard flow.

**Expected behavior:** Recent history can be managed using keyboard controls with understandable feedback.

**User impact:** Keyboard users cannot selectively remove stored search history.

**Resource impact:** No material resource change.

**Likely root cause:** Preventing input blur for pointer selection also removes a usable keyboard action.

**Recommended change:** Provide an explicit accessible history-management action or a documented Delete command for the active recent option.

**Why this is safe:** Only browser-local recent-search state changes.

**Risk of changing it:** Medium: nested controls in listbox options can confuse assistive technology.

**Potential UX regression:** Preserve existing ArrowDown/Enter selection, Escape closing, fast suggestions and focus.

**How Claude should implement it:** Verify pointer-only behavior; choose a coherent combobox/history interaction rather than simply adding nested tabbable buttons.

**How Claude should test it:** Test recent-item deletion by keyboard and pointer, announcement/focus after deletion, last item, and Enter still navigating to the selected result.

**Confidence:** HIGH


## API-001

### JSON null in quote options produces HTTP 500

**Priority:** P2

**Category:** API validation

**Affected route/component/service:** GET /api/v1/checkout/quote

**Exact files and baseline lines:** [backend/src/main/java/com/vijaysinghpuwar/trustkart/purchase/PurchaseController.java:58](../backend/src/main/java/com/vijaysinghpuwar/trustkart/purchase/PurchaseController.java)

**Evidence:** [api-measurements.json](evidence/api-measurements.json) records the malformed-options probe; runtime stack trace points to parsed.size() after decoding null.

**How to reproduce:** With a local shopper call /api/v1/checkout/quote?productId=1&options=null with normal valid session context.

**Current behavior:** Valid JSON null deserializes to a null Map and triggers an uncaught NullPointerException.

**Expected behavior:** Invalid options return a structured 400 response without server-error logging.

**User impact:** Malformed links/clients receive an incorrect server error; no successful purchase or pricing bypass was demonstrated.

**Resource impact:** Unnecessary stack trace and potential transient-error client retries.

**Likely root cause:** The parser validates map length/entries before rejecting a null result.

**Recommended change:** Reject null and malformed/non-object option values through the normal request-validation exception path.

**Why this is safe:** Does not alter accepted option maps or server pricing.

**Risk of changing it:** Low; distinguish empty/omitted options from explicit null.

**Potential UX regression:** Keep actionable validation errors rather than a generic failure screen.

**How Claude should implement it:** Verify reproduction; add explicit parser validation before dereferencing the map, preserving key/value limits.

**How Claude should test it:** Test absent, empty, null, array, scalar, nested/wrong-type values, too many/long options and valid priced variants; assert 400 vs 200 and no writes.

**Confidence:** HIGH


## API-002

### Out-of-range product pages report an incorrect zero total

**Priority:** P2

**Category:** API correctness

**Affected route/component/service:** GET /api/v1/catalog/products?page=9999

**Exact files and baseline lines:** [backend/src/main/java/com/vijaysinghpuwar/trustkart/catalog/infra/ProductSearchRepository.java:102](../backend/src/main/java/com/vijaysinghpuwar/trustkart/catalog/infra/ProductSearchRepository.java)

**Evidence:** [api-measurements.json](evidence/api-measurements.json): page 0 reports 2201 products; out-of-range page reports zero total with an empty items array.

**How to reproduce:** Read page 0 and page 9999 with the same size/filter; compare totalItems.

**Current behavior:** Total is initialized to zero and populated only by a returned row from count(*) OVER().

**Expected behavior:** Empty pages retain the true filtered total, or the API explicitly normalizes/rejects invalid page positions.

**User impact:** Clients can incorrectly conclude that no products match after pagination/filter changes.

**Resource impact:** A correctness-preserving count strategy may add a bounded query on empty pages.

**Likely root cause:** Window-count metadata disappears with an empty result set.

**Recommended change:** Return count independently when a page is empty, or combine a counted relation and page rows without dropping metadata.

**Why this is safe:** Preserves filtering and the documented page contract.

**Risk of changing it:** Medium: coordinate with DB-001 and search fallback; avoid changing ranking or SQL parameterization.

**Potential UX regression:** Do not reset user filters or claim no results solely because the page is beyond the end.

**How Claude should implement it:** Verify issue and decide the page contract; implement count handling alongside page-first hydration where appropriate.

**How Claude should test it:** Test empty catalog, genuine zero matches, last valid page, one-past-last, huge page and filter changes; verify search does not relax solely due to an out-of-range offset.

**Confidence:** HIGH


## FE-001

### Homepage builds and transfers collection tiles that are never rendered

**Priority:** P2

**Category:** Frontend / payload / backend

**Affected route/component/service:** /

**Exact files and baseline lines:** [frontend/src/features/home/HomePage.tsx:26](../frontend/src/features/home/HomePage.tsx); [backend/src/main/java/com/vijaysinghpuwar/trustkart/catalog/application/CatalogService.java:67](../backend/src/main/java/com/vijaysinghpuwar/trustkart/catalog/application/CatalogService.java); [backend/src/main/java/com/vijaysinghpuwar/trustkart/catalog/application/CatalogService.java:380](../backend/src/main/java/com/vijaysinghpuwar/trustkart/catalog/application/CatalogService.java)

**Evidence:** [payload-_catalog_home.json](evidence/payload-_catalog_home.json) has 11 tiles; UI displays 6, or 5 with recent history. The last 5 contain 20 cards and serialize to 15,331 compact JSON bytes; the tile builder queries each separately.

**How to reproduce:** Load a fresh homepage, compare the 11 returned tiles with rendered collection sections, then repeat with recent browsing history.

**Current behavior:** At least five editorial tile payloads and their cache-rebuild queries do not contribute to the current homepage.

**Expected behavior:** Home-specific data reflects visible collections while all catalog/collection routes remain accessible.

**User impact:** Same shopping content can arrive with less unused data.

**Resource impact:** At least 20 unused cards/15,331 JSON bytes per home payload; five avoidable product tile queries per home-cache rebuild. No percentage latency promise.

**Likely root cause:** Backend HOME_TILES grew beyond the UI’s fixed maximum.

**Recommended change:** Agree a six-tile home contract, or explicitly request only visible home tiles; leave collection browsing intact.

**Why this is safe:** The excluded home tiles are not rendered; product imagery and existing visible shelves need not change.

**Risk of changing it:** Low to medium: future clients/curation may depend on the larger DTO.

**Potential UX regression:** Preserve recent-history tile placement, premium richness and category discoverability.

**How Claude should implement it:** Verify visible ordering and payload; limit work before querying/building unused tile cards, then keep client display logic consistent.

**How Claude should test it:** Compare home screenshots with/without recent history at phone/tablet/desktop, payload bytes and cache-cold query counts; verify all collection links still work.

**Confidence:** HIGH


## DB-001

### Product listing hydrates primary images before limiting the page

**Priority:** P2

**Category:** PostgreSQL query shape

**Affected route/component/service:** Catalog listing, shared search repository

**Exact files and baseline lines:** [backend/src/main/java/com/vijaysinghpuwar/trustkart/catalog/infra/ProductSearchRepository.java:29](../backend/src/main/java/com/vijaysinghpuwar/trustkart/catalog/infra/ProductSearchRepository.java)

**Evidence:** [explain.txt](evidence/explain.txt): 24-row default listing performs 2,201 primary-image LATERAL probes, 6,603 image buffer hits, 7,815 total buffer hits; execution 7.505ms on the local warm database.

**How to reproduce:** Run the read-only EXPLAIN (ANALYZE, BUFFERS) listing in evidence/explain.sql against the 2,201-product audit seed.

**Current behavior:** Image lookup runs for the full matching set before count/window/sort/limit returns 24 cards.

**Expected behavior:** Filter/count/order/page product identifiers first, then hydrate primary images for returned cards.

**User impact:** Potential faster browsing as catalog grows; current local latency is small.

**Resource impact:** Measured 2,201 probes for 24 returned products; expected benefit is reduced database work, not a claimed speedup percentage.

**Likely root cause:** A LATERAL join is placed inside the full result relation.

**Recommended change:** Refactor query shape so image joins occur after pagination, maintaining count metadata and deterministic ordering.

**Why this is safe:** An appropriate image(product_id,sort_order) index already exists; this targets unnecessary work instead of speculative indexes.

**Risk of changing it:** Medium: relevance, price/discount sort, nulls and filter equivalence must remain exact.

**Potential UX regression:** No missing/wrong thumbnail or changed product order should result.

**How Claude should implement it:** Verify current plan; build a page relation/CTE or equivalent planner-stable query, coordinate API-002, and compare returned IDs/card fields.

**How Claude should test it:** EXPLAIN default and filtered/text listings; assert image-loop count tracks page size, compare counts/sorts/ties, run CatalogApiIT and browse screenshots.

**Confidence:** HIGH


## BE-001

### Catalog requests repeatedly reload and rebuild stable category metadata

**Priority:** P2

**Category:** Backend / query reuse

**Affected route/component/service:** Search, suggestions, listing, facets

**Exact files and baseline lines:** [backend/src/main/java/com/vijaysinghpuwar/trustkart/catalog/application/CatalogService.java:105](../backend/src/main/java/com/vijaysinghpuwar/trustkart/catalog/application/CatalogService.java); [backend/src/main/java/com/vijaysinghpuwar/trustkart/catalog/application/CategoryTree.java:1](../backend/src/main/java/com/vijaysinghpuwar/trustkart/catalog/application/CategoryTree.java)

**Evidence:** [api-query-counts.json](evidence/api-query-counts.json) and request-correlated SQL logs: repeated listing/search/suggestion requests each include category.findAll plus product SQL. tree() reconstructs from all 112 categories every invocation; existing DTO cache does not cache this tree.

**How to reproduce:** Request the same suggestion/listing repeatedly with SQL logging; observe the category SELECT despite unchanged catalog metadata.

**Current behavior:** Immutable-in-practice seed metadata is loaded/mapped repeatedly.

**Expected behavior:** Reuse an immutable category snapshot with bounded freshness and catalog-change invalidation.

**User impact:** Suggestion behavior can stay just as responsive and correct.

**Resource impact:** Removes repeated category query/allocation on hot catalog paths; expected benefit medium, not measured post-change.

**Likely root cause:** Public category DTO caching and internal CategoryTree construction are separate.

**Recommended change:** Introduce local snapshot reuse tied to CatalogChanged and an appropriate finite TTL.

**Why this is safe:** No per-user data belongs in the tree; there is already an invalidation event and Memo pattern.

**Risk of changing it:** Medium: invalidation ordering during catalog seeding and multiple instances matters.

**Potential UX regression:** Avoid stale category counts/names or invalid filter choices after catalog changes.

**How Claude should implement it:** Verify repeated SQL; cache only immutable metadata, keep price/stock/permissions outside it, and invalidate alongside existing catalog caches.

**How Claude should test it:** Measure repeated-query SQL counts; test catalog changes, unknown slugs, descendants and concurrent reads; avoid an added Redis round trip per request.

**Confidence:** HIGH


## BE-002

### Wishlist reads add two SQL queries per list

**Priority:** P2

**Category:** Backend / N+1

**Affected route/component/service:** GET /api/v1/wishlist

**Exact files and baseline lines:** [backend/src/main/java/com/vijaysinghpuwar/trustkart/wishlist/WishlistService.java:45](../backend/src/main/java/com/vijaysinghpuwar/trustkart/wishlist/WishlistService.java); [backend/src/main/java/com/vijaysinghpuwar/trustkart/wishlist/WishlistService.java:140](../backend/src/main/java/com/vijaysinghpuwar/trustkart/wishlist/WishlistService.java)

**Evidence:** [bounded-query-counts.json](evidence/bounded-query-counts.json): 1,2,3 lists with one product each require 4,6,8 SQL statements respectively.

**How to reproduce:** Create three local wishlists with one item each, reading /wishlist after each creation with request-correlated SQL logging.

**Current behavior:** Each list separately loads item IDs then separately calls catalog.lookupAll.

**Expected behavior:** Load owned list/item relationships in bulk, hydrate distinct products once, then group into ordered lists.

**User impact:** Lower latency when managing several lists without changing visible content.

**Resource impact:** Two extra SQL round trips per additional nonempty list; finite list/item limits constrain but do not remove this waste.

**Likely root cause:** Per-list stream mapping invokes repositories inside the mapping function.

**Recommended change:** Batch data loading across owned lists while preserving list/item order and response structure.

**Why this is safe:** Uses the existing bulk catalog lookup and existing shopper scope.

**Risk of changing it:** Medium: empty lists, duplicate products across lists and deleted products must map correctly.

**Potential UX regression:** Do not reorder saved products or collapse intentionally separate lists.

**How Claude should implement it:** Verify growth; bulk-load item associations for owned list IDs and call lookupAll once for distinct products.

**How Claude should test it:** Test 0/1/many lists, shared products, empty/deleted products and another shopper’s IDs; confirm query count stays bounded and returned content matches.

**Confidence:** HIGH


## BE-003

### Notification retention recreates and deletes old milestones on every poll

**Priority:** P2

**Category:** Backend / persistence

**Affected route/component/service:** Notification unread-count and list synchronization

**Exact files and baseline lines:** [backend/src/main/java/com/vijaysinghpuwar/trustkart/notification/NotificationService.java:138](../backend/src/main/java/com/vijaysinghpuwar/trustkart/notification/NotificationService.java); [backend/src/main/java/com/vijaysinghpuwar/trustkart/notification/NotificationService.java:168](../backend/src/main/java/com/vijaysinghpuwar/trustkart/notification/NotificationService.java); [backend/src/main/java/com/vijaysinghpuwar/trustkart/notification/NotificationService.java:180](../backend/src/main/java/com/vijaysinghpuwar/trustkart/notification/NotificationService.java)

**Evidence:** [bounded-query-counts.json](evidence/bounded-query-counts.json): 60 synthetic orders aged 8 days create 240 due milestones; first poll stores 200; second and third each execute 40 INSERTs plus one retention DELETE (47 SQL statements total).

**How to reproduce:** Use tools/bounded-data.cjs only against an isolated fixture database. Create 60 delivered synthetic orders within the 45-day lookback; poll unread count three times.

**Current behavior:** Retention deletes the very rows used as the only dedupe record, so the next sync regards deleted milestones as new.

**Expected behavior:** Unchanged old history generates no repeated insert/delete work after retention.

**User impact:** The visible list stays capped, masking repeated database work; a badge poll becomes more expensive for frequent shoppers.

**Resource impact:** 40 avoidable writes plus retention deletion per unchanged poll in the reproduced fixture; row/WAL churn grows with due history.

**Likely root cause:** Presentation retention and durable milestone-generation knowledge share the same table rows.

**Recommended change:** Separate durable generation state from retained display history, or implement a proven retention watermark that handles late/cancel/refund milestones.

**Why this is safe:** Timestamp-derived tracking and the 200-entry display cap can remain unchanged.

**Risk of changing it:** Medium to high: dedupe state and historical backfill need a careful transactional/migration design.

**Potential UX regression:** Do not suppress new updates, re-notify read events, or remove user notification preferences.

**How Claude should implement it:** Verify threshold reproduction; specify generation/retention semantics first, then persist minimal durable dedupe/high-water state without an unbounded scheduler.

**How Claude should test it:** Repeat 60-order test and require zero INSERTs on unchanged polls; test 199/200/201, late milestones, cancellation/refund, preference changes, read state, concurrent sync and 45-day expiry.

**Confidence:** HIGH


## QA-001

### CI image validation covers only the 113-product demo catalog

**Priority:** P2

**Category:** Quality coverage

**Affected route/component/service:** scripts/validate_images.py / CI images job

**Exact files and baseline lines:** [scripts/validate_images.py:28](../scripts/validate_images.py); [.github/workflows/ci.yml:72](../.github/workflows/ci.yml)

**Evidence:** [image-validation.log](evidence/image-validation.log): 113 products checked. Expanded audit inventory contains 2,201 products; 2,088 expanded-catalog products are outside the validator inputs. Independent primary-image checks found no current missing files.

**How to reproduce:** Run python3 scripts/validate_images.py; compare its input paths/count with catalog/products/*.json and catalog/images.json.

**Current behavior:** The CI job description suggests every product, but validation only reads demo/products.json and demo/images.json.

**Expected behavior:** The guard covers the same catalog inputs as the seeder, including expanded and variant image references.

**User impact:** Future catalog regressions can evade CI; this is a coverage gap, not a claim of 2,088 broken products.

**Resource impact:** A modest longer CI scan trades for reliable asset checks.

**Likely root cause:** Validator inputs were not expanded with the catalog.

**Recommended change:** Merge inputs according to seeder semantics and validate all referenced image metadata/files.

**Why this is safe:** Audit-only independent scan already demonstrates the inputs are readable and primary assets currently exist.

**Risk of changing it:** Low to medium: shared manufacturer imagery and documented non-exact matches must not become false failures.

**Potential UX regression:** Do not replace legitimate premium imagery with generic fallbacks merely to pass CI.

**How Claude should implement it:** Verify catalog count and merge precedence, expand the validator, and add small malformed fixtures for both source groups.

**How Claude should test it:** Assert known total, missing primary/variant assets, empty/generic alt/provenance and dimensions; confirm current permitted warnings remain explicit.

**Confidence:** HIGH


## QA-002

### Existing end-to-end tests are not executed by CI

**Priority:** P2

**Category:** Quality coverage

**Affected route/component/service:** .github/workflows/ci.yml

**Exact files and baseline lines:** [.github/workflows/ci.yml:1](../.github/workflows/ci.yml); [frontend/playwright.config.ts:1](../frontend/playwright.config.ts); [frontend/e2e/shopping.spec.ts:1](../frontend/e2e/shopping.spec.ts)

**Evidence:** CI definition runs Vitest, build, lint and backend tests but no Playwright. [playwright-existing.log](evidence/playwright-existing.log) proves 22 existing E2E cases pass locally; 8 project-specific skips are intentional.

**How to reproduce:** Read all jobs in ci.yml and compare to package.json e2e command and frontend/e2e/*.spec.ts.

**Current behavior:** Important browser shopping/responsive/rankings regressions are only caught when someone runs the suite manually.

**Expected behavior:** A bounded CI job runs existing E2E flows on an isolated local stack and publishes failure artifacts.

**User impact:** Reduces risk of breaking working commerce flows during the planned optimizations.

**Resource impact:** Adds CI compute; use one shared test stack per job, readiness checks and appropriate worker limits.

**Likely root cause:** Browser tests exist but are not wired into the workflow.

**Recommended change:** Add an isolated production-build E2E CI job; do not point it at production.

**Why this is safe:** Reuses passing tests and deterministic local infrastructure.

**Risk of changing it:** Medium: service readiness, seeding and test parallelism need reliable orchestration.

**Potential UX regression:** No product UI change is needed.

**How Claude should implement it:** Verify no new CI job already covers this; start local PG/Redis/API, wait for seed/readiness, serve built frontend, run E2E and upload artifacts.

**How Claude should test it:** Run the proposed job in CI, prove a deliberately failing local test fails the job during development, remove that temporary failure, and confirm teardown; preserve secret/CSRF protections.

**Confidence:** HIGH


## IMG-001

### Byte-identical product images are stored under many URLs

**Priority:** P3

**Category:** Asset storage

**Affected route/component/service:** frontend/public/images and catalog image manifests

**Exact files and baseline lines:** [frontend/public/images](../frontend/public/images); [backend/src/main/resources/catalog/images.json:1](../backend/src/main/resources/catalog/images.json); [backend/src/main/resources/demo/images.json:1](../backend/src/main/resources/demo/images.json)

**Evidence:** [asset-duplicates.json](evidence/asset-duplicates.json): 1,094 exact-hash groups with 21,273,614 redundant bytes; seven identical AMD EPYC 800px files alone duplicate 282,372 bytes beyond one copy.

**How to reproduce:** Run tools/assets.py and group public assets by SHA-256; inspect model/variant attribution before choosing candidates.

**Current behavior:** Identical byte content can be deployed and requested through different product-specific paths.

**Expected behavior:** Where provenance/model matching permits, identical assets share a canonical source without reducing quality.

**User impact:** No expected visual change; cross-product cache reuse may improve when identical URLs are shared.

**Resource impact:** About 20.3MiB redundant static storage is an upper bound, not per-page download savings; current build contains 142.6MiB of public assets.

**Likely root cause:** Product-specific generation/download paths duplicate reused manufacturer imagery.

**Recommended change:** Canonicalize only proven identical assets and update manifests/generation consistently, keeping attribution per product.

**Why this is safe:** Byte-identical image content means no recompression or blur is needed.

**Risk of changing it:** Medium migration risk despite low priority: deployed cached JSON may reference old paths.

**Potential UX regression:** Never merge different color/model photos or strip credits; retain compatibility for old asset URLs until caches expire.

**How Claude should implement it:** Verify hashes and owners; begin with a small safe group, map shared content while preserving source metadata, and coordinate old URL retention with CDN stale policies.

**How Claude should test it:** Validate all manifests/variants, compare screenshots, check old and new URLs, measure output size and actual cross-product requests; roll back manifests if any broken image appears.

**Confidence:** HIGH

## Quick Wins

Start with A11Y-001 (two inline link styles), API-001 (null validation) and UX-003 (summary layout). FE-001 is a useful payload reduction after verifying all consumers. These have narrow scope and clear before/after checks. UX-001 is also localized but must be tested with the sticky header and keyboard focus. Address SEC-001 first because priority overrides implementation convenience.

## High-Value Structural Improvements

| IDs | Expected benefit | Risk/dependency | Required evidence | Rollback |
|---|---|---|---|---|
| DB-001, API-002 | Medium: fewer image probes and correct page metadata | Medium; shared sorting/filter/count contract | Equivalent result sets plus EXPLAIN on full and selective queries | Restore former query while keeping a separately tested empty-page count fix |
| BE-002 | Medium: bounded wishlist round trips | Medium; preserve owned associations/order | 1/2/3/max-list SQL counts and ownership tests | Restore old read mapping; no schema change required |
| BE-003 | Medium: eliminate repeated notification writes | Medium/high; durable generation history design | Zero inserts after stable retention; late/refund/concurrent cases | Keep additive metadata schema, roll back reader/writer behavior together; do not destroy user history |
| BE-001 | Medium: reuse category metadata | Medium; catalog-change invalidation | Query counts before/after plus invalidation tests | Disable snapshot reuse and return to live metadata reads |
| IMG-001 | Low user / medium storage benefit | Manifest and cached old URL compatibility | Exact hashes, full catalog validation and screenshots | Restore prior manifests and retain old files |

## Things Claude Should Leave Alone

Keep server-side repricing, wallet/order/ledger atomicity, stock checks, idempotency and ownership checks. Preserve CSRF, cookie/session hardening, Argon2 and rate limiting. Preserve PostgreSQL authority for commerce and rankings. Do not move these values exclusively into Redis.

Keep the working 150ms search debounce, cancellation and minimum query length; do not add semantic/AI network calls as an “optimization.” Retain timestamp-derived seven-day tracking with no per-order timers, the two-minute notification cadence and mutation-driven wallet/cart updates. Fix BE-003 rather than hiding it with a much slower polling interval.

Keep existing route-level splitting, 400/800px WebP srcset, intrinsic image dimensions, below-fold lazy loading and quality-preserving object-contain wells. The tested pages do not justify blanket React.memo, virtualization, a package replacement or a visual redesign. No unused production dependency was established. Preserve the premium dark retail identity, responsive shelves, product imagery and useful microinteractions.
