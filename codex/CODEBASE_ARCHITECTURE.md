# Verified codebase architecture

Baseline `177fb10`. Source inspection, executable tests, runtime API responses and database counts supersede older README/catalog counts. Tracked inventory: 8,283 files, dominated by 7,970 frontend files including images; 223 backend files, 60 catalog-data files, 12 docs and 8 scripts. This was a repository-wide inventory and module/config/test review, not a claim that every image's licensing or every possible branch was independently proved.

## Runtime and deployment

React 19.2.8 + React DOM, TypeScript 6, Vite 8.3, React Router 8.4, TanStack Query 5.104 and Tailwind 4. Fonts are locally packaged Inter and JetBrains Mono; lucide-react supplies icons. The lockfile and installed production build were inspected. Java 21 / Spring Boot 4.1.1 uses Spring MVC, Spring Security, Validation, JPA/Hibernate, JdbcClient/JdbcTemplate, Flyway, PostgreSQL and Redis/Lettuce. Maven wrapper is the backend build entry point.

Vercel serves the static frontend and rewrites `/api/*` to a Render API. `backend/Dockerfile` builds a layered Spring jar using a Maven/JDK stage and runs as a non-root user on a JRE 21 base. `render.yaml` provisions PostgreSQL 17 and refers to shared hosted Redis with `tk:` key prefixes. Actual hosted configuration and performance were not inspected or contacted. Local compose contains development PostgreSQL (pgvector capable) and Redis, not a full production frontend service.

## Frontend modules

`frontend/src/app/router.tsx` defines 27 meaningful route cases including the 404 fallback. Home and 404 are eager; other page modules use React.lazy. RootLayout owns shared header/navigation/footer and suspense/error handling. Feature folders cover home, search, product, compare, cart, checkout, wallet, collection, wishlist, rankings, auth, account and about pages. Shared UI/commerce components include product imagery/cards/shelves, dialogs, buttons, fields and pagination. Theme tokens live in the frontend CSS theme; repeated card/button patterns use shared components.

`src/data/` owns TanStack Query reads/mutations and key families. `src/lib/api.ts` implements credentialed API access, CSRF bootstrapping/refresh behavior and error normalization. Browser-local state stores comparison/recent history; server state governs commerce. See [route matrix](ROUTE_MATRIX.md) for concrete URLs and observed states.

## Backend module boundaries and API

Packages under `com.vijaysinghpuwar.trustkart` separate catalog, search, auth, security, shopper, cart, wallet, purchase, address, wishlist, collection, notification, leaderboard, common and configuration. API endpoints are under `/api/v1`; Actuator exposes limited health/info, and API docs are disabled unless explicitly enabled. Controllers map DTOs and validation; transactional commerce work lives in services. JPA is used for entities and relationships, while native JDBC handles projection-heavy search, rankings, notifications and other reads. Open Session in View is disabled; migrations own schema and Hibernate validates it.

## Authentication and authorization

Passwords use Argon2; short-lived JWT access tokens are delivered as cookies. Refresh/session state is stored in PostgreSQL; CSRF protection applies to credentialed writes. Role/permission checks protect admin leaderboard operations. Guest shoppers have a hashed guest-token association and their own server-backed cart/wallet/orders. Ownership is enforced server-side and was exercised across two synthetic shoppers.

Google OAuth/OIDC is conditional on configured credentials and has tests using a fake provider. Browser callback/state integration was reviewed, but no real Google account was used. Redis backs short-lived OAuth state, request-rate buckets and revoked-session IDs. SEC-001 documents the verified missing-key revocation weakness; this limits any claim that Redis is entirely optional for security correctness.

## Catalog/search/images

The local seed contains **2,201 products, 198 brands, 112 categories (18 roots)**. Base `demo/products.json` contributes 113 products; expanded `catalog/products/*.json` adds 2,088. Image manifests associate public WebP paths and provenance. Variant configuration and typed JSON specifications feed detail pages, comparisons, option pricing and facets. Category hierarchy/spec inheritance support catalog filtering.

Seeding uses bounded batches and a fingerprint to skip unchanged work; CatalogChanged invalidates local catalog caches. Listing cards are projected DTOs, not serialized complete entities. Product detail includes the product's options/specifications/gallery metadata and bounded related products; no review/question system was found. Product list/search pagination is server-side. PostgreSQL full-text/trigram search and a rule-based natural-language interpreter are active. The UI explicitly disables Smart/AI mode; no runtime embedding or semantic API calls were implemented. Vector capability/schema availability is not evidence of active semantic search.

Images use 400/800px WebP srcset, sizes hints, intrinsic dimensions, lazy loading and object-contain wells. Hero/detail priority loading is explicit. See IMAGE_ASSET_AUDIT.md for measured inventory and duplicate content.

## Wallet, orders and tracking

PostgreSQL owns wallet balance/mode, ledger transactions, virtual purchase records and item snapshots. Checkout recomputes prices/options server-side, locks the wallet, conditionally updates inventory in deterministic order and commits the purchase/ledger/cart changes atomically. Idempotency keys prevent duplicate order creation. Tests cover rollback and concurrent inventory/purchase behavior. Real payment processing is absent: purchases and funds are simulated.

Delivery tracking is derived from server timestamps over seven days, including cancellation/refund constraints. It does not persist a timer per order or run a seconds-level tracking scheduler. Collection is derived from qualifying order items and includes achievements/summary data. Saved addresses and multiple wishlists are implemented. Preserve these domain semantics while changing read paths.

## Notifications and rankings

Notifications synchronize due milestones during reads, dedupe by keys and retain 200 visible records per shopper with a 45-day order lookback. Frontend unread count polls every two minutes, usually only in the foreground; browser-alert opt-in permits background polling. BE-003 identifies retention/dedupe churn; UX-004 identifies missing full-page pagination.

Monthly Top 50, all-time Top 100 and personal standing use PostgreSQL aggregates with eligibility/privacy rules. Cached public leaderboard snapshots have a 30-second TTL and are invalidated after relevant commits. Admin operations exist as protected APIs; there is no implemented admin dashboard route. No Redis-only spending authority was found.

## Database and tests

Thirteen Flyway migrations create catalog/identity/security/commerce/wishlist/notification/leaderboard tables and indexes. PostgreSQL extensions include citext, pg_trgm and vector capability. See DATABASE_AUDIT.md for query plans and constraint review.

JUnit/Mockito unit tests and Testcontainers integration tests exercise the actual PostgreSQL/Redis behavior; mutable clocks support tracking. Vitest currently covers money/options utilities. Playwright covers shopping, rankings and selected responsive conditions. CI also checks images, dependency vulnerabilities, secrets and Docker builds, but omits existing E2E execution (QA-002).
