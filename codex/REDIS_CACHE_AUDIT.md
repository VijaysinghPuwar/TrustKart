# Redis / caching audit

Redis is used for Bucket4j distributed rate buckets, short-lived OAuth state and revoked JWT session IDs. Commerce balances, orders, collection and leaderboard source data remain in PostgreSQL. Catalog/leaderboard DTO caching uses bounded in-process snapshots; this audit found no need to introduce Redis caching for everything.

## Actual key lifetimes and connections

`tk:` namespaces separate TrustKart keys. Session denylist TTL is access-token lifetime plus one minute (11 minutes with this configuration). OAuth state expires after ten minutes. Rate buckets expire after the refill horizon plus a ten-minute margin. Spring Redis operations and a dedicated Lettuce connection for Bucket4j are separately configured; do not merge them without lifecycle/protocol reasoning merely to save one connection.

The isolated Redis INFO memory snapshot reports approximately 1.65MiB logical used memory. Container/process RSS readings differ because they account for allocator/runtime memory differently; see RESOURCE_USAGE_AUDIT.md. Local Redis reported maxmemory=0 and policy=noeviction. That is the observed isolated development setting, not a recommendation to use unlimited hosted memory or a claim about production eviction policy.

## Security finding

[SEC-001](OPTIMIZATION_FINDINGS.md#sec-001) is the only P1 finding: a Redis miss is accepted as a valid session, while PostgreSQL is queried only on Redis exceptions. Deleting one synthetic revoked-session key changed the replay response from 401 to 200. This directly demonstrates the lost-key path; it does not claim that eviction was observed spontaneously in local noeviction configuration.

Keys can disappear through restart/data loss or an eviction-configured deployment. Redis documents eviction as expected behavior under configured memory policies ([official eviction reference](https://redis.io/docs/latest/develop/reference/eviction/)). Correct authorization must not infer “not revoked” from absent cache data. Use PostgreSQL authoritative session state on misses; do not cache sensitive sessions publicly or remove revocation checks for speed.

## Safe opportunities and controls

BE-001 is a candidate for a small immutable **local** category snapshot with event/TTL invalidation. Adding a Redis trip for every category lookup could erase the benefit. FE-001 reduces the size/work of the existing home cache. Public rankings already use 30-second snapshots and after-commit invalidation; measured warm endpoints did no SQL. There is no measured reason to replace that with Redis-only scores or to cache every personal rank.

Rate limiting has an existing fail-open availability policy when Redis fails. This is a reviewed architectural tradeoff, not permission to broaden bypasses; no change is recommended as a performance shortcut. Invalidation must remain after committed wallet/order/refund changes. Preserve cache/privacy separation verified by PublicCacheIT.

Redis hit-rate under sustained production traffic, hosted memory limits, restart recovery and real Google state expiry were not load-tested. The key-deletion reproduction is narrowly scoped and was run only on isolated audit data.
