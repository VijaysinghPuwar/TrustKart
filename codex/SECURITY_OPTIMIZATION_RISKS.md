# Security and correctness constraints for optimization

[SEC-001](OPTIMIZATION_FINDINGS.md#sec-001) is a reproduced authorization persistence weakness, not an optional speed improvement. Fix Redis-miss revocation semantics before reducing other authentication work. No P0 exploit was demonstrated. Fifteen of sixteen independent security assertions passed; the missing-key replay assertion failed. Do not interpret passing existing tests as coverage of Redis eviction/recovery.

## Verified local probes

Guest protected/session and admin reads returned 401. A normal customer received 403 for admin leaderboard access. Writes without CSRF returned 403. Negative quantities and injected client price fields returned 400. Cross-shopper cart/order/refund/notification access returned 404. A forged expected total returned 409 with server quote data. Repeated idempotency key returned the same order. Logout invalidated the cookie while its denylist entry existed; deleting only that test entry exposed SEC-001. All accounts/objects were synthetic and local.

## Required invariants by change

| Change IDs | Invariants / dangerous shortcut to avoid | Security regression evidence |
|---|---|---|
| SEC-001 | Missing cache data never proves authorization; missing/expired/revoked DB sessions rejected | Key loss, write failure/recovery, outage, valid sessions, device/password revocation |
| FE-001, BE-001 | Cache only public catalog metadata; invalidation must follow catalog changes | PublicCacheIT; no Set-Cookie/private user payload in public cache; fresh category edit |
| DB-001, API-002 | Parameterized filters, exact prices/stock/sort/count semantics | Query equivalence, injection/invalid-filter validation, out-of-range totals |
| BE-002 | Batch only lists belonging to the resolved shopper | Cross-shopper IDs and mixed duplicate/empty list fixtures |
| BE-003, UX-004 | Notification ownership, preferences, read state and exactly-once visible milestone intent | Concurrency, retention boundary, late refund/cancel, paged IDOR/read-all |
| API-001 | Validation rejects malformed values; client totals remain untrusted | 400 cases plus valid variant checkout and price manipulation tests |
| UX-001/002/003, A11Y-001/002/003 | UI changes do not invoke duplicate mutations or hide confirmation/actions | Keyboard/pointer flows, one mutation per action, server response matches visible money |
| IMG-001, QA-001 | Preserve attribution, safe static URLs, image fallback and deployed references | All manifests/variants validate; no broken cached URLs or remote tracking hosts introduced |
| QA-002 | Local test data and credentials remain isolated; do not bypass CSRF/auth to simplify CI | Real security middleware enabled, protected API tests, no production URLs/secrets |

Wallet and order transaction boundaries, ledger, stock conditions and server repricing must remain intact. Redis must not become the only source for wallet, orders, collections or leaderboard spending. Public ranking privacy/eligibility rules and after-commit invalidation must remain correct. Guest and registered ownership are separate concerns; test both.

Do not disable CSRF/CORS, validation, rate limits, audit/security logs or refresh rotation to improve benchmarks. Production cookie settings differ intentionally from plain-HTTP local dev. Public cache allowlists must not broaden to account/cart/wallet/order/notification responses. Do not expose raw errors or credentials in new performance instrumentation. The audit used request-correlated SQL without bind-value logging; its temporary debug level is not a deployment recommendation.

External Google consent/provider failures, hosted proxy trust configuration, active penetration testing, full-history secret scanning and a complete Maven vulnerability scan were outside executed verification. Their source/config and existing tests were reviewed where present. The audit does not assert absence of every security vulnerability.
