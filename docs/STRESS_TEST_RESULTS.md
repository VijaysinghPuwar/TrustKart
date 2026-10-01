# Stress and concurrency test results

Controlled local tests run on October 1, 2026 against an isolated stack. Nothing here touched a hosted service,
Google, or any paid API. These are local lab numbers for one machine. They show stability and correctness under
load, not production capacity.

## Environment

| Item | Value |
|---|---|
| Machine | Apple M5, 10 cores, 24 GB RAM, macOS |
| API | Spring Boot jar, Java 21 (Homebrew OpenJDK 21.0.12), `-Xmx512m`, `dev` profile, Hikari pool 10 |
| Database | `pgvector/pgvector:pg17` container (compose project `tk-verify`, own volume) |
| Redis | `redis:8-alpine` container, same project |
| Data | Seeded demo catalog (2,201 products) plus synthetic `example.test` accounts and orders |
| Tool | A small Node 26 closed-loop load generator (no k6, wrk or hey was installed). Each virtual user sends requests back to back for a fixed time; latency is measured client-side; JVM RSS/CPU are sampled with `ps` and connections from `pg_stat_activity` every second. |

Load generator, API and database share the machine, so client and server compete for CPU. Throughput plateaus are
partly the client's own limit.

## Read load

### Public catalog and search

Endpoints in rotation: product listing (3 shapes), product detail, categories, home, laptop facets, search,
suggestions, monthly and all-time leaderboards. Each virtual user has its own client IP (`X-Forwarded-For` from
loopback, which Tomcat trusts), as real visitors would.

Final build, 15 seconds per step:

| Users | Requests | Req/s | 5xx | Network errors | 429 | p50 ms | p95 ms | p99 ms | Max ms | Peak RSS MiB | DB conns |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | 3,907 | 260 | 0 | 0 | 561 | 2.7 | 6.9 | 8.2 | 117.5 | 525 | 11 |
| 10 | 33,959 | 2,264 | 0 | 0 | 4,802 | 2.9 | 10.2 | 13.5 | 120.2 | 537 | 11 |
| 50 | 39,752 | 2,650 | 0 | 0 | 1,276 | 15.5 | 39.8 | 109.2 | 196.1 | 539 | 11 |
| 100 | 42,208 | 2,814 | 0 | 0 | 2,173 | 27.5 | 96.8 | 137.7 | 250.4 | 541 | 11 |

The 429s are expected: each user sends about 120+ searches a minute from one IP and hits the search rate limit
(120 per IP per minute). That shows the limiter working under load; no other endpoint returned 429. DB connections
are the pool of 10 plus the monitoring query.

### Authenticated reads

Each virtual user is a separately registered account with its own IP, rotating through `/me`, cart, wallet, unread
count, notifications, purchases, collection, wishlist, own leaderboard standing and sessions. Every request runs the
per-request session check added for SEC-001.

| Users | Requests | Req/s | Non-2xx | p50 ms | p95 ms | p99 ms | Peak RSS MiB |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | 3,155 | 210 | 0 | 3.8 | 7.3 | 9.5 | 495 |
| 5 | 15,656 | 1,044 | 0 | 3.8 | 8.2 | 10.5 | 496 |
| 10 | 26,144 | 1,743 | 0 | 4.4 | 10.1 | 13.2 | 496 |
| 25 | 29,869 | 1,991 | 0 | 10.6 | 20.3 | 98.9 | 496 |
| 50 | 31,270 | 2,085 | 0 | 20.9 | 41.8 | 112.6 | 496 |

**SEC-001 cost, same test, one server at a time** (the other server process was paused with `SIGSTOP` so they did
not compete):

| Build | Users | Req/s | p50 ms | p95 ms | p99 ms |
|---|---:|---:|---:|---:|---:|
| Baseline `177fb10` (Redis denylist) | 10 | 1,534 | 4.9 | 12.2 | 19.3 |
| Fixed (session table lookup) | 10 | 1,729 | 4.3 | 10.1 | 13.0 |
| Baseline `177fb10` | 50 | 2,201 | 18.6 | 47.3 | 113.4 |
| Fixed | 50 | 1,974 | 21.9 | 44.1 | 119.5 |

The two builds are within run-to-run noise of each other: the baseline's 10-user run included JIT warm-up. The extra
primary-key lookup has no measurable effect at this scale.

### Endurance

25 users on the public rotation for 180 seconds: 535,497 requests (2,975/s), 0 HTTP 5xx, 0 network errors, p50 6.6 ms,
p95 17.7 ms, p99 27.1 ms. JVM RSS went from 541 to 543 MiB (it sits at the heap ceiling plus metaspace and does not
grow), DB connections stayed at 11, and the API log had no ERROR lines.

## Write concurrency

Targeted races against real endpoints, each checked against the database afterwards, not just status codes. Each
uses fresh synthetic products and accounts so the checks do not interfere.

| # | Scenario | Result |
|---|---|---|
| A | 20 simultaneous checkouts with one Idempotency-Key | 1 order, 1 stock unit, one $100 debit; all 20 responses return that same order |
| B | 10 simultaneous different orders, wallet covers 3 | 3 succeed, 7 rejected (422); balance exactly start − 3 × price; no negative wallets |
| C | 12 shoppers buy the last 3 units at once | 3 sold, 9 rejected (409), stock 0, never negative |
| D | 10 simultaneous refunds of one order | Refunded once: one REFUND ledger row, +$250 once, stock restored once |
| E | 25 simultaneous notification syncs, 3 delivered orders | 12 milestone notifications, 0 duplicate keys, no 5xx |
| F | 15 simultaneous wallet credits with one Idempotency-Key | Credited once (+$500) |
| G | 8 simultaneous adds to an existing cart line, then 6 simultaneous first adds of another product | **Failed before the fix** (quantity 2 instead of 9; five HTTP 500s). Now 9 and 6 |
| H | 6 simultaneous orders then leaderboard standing | Leaderboard spend equals the sum of completed orders ($7,407.36) |
| I | 6 simultaneous first wishlist hearts (no list yet) | **Failed before the fix** (five HTTP 500s, five saves lost). Now one default list with all 6 |

G and I were real defects (NEW-001 and NEW-002 in [CODEX_VERIFICATION.md](CODEX_VERIFICATION.md)). Both now take a
row lock on the shopper before reading and writing. `CartConcurrencyIT` and `WishlistIT` reproduce them against
Testcontainers PostgreSQL and failed before the fix. After the fix, all 9 scenarios passed on the rebuilt API.

## Failure simulation

| Simulated failure | Observed |
|---|---|
| Redis stopped | Listing, search, registration, sessions, cart: all 200. Rate limiter fails open with one WARN per policy (no log spam). A revoked access token is still rejected (401), because revocation no longer depends on Redis. Recovered when Redis restarted |
| Redis key for a revoked session deleted | 401 (was 200 before SEC-001) |
| Malformed quote options | 400 for null, array, scalar, nested and unparseable values (null was 500 before API-001) |
| Page far past the end | Empty page with the true total (was a total of 0) |

Browser-side failure handling (API unavailable, broken images) is covered in [FINAL_VERIFICATION.md](FINAL_VERIFICATION.md).

## Limits

- One machine running client, API and database. The plateau around 2,500 to 3,000 requests per second is partly the
  load generator's own limit, so it is not the API's ceiling.
- The production deployment (a small Render instance with pool size 5, hosted Redis, Vercel's CDN in front) was not
  tested and these numbers do not predict it.
- Local Redis uses `noeviction`; real eviction was not observed, only simulated by deleting a key.
- Load was read-heavy by design. State-changing endpoints were tested with targeted races, not high-volume load.
