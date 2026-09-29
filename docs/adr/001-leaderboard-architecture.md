# ADR 001: Leaderboard architecture

Status: accepted, 2026-09-28

## Context

TrustKart ranks shoppers by virtual spend on two public boards: this month (Top 50) and all time (Top 100).
Rankings must be correct after cancellations and returns, must never reveal who a shopper is unless they choose
to, and must not depend on infrastructure the free hosting tier cannot guarantee.

## Decisions

**Order history is the only source of truth.** A rank is computed from `virtual_purchase` rows with status
`COMPLETED`. There is no separate score table to drift out of sync. Adding wallet funds never counts, because it
is not a purchase. A cancelled or returned order leaves `COMPLETED`, so it stops counting automatically.
Idempotent checkout means a replayed request cannot count twice.

**Periods are keys, not resets.** A month is a UTC calendar month (`2026-09`); all time has no bounds. Nothing is
deleted or reset at month end, so any past month can be recomputed exactly. The month-end result notification
uses this.

**Ranking in one SQL query.** `RANK()` gives tied totals the same rank (1, 2, 2, 4). `ROW_NUMBER()` with the
tie-breakers (earlier last purchase, then account id) fixes the display order and the list cut-off. A personal
rank uses the same query filtered to one account, so it matches the board and works outside the top lists.

**Who is eligible.** Only active accounts whose only role is `CUSTOMER`. Staff are excluded, and an operator can
mark an account ineligible (`leaderboard_profile.eligible`). Guest orders count once they merge into an account
at sign-in or sign-up.

**Privacy model.** Everyone ranked is shown as "Anonymous collector" until they opt in. The public name is
separate from the account name, 3 to 20 letters, numbers or underscores, unique, and cannot start with reserved
words such as `admin` or `trustkart`. A profile photo is shown only with a second opt-in. Responses never contain
email addresses, Google identities or internal ids.

**No Redis.** With a partial index on completed orders, the ranking query runs in well under a millisecond on the
current data (`EXPLAIN ANALYZE`, 0.04 ms). Each board is cached in memory for 30 seconds and evicted after any
purchase, cancellation, return, guest merge or profile change commits. Redis stays optional for rate limiting only.

**Rebuild is not needed.** Because nothing is stored besides orders and profiles, "rebuild" is just dropping the
cache (`POST /api/v1/admin/leaderboards/refresh`). `GET .../reconcile` re-sums every ranked account's orders
independently and reports any mismatch.

**Notifications stay quiet.** At most one notification per tier per month (monthly #1, Top 3, 10, 50; all-time
Top 10, 25, 100), sent only when a better tier is reached, plus one result message after each month ends.
Shoppers can turn them off with "Rank updates".

## Consequences

- The query cost grows with order volume. If it ever becomes slow, per-month totals can be materialised and
  refreshed from the same events without changing the API.
- A server restart simply rebuilds the cache on the next request.
- Running more than one API instance means each has its own 30 second cache. That is acceptable for rankings.

## Possible next steps

- A monthly Hall of Fame page showing past winners (the data already exists).
- Time-boxed ranked challenges, for example a category-specific month.
