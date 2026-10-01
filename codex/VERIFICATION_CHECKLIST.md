# Reusable optimization verification checklist

Run the relevant checks after every phase, then the full matrix at the end. Verify findings still exist on the current commit first. Read canonical details rather than relying on IDs alone.

## Before editing

- [ ] Record branch, HEAD, upstream, git status and concurrent changes. Preserve existing untracked files.
- [ ] Read the relevant finding, source lines and evidence; reproduce on a local snapshot.
- [ ] Record pre-change screenshot/query/response/metric with fixture, viewport/DPR and cache state.
- [ ] Identify user-visible behavior and security/atomicity invariants; define a reversible change boundary.

## Build and automated checks

- [ ] Frontend `npm run lint`, `npm run typecheck`, `npm run format:check`, `npm test`, `npm run build`.
- [ ] Backend Java 21 `./mvnw -B -ntp verify` with real Testcontainers PostgreSQL/Redis access.
- [ ] Relevant new regression exercises actual failure behavior; avoid tests that merely mirror implementation.
- [ ] Full catalog/variant image validator after asset/catalog changes (QA-001).
- [ ] Existing Playwright using a local seeded production-build stack; preserve 22 passing cases and explain any intentional skips.
- [ ] Docker build/compose validation after infrastructure changes; CI executes the browser suite (QA-002).

## Browser and responsive checks

- [ ] Search → product/options → cart → quote → virtual checkout → receipt/tracking → collection works for guest/customer.
- [ ] Wallet funding/mode/refund totals match server authority; repeat actions do not create duplicate orders.
- [ ] Wishlist, notification history/settings, rankings/privacy, addresses/security/account and 404 remain usable.
- [ ] All affected routes at 390/768/1024/1440/1920; core flows at all 14 sizes in RESPONSIVE_AUDIT.md.
- [ ] Overlay rectangles stay in viewport (UX-001); tray does not cover purchase controls (UX-002); full money/mode visible (UX-003).
- [ ] More than 30 notifications remain reachable without unbounded fetching (UX-004).
- [ ] Compare screenshots, image sharpness, premium dark hierarchy, typography and spacing; do not hide useful features.
- [ ] Check loading/empty/error/long-value states and settled animations, not only successful base pages.

## Accessibility

- [ ] Re-run axe at phone/desktop widths; A11Y-001 inline links have a non-color cue.
- [ ] Tab/Shift+Tab/Enter/Space/Escape and arrow flows work; A11Y-002 tabs follow a consistent keyboard model.
- [ ] A11Y-003 recent history can be removed without a pointer; focus/announcement remain understandable.
- [ ] Modal opening/closing/focus restoration, visible focus, headings, labels/errors and reduced motion work.
- [ ] Inspect dark/light themes, zoom and physical-device/assistive-technology behavior where the change warrants it; record limits.

## Network, backend and security

- [ ] Capture console/page errors and failed/duplicate requests; bounded retry behavior still excludes ordinary 4xx.
- [ ] Search retains the current responsive debounce/cancellation; no unnecessary AI calls or slow typing feedback.
- [ ] FE-001 home bytes/cache-rebuild work decrease with identical visible content.
- [ ] API-001 malformed options return 400; API-002 empty pages preserve truthful total metadata.
- [ ] DB-001 image lookup loops track page size; returned IDs/sort/count/filter/price fields are identical.
- [ ] BE-001 metadata cache invalidates on catalog change; BE-002 queries stay bounded across list counts.
- [ ] BE-003 unchanged polls after retention produce zero new milestone INSERTs; late/refund/concurrent events remain correct.
- [ ] SEC-001 revoked sessions remain rejected after lost Redis keys and outage/recovery; valid sessions still work.
- [ ] Guest/customer/admin separation, cart/order/notification ownership, CSRF, price tampering, negative quantity and idempotency tests pass.
- [ ] Public cache does not contain personal data/cookies; PostgreSQL stays authoritative for commerce.
- [ ] Inspect runtime logs for retry storms, unexpected SQL growth and sensitive data; remove audit-only DEBUG from launch arguments.

## Assets, resources and closeout

- [ ] IMG-001 preserves identical visual content, attribution, variant correctness and old cached asset URL compatibility.
- [ ] Record actual bundle/transfer/query/memory improvements with comparable conditions; no invented percentages/scores.
- [ ] Do not blindly shrink Hikari/JVM limits, disable security, lower all image quality, virtualize every page or memoize every component.
- [ ] Review Git diff/status for unrelated changes, secrets, generated build files and concurrent edits.
- [ ] Update resolved IDs with before/after evidence and any remaining risks; no commit/push/deploy without applicable authorization.

## Finding completion ledger

| Finding | Reproduced on current commit | Fix reviewed | Targeted regression | UI/resource/security checks | Status |
|---|---|---|---|---|---|
| [SEC-001](OPTIMIZATION_FINDINGS.md#sec-001) | [ ] | [ ] | [ ] | [ ] | Not implemented by this audit |
| [UX-001](OPTIMIZATION_FINDINGS.md#ux-001) | [ ] | [ ] | [ ] | [ ] | Not implemented by this audit |
| [UX-002](OPTIMIZATION_FINDINGS.md#ux-002) | [ ] | [ ] | [ ] | [ ] | Not implemented by this audit |
| [UX-003](OPTIMIZATION_FINDINGS.md#ux-003) | [ ] | [ ] | [ ] | [ ] | Not implemented by this audit |
| [UX-004](OPTIMIZATION_FINDINGS.md#ux-004) | [ ] | [ ] | [ ] | [ ] | Not implemented by this audit |
| [A11Y-001](OPTIMIZATION_FINDINGS.md#a11y-001) | [ ] | [ ] | [ ] | [ ] | Not implemented by this audit |
| [A11Y-002](OPTIMIZATION_FINDINGS.md#a11y-002) | [ ] | [ ] | [ ] | [ ] | Not implemented by this audit |
| [A11Y-003](OPTIMIZATION_FINDINGS.md#a11y-003) | [ ] | [ ] | [ ] | [ ] | Not implemented by this audit |
| [API-001](OPTIMIZATION_FINDINGS.md#api-001) | [ ] | [ ] | [ ] | [ ] | Not implemented by this audit |
| [API-002](OPTIMIZATION_FINDINGS.md#api-002) | [ ] | [ ] | [ ] | [ ] | Not implemented by this audit |
| [FE-001](OPTIMIZATION_FINDINGS.md#fe-001) | [ ] | [ ] | [ ] | [ ] | Not implemented by this audit |
| [DB-001](OPTIMIZATION_FINDINGS.md#db-001) | [ ] | [ ] | [ ] | [ ] | Not implemented by this audit |
| [BE-001](OPTIMIZATION_FINDINGS.md#be-001) | [ ] | [ ] | [ ] | [ ] | Not implemented by this audit |
| [BE-002](OPTIMIZATION_FINDINGS.md#be-002) | [ ] | [ ] | [ ] | [ ] | Not implemented by this audit |
| [BE-003](OPTIMIZATION_FINDINGS.md#be-003) | [ ] | [ ] | [ ] | [ ] | Not implemented by this audit |
| [QA-001](OPTIMIZATION_FINDINGS.md#qa-001) | [ ] | [ ] | [ ] | [ ] | Not implemented by this audit |
| [QA-002](OPTIMIZATION_FINDINGS.md#qa-002) | [ ] | [ ] | [ ] | [ ] | Not implemented by this audit |
| [IMG-001](OPTIMIZATION_FINDINGS.md#img-001) | [ ] | [ ] | [ ] | [ ] | Not implemented by this audit |
