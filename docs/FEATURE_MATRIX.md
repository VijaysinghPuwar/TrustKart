# TrustKart Feature Matrix

Status values: PLANNED · IN PROGRESS · IMPLEMENTED (works, tests incomplete) ·
TESTED (automated tests + browser check) · DEFERRED.

A feature is only marked TESTED when its tests pass and it has been checked in
a real browser where it has UI. Last reviewed 2026-09-29.

| Feature | Status | Backend | Frontend | Tests | Priority |
|---|---|---|---|---|---|
| Legacy static clone retired | TESTED | n/a | n/a | n/a | P0 |
| Docker Compose (Postgres 17 + pgvector, Redis 8) | TESTED | compose | n/a | used by dev + stress test | P0 |
| Health endpoint, request ID, structured errors | TESTED | Actuator, filter, advice | error UI with support reference | MockMvc (PlatformIT) | P0 |
| CI workflow | IMPLEMENTED | Maven | npm | `.github/workflows/ci.yml` (runs on push) | P0 |
| Design tokens (light + dark) | TESTED | n/a | Tailwind `@theme` tokens | visual audit | P0 |
| Header, category nav (overflow collapses into "More"), footer | TESTED | n/a | layout | E2E + screenshots at 390/1024/1440/1920 | P0 |
| Home: rotating deals banner, collection tiles, deals, featured, departments | TESTED | home API (studio-photo banner rule) | home | E2E + screenshots | P0 |
| Catalog: 905 products, 18 departments, JSONB specs, inventory | TESTED | Flyway + JPA, idempotent seeder | n/a | CatalogApiIT | P0 |
| Product photography: official manufacturer images, provenance recorded | TESTED | images.json + credits | credits page | `scripts/validate_images.py` (0 errors) | P0 |
| Product list: filter, sort, paginate, spec facets | TESTED | search SQL + facets | search page | API + E2E | P0 |
| Product detail page | TESTED | product API | PDP | E2E | P0 |
| Product variants (storage / colour / configuration, priced by server) | TESTED | V9 options, V10 cart selection, resolver | option chips, labels in cart/checkout/receipt | VariantCheckoutIT, unit tests, browser | P0 |
| Exact search + suggestions + query chips | TESTED | Postgres FTS + interpreter | SmartSearch combobox | API + unit | P0 |
| Smart (semantic) search + fallback | PLANNED | Spring AI + pgvector | mode toggle present, disabled | n/a | P2 |
| Registration, login, logout, Google sign-in | TESTED | Spring Security, OIDC | auth pages | AuthIT, GoogleSignInIT | P0 |
| JWT access + rotating refresh cookies, reuse detection | TESTED | auth module | api client | AuthIT | P0 |
| CSRF (cookie SPA pattern) | TESTED | Security config | api client | MockMvc | P0 |
| RBAC roles + permissions | IMPLEMENTED | roles/permissions seeded | n/a (no admin UI yet) | partial | P0 |
| Rate limiting (Bucket4j + Redis) + account lockout | TESTED | filter | error messages | AuthIT + stress test | P0 |
| Guest shopper + merge on sign-in | TESTED | ShopperMergedEvent | n/a | integration | P1 |
| Cart (add, quantity, remove, save for later) | TESTED | cart module | cart page + drawer | API + E2E | P0 |
| Wishlist + named lists | TESTED | wishlist module | wishlist UI | API | P1 |
| Address book | TESTED | address module | checkout + account | AddressIT | P0 |
| TrustKart Wallet (balance, add funds, Budget / Unlimited) | TESTED | wallet ledger | wallet page | VirtualCommerceIT | P0 |
| Checkout (cart → delivery → payment → review, processing, confirmation) | TESTED | quote + purchase | checkout flow | E2E, forced-error UI checks | P0 |
| Atomic purchase, idempotency, no oversell, server repricing | TESTED | purchase service | double-submit guard | concurrency ITs + stress test | P0 |
| Order history + receipt | TESTED | purchase API | account pages | API + E2E | P0 |
| 7-day order tracking (stages, tracking number, shipment history) | TESTED | clock-based OrderTracking, V8 | tracking panel, order list status | OrderTrackingIT, browser | P0 |
| Cancel before shipping / return within 30 days of delivery | TESTED | purchase service | receipt actions | OrderTrackingIT | P1 |
| Notification center (bell, unread count, preferences, browser alerts) | TESTED | idempotent milestone notifications | bell + notifications page | OrderTrackingIT, browser | P1 |
| My Collection + value + stats | TESTED | collection module | collection page | API | P0 |
| Achievements (76, bronze → legendary, "Up next", group filters) | TESTED | computed from history on read | achievements section | unit + browser | P1 |
| Monthly leaderboard (Top 50, UTC calendar month) | TESTED | `LeaderboardService`, ranked from completed orders | /rankings "This Month" tab, podium, table / cards | LeaderboardIT (boundaries, limit, ties), E2E | P1 |
| All-time leaderboard (Top 100) | TESTED | same query, no period filter | "All Time" tab, member since | LeaderboardIT, E2E | P1 |
| Personal rank outside the top lists, gap to the list | TESTED | window-function lookup | "Your position" card, account overview | LeaderboardIT, E2E | P1 |
| Leaderboard privacy (anonymous by default, public name, photo opt-in) | TESTED | `leaderboard_profile` (V11), reserved names | account/rankings settings | LeaderboardIT (no email or account name exposed) | P1 |
| Leaderboard notifications (milestones, month-end result) | TESTED | idempotent keys per month and tier | "Rank updates" preference | LeaderboardIT | P2 |
| Historical monthly results | IMPLEMENTED | any past month is recomputable from order history | month-end notification only (no archive page yet) | LeaderboardIT | P2 |
| Leaderboard admin: status, reconcile, cache refresh | TESTED | `/api/v1/admin/leaderboards` (analytics permission) | n/a (no admin UI yet) | LeaderboardIT (403 / 401, 0 mismatches) | P2 |
| Product comparison (2-4, differences only) | TESTED | compare API | compare tray + page | API + E2E | P1 |
| Security Center: sessions, login history | TESTED | auth module | account/security | AuthIT | P1 |
| Build Your Dream PC (compatibility rules) | PLANNED | builder module | builder UI | unit (rules) | P2 |
| Dream Setup / Homelab builder | PLANNED | setup module | builder UI | API | P2 |
| TOTP 2FA + backup codes | PLANNED | auth module | setup flow | unit + integration | P1 |
| Passkeys (Spring Security WebAuthn) | PLANNED | auth module | security center | integration | P2 |
| Audit log | PLANNED | audit module | admin table | integration | P1 |
| Admin: products, inventory, purchases, users, audit | PLANNED | admin module | admin app | permission tests | P1 |
| Reviews + Q&A | DEFERRED | | | | P3 |
| Real payments | DEFERRED (removed by design) | n/a | n/a | n/a | n/a |
| OpenAPI docs | IMPLEMENTED | springdoc (off unless `TRUSTKART_API_DOCS_ENABLED`) | n/a | smoke | P1 |
| Security headers + CSP (API and site) | TESTED | Security config | `vercel.json` CSP | MockMvc + zero-violation browser check | P0 |
| Dark mode | TESTED | n/a | theme toggle | visual | P1 |
| Accessibility (WCAG 2.2 AA target) | IMPLEMENTED | n/a | all | axe visual audit; manual review pending | P0 |
| Playwright E2E suite | TESTED | n/a | n/a | desktop + Pixel 7, plus responsive guards 320 to 1920 px | P0 |
| Performance pass (layout shift, requests, query counts) | TESTED | home/category cache, batched pricing, lock-free reads, lighter polling | early home data, stable banner, lazy quote | `scripts/measure.mjs`, Lighthouse, docs/PERFORMANCE_AUDIT.md | P1 |
| Load / stress test | TESTED | n/a | n/a | `scripts/stress_test.py` (browse, rate-limit, checkout, oversell, invariants) | P1 |
| Deployment (Vercel frontend, container backend) | TESTED | Dockerfile, proxy + OAuth config | `vercel.json` | live: Vercel (site) + Render (API, Postgres); see docs/DEPLOYMENT.md | P1 |
| `/about/project` page | TESTED | n/a | page | visual | P2 |
