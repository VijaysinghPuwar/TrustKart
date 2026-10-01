# PostgreSQL / JPA audit

Thirteen Flyway migrations were applied successfully to an isolated PostgreSQL 17 database and inspected in `evidence/migrations-inspected.sql`. Current seeded counts: 2,201 products, 198 brands, 112 categories. The resource/table snapshot was taken before the later 60-order notification stress fixture; do not confuse the small eight-order snapshot with that later test.

## Schema and authority

Catalog uses product/brand/category/inventory/image/spec/collection relationships, typed monetary values and JSONB configuration. Identity uses users, roles/permissions, identities and sessions; guest/user shopper IDs scope commerce. Wallet, ledger, order and item snapshots are persistent PostgreSQL state. Uniqueness and ownership constraints, idempotency identifiers and conditional stock changes complement service validation. Migrations—not Hibernate create/update—own schema. Open-in-view is false.

Foreign keys, uniqueness and indexes were reviewed against observed query paths. Existing indexes cover primary product image selection, full-text search and qualifying leaderboard purchases. No new index is recommended without additional query evidence. In particular, DB-001 is not solved by adding another primary-image index: that index is already used thousands of times unnecessarily.

## EXPLAIN evidence

`evidence/explain.sql` executes read-only query plans inside a transaction followed by ROLLBACK. `evidence/explain.txt` contains actual EXPLAIN (ANALYZE, BUFFERS) output for listing, full-text search, laptop brand facets and leaderboard aggregation.

| Query | Actual evidence | Interpretation |
|---|---|---|
| Default 24-product listing | 2,201 image probes, 6,603 image buffer hits, 7,815 total buffer hits, 7.505ms | DB-001: hydrate images after selecting the page |
| Full-text query | Planner uses existing GIN search index | No speculative replacement index justified by this fixture |
| Laptop brand facet | 140 matching products grouped into 16 brands; existing catalog indexes used | No additional index justified by this small local facet plan |
| All-time leaderboard | Existing partial purchase index used; 0.127ms at eight orders/six account fixture | Too small to claim production-scale aggregate performance |

The listing still has to filter/count/sort matching rows; page-first image hydration does not magically eliminate all O(N) work. Preserve deterministic ties and every sort/filter combination. [API-002](OPTIMIZATION_FINDINGS.md#api-002) separately requires count metadata for empty pages. These should share contract tests.

## Transaction correctness and bounded reads

Purchase concurrency/atomicity tests passed on real PostgreSQL. Preserve wallet locks and server repricing; never trust client totals to reduce SQL. Refund/order history/collection and rankings retain PostgreSQL authority. Public rankings are bounded to 50/100 and have a short local snapshot cache. Pagination exists for catalog, search, purchases and notifications. Collection lifetime aggregation is not yet backed by a high-volume measurement in this audit; test representative histories before a schema/materialized-view project.

Wishlist batch work (BE-002) can reduce round trips without a schema change. Notification generation history (BE-003) may require additive metadata; its migration/retention semantics need explicit review. Any new index proposed during implementation must list its actual query, EXPLAIN benefit and write/storage cost. Do not add a generic set of speculative indexes.

## Connections and storage

Hikari maximum is configurable, default 10; the Render blueprint sets 5. The local snapshot showed ten idle connections plus the active diagnostic connection. This reflects the configured pool, not a connection leak. Virtual threads do not justify blindly increasing database connections. No pool-size change is recommended without deployment concurrency and wait-time measurements.

At the early fixture snapshot, product table plus indexes occupied 11,239,424 bytes and product_image 1,015,808 bytes. Other relation sizes are in `database-state.txt`. These small local sizes do not predict production vacuum/bloat behavior. Sustained load, long-running transaction sampling and production EXPLAIN were not performed.
