# Codex audit: independent verification

The Codex audit in [`codex/`](../codex/README.md) inspected commit `177fb10` (September 30 to October 1, 2026). This
document records what happened to each of its findings when they were re-checked against the same commit on
October 1, 2026, before any change was made. The audit itself is kept unchanged as historical evidence.

Every finding was reproduced on an isolated local stack (its own PostgreSQL and Redis containers, throwaway
credentials, synthetic accounts) or confirmed by reading the current source, before anything was edited. Four
additional defects found during stress, accessibility and failure testing are listed at the end.

## Summary

| Result | Count | Findings |
|---|---:|---|
| Confirmed and fixed | 16 | SEC-001, UX-001, UX-002, UX-004, A11Y-001, A11Y-002, A11Y-003, API-001, API-002, FE-001, DB-001, BE-001, BE-002, BE-003, QA-001, QA-002 |
| Partially confirmed and fixed | 1 | UX-003 |
| Confirmed, deferred with reason | 1 | IMG-001 (P3) |
| Rejected, not reproducible or already fixed | 0 | |
| Found independently and fixed | 4 | NEW-001, NEW-002, NEW-003, NEW-004 |

Codex reported no P0. Its single P1 (SEC-001) is fixed.

## Findings

| Finding | Codex priority | Verified? | Status | Action |
|---|---|---|---|---|
| SEC-001 revoked token valid after Redis key loss | P1 | Reproduced: 401 after logout, 200 after deleting only that session's `tk:revoked-sid` key | VERIFIED_AFTER_FIX | Session table is now the only authority |
| UX-001 phone notification panel off-screen | P2 | Reproduced: panel left edge at -34px at 320px | VERIFIED_AFTER_FIX | Viewport-pinned panel on phones |
| UX-002 compare tray covers phone purchase bar | P2 | Reproduced: tray bottom 824px vs bar top 779px at 320px | VERIFIED_AFTER_FIX | Shared bottom-bar height contract |
| UX-003 tablet account cards truncate | P2 | Partially: six-figure collection value clipped at 768px; "∞ Unlimited" fit in Chromium | VERIFIED_AFTER_FIX | Width-aware grid, values wrap |
| UX-004 notifications beyond 30 unreachable | P2 | Confirmed in source and UI: fixed `size=30`, no paging | VERIFIED_AFTER_FIX | Paged history page |
| A11Y-001 inline links rely on colour | P2 | Reproduced: computed `text-decoration-line: none` on "Change" | VERIFIED_AFTER_FIX | Existing inline-link rule extended |
| A11Y-002 leaderboard tabs ignore arrows | P2 | Reproduced: no roving tabindex, ArrowRight did nothing | VERIFIED_AFTER_FIX | WAI-ARIA tabs keyboard pattern |
| A11Y-003 recent search removal pointer-only | P2 | Reproduced: remove button `tabIndex=-1`, `onMouseDown` only | VERIFIED_AFTER_FIX | Delete key on the active option |
| API-001 `options=null` quote returns 500 | P2 | Reproduced: 500 (other malformed values already 400) | VERIFIED_AFTER_FIX | Explicit null check, 400 |
| API-002 out-of-range page reports total 0 | P2 | Reproduced: page 9999 reported 0 of 2,201 | VERIFIED_AFTER_FIX | Count query when a page is empty |
| FE-001 home builds 11 tiles, shows 6 | P2 | Reproduced: 11 tiles, UI slices to 6 or 5 | VERIFIED_AFTER_FIX | Backend stops at 6 |
| DB-001 image join before LIMIT | P2 | Reproduced: 2,201 image index loops for 24 cards | VERIFIED_AFTER_FIX | Page first, then join images |
| BE-001 category tree reloaded per request | P2 | Reproduced: `category` SELECT on every search/suggest/listing | VERIFIED_AFTER_FIX | Cached snapshot, cleared on catalog change |
| BE-002 wishlist N+1 | P2 | Reproduced: 4/6/8 statements for 1/2/3 lists | VERIFIED_AFTER_FIX | Two batched queries |
| BE-003 notification retention churn | P2 | Reproduced: 40 INSERTs + 1 DELETE on every unchanged poll | VERIFIED_AFTER_FIX | Skip milestones below the retention floor |
| QA-001 image CI covers 113 of 2,201 products | P2 | Confirmed: validator read only `demo/` | VERIFIED_AFTER_FIX | Validator follows seeder inputs |
| QA-002 Playwright not run in CI | P2 | Confirmed: no e2e job in `ci.yml` | FIXED | New `e2e` job; runs on the next CI run |
| IMG-001 duplicate image bytes | P3 | Reproduced exactly: 1,094 groups, 20.3 MiB | DEFERRED_WITH_REASON | See below |

## Detail per fix

Measurements below were taken on the same isolated stack and seed (2,201 products), with the baseline and fixed
builds of the API run side by side against the same database. SQL counts come from PostgreSQL statement logging.

### SEC-001: session revocation

- **Root cause.** `SessionRevocation.isRevoked` returned "not revoked" whenever the Redis denylist key was missing and
  consulted PostgreSQL only if Redis threw. A key lost to eviction, restart or a failed write re-enabled a signed-out
  access token for the rest of its 10-minute life. The `ACCOUNT_DISABLED` path never wrote a denylist key at all.
- **Change.** The check is a single primary-key lookup on `user_session` (revoked, expired or missing rows are
  rejected). The Redis denylist and its four writers were removed rather than kept as a "fast path": a Redis hit only
  shortcut revoked tokens, while every valid request paid an extra Redis round trip. If the database can't answer,
  the request fails rather than being allowed.
- **Tests.** `AuthIT.accessTokensFollowTheSessionTableNotACache` (revoked, expired and deleted rows, plus a live
  session) and every existing logout, device-revoke, password-change and refresh-reuse test. Live replay after key
  deletion now returns 401. With Redis stopped, a revoked token is still rejected.
- **Cost.** One extra indexed SELECT per authenticated request. Authenticated read throughput under load was within
  run-to-run noise of the baseline (see [STRESS_TEST_RESULTS.md](STRESS_TEST_RESULTS.md)).

### UX-001: notification panel

- **Root cause.** `absolute right-0` anchored a nearly viewport-wide panel to the bell, which sits left of the cart.
- **Change.** Below `sm` the panel is `fixed` with 12px side insets, starting just below the bell (measured when it
  opens; the header is sticky). Escape now also returns focus to the bell. Desktop placement is unchanged.
- **Tests.** Playwright `overlays-and-keyboard.spec.ts`: panel inside the viewport at 320, 390, 430, 768 and 1440, and
  focus returns to the bell. The same spec fails against the unmodified build (left edge -34px).

### UX-002: compare tray and purchase bar

- **Root cause.** Two independent fixed layers (`bottom-5 z-30` tray, `bottom-0 z-20` bar) with no shared contract.
- **Change.** The phone purchase bar publishes its measured height as `--tk-bottom-bar` (0 when hidden at `md`+). The
  tray and toasts sit above it. The bar also respects the bottom safe-area inset.
- **Tests.** Playwright at 320, 390 and 430: tray bottom ≤ bar top, the bar's Add to cart is clickable, and Clear closes
  the tray. This fails against the unmodified build.

### UX-003: account summary cards

- **Root cause.** `sm:grid-cols-3` followed the viewport, not the column left beside the 220px account menu, and
  `truncate` cut values.
- **Change.** `repeat(auto-fit, minmax(12rem, 1fr))` with wrapping values instead of truncation.
- **Tests.** Playwright with a signed-in Unlimited account that bought the most expensive in-stock product: neither the
  wallet nor the collection value is clipped at 600, 768, 820, 1024 or 1440. This fails at 768 against the
  unmodified build ("Collection value" clipped).

### UX-004: notification history

- **Change.** The full page uses its own page-aware query (`['notifications','page',n]`, 30 per page,
  `keepPreviousData`) and the shared `Pagination` control. The bell keeps its small first-page query. Prefix
  invalidation still refreshes both after read and read-all. A page past the end follows the history back.
- **Tests.** Playwright with a mocked 65-entry history: page 3 shows entry 65 and requests `page=2`. Backend paging
  was already covered.

### A11Y-001, A11Y-002, A11Y-003

- **A11Y-001.** The project's existing WCAG 1.4.1 rule underlined links in `p`, `li`, `dd` and `figcaption`. It now
  also covers class-less links in `span` and `footer`, which fixes both flagged links (and any other inline link in
  those contexts) without a one-off style. Test: computed `text-decoration-line` is `underline`.
- **A11Y-002.** Roving `tabIndex` (one tab stop), Left/Right with wrap, Home/End, automatic activation. Both boards
  are cached queries, so arrowing does not multiply requests. Test: focus and `aria-selected` follow the keys.
- **A11Y-003.** Delete (or Shift+Delete, the browser autocomplete convention) removes the highlighted recent search.
  The option tells screen readers "Press Delete to remove", and a polite status announces the removal. The pointer
  button is unchanged. Test: removal by keyboard, status announced, focus stays in the box.

### API-001: malformed quote options

Explicit null check before the size and entry checks. `VariantCheckoutIT` now asserts 400 with `fieldErrors[0].field
== "options"` for `null`, `[]`, `1` and a nested object. Live: absent and `{}` return 200; null, array, scalar,
nested and unparseable all return 400.

### DB-001 and API-002: listing query

- **Change.** A `page` CTE filters, counts (window function), sorts and limits product rows. Only that page joins its
  primary image. The outer query re-applies the same `ORDER BY`. When a page past the end comes back empty, a
  separate `count(*)` with the same filter supplies the real total, so search also stops "relaxing" on an
  out-of-range page.
- **Equivalence.** 44 listing and search variants (7 sorts × default, page 3, category, two searches, plus filters,
  last page and out-of-range) compared between baseline and fixed builds: 41 byte-identical (ids, order, image URLs,
  prices, stock). The 3 that differ are the out-of-range totals this fix corrects.
- **EXPLAIN ANALYZE** (default 24-card listing, five runs each): image index loops 2,201 → 24, shared buffer hits
  7,807 → 544, execution time median about 3.6 ms → 1.5 ms (local, warm cache).
- **Tests.** `CatalogApiIT.pagesPastTheEndKeepTheRealTotal` and the existing catalog and search suite.

### FE-001: home tiles

`HOME_TILES` stays the editorial order of all collections (each keeps its collection page). The home build now stops
after the first six non-empty tiles (a lazy stream, so the rest are never queried). Cache-cold home build: 15 → 9
SQL statements; response 65,994 → 50,699 bytes. `CatalogApiIT` asserts exactly six tiles.

### BE-001: category tree

`CatalogService.tree()` returns a cached `CategoryTree` (same 5-minute `Memo` and `CatalogChanged` invalidation as the
public tree; child lists are now immutable because the snapshot is shared). Warm statements per request: suggest
2 → 1, search 2 → 1, category listing 3 → 2.

### BE-002: wishlist reads

All of a shopper's list items in one query, all product cards in one more, grouped in memory. Statements for 1, 2
and 3 populated lists: 4/6/8 → 4/4/4. New `WishlistIT` covers order, a product in two lists, empty lists, drafted
products and ownership.

### BE-003: notification retention churn

- **Root cause.** Retention keeps the newest 200 rows, but those rows are also the only dedupe record. A trimmed
  milestone looked new on the next poll, so it was inserted and trimmed again on every poll.
- **Change.** When milestones are missing, one query reads the oldest retained row (the 200th newest). A missing
  milestone strictly older than it is skipped: it would be trimmed at once anyway, so the visible list is the same.
  No schema change.
- **Measured.** Same 60-delivered-order fixture as Codex, three unchanged polls: baseline 247/47/47 statements
  (40 INSERTs + 1 DELETE per unchanged poll), fixed 248/7/7 (no writes).
- **Tests.** `OrderTrackingIT.trimmedMilestonesAreNotRecreatedOnEveryPoll` checks the identity sequence, which
  advances even for a row deleted in the same poll. It fails without the fix ("expected 203"), and a refund on a full
  history still produces a notification.

### QA-001: image validation

`scripts/validate_images.py` now loads exactly what `DemoCatalogSeeder` loads: `demo/products.json` plus every
`catalog/products/*.json`, with `catalog/images.json` overriding `demo/images.json` per slug, and parked SKUs allowed
without an image. It also checks unique slugs and SKUs and all 1,695 option images. Manufacturer photos shared
between catalog SKUs (23 groups) are counted for review rather than failed; the strict duplicate rule still applies
to the demo set. Result: 2,201 products, 0 errors, under a second. A planted duplicate slug, missing option file,
generic option alt and wrong recorded size were all reported and failed the run.

### QA-002: end-to-end tests in CI

New `e2e` job: PostgreSQL and Redis service containers, the API from the built jar (dev profile, per-run JWT key),
the production frontend build behind `vite preview`, a wait for the seeded catalog, `playwright test --workers=2`, and
traces and logs uploaded on failure. Locally, the identical steps pass: 30 tests, with 16 skips that are intentional
(viewport-setting specs run once, in the desktop project).
The job's first real run happens on GitHub; see [FINAL_VERIFICATION.md](FINAL_VERIFICATION.md) for its status.

### IMG-001: deferred

Reproduced exactly (1,094 byte-identical groups, 21,273,614 redundant bytes). Deferred because:

- it is storage, not per-page transfer: each product page loads its own photo once either way;
- catalog JSON is served with `stale-while-revalidate=604800`, so CDN copies can reference the old URLs for up to a
  week. The old files would have to stay deployed anyway, so the storage saving would not arrive with the change;
- the manifest rewrite touches 2,000+ products' provenance records for a P3 benefit.

It is worth doing together with a future catalog regeneration, keeping old paths for one cache lifetime.

## Found independently (not in the Codex audit)

| ID | Priority | Defect | Status |
|---|---|---|---|
| NEW-001 | P2 | Concurrent "add to cart" lost increments, and concurrent first adds of a product returned HTTP 500 | VERIFIED_AFTER_FIX |
| NEW-002 | P2 | Hearting several products at once (shopper with no list yet) returned HTTP 500 for all but one and lost those saves | VERIFIED_AFTER_FIX |
| NEW-003 | P2 | Light theme: small text on the tinted `primary-subtle` background failed WCAG AA contrast (rankings initials 4.44:1, collection chip counts 4.12:1, unread notification timestamps) | VERIFIED_AFTER_FIX |
| NEW-004 | P2 | A tab left open across a deploy showed "Something went wrong" when it navigated to a page whose code chunk the new deployment no longer serves | VERIFIED_AFTER_FIX |

- **NEW-001.** `CartService.add` read a line's quantity and wrote quantity+n with no lock. Eight concurrent adds to an
  existing line ended at quantity 2 instead of 9. Six concurrent first adds produced five 500s (unique key on the
  line). Fix: lock the shopper row (`SELECT … FOR UPDATE`) at the start of `add`, so adds for one shopper run one at
  a time. Checkout locks the wallet, not the shopper, so there is no lock-order cycle. `CartConcurrencyIT` (both
  cases) failed before the fix.
- **NEW-002.** Each concurrent request found no default wishlist and created one; the partial unique index
  `wishlist_one_default` rejected all but the first. Fix: the same per-shopper lock in `add` and `createList`, which
  also makes the 20-list and 100-item limits race-free. `WishlistIT.heartsTappedAtOnceAllLand` failed before the fix.

- **NEW-003.** Codex's axe scans ran in the dark theme only. In light mode, `#1e5eff` on `#e8efff` (4.44:1) and
  `#64748b` on `#e8efff` (4.12:1) were flagged as serious. Those three spots now use the existing stronger tokens
  (`text-primary-hover`, `text-ink-muted`), so the palette is unchanged. axe now reports 0 violations across 17 routes
  at 390 and 1440 plus the open notification panel and search suggestions, in both themes. A Playwright test checks
  rankings and collection in both schemes and fails against the old build.
- **NEW-004.** Pages other than Home and 404 are lazy chunks with content hashes. After a deploy, an old tab's next
  navigation asked for a chunk that no longer exists and landed on the route error page. `main.tsx` now listens for
  Vite's `vite:preloadError` and reloads once to pick up the current build; a 10-second session guard prevents a
  reload loop if a chunk is genuinely missing. Simulated by serving a 404 for the first request of the rankings
  chunk: the page reloaded and rendered.

A smaller polish item from the same pass: rank notifications have no product photo and showed an empty grey well;
they now show a trophy icon.

Checkout's price authority, idempotency, wallet locking, stock checks and refund rules were stress-tested
concurrently and needed no change (see [STRESS_TEST_RESULTS.md](STRESS_TEST_RESULTS.md)).
