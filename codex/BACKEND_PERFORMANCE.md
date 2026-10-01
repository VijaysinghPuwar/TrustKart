# Backend performance audit

Java 21 / Spring Boot 4.1.1 compiled, packaged and passed Maven verify: 24 unit and 112 integration tests. Controllers, services, repositories, transactional commerce, external OAuth clients, scheduled work and serialization paths were inspected. Runtime SQL was enabled only by audit process arguments and recorded with request IDs; application logging files were not changed.

## Measured read-path waste

- [DB-001](OPTIMIZATION_FINDINGS.md#db-001): page-of-24 listing performs 2,201 primary-image probes before pagination.
- [BE-001](OPTIMIZATION_FINDINGS.md#be-001): category metadata is queried/rebuilt on repeated search/list/suggest calls despite stable categories.
- [BE-002](OPTIMIZATION_FINDINGS.md#be-002): wishlist reads scale 4/6/8 statements for 1/2/3 populated lists.
- [BE-003](OPTIMIZATION_FINDINGS.md#be-003): retention causes 40 inserts plus a delete on each unchanged poll in a 60-order fixture.
- [FE-001](OPTIMIZATION_FINDINGS.md#fe-001): home cache rebuild queries tile groups the client discards.

API-001/002 are correctness defects, not timing optimizations. Warm endpoint medians were 1.62–11.71ms on this local fixture; do not portray these as production slowness. The purpose of the structural work is to remove demonstrated unnecessary work while preserving behavior.

## Transaction and object graph review

Wallet, purchase, ledger, inventory and cart updates share the transaction boundary required for correctness. The integration suite exercises duplicate requests, concurrent stock purchase, rollback, variants, refunds and ownership. Keep deterministic inventory lock/update order, authoritative pricing and idempotency. No external AI/network call was found inside the purchase path. Moving these checks outside the transaction would be an unsafe optimization.

Catalog cards use JDBC projections and explicit image selection rather than sending entity graphs. JPA open-in-view is disabled and default batch fetch size is 50. Detail lookup uses bounded explicit relationships; no whole review/question tree exists. Six statements on a detail request and thirteen on the fixture collection are recorded observations, not automatically N+1 findings. Wishlist N+1 was confirmed by increasing list count. Avoid changing every fetch strategy based only on the presence of JPA.

## HTTP clients, dependencies and logs

Google OAuth is implemented through Spring's configured OAuth client flow; no AI provider client, repeated embedding call or semantic retry loop exists. Actual external Google response timings/failure behavior were not exercised. Security/resource-server/OAuth, PostgreSQL/Flyway, Redis/Bucket4j, Actuator, OpenAPI and Bouncy Castle dependencies have concrete roles. The cache starter is declared while caching largely uses custom Memo snapshots; absence of annotations alone is insufficient proof that removing the starter has a useful benefit. No package removal is a prioritized finding.

Production config does not enable SQL DEBUG; structured prod logging is configured and error responses exclude stack traces. The audit null-options probe generated an internal error stack trace (API-001). Audit SQL logs must not be used as the proposed normal production logging level. No broad request-body/password logging optimization is recommended.

## Background work

Guest cleanup is scheduled daily at 03:17. Demo inventory restock is every 15 minutes with a one-minute initial delay. Tracking derives state from order timestamps; notifications materialize due messages on reads. No per-order scheduler or one-second poll was found. Preserve the simple tracking design; fix retention dedupe instead of adding a message broker or WebSocket system.

Read resource and SQL details in RESOURCE_USAGE_AUDIT.md and DATABASE_AUDIT.md. Sustained backend CPU profiling, production workload replay and hosted memory-limit stress testing were not performed.
