# Commands, tests and verification results

Audit snapshot: `177fb10`, copied with git archive into `/private/tmp/trustkart-audit-20260930`. Installed frontend node_modules were reused through a symlink; no npm install/lockfile rewrite was needed. Java 21 came from `/opt/homebrew/opt/openjdk@21`; system `java` had no configured runtime. Local Node was v26.8.1 (CI specifies Node 22). Tests used local Testcontainers or the isolated audit compose stack, never production.

## Counted automated results (final runs; retries not double-counted)

| Suite | Executed | Passed | Failed | Skipped |
|---|---:|---:|---:|---:|
| Backend unit (Surefire) | 24 | 24 | 0 | 0 |
| Backend integration (Failsafe) | 112 | 112 | 0 | 0 |
| Frontend Vitest (two utility files) | 11 | 11 | 0 | 0 |
| Existing Playwright | 22 | 22 | 0 | 8 |
| Independent security assertions | 16 | 15 | 1 | 0 |
| **Total executed tests/assertions** | **185** | **184** | **1** | **8** |

Existing repository tests: **169 passed, zero failed**. Eight skipped Playwright project cases avoid repeating viewport-specific checks in the mobile project; they were not hidden failures. Independent failing assertion: SEC-001, old revoked cookie unexpectedly accepted after its Redis key was removed. This behavior exists in the audited application; no source was changed. Recommended fix/test cases are in the canonical finding.

The 207 route/viewport observations, 54 axe scans, 30 performance loads, 105 endpoint timing samples and targeted interaction/data probes are **separate evidence**, not added to that automated test count. Axe found four scan violations at two unique link targets. Manual/API probes also demonstrated API-001/002 and UX/resource defects; they are not counted as existing suite failures.

## Command/test matrix

Commands are shown with their meaningful arguments; output redirects point to `codex/evidence/`. Temporary local env wrappers supplied random audit credentials, loopback ports and Java/Docker paths. Do not reuse root .env or production connection strings.

| Area | Command/test | Result | Evidence / notes |
|---|---|---|---|
| Repository baseline | `git status --short`, `git branch --show-current`, `git log`, `git ls-files` | Inspected | Tracked source clean; pre-existing scripts/renders retained |
| Requested local update | `git fetch origin`; `git merge --ff-only origin/trustkart-rebuild` | Already up to date | HEAD/upstream 177fb10; origin/main different history but empty tree diff |
| Snapshot | `git archive HEAD` extracted into temporary audit directory | PASS | Builds isolated from concurrent source work |
| Frontend build | `npm run build` | PASS | frontend-build.log; tsc -b plus Vite |
| Frontend lint | `npm run lint` | PASS | frontend-lint.log |
| Frontend typecheck | `npm run typecheck` | PASS | frontend-typecheck.log |
| Frontend format | `npm run format:check` | PASS | frontend-format.log; read-only check |
| Frontend tests | `npm test` | 11 PASS | frontend-test.log |
| Backend initial attempt | `./mvnw -B -ntp verify` with Java 21 | Infrastructure failure | backend-verify.log: sandbox denied Docker; not an application test regression |
| Backend authorized retry | `./mvnw -B -ntp verify` with Docker access | 136 PASS, build success | backend-verify-local.log; 16.740s reported Maven duration |
| Compose validation | `docker compose --env-file <temporary audit.env> config --quiet` | PASS | Existing compose used with audit-local values |
| Local infrastructure | `docker compose --project-name codex-trustkart-audit --env-file <temporary audit.env> up -d postgres redis` | PASS | New isolated ports 15433/16380; existing containers untouched |
| API startup | Java 21 `-Xmx512m -jar target/trustkart-0.1.0-SNAPSHOT.jar` with temporary SQL DEBUG arguments | PASS | backend-runtime.log; API localhost:18080; seed 2,201 products |
| Frontend startup | `VITE_API_PROXY_TARGET=http://localhost:18080 npm run preview -- --host 127.0.0.1 --port 15173` | PASS | frontend-runtime.log; production output |
| Existing browser suite | `E2E_BASE_URL=http://127.0.0.1:15173 npx playwright test --workers=2 --output=<codex/evidence/playwright-results>` | 22 PASS, 8 skipped | playwright-existing.log |
| Initial visual verification | `npx --yes agent-browser --session trustkart-audit` open/snapshot/screenshot/errors | Completed | Named screenshots; early seed-start captures excluded from steady-state conclusions |
| Route/axe harness | `node codex/tools/browser-audit.cjs` | 207 captures, 54 scans after resume | route-matrix.json, accessibility.json; networkidle timing limitations below |
| Performance | `node codex/tools/performance.cjs` | 30 measured cold loads | performance.json; zero captured page errors/duplicate APIs |
| API timing/probes | `node codex/tools/api-measure.cjs` | 21×5 timings; 2 defects reproduced | api-measurements.json; API-001/002 |
| Security probes | `node codex/tools/security-check.cjs` | 15/16 assertions PASS | security-checks.json; SEC-001 |
| UI keyboard/overlays | `node codex/tools/interactions.cjs`; `node codex/tools/ui-confirm.cjs` | Issues and positive checks recorded | interactions.json and settled confirmation |
| Bounded-data probes | `node codex/tools/bounded-data.cjs` | BE-002/003, UX-004 reproduced | bounded-query-counts.json; synthetic local orders only |
| Existing image validation | `python3 scripts/validate_images.py` | PASS: 113 checked, 25 warnings | image-validation.log; limited scope QA-001 |
| Expanded assets | `python3 codex/tools/assets.py` | 2,201 primary metadata records, no validation errors | assets.json / asset inventory / duplicates |
| SQL plans | `psql` in isolated container, `evidence/explain.sql` | PASS, read-only plans | explain.txt; explicit ROLLBACK |
| Production dependency scan | `npm audit --omit=dev --audit-level=high --json` | 0 vulnerabilities reported | npm-audit.json |
| Docker build | `docker build -t codex-trustkart-audit:177fb10 backend` (snapshot context) | PASS | docker-build.log; 459MB displayed image size |
| Resources | `docker stats --no-stream`, `docker image ls`, Redis `INFO memory`, scoped `ps` | Measured | Resource evidence text files |
| Lighthouse availability | `npx --no-install lighthouse --version` | Not available | Initial sandbox registry error; authorized retry required uncached lighthouse@13.5.0 and canceled without install |
| Report generation/QC | `python3 codex/tools/write_reports.py` and QC checks | Documentation only | No application optimization applied |

## Failures and limits, without silent fixes

The first Maven attempt failed because sandboxed execution could not reach the Docker daemon. The authorized retry passed all tests; do not log this as a product defect. Browser automation initially hit an unbounded networkidle wait after 83 cases; it resumed with bounded waits. Four bounded waits timed out while captured route headings/data still rendered. No fix was made to application loading logic on that basis.

Initial browser navigation happened before seeding completed and produced transient unavailable/empty states. Final route/measurement passes waited for the seeded catalog; those early screenshots are not used as steady-state UI findings. Filter/wallet animation timing initially looked suspicious; settled checks confirmed normal Escape and geometry. Lighthouse was unavailable, so scores/INP remain unmeasured. Some shell diagnostics needed ordinary sandbox/Docker approval retries; these are tooling constraints, not test failures.

## Feature-to-test map

| Feature | Existing exercised coverage | Independent audit / remaining gap |
|---|---|---|
| Password sessions / Google | AuthIT, GoogleSignInIT | Local ownership/role/CSRF probes; SEC-001 missing Redis-loss case; no real external Google consent |
| Wallet / purchase / idempotency | VirtualCommerceIT, PurchaseConcurrencyIT, PurchaseAtomicityIT | Forged total/client price/negative quantity/idempotency probes; preserve all atomicity tests |
| Variants / addresses | VariantCheckoutIT, AddressIT | Product/checkout screenshots; malformed quote null missing case |
| Tracking / refunds / notifications | OrderTrackingIT and commerce integration flows | Timestamp/ownership review; >200 notification churn and >30 navigation gaps |
| Catalog / search / cache | CatalogApiIT, QueryInterpreterTest, PublicCacheIT, StockStatusTest | SQL plans, payload/network/search typing; out-of-range total gap |
| Leaderboard / privacy | LeaderboardIT, rankings.spec.ts | Guest/admin/customer probes; keyboard tabs missing |
| Collection | Commerce integration + AchievementsTest, shopping.spec.ts | Acquired-product UI; large lifetime collection not stress-tested |
| Cart / checkout / wallet UI | shopping.spec.ts at two projects | Complete local flow passed; overlay collision not covered by suite |
| Responsive | responsive.spec.ts | 207 observations; existing root-overflow test misses clipped overlays and truncated money |
| Frontend utilities | Money/options Vitest tests | No broad React component interaction suite demonstrated |
| Assets | validate_images.py | Expanded 2,088 products absent from current CI inputs |

The initial Java compile emitted two possible-this-escape warnings for CatalogService Memo suppliers (lines 92/93); they are retained in backend-verify.log. No runtime initialization failure was reproduced, so these warnings were not promoted to an optimization finding.

No production E2E, sustained load/CPU profiler, Maven CVE scan, full-history gitleaks execution, screen reader, Safari/Firefox or hosted resource benchmark was performed. CI configuration for these available checks was inspected rather than falsely reported as executed.

## Audit closeout

The named audit browser session, temporary Java API and Vite preview processes were stopped. Only `codex-trustkart-audit-postgres-1` and `codex-trustkart-audit-redis-1` were stopped; existing unrelated containers/processes were not stopped. The temporary snapshot, stopped isolated fixture containers/volume and local audit Docker image were retained for reproducibility; they consume disk but no running application CPU. Final Git checks showed no tracked or staged application diff, with only `codex/` plus pre-existing `scripts/renders/` untracked. No commits, pushes or deployments occurred.
