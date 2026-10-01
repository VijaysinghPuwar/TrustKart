# Final verification

**Date:** October 1, 2026
**Branch:** `trustkart-rebuild`, starting from `177fb10` (the commit the Codex audit inspected)
**Scope:** verification of the Codex audit in [`codex/`](../codex/README.md), fixes, regression and controlled stress
testing. Per-finding detail is in [CODEX_VERIFICATION.md](CODEX_VERIFICATION.md) and load results in
[STRESS_TEST_RESULTS.md](STRESS_TEST_RESULTS.md).

No P0 or P1 defects are known after this pass under the tested scenarios. That is a statement about what was
tested, not a guarantee that none exist.

## Codex findings processed

| Outcome | Count |
|---|---:|
| Findings in the audit | 18 (P0 0, P1 1, P2 16, P3 1) |
| Confirmed and fixed | 16 |
| Partially confirmed and fixed (UX-003) | 1 |
| Confirmed, deferred with reason (IMG-001, P3) | 1 |
| Rejected / not reproducible / already fixed | 0 |
| Additional defects found independently and fixed | 4 (NEW-001 to NEW-004) |

## Tests run on the final source

| Suite | Command | Result |
|---|---|---|
| Backend unit + integration (Testcontainers PostgreSQL and Redis) | `cd backend && ./mvnw -B -ntp verify` | 144 passed (24 unit, 120 integration), 0 failed. Was 136 before; 8 new regression tests |
| Frontend lint | `npm run lint` | Pass, 0 warnings |
| Frontend typecheck | `npm run typecheck` | Pass |
| Frontend format | `npm run format:check` | Pass |
| Frontend unit | `npm test` | 11 passed |
| Frontend production build | `npm run build` | Pass |
| End-to-end (production build + local API) | `npx playwright test` | 30 passed, 0 failed, 16 intentional skips (viewport-setting specs run only in the desktop project). Was 22 passed / 8 skipped; 8 new tests |
| Image validation (whole catalog) | `python3 scripts/validate_images.py` | 2,201 products and 1,695 option images, 0 errors, 146 review warnings (non-exact model matches, unchanged) |
| Backend Docker image | `docker build backend` | Pass, 459 MB (unchanged) |
| Write concurrency | 9 targeted races (see stress results) | 9/9 pass |
| Read load | 1 to 100 users, plus 180 s endurance | 0 HTTP 5xx, 0 network errors |

Every new regression test was also run against the unmodified code and failed there: the 8 Playwright tests against a
build of `177fb10`, and the backend tests (`AuthIT`, `OrderTrackingIT`, `CartConcurrencyIT`, `WishlistIT`) with the
fix removed. Two of my first drafts did not fail against the old code; they were rewritten until they did.

## Bugs fixed

| ID | What was wrong |
|---|---|
| SEC-001 (P1) | A signed-out access token worked again if its Redis denylist key was lost |
| API-001 | `options=null` on the quote endpoint returned 500 |
| API-002 | A page past the end reported a total of 0, and search widened its query because of it |
| UX-001 | Phone notification panel ran 34px off the left edge |
| UX-002 | Compare tray covered the phone "Add to cart" bar |
| UX-003 | Tablet account cards cut off large amounts |
| UX-004 | Notifications after the newest 30 could not be reached |
| A11Y-001/002/003 | Colour-only links, leaderboard tabs without arrow keys, recent searches removable only by mouse |
| NEW-001 | Concurrent adds to cart lost quantity or returned 500 |
| NEW-002 | Hearting several products at once returned 500 and lost saves |
| NEW-003 | Light theme: small text on tinted chips below AA contrast |
| NEW-004 | A tab open across a deploy showed an error page on its next navigation |

## Performance and resource measurements

All before/after pairs use the same seed, database and machine, with the baseline and fixed API running side by side.

| What | Before | After |
|---|---:|---:|
| 24-card listing: image index lookups (EXPLAIN ANALYZE) | 2,201 | 24 |
| 24-card listing: shared buffer hits | 7,807 | 544 |
| 24-card listing: execution time, median of 5 (local) | ~3.6 ms | ~1.5 ms |
| SQL per warm suggestion / search / category listing | 2 / 2 / 3 | 1 / 1 / 2 |
| SQL per wishlist read, 1 / 2 / 3 lists | 4 / 6 / 8 | 4 / 4 / 4 |
| Unchanged notification poll, 60 delivered orders | 47 statements (40 INSERT, 1 DELETE) | 7 (reads only) |
| Home response, cache-cold build | 65,994 bytes, 15 statements | 50,699 bytes, 9 statements |
| SQL per authenticated request (SEC-001 cost) | +0 (Redis round trip instead) | +1 primary-key SELECT, no Redis call |

Authenticated read throughput with the SEC-001 change was within run-to-run noise of the baseline. Frontend entry
chunk: 288.47 kB raw / 88.67 kB gzip (audit baseline 287.59 kB raw), within the 110 kB gzip budget. CSS 69.66 kB raw /
20.12 kB gzip. No route chunks were added or merged.

## Responsive and browser checks

- A Playwright sweep of 27 route cases (signed in with an order, a wishlist item and notifications; sign-in and
  sign-up signed out) at all 13 requested widths (320, 360, 375, 390, 430, 600, 768, 820, 1024, 1280, 1440, 1728,
  1920): 351 observations, 0 with page-level horizontal overflow, 0 broken images, every page with a heading. Five
  430px captures were blank because the preview server was rebuilt during the sweep; they were rechecked and render
  correctly with no console errors.
- Screenshots at 390, 768 and 1440 were reviewed for the changed pages: account (tablet), product with compare tray,
  rankings, notifications.
- New Playwright guards: notification panel inside the viewport at 320 to 1440, tray above the purchase bar at 320
  to 430, account values unclipped at 600 to 1440.

## Accessibility

- axe (WCAG 2.0/2.1/2.2 A and AA tags) on 17 routes at 390 and 1440, plus the open notification panel and search
  suggestions: 0 violations in light and in dark themes (76 scans). Before NEW-003, the light theme had 6 serious
  contrast violations on 3 routes.
- Keyboard checks in Playwright: rankings tabs (arrows, Home/End, wrap, roving focus), recent-search removal with
  Delete and a status announcement, Escape on the notification panel returning focus to the bell.
- Not done: a screen-reader session with real assistive technology, Safari and Firefox, physical devices.

## Failure handling checked in the browser

| Simulated | Result |
|---|---|
| All API calls return 503 | The "store server is starting" notice and skeletons, bounded retries (the designed cold-start behaviour) |
| Product request network failure | Same notice, then the error state after the bounded retries |
| Every product image 404 | Placeholder well; no visibly broken images |
| HTML instead of JSON from `/catalog/home` | Treated as a transient failure; placeholders while retrying |
| Session cookies cleared | Security Center asks the shopper to sign in |
| Old chunk missing after a deploy | One automatic reload, page renders (was an error page) |
| Redis stopped | Store keeps working; revoked tokens still rejected |

## Memory

48 client-side navigations across six routes: DOM nodes and event listeners returned to their starting values
(2,061 and 622). JS heap after forced GC grew 4.67 → 5.26 MiB, with the increments shrinking each round, consistent with
TanStack Query's cache filling within its 5-minute `gcTime`. No console errors. Backend RSS was flat during the
180-second endurance run. This does not show a leak, though a longer session was not tested.

## Security regression checks

- Existing AuthIT (CSRF required on writes, tampered tokens rejected, other users' sessions not revocable, admin APIs
  refused to customers, refresh-token reuse burns the session, lockout, rate limits) all pass.
- Ownership: wishlist lists cannot be written by another shopper (new test); cart, order, refund and notification
  ownership tests pass.
- Checkout still prices on the server, holds the wallet row lock, checks stock and honours Idempotency-Key (stress
  scenarios A to D and F).
- New row locks (cart and wishlist writes) lock the shopper row only; checkout locks the wallet, so there is no
  lock-order cycle.
- The public cache allowlist and CSRF configuration were not changed; PublicCacheIT passes.
- The e2e CI job uses throwaway service containers, the documented `change-me-` placeholder password pattern and a
  JWT key generated per run. It never contacts a hosted service.
- No secrets, credentials or personal data in the diff (manual review plus gitleaks; see the push notes in the final
  report).

## Known limitations and remaining items

- **IMG-001 (P3), deferred.** 20.3 MiB of byte-identical images under different paths. Reasons are in
  CODEX_VERIFICATION.md.
- **CI e2e job.** Its steps were run locally; its first real run happens on GitHub after this push.
- **Not tested:** the hosted deployment (Render, Vercel, Upstash), real Google sign-in, Safari and Firefox, physical
  phones, screen readers, Lighthouse scores and field Web Vitals.
- **Header balance format (P3).** The header shows the wallet rounded to whole dollars ($96,700) while the account card
  shows cents ($96,700.01). It looks deliberate for a compact header; left as is.
- **Load numbers are local.** Client, API and database shared one machine; see STRESS_TEST_RESULTS.md.
