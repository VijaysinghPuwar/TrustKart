# TrustKart independent audit summary

**Audit complete; implementation has not started.** Baseline: `177fb10` on `trustkart-rebuild`, September 30–October 1, 2026. Local origin was fetched and the branch fast-forward checked: already current with origin/trustkart-rebuild. Origin/main has newer merge history but an identical source tree. Existing untracked render scripts and concurrent coding processes were preserved. Only `codex/` was added; no application source/config/tests/migrations/docs were changed, committed or pushed.

## Verified state and overall health

TrustKart is a working React/TypeScript/Vite retail SPA backed by Java 21/Spring Boot, PostgreSQL and Redis. It contains **2,201 products, 198 brands and 112 categories**, with catalog/search, comparison, wishlist/cart, simulated wallet/checkout, seven-day tracking, collection, notifications, rankings, account/security and optional Google login. Smart/semantic AI search and an admin dashboard are not implemented; protected admin ranking APIs exist. Read CODEBASE_ARCHITECTURE.md before assuming planned features are live.

The existing build/test baseline is healthy and the premium design is coherent. Targeted inspection nevertheless exposed one important session-security defect, several concrete UI/accessibility defects and avoidable database/payload work. This is neither a clean bill of health nor evidence that the architecture needs a rewrite.

## Scope and executed verification

Repository structure, all module areas, 13 migrations, catalog/image manifests, Docker/CI/deployment/config examples, existing docs and tests were inspected. Builds ran in a temporary commit snapshot; app services used isolated local PostgreSQL/Redis and synthetic accounts. Frontend production build, lint, typecheck, formatting, tests; backend compile/package/verify; existing Playwright; image validation; npm production audit; compose validation and Docker build completed.

Browser evidence covers **27 route cases**, **207 route/viewport observations**, **all 14 requested viewport sizes**, **54 axe scans**, selected manual keyboard/overlay interactions and **30 cold production-build lab loads**. Twenty-one APIs were timed five times each; request-correlated SQL and four read-only EXPLAIN plans were captured. Targeted fixtures exposed wishlist query growth and notification retention churn. Routes were inspected visually through full captures and desktop/tablet/mobile contact sheets.

## Findings by priority

| Priority | Count | Summary |
|---|---:|---|
| P0 | 0 | No critical breakage/exploit demonstrated |
| P1 | 1 | SEC-001: revoked access JWT accepted after its Redis denylist key disappears |
| P2 | 16 | UI/keyboard defects, malformed-input/page-count bugs, query/payload waste, notification churn and test-coverage gaps |
| P3 | 1 | IMG-001: byte-identical images duplicated across static paths |
| **Total** | **18** | Canonical details and evidence in OPTIMIZATION_FINDINGS.md |

SEC-001 was reproduced: old cookie returned 401 after logout, then 200 after deleting only that synthetic session's Redis revocation key while PostgreSQL still recorded revocation. The affected window is the remaining short-lived access token lifetime. Fix the missing-key authorization semantics first; do not suppress database checks for speed.

## Test status

Existing tests: **169 executed, 169 passed, zero failed** (136 backend, 11 Vitest, 22 Playwright); eight intentional Playwright project skips. Independent security assertions: **16 executed, 15 passed, one failed** (SEC-001). Combined counted tests/assertions: **185 executed, 184 passed, one failed**. Browser scans/manual probes are reported separately rather than inflating that count. TEST_RESULTS.md explains infrastructure retries and measurement limits.

## Major UI and responsive issues

1. UX-001: notification panel starts 34px outside the left edge at 320/390 widths.
2. UX-002: comparison tray overlaps the phone product purchase bar by 45px.
3. UX-003: tablet account summary truncates Unlimited mode and large monetary values.
4. UX-004: only the first 30 notifications are reachable despite 200 retained entries.
5. A11Y-003: recent-search deletion is pointer-only; A11Y-001/002 additionally cover inline-link cues and leaderboard keyboard tabs.

Root-page overflow checks passed, illustrating why they are insufficient by themselves. Search typing/keyboard submission and settled wallet/filter Escape behavior worked. Retain the premium image wells, dark theme, intentional shelves and desktop whitespace.

## Largest safe optimization opportunities

| Finding | Measured waste | Recommended direction |
|---|---|---|
| DB-001 | 2,201 primary-image probes for 24 returned products | Page before image hydration; preserve sort/count/filter semantics |
| BE-003 | 40 INSERTs plus retention DELETE on each unchanged 60-order notification poll | Preserve durable milestone-generation knowledge beyond display retention |
| BE-002 | 4/6/8 SQL statements for 1/2/3 wishlists | Batch owned associations and one distinct-product lookup |
| FE-001 | At least 20 unused home cards / 15,331 JSON bytes plus five cache-rebuild queries | Return/build only the visible home tile contract |
| BE-001 | Repeated full category metadata query/rebuild on list/search/suggest | Reuse immutable metadata with finite freshness and invalidation |

IMG-001 offers a lower-priority **20.3MiB storage upper bound** from exact duplicates. Current 400/800px WebP assets are already small; the largest product image is 196,998 bytes. Do not sacrifice sharpness to chase a score.

## Measured performance/resource snapshot

Desktop home initially transferred ~948.2KiB across 83 requests, including 145,007 bytes JS and 20,230 bytes CSS. Median observed home LCP was 568ms desktop and 884ms under the stated mobile simulation; these are local lab values, not production percentiles. The complete raw/gzip bundle and other vitals are in FRONTEND_PERFORMANCE.md. Slowest tested warm API median was facets at 11.71ms. Backend Docker image displayed 459MB locally. Frontend Docker image is not applicable. Lighthouse scores and INP: **Not measured**.

Backend/database changes should remove demonstrated work, not presume local millisecond requests are already harming production users. SQL plans used the current 2,201-product catalog; leaderboard history was small. No arbitrary index, pool-size, heap-size or package replacement is justified by this evidence.

## Things Claude should not change

Preserve authoritative server pricing, wallet/ledger/order transactions, stock checks, idempotency, ownership, CSRF and password/session protections. Keep PostgreSQL authoritative; fix Redis revocation correctness rather than caching more security state blindly. Preserve timestamp-derived tracking, mutation-driven wallet/cart updates, two-minute notification polling, fast 150ms cancellable search suggestions, existing lazy routes and responsive high-quality images.

## Limits and handoff

No production services were contacted, no real Google consent tested, no hosted load/CPU profiling run, and no complete screen-reader/Safari/Firefox/light-theme campaign performed. React commit profiling and field Web Vitals were not available. Some networkidle waits timed out during the broad harness; settled captures and later measurements are identified explicitly. A green test count is not a security certificate.

Start implementation with [CLAUDE_IMPLEMENTATION_PLAN.md](CLAUDE_IMPLEMENTATION_PLAN.md), verify each finding still exists, and follow [VERIFICATION_CHECKLIST.md](VERIFICATION_CHECKLIST.md). Evidence and screenshots remain in this folder.

Implementation has NOT been started. Review codex/CLAUDE_IMPLEMENTATION_PLAN.md before making changes.
