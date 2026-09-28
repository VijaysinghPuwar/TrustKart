# TrustKart Feature Matrix

Status values: PLANNED · IN PROGRESS · IMPLEMENTED (works, tests incomplete) ·
TESTED (automated tests + browser check) · DEFERRED.

A feature is only marked TESTED when its tests pass and it has been checked in
a real browser where it has UI.

| Feature | Status | Backend | Frontend | Tests | Priority |
|---|---|---|---|---|---|
| Legacy static clone retired | PLANNED | n/a | n/a | n/a | P0 |
| Monorepo, Docker Compose (Postgres + pgvector, Redis) | PLANNED | compose | n/a | compose smoke | P0 |
| Health endpoint, request ID, structured errors | PLANNED | Actuator, filter, advice | error UI | MockMvc | P0 |
| CI (backend, frontend, secret scan, dependency review) | PLANNED | Maven | npm | workflow | P0 |
| Design tokens (light + dark) | PLANNED | n/a | tokens.css + Tailwind | visual | P0 |
| Header, category nav, footer, virtual store indicator | PLANNED | n/a | layout | RTL + visual | P0 |
| Home page (hero, tiles, trust strip, shelves, categories) | PLANNED | collections API | home | E2E | P0 |
| Product card + skeleton | PLANNED | n/a | commerce/ProductCard | RTL | P0 |
| Catalog schema (category, brand, product, specs JSONB, images, inventory) | PLANNED | Flyway + JPA | n/a | Testcontainers | P0 |
| Demo catalog seed (~100 products, licensed images) | PLANNED | profile-gated seeder | n/a | idempotency test | P0 |
| Product list: filter, sort, paginate | PLANNED | JPA spec + JSONB | search page | API + E2E | P0 |
| Category-specific spec filters | PLANNED | spec_definition facets | filter panel | API | P1 |
| Product detail page | PLANNED | product API | PDP | E2E | P0 |
| Exact search + suggestions | PLANNED | Postgres FTS | SmartSearch combobox | API + RTL | P0 |
| Query interpretation chips (rule-based) | PLANNED | parser | chips | unit | P1 |
| Smart (semantic) search + fallback | PLANNED | Spring AI + pgvector | mode toggle, fallback banner | integration | P2 |
| Registration, login, logout | PLANNED | Spring Security | auth pages | security tests | P0 |
| JWT access + rotating refresh cookies, reuse detection | PLANNED | auth module | api client | security tests | P0 |
| CSRF (cookie SPA pattern) | PLANNED | Security config | api client | MockMvc | P0 |
| RBAC with permissions | PLANNED | method security | admin guard | permission matrix | P0 |
| Rate limiting (Bucket4j + Redis) | PLANNED | filter | lockout UI | Testcontainers Redis | P0 |
| Guest identity (cookie) + merge on sign-in | PLANNED | guest filter | n/a | integration | P1 |
| Cart (add, quantity, remove, save for later) | PLANNED | cart module | cart page + drawer | API + E2E | P0 |
| Wishlist + named lists | PLANNED | wishlist module | wishlist UI | API | P1 |
| Virtual wallet (balance, add funds, reset) | PLANNED | wallet module | wallet page, add-funds modal | unit + integration | P0 |
| Budget / Unlimited mode | PLANNED | wallet | mode switch | unit | P0 |
| Virtual checkout (review, delivery presets, wallet step, review, confirm) | PLANNED | quote + purchase | checkout flow | E2E | P0 |
| Atomic purchase, idempotency, concurrency safety | PLANNED | purchase service | double-submit guard | Testcontainers concurrency | P0 |
| Server-side repricing (client price ignored) | PLANNED | purchase service | n/a | security test | P0 |
| Purchase history + virtual receipt | PLANNED | purchase API | account pages | API + E2E | P0 |
| Virtual refund (restore balance, remove from collection) | PLANNED | purchase service | refund action | integration | P1 |
| My Collection + value + stats | PLANNED | collection module | collection page | API | P0 |
| Achievements | PLANNED | collection module | badges | unit | P2 |
| Product comparison (2-4, differences only) | PLANNED | compare API | compare tray + page | API + E2E | P1 |
| Build Your Dream PC (compatibility rules) | PLANNED | builder module | builder UI | unit (rules) | P2 |
| Dream Setup / Homelab builder | PLANNED | setup module | builder UI | API | P2 |
| Security Center: sessions, login history | PLANNED | auth module | account/security | API | P1 |
| TOTP 2FA + backup codes | PLANNED | auth module | setup flow | unit + integration | P1 |
| Passkeys (Spring Security WebAuthn) | PLANNED | auth module | security center | integration | P2 |
| Audit log | PLANNED | audit module | admin table | integration | P1 |
| Admin: products, inventory, purchases, users, audit, security, virtual analytics | PLANNED | admin module | admin app | permission tests | P1 |
| Reviews + Q&A | DEFERRED | | | | P3 |
| Real payments (Stripe) | DEFERRED (removed by design) | n/a | n/a | n/a | n/a |
| OpenAPI docs | PLANNED | springdoc | n/a | smoke | P1 |
| Security headers + CSP | PLANNED | Security config | n/a | MockMvc | P0 |
| Dark mode | PLANNED | n/a | theme toggle | visual | P1 |
| Accessibility (WCAG 2.2 AA target) | PLANNED | n/a | all | axe + manual | P0 |
| Playwright E2E suite | PLANNED | n/a | n/a | Playwright | P0 |
| Deployment (Vercel frontend, container backend) | PLANNED | Dockerfile | vercel.json | smoke | P1 |
| `/about/project` page | PLANNED | n/a | page | visual | P2 |
