from pathlib import Path
import json,statistics,collections
R=Path(__file__).resolve().parents[1]; E=R/'evidence'; REPO=R.parent
B='backend/src/main/java/com/vijaysinghpuwar/trustkart/'
F='frontend/src/'
findings=[]
def add(id,title,priority,category,route,files,evidence,repro,current,expected,user,resource,cause,change,safe,risk,ux,implement,test,bug=True):
 findings.append(dict(id=id,title=title,priority=priority,category=category,route=route,files=files,evidence=evidence,repro=repro,current=current,expected=expected,user=user,resource=resource,cause=cause,change=change,safe=safe,risk=risk,ux=ux,implement=implement,test=test,confidence='HIGH',bug=bug))
add('SEC-001','Revoked access tokens become valid after Redis denylist key loss','P1','Security / Redis','Authenticated API after logout',[(B+'security/SessionRevocation.java',40),(B+'security/JwtConfig.java',1)],
 '[security-checks.json](evidence/security-checks.json): logout 204; replay 401; delete only the synthetic audit session’s Redis revocation key; replay 200. PostgreSQL revocation remains recorded.',
 'On an isolated stack register a disposable account, retain its access cookie, log out, replay GET /api/v1/me/sessions, delete that session’s tk:revoked-sid key, then replay the same cookie. See tools/security-check.cjs. Never flush a shared Redis.',
 'Redis false/missing key is treated as a live session. PostgreSQL is consulted only when Redis throws.',
 'Logout remains effective after eviction, data loss, or a failed denylist write followed by Redis recovery.',
 'An already-issued revoked access token can regain protected access until token expiry (configured access lifetime 10 minutes). This requires possession of that token and key loss; it is not unauthenticated account takeover.',
 'Correctness fix may add indexed session reads; measure them rather than preserving this unsafe shortcut.',
 'A negative cache lookup is being used as authoritative proof of non-revocation.',
 'Consult authoritative session state on a denylist miss, or design another scheme that cannot interpret lost cache state as authorization.',
 'PostgreSQL already stores session revocation and is already used by the exception fallback.',
 'Medium: every authenticated request is affected; database failure must have explicit fail-closed behavior.',
 'Avoid sign-in loops or unexplained account logout; preserve valid sessions and short access/rotating refresh semantics.',
 'Verify the reproduction still fails; make the missing-key path use the existing indexed session check; retain Redis positive hits only as an optimization. Handle unknown/expired sessions consistently.',
 'Add integration cases for key deletion, Redis write failure then recovery, Redis outage, unknown session, logout/device revoke/password change, and valid sessions. Repeat ownership/role/CSRF tests and measure authenticated query count.')
add('UX-001','Mobile notification panel opens partly outside the viewport','P2','Responsive UI','Header notification bell',[(F+'components/layout/NotificationBell.tsx',129)],
 '[interactions.json](evidence/interactions.json) reports x=-34 at both 320px and 390px; [320px screenshot](screenshots/notification-open-320.png) shows clipped title/body/link.',
 'At 320×568 or 390×844 open the header bell. Inspect the panel’s left edge.',
 'The panel width is viewport-bounded but its right edge is positioned relative to the bell, leaving its left side off-screen.',
 'The entire panel and its controls stay inside the viewport.',
 'Notification text and navigation are cut off.', 'No meaningful CPU/transfer effect.',
 'absolute right-0 anchors a nearly viewport-wide panel to a bell that is left of the cart.',
 'Use viewport-constrained positioning at phone widths; preserve the desktop anchored panel.',
 'A layout-only fix can retain fetching, notification state and styling.',
 'Low to medium: sticky header, scroll and focus behavior need checks.',
 'Do not replace a quick preview with a confusing full-screen interruption or lose Escape/close behavior.',
 'Verify geometry before changing it; use a shared positioned overlay or a mobile-specific placement with a 12px inset and bounded height.',
 'Open empty/populated panels at 320,390,430,768,1440; assert rect.left>=0 and right<=innerWidth; verify Escape, outside click, keyboard link activation and focus return.')
add('UX-002','Compare tray covers the mobile product purchase bar','P2','Responsive UI','/p/:slug with a selected comparison item',[(F+'components/commerce/CompareTray.tsx',26),(F+'features/product/ProductPage.tsx',248)],
 '[ui-confirm.json](evidence/ui-confirm.json): at 390×844 the tray overlaps the sticky bar by 45px and intercepts its button center; [screenshot](screenshots/compare-tray-overlap-390.png).',
 'Open a product on a phone viewport, add it to comparison, then scroll until the sticky purchase bar is shown.',
 'Compare tray z-30 bottom-5 sits over the product bar z-20 bottom-0.',
 'Both comparison and primary purchase actions remain visible and operable.',
 'The sticky purchase action is obstructed; an in-page action still exists above.', 'No material resource change.',
 'Independent fixed overlays do not reserve space for one another.',
 'Coordinate overlay placement using the actual occupied height or a shared bottom-action layout.',
 'Presentation can change independently of cart/checkout authority.',
 'Medium: long names, safe-area insets and tray wrapping vary in height.',
 'Do not hide comparison or remove convenient product buying controls.',
 'Verify overlap; make the tray and bar share an explicit stacking/spacing contract, including the tray’s wrapped state.',
 'Test one to four compared products, long labels, clear/remove, 320–430 widths, bottom safe-area and scroll positions; click both actions and verify no accidental purchase/navigation.')
add('UX-003','Tablet account cards truncate wallet mode and monetary value','P2','Responsive UI','/account at 768px',[(F+'features/account/AccountOverview.tsx',45),(F+'features/account/AccountOverview.tsx',107),(F+'features/account/AccountLayout.tsx',1)],
 '[account-768.png](screenshots/account-768.png): “∞ Unli…” and “$450,0…” in summary cards beside the account sidebar.',
 'At 768×1024 use an account with Unlimited mode and a six-digit collection value; open Account.',
 'Three summary columns occupy the narrow space remaining beside a 220px sidebar; value spans truncate.',
 'Wallet mode and full amount can be read without guessing.',
 'Critical commerce information is ambiguous.', 'No measurable resource cost.',
 'Viewport sm:grid-cols-3 ignores available content width; truncate hides essential values.',
 'Use fewer columns at this content width, or a container-aware grid; let essential numbers remain readable.',
 'Retains the same information and design tokens without changing calculations.',
 'Low: longer localized labels and very large balances need attention.',
 'Avoid shrinking typography until money becomes hard to read.',
 'Verify with the same large-value fixture, then adjust summary layout breakpoints/available-width rules.',
 'Inspect 600,768,820,1024,1440; finite/unlimited wallets, zero and large totals, 200% zoom; compare displayed amounts with the API.')
add('UX-004','Full notifications page has no way to reach records beyond the first 30','P2','Navigation / pagination','/account/notifications',[(F+'data/notifications.ts',27),(F+'features/account/NotificationsPage.tsx',52)],
 '[bounded-data.json](evidence/bounded-data.json): endpoint returns 30 items with totalItems=200. Hook fixes size=30 and page renders only that response with no page/load-more control.',
 'Populate more than 30 notifications in the isolated fixture, open the full notifications page, and try to access older retained entries.',
 'Only the first page can be viewed although older retained records exist.',
 'All retained notifications are reachable through bounded pagination or incremental loading.',
 'Older order/delivery updates become inaccessible from the notification page.',
 'Keep bounded requests; do not fix by loading all history on every header render.',
 'The preview and full-page hook share a fixed first-page query without pagination state.',
 'Add page-aware query keys and an accessible paging control to the full page; keep the bell preview small.',
 'Backend already returns page/size/totalItems.',
 'Medium: marking records read changes counters while navigating pages.',
 'Maintain scroll/focus and avoid jumping to page one after every read action.',
 'Verify >30 fixture; parameterize the full-page hook, retain prefix invalidation, render count and navigation with loading/error states.',
 'Test 0,1,30,31,200 entries, last-page deletion/read-all, back navigation, keyboard and 320px layout; ensure requests remain bounded and shopper-scoped.')
add('A11Y-001','Two inline links rely on color alone','P2','Accessibility','/rankings and /account/purchases/:id',[(F+'features/rankings/RankingsPage.tsx',260),(F+'features/account/ReceiptPage.tsx',139)],
 '[accessibility.json](evidence/accessibility.json): link-in-text-block violations at 1440 and 390 for “Change” and “store policy”; axe reports surrounding-text contrast 1.23:1.',
 'With the audit account open rankings or a receipt in dark theme; inspect the inline links visually and run axe.',
 'These inline links lack a persistent non-color distinction from surrounding text.',
 'Readers can identify links without relying solely on hue.',
 'Reduced link discoverability for people with color-vision differences.', 'Negligible resource effect.',
 'Inline anchors inherit text styling without an underline or equivalent cue.',
 'Add a consistent persistent underline/non-color treatment to these links.',
 'Uses existing palette and preserves destination and copy.', 'Low; verify contrast and hover/focus in both themes.',
 'Do not underline every button or alter the premium palette globally.',
 'Verify the two violations still exist; reuse the project’s inline-link styling rather than a new one-off color.',
 'Run axe on both routes at phone/desktop widths and visually inspect normal/hover/focus in dark/light themes.')
add('A11Y-002','Leaderboard tabs lack expected arrow-key navigation','P2','Accessibility','/rankings period selector',[(F+'features/rankings/RankingsPage.tsx',307)],
 '[interactions.json](evidence/interactions.json): ArrowRight leaves focus/selection on This Month; Tab then Space activates All Time. Both buttons use role=tab.',
 'Focus This Month, press ArrowRight/ArrowLeft, then compare Tab+Space behavior.',
 'The widget exposes tab semantics but behaves as separate ordinary buttons.',
 'Tab enters the active tab; arrows navigate tabs, with a consistent manual/automatic activation model.',
 'Keyboard and screen-reader users encounter an unexpected tab interaction; the content is still reachable with Tab+Space.', 'Negligible resource effect.',
 'Missing roving tabIndex and arrow-key handler.',
 'Complete the ARIA tabs keyboard pattern using existing tab/panel IDs.',
 'No data or ranking algorithm change is required.', 'Low; avoid stealing page-level arrow keys outside the widget.',
 'Do not unexpectedly fetch repeatedly while holding an arrow key.',
 'Verify behavior; implement left/right wrap and Home/End if supported, selected tab focusability and correct aria-controls/labelledby.',
 'Test Tab, Shift+Tab, Left/Right, Enter/Space and visible focus; verify selected panel and bounded query behavior.')
add('A11Y-003','Recent-search removal is pointer-only','P2','Accessibility','Smart Search recent suggestions',[(F+'components/search/SmartSearch.tsx',302)],
 '[ui-confirm.json](evidence/ui-confirm.json) records the labeled Remove macbook button with tabIndex=-1; source binds removal only to onMouseDown and the input keyboard handler provides no equivalent removal command.',
 'Submit a search, refocus the empty search input, navigate recent suggestions using only the keyboard, and attempt to remove one.',
 'A mouse user can remove a recent item, but the button cannot be tabbed to or activated from the combobox keyboard flow.',
 'Recent history can be managed using keyboard controls with understandable feedback.',
 'Keyboard users cannot selectively remove stored search history.', 'No material resource change.',
 'Preventing input blur for pointer selection also removes a usable keyboard action.',
 'Provide an explicit accessible history-management action or a documented Delete command for the active recent option.',
 'Only browser-local recent-search state changes.',
 'Medium: nested controls in listbox options can confuse assistive technology.',
 'Preserve existing ArrowDown/Enter selection, Escape closing, fast suggestions and focus.',
 'Verify pointer-only behavior; choose a coherent combobox/history interaction rather than simply adding nested tabbable buttons.',
 'Test recent-item deletion by keyboard and pointer, announcement/focus after deletion, last item, and Enter still navigating to the selected result.')
add('API-001','JSON null in quote options produces HTTP 500','P2','API validation','GET /api/v1/checkout/quote',[(B+'purchase/PurchaseController.java',58)],
 '[api-measurements.json](evidence/api-measurements.json) records the malformed-options probe; runtime stack trace points to parsed.size() after decoding null.',
 'With a local shopper call /api/v1/checkout/quote?productId=1&options=null with normal valid session context.',
 'Valid JSON null deserializes to a null Map and triggers an uncaught NullPointerException.',
 'Invalid options return a structured 400 response without server-error logging.',
 'Malformed links/clients receive an incorrect server error; no successful purchase or pricing bypass was demonstrated.',
 'Unnecessary stack trace and potential transient-error client retries.',
 'The parser validates map length/entries before rejecting a null result.',
 'Reject null and malformed/non-object option values through the normal request-validation exception path.',
 'Does not alter accepted option maps or server pricing.', 'Low; distinguish empty/omitted options from explicit null.',
 'Keep actionable validation errors rather than a generic failure screen.',
 'Verify reproduction; add explicit parser validation before dereferencing the map, preserving key/value limits.',
 'Test absent, empty, null, array, scalar, nested/wrong-type values, too many/long options and valid priced variants; assert 400 vs 200 and no writes.')
add('API-002','Out-of-range product pages report an incorrect zero total','P2','API correctness','GET /api/v1/catalog/products?page=9999',[(B+'catalog/infra/ProductSearchRepository.java',102)],
 '[api-measurements.json](evidence/api-measurements.json): page 0 reports 2201 products; out-of-range page reports zero total with an empty items array.',
 'Read page 0 and page 9999 with the same size/filter; compare totalItems.',
 'Total is initialized to zero and populated only by a returned row from count(*) OVER().',
 'Empty pages retain the true filtered total, or the API explicitly normalizes/rejects invalid page positions.',
 'Clients can incorrectly conclude that no products match after pagination/filter changes.',
 'A correctness-preserving count strategy may add a bounded query on empty pages.',
 'Window-count metadata disappears with an empty result set.',
 'Return count independently when a page is empty, or combine a counted relation and page rows without dropping metadata.',
 'Preserves filtering and the documented page contract.',
 'Medium: coordinate with DB-001 and search fallback; avoid changing ranking or SQL parameterization.',
 'Do not reset user filters or claim no results solely because the page is beyond the end.',
 'Verify issue and decide the page contract; implement count handling alongside page-first hydration where appropriate.',
 'Test empty catalog, genuine zero matches, last valid page, one-past-last, huge page and filter changes; verify search does not relax solely due to an out-of-range offset.')
add('FE-001','Homepage builds and transfers collection tiles that are never rendered','P2','Frontend / payload / backend','/',[(F+'features/home/HomePage.tsx',26),(B+'catalog/application/CatalogService.java',67),(B+'catalog/application/CatalogService.java',380)],
 '[payload-_catalog_home.json](evidence/payload-_catalog_home.json) has 11 tiles; UI displays 6, or 5 with recent history. The last 5 contain 20 cards and serialize to 15,331 compact JSON bytes; the tile builder queries each separately.',
 'Load a fresh homepage, compare the 11 returned tiles with rendered collection sections, then repeat with recent browsing history.',
 'At least five editorial tile payloads and their cache-rebuild queries do not contribute to the current homepage.',
 'Home-specific data reflects visible collections while all catalog/collection routes remain accessible.',
 'Same shopping content can arrive with less unused data.',
 'At least 20 unused cards/15,331 JSON bytes per home payload; five avoidable product tile queries per home-cache rebuild. No percentage latency promise.',
 'Backend HOME_TILES grew beyond the UI’s fixed maximum.',
 'Agree a six-tile home contract, or explicitly request only visible home tiles; leave collection browsing intact.',
 'The excluded home tiles are not rendered; product imagery and existing visible shelves need not change.',
 'Low to medium: future clients/curation may depend on the larger DTO.',
 'Preserve recent-history tile placement, premium richness and category discoverability.',
 'Verify visible ordering and payload; limit work before querying/building unused tile cards, then keep client display logic consistent.',
 'Compare home screenshots with/without recent history at phone/tablet/desktop, payload bytes and cache-cold query counts; verify all collection links still work.',False)
add('DB-001','Product listing hydrates primary images before limiting the page','P2','PostgreSQL query shape','Catalog listing, shared search repository',[(B+'catalog/infra/ProductSearchRepository.java',29)],
 '[explain.txt](evidence/explain.txt): 24-row default listing performs 2,201 primary-image LATERAL probes, 6,603 image buffer hits, 7,815 total buffer hits; execution 7.505ms on the local warm database.',
 'Run the read-only EXPLAIN (ANALYZE, BUFFERS) listing in evidence/explain.sql against the 2,201-product audit seed.',
 'Image lookup runs for the full matching set before count/window/sort/limit returns 24 cards.',
 'Filter/count/order/page product identifiers first, then hydrate primary images for returned cards.',
 'Potential faster browsing as catalog grows; current local latency is small.',
 'Measured 2,201 probes for 24 returned products; expected benefit is reduced database work, not a claimed speedup percentage.',
 'A LATERAL join is placed inside the full result relation.',
 'Refactor query shape so image joins occur after pagination, maintaining count metadata and deterministic ordering.',
 'An appropriate image(product_id,sort_order) index already exists; this targets unnecessary work instead of speculative indexes.',
 'Medium: relevance, price/discount sort, nulls and filter equivalence must remain exact.',
 'No missing/wrong thumbnail or changed product order should result.',
 'Verify current plan; build a page relation/CTE or equivalent planner-stable query, coordinate API-002, and compare returned IDs/card fields.',
 'EXPLAIN default and filtered/text listings; assert image-loop count tracks page size, compare counts/sorts/ties, run CatalogApiIT and browse screenshots.',False)
add('BE-001','Catalog requests repeatedly reload and rebuild stable category metadata','P2','Backend / query reuse','Search, suggestions, listing, facets',[(B+'catalog/application/CatalogService.java',105),(B+'catalog/application/CategoryTree.java',1)],
 '[api-query-counts.json](evidence/api-query-counts.json) and request-correlated SQL logs: repeated listing/search/suggestion requests each include category.findAll plus product SQL. tree() reconstructs from all 112 categories every invocation; existing DTO cache does not cache this tree.',
 'Request the same suggestion/listing repeatedly with SQL logging; observe the category SELECT despite unchanged catalog metadata.',
 'Immutable-in-practice seed metadata is loaded/mapped repeatedly.',
 'Reuse an immutable category snapshot with bounded freshness and catalog-change invalidation.',
 'Suggestion behavior can stay just as responsive and correct.',
 'Removes repeated category query/allocation on hot catalog paths; expected benefit medium, not measured post-change.',
 'Public category DTO caching and internal CategoryTree construction are separate.',
 'Introduce local snapshot reuse tied to CatalogChanged and an appropriate finite TTL.',
 'No per-user data belongs in the tree; there is already an invalidation event and Memo pattern.',
 'Medium: invalidation ordering during catalog seeding and multiple instances matters.',
 'Avoid stale category counts/names or invalid filter choices after catalog changes.',
 'Verify repeated SQL; cache only immutable metadata, keep price/stock/permissions outside it, and invalidate alongside existing catalog caches.',
 'Measure repeated-query SQL counts; test catalog changes, unknown slugs, descendants and concurrent reads; avoid an added Redis round trip per request.',False)
add('BE-002','Wishlist reads add two SQL queries per list','P2','Backend / N+1','GET /api/v1/wishlist',[(B+'wishlist/WishlistService.java',45),(B+'wishlist/WishlistService.java',140)],
 '[bounded-query-counts.json](evidence/bounded-query-counts.json): 1,2,3 lists with one product each require 4,6,8 SQL statements respectively.',
 'Create three local wishlists with one item each, reading /wishlist after each creation with request-correlated SQL logging.',
 'Each list separately loads item IDs then separately calls catalog.lookupAll.',
 'Load owned list/item relationships in bulk, hydrate distinct products once, then group into ordered lists.',
 'Lower latency when managing several lists without changing visible content.',
 'Two extra SQL round trips per additional nonempty list; finite list/item limits constrain but do not remove this waste.',
 'Per-list stream mapping invokes repositories inside the mapping function.',
 'Batch data loading across owned lists while preserving list/item order and response structure.',
 'Uses the existing bulk catalog lookup and existing shopper scope.',
 'Medium: empty lists, duplicate products across lists and deleted products must map correctly.',
 'Do not reorder saved products or collapse intentionally separate lists.',
 'Verify growth; bulk-load item associations for owned list IDs and call lookupAll once for distinct products.',
 'Test 0/1/many lists, shared products, empty/deleted products and another shopper’s IDs; confirm query count stays bounded and returned content matches.',False)
add('BE-003','Notification retention recreates and deletes old milestones on every poll','P2','Backend / persistence','Notification unread-count and list synchronization',[(B+'notification/NotificationService.java',138),(B+'notification/NotificationService.java',168),(B+'notification/NotificationService.java',180)],
 '[bounded-query-counts.json](evidence/bounded-query-counts.json): 60 synthetic orders aged 8 days create 240 due milestones; first poll stores 200; second and third each execute 40 INSERTs plus one retention DELETE (47 SQL statements total).',
 'Use tools/bounded-data.cjs only against an isolated fixture database. Create 60 delivered synthetic orders within the 45-day lookback; poll unread count three times.',
 'Retention deletes the very rows used as the only dedupe record, so the next sync regards deleted milestones as new.',
 'Unchanged old history generates no repeated insert/delete work after retention.',
 'The visible list stays capped, masking repeated database work; a badge poll becomes more expensive for frequent shoppers.',
 '40 avoidable writes plus retention deletion per unchanged poll in the reproduced fixture; row/WAL churn grows with due history.',
 'Presentation retention and durable milestone-generation knowledge share the same table rows.',
 'Separate durable generation state from retained display history, or implement a proven retention watermark that handles late/cancel/refund milestones.',
 'Timestamp-derived tracking and the 200-entry display cap can remain unchanged.',
 'Medium to high: dedupe state and historical backfill need a careful transactional/migration design.',
 'Do not suppress new updates, re-notify read events, or remove user notification preferences.',
 'Verify threshold reproduction; specify generation/retention semantics first, then persist minimal durable dedupe/high-water state without an unbounded scheduler.',
 'Repeat 60-order test and require zero INSERTs on unchanged polls; test 199/200/201, late milestones, cancellation/refund, preference changes, read state, concurrent sync and 45-day expiry.')
add('QA-001','CI image validation covers only the 113-product demo catalog','P2','Quality coverage','scripts/validate_images.py / CI images job',[('scripts/validate_images.py',28),('.github/workflows/ci.yml',72)],
 '[image-validation.log](evidence/image-validation.log): 113 products checked. Expanded audit inventory contains 2,201 products; 2,088 expanded-catalog products are outside the validator inputs. Independent primary-image checks found no current missing files.',
 'Run python3 scripts/validate_images.py; compare its input paths/count with catalog/products/*.json and catalog/images.json.',
 'The CI job description suggests every product, but validation only reads demo/products.json and demo/images.json.',
 'The guard covers the same catalog inputs as the seeder, including expanded and variant image references.',
 'Future catalog regressions can evade CI; this is a coverage gap, not a claim of 2,088 broken products.',
 'A modest longer CI scan trades for reliable asset checks.',
 'Validator inputs were not expanded with the catalog.',
 'Merge inputs according to seeder semantics and validate all referenced image metadata/files.',
 'Audit-only independent scan already demonstrates the inputs are readable and primary assets currently exist.',
 'Low to medium: shared manufacturer imagery and documented non-exact matches must not become false failures.',
 'Do not replace legitimate premium imagery with generic fallbacks merely to pass CI.',
 'Verify catalog count and merge precedence, expand the validator, and add small malformed fixtures for both source groups.',
 'Assert known total, missing primary/variant assets, empty/generic alt/provenance and dimensions; confirm current permitted warnings remain explicit.')
add('QA-002','Existing end-to-end tests are not executed by CI','P2','Quality coverage','.github/workflows/ci.yml',[('.github/workflows/ci.yml',1),('frontend/playwright.config.ts',1),('frontend/e2e/shopping.spec.ts',1)],
 'CI definition runs Vitest, build, lint and backend tests but no Playwright. [playwright-existing.log](evidence/playwright-existing.log) proves 22 existing E2E cases pass locally; 8 project-specific skips are intentional.',
 'Read all jobs in ci.yml and compare to package.json e2e command and frontend/e2e/*.spec.ts.',
 'Important browser shopping/responsive/rankings regressions are only caught when someone runs the suite manually.',
 'A bounded CI job runs existing E2E flows on an isolated local stack and publishes failure artifacts.',
 'Reduces risk of breaking working commerce flows during the planned optimizations.',
 'Adds CI compute; use one shared test stack per job, readiness checks and appropriate worker limits.',
 'Browser tests exist but are not wired into the workflow.',
 'Add an isolated production-build E2E CI job; do not point it at production.',
 'Reuses passing tests and deterministic local infrastructure.',
 'Medium: service readiness, seeding and test parallelism need reliable orchestration.',
 'No product UI change is needed.',
 'Verify no new CI job already covers this; start local PG/Redis/API, wait for seed/readiness, serve built frontend, run E2E and upload artifacts.',
 'Run the proposed job in CI, prove a deliberately failing local test fails the job during development, remove that temporary failure, and confirm teardown; preserve secret/CSRF protections.')
add('IMG-001','Byte-identical product images are stored under many URLs','P3','Asset storage','frontend/public/images and catalog image manifests',[('frontend/public/images',None),('backend/src/main/resources/catalog/images.json',1),('backend/src/main/resources/demo/images.json',1)],
 '[asset-duplicates.json](evidence/asset-duplicates.json): 1,094 exact-hash groups with 21,273,614 redundant bytes; seven identical AMD EPYC 800px files alone duplicate 282,372 bytes beyond one copy.',
 'Run tools/assets.py and group public assets by SHA-256; inspect model/variant attribution before choosing candidates.',
 'Identical byte content can be deployed and requested through different product-specific paths.',
 'Where provenance/model matching permits, identical assets share a canonical source without reducing quality.',
 'No expected visual change; cross-product cache reuse may improve when identical URLs are shared.',
 'About 20.3MiB redundant static storage is an upper bound, not per-page download savings; current build contains 142.6MiB of public assets.',
 'Product-specific generation/download paths duplicate reused manufacturer imagery.',
 'Canonicalize only proven identical assets and update manifests/generation consistently, keeping attribution per product.',
 'Byte-identical image content means no recompression or blur is needed.',
 'Medium migration risk despite low priority: deployed cached JSON may reference old paths.',
 'Never merge different color/model photos or strip credits; retain compatibility for old asset URLs until caches expire.',
 'Verify hashes and owners; begin with a small safe group, map shared content while preserving source metadata, and coordinate old URL retention with CDN stale policies.',
 'Validate all manifests/variants, compare screenshots, check old and new URLs, measure output size and actual cross-product requests; roll back manifests if any broken image appears.',False)

(E/'findings.json').write_text(json.dumps(findings,indent=2)+'\n')
def write(name,text): (R/name).write_text(text.strip()+'\n')
def refs(files):
 return '; '.join(f'[{p}'+(f':{n}' if n else '')+f'](../{p})' for p,n in files)
def link(f): return f'[{f["id"]}](OPTIMIZATION_FINDINGS.md#{f["id"].lower()})'
intro='''# TrustKart verified findings

Audit baseline: `177fb10` on `trustkart-rebuild`, inspected September 30–October 1, 2026. These are observations of that snapshot, not assumptions about a future checkout. Verify each finding still exists before changing it. Full evidence is local under `evidence/`; synthetic accounts and orders were used only on isolated local services.

**18 findings: P0 0 · P1 1 · P2 16 · P3 1.** HIGH means directly reproduced/measured, including direct inspection of CI/validator inputs. It does not mean a proposed fix is risk-free. No production measurements or field performance claims are made.

| ID | Priority | Area | Finding | User impact | Resource impact | Confidence |
|---|---|---|---|---|---|---|
'''
for f in findings: intro+=f'| {link(f)} | {f["priority"]} | {f["category"]} | {f["title"]} | {f["user"]} | {f["resource"]} | {f["confidence"]} |\n'
for f in findings:
 intro+=f'\n## {f["id"]}\n\n### {f["title"]}\n\n'
 fields=[('Priority',f['priority']),('Category',f['category']),('Affected route/component/service',f['route']),('Exact files and baseline lines',refs(f['files'])),('Evidence',f['evidence']),('How to reproduce',f['repro']),('Current behavior',f['current']),('Expected behavior',f['expected']),('User impact',f['user']),('Resource impact',f['resource']),('Likely root cause',f['cause']),('Recommended change',f['change']),('Why this is safe',f['safe']),('Risk of changing it',f['risk']),('Potential UX regression',f['ux']),('How Claude should implement it',f['implement']),('How Claude should test it',f['test']),('Confidence',f['confidence'])]
 for k,v in fields: intro+=f'**{k}:** {v}\n\n'
intro+='''## Quick Wins

Start with A11Y-001 (two inline link styles), API-001 (null validation) and UX-003 (summary layout). FE-001 is a useful payload reduction after verifying all consumers. These have narrow scope and clear before/after checks. UX-001 is also localized but must be tested with the sticky header and keyboard focus. Address SEC-001 first because priority overrides implementation convenience.

## High-Value Structural Improvements

| IDs | Expected benefit | Risk/dependency | Required evidence | Rollback |
|---|---|---|---|---|
| DB-001, API-002 | Medium: fewer image probes and correct page metadata | Medium; shared sorting/filter/count contract | Equivalent result sets plus EXPLAIN on full and selective queries | Restore former query while keeping a separately tested empty-page count fix |
| BE-002 | Medium: bounded wishlist round trips | Medium; preserve owned associations/order | 1/2/3/max-list SQL counts and ownership tests | Restore old read mapping; no schema change required |
| BE-003 | Medium: eliminate repeated notification writes | Medium/high; durable generation history design | Zero inserts after stable retention; late/refund/concurrent cases | Keep additive metadata schema, roll back reader/writer behavior together; do not destroy user history |
| BE-001 | Medium: reuse category metadata | Medium; catalog-change invalidation | Query counts before/after plus invalidation tests | Disable snapshot reuse and return to live metadata reads |
| IMG-001 | Low user / medium storage benefit | Manifest and cached old URL compatibility | Exact hashes, full catalog validation and screenshots | Restore prior manifests and retain old files |

## Things Claude Should Leave Alone

Keep server-side repricing, wallet/order/ledger atomicity, stock checks, idempotency and ownership checks. Preserve CSRF, cookie/session hardening, Argon2 and rate limiting. Preserve PostgreSQL authority for commerce and rankings. Do not move these values exclusively into Redis.

Keep the working 150ms search debounce, cancellation and minimum query length; do not add semantic/AI network calls as an “optimization.” Retain timestamp-derived seven-day tracking with no per-order timers, the two-minute notification cadence and mutation-driven wallet/cart updates. Fix BE-003 rather than hiding it with a much slower polling interval.

Keep existing route-level splitting, 400/800px WebP srcset, intrinsic image dimensions, below-fold lazy loading and quality-preserving object-contain wells. The tested pages do not justify blanket React.memo, virtualization, a package replacement or a visual redesign. No unused production dependency was established. Preserve the premium dark retail identity, responsive shelves, product imagery and useful microinteractions.
'''
write('OPTIMIZATION_FINDINGS.md',intro)
print('Wrote canonical findings',len(findings))

write('README.md','''# TrustKart independent audit

Start with [AUDIT_SUMMARY.md](AUDIT_SUMMARY.md), then [CLAUDE_IMPLEMENTATION_PLAN.md](CLAUDE_IMPLEMENTATION_PLAN.md). The authoritative issue descriptions are in [OPTIMIZATION_FINDINGS.md](OPTIMIZATION_FINDINGS.md). Area reports cross-reference those IDs instead of duplicating recommendations.

This audit inspected commit `177fb10` on `trustkart-rebuild` on September 30–October 1, 2026. Origin was fetched and the current branch was fast-forward checked against its upstream: already current. `origin/main` has different merge history but the same source tree. Existing untracked `scripts/renders/` files and other running coding processes were left alone.

Only this `codex/` directory was added to the working repository. Builds ran in `/private/tmp/trustkart-audit-20260930`, copied from the audited commit, using installed frontend dependencies. Source, tests, migrations, configuration and project documentation were not edited. Nothing was committed, pushed or deployed.

## Read by area

- [Architecture](CODEBASE_ARCHITECTURE.md) and [route matrix](ROUTE_MATRIX.md)
- [UI/UX](UI_UX_AUDIT.md), [responsive](RESPONSIVE_AUDIT.md), [accessibility](ACCESSIBILITY_AUDIT.md)
- [Frontend](FRONTEND_PERFORMANCE.md), [assets](IMAGE_ASSET_AUDIT.md), [API/network](API_NETWORK_AUDIT.md)
- [Backend](BACKEND_PERFORMANCE.md), [database](DATABASE_AUDIT.md), [Redis](REDIS_CACHE_AUDIT.md)
- [Resources/deployment](RESOURCE_USAGE_AUDIT.md), [security implications](SECURITY_OPTIMIZATION_RISKS.md)
- [Tests](TEST_RESULTS.md), [bugs](BUGS_FOUND.md), [verification checklist](VERIFICATION_CHECKLIST.md)

## Evidence and reproducibility

`evidence/` contains build/test logs, SQL plans, raw measurements, payload samples and synthetic fixture results. `screenshots/` contains full-page captures and contact sheets. Initial `initial-home`/`home-ready` captures preceded completed seeding and are not evidence of the final UI. Use named route/width captures for conclusions.

`tools/` contains audit-only harnesses, not application tests or production tooling. They hard-code the temporary snapshot dependency path and local ports; read and adapt them before re-running. `security-check.cjs` deliberately removes one synthetic session denylist key; `bounded-data.cjs` creates synthetic local orders. Never run either against a shared or production service. Generated sample accounts use example.test and fictional addresses. No production credentials or browser cookie-state files are included.

Browser review used Chromium and a production frontend build backed by an isolated seeded API/PostgreSQL/Redis stack. Lab measurements are not field Core Web Vitals. Reported PASS means the specified observation passed; it is not a warranty of every state, browser or device.

Implementation has NOT been started. Review codex/CLAUDE_IMPLEMENTATION_PLAN.md before making changes.
''')
write('CODEBASE_ARCHITECTURE.md','''# Verified codebase architecture

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
''')

routes=json.loads((E/'route-matrix.json').read_text()); main=[x for x in routes if x['width']==1440]
route_labels={'category':'/c/:category','curated':'/collections/:collection','product':'/p/:slug','tracking':'/account/purchases/:id','404':'* (404)'}
issues={'product':'UX-002 when comparison tray is open','account':'UX-003 at 768px','notifications':'UX-004 after 30 records','rankings':'A11Y-001, A11Y-002','tracking':'A11Y-001','search':'A11Y-003 in recent suggestions'}
route_doc='''# Route inspection matrix

27 route cases were loaded against real local APIs. Signed-in fixture coverage includes a purchase, wishlist item and fictional checkout address; the saved-address page was inspected in its empty state; guest shopping is separately covered by E2E tests. Search/deals/category/curated share SearchPage but each route was visited. Receipt includes tracking. No admin UI, separate tracking route, review page or standalone 500 page exists in the router. ErrorBoundary source was reviewed; an exhaustive injected-failure UI campaign was not performed.

Every route was captured at 390,768,1024,1440,1920 widths. Desktop/tablet/mobile contact sheets were visually reviewed; additional captures/DOM checks cover all 14 requested sizes for core routes. “Loads” refers to rendered route content, not merely the SPA HTTP 200 response. Console capture was limited by one harness restart; see notes below.

| Route | Loads | UI | Responsive | Console errors | Major finding |
|---|---|---|---|---|---|
'''
for x in main:
 n=x['name'];label=route_labels.get(n,x['url'].split('?')[0]);issue=issues.get(n,'Shared mobile bell: UX-001')
 route_doc+=f'| `{label}` | PASS | '+('ISSUE' if n in issues else 'PASS, inspected base state')+f' | See responsive matrix | No captured page error; coverage limited | {issue} |\n'
route_doc+='''
`evidence/route-matrix.json` records actual URLs, headings, viewport, DOM size, overflow and broken-image checks for all 207 cases. `browser-api-responses.json` captures responses in the resumed run. Four bounded `networkidle` waits timed out; subsequent captures rendered route headings/content, and no failed API status was recorded in that resumed capture. These are instrumentation timing limitations, not proven route failures. The first harness stopped on an unbounded networkidle wait and was resumed; do not interpret missing early console records as proof of a clean console in every state. The separate 30 cold performance runs recorded no page errors and no duplicate API requests.

All inspected base pages had headings, no root horizontal overflow and no detected failed product image after loading. Overlay clipping and fixed-layer collisions are separate checks and did fail; a passing root overflow assertion alone is insufficient.
'''
write('ROUTE_MATRIX.md',route_doc)

resp='''# Responsive audit

207 route/viewport observations, all 14 requested sizes represented. All 27 route cases were inspected at 390×844, 768×1024, 1024×768, 1440×900 and 1920×1080. Eight core flows (home, search, product, compare, cart, checkout, receipt/tracking, rankings) additionally cover the other nine requested sizes. Test harness: Chromium production build, dark theme, synthetic data; these are viewport simulations, not physical-device testing.

| Route | 390 | 768 | 1024 | 1440 | 1920 |
|---|---|---|---|---|---|
'''
for x in main:
 n=x['name']; label=route_labels.get(n,x['url'].split('?')[0]); cells=['PASS']*5
 # Shared bell geometry is an interaction issue on every shared header at the tested phone width.
 cells[0]='ISSUE UX-001'+(' / UX-002' if n=='product' else '')
 if n=='account':cells[1]='ISSUE UX-003'
 resp+='| `'+label+'` | '+' | '.join(cells)+' |\n'
resp+='''
PASS denotes the inspected base layout: heading/content present, no root overflow, visually usable stacking. It does not certify all values, overlays, keyboard behavior or every intermediate width. UX-001 was reproduced in the shared header at 320/390 and applies to routes using that header; it was not reopened independently on every route. UX-002 is conditional on an open compare tray. Notifications pagination and link/tab accessibility are recorded separately rather than being mislabeled as viewport defects.

## Exact size coverage

| Viewport | Coverage |
|---|---|
| 320×568 | Eight core flows, screenshots including cart/checkout/tracking; notification overlay interaction |
| 360×800 | Eight core flows, DOM/layout measurements |
| 375×812 | Eight core flows, DOM/layout measurements |
| 390×844 | All 27 routes, screenshots, axe and selected keyboard/overlay flows |
| 430×932 | Eight core flows, DOM/layout measurements |
| 600×960 | Eight core flows, DOM/layout measurements |
| 768×1024 | All 27 routes, screenshots and tablet visual review |
| 820×1180 | Eight core flows, DOM/layout measurements |
| 1024×768 | All 27 routes, screenshots/layout checks |
| 1024×1366 | Eight core flows, DOM/layout measurements |
| 1280×800 | Eight core flows, DOM/layout measurements |
| 1440×900 | All 27 routes, screenshots, axe and desktop visual review |
| 1728×1117 | Eight core flows, DOM/layout measurements |
| 1920×1080 | All 27 routes, screenshots/layout checks |

Non-core routes at the additional nine widths: **NOT TESTED**. No missing-size result is inferred from a neighboring breakpoint.

## Verified issues and strengths

[UX-001](OPTIMIZATION_FINDINGS.md#ux-001) clips the mobile notification panel by 34px; [UX-002](OPTIMIZATION_FINDINGS.md#ux-002) covers 45px of the mobile product action bar; [UX-003](OPTIMIZATION_FINDINGS.md#ux-003) hides important tablet summary values. The screenshots and measured rectangles make each reproducible.

Mobile shelves intentionally scroll horizontally within their own regions; this is not root-page overflow. Mobile filters settle within the viewport (bottom=844 at height 844), and Escape returns focus to Filters. An early animation-frame measurement appeared outside the viewport; the settled confirmation disproved that as a persistent defect. Wallet dialog Escape also works after settling.

Desktop maximum content widths and whitespace remain coherent with the retail design. Tablet product/search grids remain useful; the account summary is the concrete exception. No evidence supports replacing the design system, hiding half the mobile catalog, or shrinking all typography. Physical soft-keyboard behavior, safe-area hardware, browser zoom across all routes and Safari/Firefox were not comprehensively tested.

Evidence: [route data](evidence/route-matrix.json), [interaction data](evidence/interactions.json), [settled confirmation](evidence/ui-confirm.json), and `screenshots/contact-{390,768,1440}-*.jpg`.
'''
write('RESPONSIVE_AUDIT.md',resp)
write('UI_UX_AUDIT.md','''# UI/UX audit

Visual review covered the actual production UI, not only JSX: home, four search/listing entry points, product/options, compare, cart, checkout, wallet, collection, wishlist, rankings, authentication, seven account views, about/privacy/project/credits and 404. [ROUTE_MATRIX.md](ROUTE_MATRIX.md) enumerates the 27 route cases; full-page screenshots and contact sheets are retained.

The premium dark shopping identity is already coherent: prominent product photography, blue hero/brand accents, warm cart actions, consistent image wells and dense but legible retail hierarchy. Product/card styles are shared. A token-based theme and shared Button/Field/Dialog components exist; arbitrary-value classes alone did not establish a meaningful performance problem. No redesign or broad token rewrite is recommended.

## Actual friction

- [UX-001](OPTIMIZATION_FINDINGS.md#ux-001): notification preview clips off the left edge on phones.
- [UX-002](OPTIMIZATION_FINDINGS.md#ux-002): comparison tray obstructs the sticky product purchase action.
- [UX-003](OPTIMIZATION_FINDINGS.md#ux-003): tablet account summary truncates wallet mode and money.
- [UX-004](OPTIMIZATION_FINDINGS.md#ux-004): older retained notifications have no full-page navigation.
- [A11Y-003](OPTIMIZATION_FINDINGS.md#a11y-003): recent-search deletion is unavailable through the keyboard.

[A11Y-001](OPTIMIZATION_FINDINGS.md#a11y-001) and [A11Y-002](OPTIMIZATION_FINDINGS.md#a11y-002) cover link discoverability and leaderboard tab behavior. These are concrete interaction problems; do not treat personal preferences about whitespace, dark colors or shelf density as bugs.

## Working flows worth preserving

The existing shopping E2E suite completed search → add to cart → address checkout → confirmation → collection at desktop and phone sizes. Wallet funding, comparison and rankings opt-in/purchase flows passed. Product options and server quotes preserve authoritative totals. Signed-in account/security screens rendered with synthetic records; the saved-address empty state was inspected. Guest and empty-cart views were inspected separately. Search typing produced one request for rapidly entered “macbook,” keyboard result selection/submission worked, and Escape closed suggestions.

Filter and wallet dialogs close with Escape and restore their triggers after the animation settles. Collection/category rows intentionally use swipe/scroll affordances on phones. Tracking communicates timestamp-derived stages; no fake sub-second progress animation was needed. Existing image fallbacks and error components are useful; do not remove them to reduce DOM counts.

## Scope limits

All implemented route cases were visited, but this is not every catalog product, option combination, possible error, notification age or authentication-provider state. The malformed quote error was reproduced through HTTP, not a naturally generated browser link. Google external consent, real assistive technology, all light-theme screens, production cold starts and a complete failure-injection campaign were not performed. There is no admin UI to visually review; protected admin APIs were probed.
''')

perf=json.loads((E/'performance.json').read_text()); bundle=json.loads((E/'bundle.json').read_text())
def med(rows,fn):return statistics.median(fn(x) for x in rows)
pt='| Page / profile | Initial requests | Initial KiB | After-scroll KiB | LCP ms | FCP ms | TTFB ms | Observed CLS | DOM elements | JS heap MiB |\n|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n'
for name in ['home','search','product','cart','rankings']:
 for mobile in [False,True]:
  rr=[x for x in perf if x['name']==name and x['mobile']==mobile]
  pt+=f'| {name} / '+('mobile' if mobile else 'desktop')+f' | {med(rr,lambda x:x["initialRequests"]):.0f} | {med(rr,lambda x:x["initialBytes"])/1024:.1f} | {med(rr,lambda x:x["scrolledBytes"])/1024:.1f} | {med(rr,lambda x:x["vitals"]["lcp"]):.0f} | {med(rr,lambda x:x["vitals"]["fcp"]):.0f} | {med(rr,lambda x:x["vitals"]["ttfb"]):.1f} | {med(rr,lambda x:x["vitals"]["cls"]):.6f} | {med(rr,lambda x:x["dom"]):.0f} | {med(rr,lambda x:x["jsHeapUsed"])/1048576:.1f} |\n'
js=[x for x in bundle if x['file'].endswith('.js')];css=[x for x in bundle if x['file'].endswith('.css')]
bt='| Build output | Raw bytes | Local gzip bytes |\n|---|---:|---:|\n'
bt+=f'| All {len(js)} JS chunks (not all initially loaded) | {sum(x["bytes"] for x in js):,} | {sum(x["gzipBytes"] for x in js):,} |\n'
for x in sorted(js,key=lambda x:x['bytes'],reverse=True)[:10]+css:bt+=f'| `{x["file"]}` | {x["bytes"]:,} | {x["gzipBytes"]:,} |\n'
write('FRONTEND_PERFORMANCE.md','''# Frontend performance

Production build, lint, typecheck, format and unit tests passed. Existing route-level lazy loading already separates non-home pages; Security Center, comparison, rankings, checkout and account code have separate chunks. No admin UI exists to accidentally include in the home bundle. Seven production dependencies were checked against source/config usage; no unused production package or clearly wasteful whole-library import was established. Production npm audit reported zero vulnerabilities at audit time; this does not cover all dev dependencies or Maven CVEs.

## Bundle measurements

'''+bt+'''
Raw and gzip bytes above come from `evidence/bundle.json`; gzip was calculated locally and may differ slightly from Vite's compression reporting. The initial browser load imports shared chunks too: desktop home transferred **145,007 bytes of JavaScript** and **20,230 bytes of CSS**, including response overhead as counted by CDP. The entry chunk's compressed size alone is not “initial JS.”

## Measured local loads

30 cold browser loads: three runs per route/profile; table values are medians. Desktop: 1440×900, DPR 1, no throttling. Mobile: 390×844, DPR 3, 4× CPU throttle, configured 60ms network latency and approximately 9Mbps download/2Mbps upload. Cache disabled, production Vite preview with same-origin proxy to a local warm Java server. Metrics were sampled after network idle plus one second, then the page was scrolled to collect lazy assets. Guest empty cart was used for performance; populated cart/checkout were covered in functional QA.

'''+pt+'''
These are laboratory observations on this Mac, not production percentiles or a guarantee for phones. TTFB is the document navigation responseStart measurement, not end-to-end API latency. CLS is observed load-time shift accumulation, not a field session measurement. LCP candidate on home was the hero product image; desktop first sample was a 400px source rendered at 360px. Image loading—not a large video or embedding client—dominates transfer. No page errors or duplicate API URLs were captured across these 30 loads.

Lighthouse Performance score: **Not measured**. Lighthouse Accessibility score: **Not measured**. INP: **Not measured**. A cached Lighthouse binary was unavailable; no score was fabricated. React DevTools profiling/commit counts: **Not measured**. CDP JS heap/script/layout metrics are in `performance.json`, but they do not prove a React rerender problem or a memory leak. No blanket memoization recommendation follows from them.

## React, data and timers

QueryClient is created once with useState. Query keys are centralized; default staleTime is 30 seconds and default window-focus refetch is off. Home/category hooks provide longer freshness where appropriate. GET helpers pass AbortSignal; smart suggestions use a 150ms debounce and a minimum query length. Rapid macbook typing made one request; ArrowDown/Enter and Escape worked. Preserve this behavior.

Cart mutations apply the returned server cart through setQueryData and invalidate quotes; wallet/order mutations invalidate their relevant families. No whole-app cart reload, wallet interval or per-order tracking polling was found. There is no measured reason to add optimistic financial state complexity. Query retries are bounded (up to eight transient retries with delay capped at 15 seconds, designed for cold API startup), and 4xx responses are excluded. Notification count overrides retry to one. Failure-screen usability during a real hosted cold start remains unmeasured.

Two repeating UI timers were found: search placeholder rotation at 3.5 seconds and hero rotation at 7 seconds. Source includes cleanup; hero pauses for interaction/reduced motion. Shared theme/compare/drawer contexts can rerender consumers, but no expensive measured interaction justified React.memo everywhere. Measured DOM sizes (~500–1,400 elements in these performance routes) do not alone justify virtualization. A very large acquired collection was not stress-tested.

## Actionable change

[FE-001](OPTIMIZATION_FINDINGS.md#fe-001) removes proven unused homepage payload and backend tile work. [UX-004](OPTIMIZATION_FINDINGS.md#ux-004) adds bounded notification navigation. UI overlay fixes should retain existing shared providers and query ownership. See IMAGE_ASSET_AUDIT.md and API_NETWORK_AUDIT.md for transfer details.
''')
assets=json.loads((E/'assets.json').read_text())
write('IMAGE_ASSET_AUDIT.md',f'''# Image and asset audit

Independent binary/dimension/hash scan: **{assets['assetFiles']:,} public files, {assets['assetBytes']:,} bytes (142.6MiB)**, including **7,833 WebP** images and four PNGs. All 2,201 product primary image metadata records were checked for referenced file existence and primary metadata/alt coverage by the audit script; no validation errors were found. This is not a full independent licensing/model-authenticity investigation, nor proof that every option photo reference was tested in the browser.

| Asset group | Files | Mean bytes | Median bytes | Largest bytes |
|---|---:|---:|---:|---:|
| 400px WebP | 3,925 | 8,601 | 7,276 | 42,366 |
| 800px WebP | 3,907 | 29,538 | 22,602 | 196,998 |

Largest public asset: `brand/icon-512.png`, 219,131 bytes. Largest product file: `images/catalog/samsung-micro-rgb-r95h-800.webp`, 196,998 bytes at 800×800. The remaining WebP inventory includes assets outside those filename groups. No multi-megabyte 5,000px product-image pattern was found; recommending universal recompression or smaller source quality would be unsupported.

## Delivery and visual behavior

`ProductImage.tsx` supplies 400w/800w srcset, sizes, intrinsic width/height, async decoding, priority-aware eager/lazy loading and an accessible named fallback. Images use object-contain. Hero first-slide priority is explicit; the first home LCP candidate was the hero product image. Browser-selected sources account for high-DPR mobile transfer: fewer requests can still download more bytes than desktop. Preserve 800px options for sharp mobile/detail images.

The route sweep detected no broken loaded product images across 207 captures. Product detail sends gallery/options metadata, but image downloads are governed by rendered elements and lazy loading. The 30 performance runs quantify initial versus after-scroll transfer in FRONTEND_PERFORMANCE.md. No evidence supports stripping useful gallery content or removing premium hero imagery.

## Findings

[IMG-001](OPTIMIZATION_FINDINGS.md#img-001): 1,094 byte-identical hash groups account for 21,273,614 redundant bytes (~20.3MiB). This is a deployment-storage upper bound, not a promise of that much less per-page transfer. Canonical URLs can also help reuse between products, but model/color matching, attribution and old CDN-cached URLs must be preserved.

[QA-001](OPTIMIZATION_FINDINGS.md#qa-001): the repository validator passed with 25 warnings but checked only the 113 demo products. Extend it to the actual expanded catalog and variant references. The audit's broader primary-image scan should not be mistaken for existing CI coverage.

[FE-001](OPTIMIZATION_FINDINGS.md#fe-001) removes unused card metadata; it does not remove displayed imagery. No blanket AVIF conversion, lower quality setting or gallery deletion is recommended without side-by-side quality/transfer comparisons.

Evidence: [asset summary](evidence/assets.json), [file inventory](evidence/asset-inventory.json), [exact duplicates](evidence/asset-duplicates.json), [validator output](evidence/image-validation.log). Source/provenance docs were read as claims and compared to manifests; they were not rewritten.
''')
api=json.loads((E/'api-query-counts.json').read_text());at='| Endpoint (under /api/v1) | Median ms | JSON body bytes | SQL statements/request |\n|---|---:|---:|---:|\n'
for a in api:at+=f'| `{a["endpoint"]}` | {a["medianMs"]:.2f} | {a["bytes"]:,} | {a["sqlCounts"][0]} |\n'
write('API_NETWORK_AUDIT.md','''# API and network audit

Twenty-one endpoints were each sampled five times with request-correlated SQL logging on the isolated warm stack. These medians include local HTTP handling and diagnostic logging; they are not production load-test percentiles. Bodies are decoded JSON bytes, while browser transfer numbers include protocol/response overhead. Long receipt UUIDs identify synthetic local orders only.

'''+at+'''
Slowest median among tested endpoints: laptop facets, 11.71ms. A larger query count does not automatically mean unacceptable latency: the detail and collection endpoints use deliberate separate projections too. Wishlist query growth and notification write churn were separately reproduced at increasing data sizes and are findings.

## Initial home requests and payload

Cold desktop home issued 83 total requests, including **seven API reads**: catalog/home, catalog/categories, cart, wallet, me, notifications/unread-count and wishlist/ids. Initial transferred bytes were ~948.2KiB; image traffic was 672,784 bytes, fonts 48,577, scripts 145,007, CSS 20,230 and Fetch 83,287 in the representative desktop run. No duplicate API URL was captured in the 30 measured cold route loads. Recently viewed state can add a lookup request; it was empty in the cold home fixture.

The home JSON body was 66,115 bytes; list page size 24 was 16,942 bytes, and product detail 10,135 bytes. Cards are summaries with card-required pricing/stock/image data rather than full descriptions/reviews/options. Home returns 11 collection tiles despite rendering at most six: [FE-001](OPTIMIZATION_FINDINGS.md#fe-001). Category data also appears in home plus the independent header categories response (12,257 bytes); this is an overlap worth considering when designing that change, but removing the header query blindly could create a navigation waterfall on non-home routes. It is not a separate quantified fix claim.

## Search and request control

Typing m → ma → mac → macb → macbo → macbook at 60ms per character generated one suggestions call for macbook. Suggestions have 150ms debounce, cancellation and a two-character minimum. Search interpretation is local/backend rule-based and SQL-based; no embedding generation or AI request occurred. Smart mode is explicitly disabled. Preserve fast suggestions rather than introducing a large delay.

GET reads use abort signals; refresh/CSRF bootstrapping use shared promises to avoid concurrent duplicate refresh requests. Query retries are bounded and do not retry normal 4xx. No wallet polling or whole-app refetch after cart quantity changes was found. Notifications use two-minute polling; BE-003 is a write-amplification problem under that otherwise reasonable cadence.

## Correctness and bounded lists

[API-001](OPTIMIZATION_FINDINGS.md#api-001) reproduces an options=null HTTP 500. [API-002](OPTIMIZATION_FINDINGS.md#api-002) reproduces incorrect totals on an empty out-of-range page. [UX-004](OPTIMIZATION_FINDINGS.md#ux-004) covers UI access to older notification pages.

Product/search and purchase/notification APIs have server-side page bounds. Public rankings cap at monthly 50/all-time 100. Wishlists have finite list/item limits but use repeated read queries (BE-002). Collection currently returns the shopper's aggregate acquired-product collection without page controls; a large lifetime collection was not generated, so this audit makes no measured slowdown claim. Before applying virtualization or a new API contract there, generate a representative high-volume fixture and measure response/DOM size.

## Caching and compression limits

Local API responses did not contain Content-Encoding; the preview did gzip larger JS/CSS. Hosted Vercel compression was not measured, so do not claim that production JSON is uncompressed. Catalog/search public responses carry CDN/browser cache headers; personal data must remain private. Existing PublicCacheIT verifies public cookie-free versus private behavior. Catalog stale-if-error/stale-while-revalidate policy can retain older public data during upstream sleep/outage; server-side repricing protects checkout correctness. Do not weaken that check to avoid a refresh.

Evidence: `api-measurements.json`, `api-query-counts.json`, `performance.json`, payload snapshots, `interactions.json` and `bounded-query-counts.json`.
''')
write('BACKEND_PERFORMANCE.md','''# Backend performance audit

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
''')
write('DATABASE_AUDIT.md','''# PostgreSQL / JPA audit

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
''')
write('REDIS_CACHE_AUDIT.md','''# Redis / caching audit

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
''')

write('ACCESSIBILITY_AUDIT.md','''# Accessibility audit

54 axe scans covered all 27 route cases at 1440px and 390px in dark theme. Two distinct inline-link targets failed `link-in-text-block` at both widths (four route/width scan violations). Other scanned base states had zero axe violations. Zero automated violations is not a WCAG conformance claim.

## Verified findings

[A11Y-001](OPTIMIZATION_FINDINGS.md#a11y-001): “Change” on rankings and “store policy” on receipts need a persistent non-color cue. This is consistent with [W3C guidance on use of color](https://www.w3.org/WAI/WCAG22/Understanding/use-of-color.html). Keep the palette and add a coherent inline-link treatment.

[A11Y-002](OPTIMIZATION_FINDINGS.md#a11y-002): leaderboard controls expose tab roles but ArrowRight does not move focus/selection. Tab+Space still works. Implement a complete keyboard model consistent with the [W3C tabs pattern](https://www.w3.org/WAI/ARIA/apg/patterns/tabs/), rather than claiming the board is wholly inaccessible.

[A11Y-003](OPTIMIZATION_FINDINGS.md#a11y-003): recent-search remove buttons are tabIndex=-1 and pointer-only with no input keyboard equivalent. Preserve the current functioning combobox selection flow while adding accessible history management.

UX-001/002 also affect accessible operability: clipping/occlusion can hide text and actions even when automated checks pass. UX-003 obscures important monetary values.

## Manual keyboard and semantic checks

| Interaction | Observed result |
|---|---|
| Search typing + ArrowDown | Suggestions appear; active descendant changes |
| Search Enter | Navigates to submitted results |
| Search Escape | Suggestions close |
| Recent search removal | ISSUE A11Y-003 |
| Rankings ArrowRight | ISSUE A11Y-002 |
| Rankings Tab then Space | Changes to All Time |
| Filter dialog Escape | Closes and restores Filters focus |
| Wallet dialog Escape | Settled confirmation closes and restores Add funds focus |
| Notification panel Escape | Closes at tested phone/tablet/desktop widths |
| Filter Shift+Tab during opening | Initial observation landed on BODY; timing/state inconclusive, not promoted to a finding |

Shared skip link, main region, heading presence, labeled controls and product alt text were inspected. Native dialog handling and close controls exist; separate settled captures prevented treating transition frames as persistent viewport/focus bugs. Focus styling, form labels/errors, card image alternatives, table/tab roles and reduced-motion CSS/hero behavior were reviewed in source and representative views. Screenshot/axe review did not identify a broad text-contrast regression.

## Limits and implementation checks

No actual screen-reader session, full light-theme contrast sweep, high-contrast OS setting, exhaustive touch-target measurement, physical keyboard/IME test or all-route zoom campaign was performed. Small icon controls should be evaluated in context; no blanket “all targets are too small” claim is made. Checkout/security keyboard paths were sampled through existing browser flows and source, not fully certified key-by-key.

Re-run axe after each affected UI phase, but also exercise Tab/Shift+Tab/Enter/Space/Escape/arrows manually. Test dialogs after animations settle and with reduced motion. Verify names, state and focus restoration; do not remove useful ARIA or hide controls solely to silence a scanner. Evidence: `accessibility.json`, `interactions.json`, `ui-confirm.json` and screenshots.
''')
write('RESOURCE_USAGE_AUDIT.md','''# Resource usage and deployment audit

All numbers below are local observations on the audit machine or build artifacts. The application was not run against production services. No synthetic percentage improvement is promised.

| Resource | Measured result / scope |
|---|---|
| Frontend build | See FRONTEND_PERFORMANCE.md for all JS/CSS chunks and gzip sizes |
| Home initial desktop JS | 145,007 transferred bytes including shared chunks/response overhead |
| Home initial desktop CSS | 20,230 transferred bytes |
| Home initial desktop transfer / requests | ~948.2KiB / 83 requests; 7 API reads |
| Home initial mobile transfer / requests | ~1,031.0KiB / 48 requests at DPR 3 |
| Largest product image | 196,998 bytes, 800×800 WebP |
| Entire public asset tree | 149,513,494 bytes (142.6MiB) |
| Exact-duplicate asset storage | 21,273,614 redundant bytes upper bound |
| Backend Docker image | `docker image ls`: 459MB, tag codex-trustkart-audit:177fb10; local ARM build |
| Frontend Docker image | Not applicable: no frontend Dockerfile; static Vite deployment |
| Java late idle sample | RSS 187,152KiB (~182.8MiB), 0.0% CPU; audit process used -Xmx512m |
| Redis logical used memory | ~1.65MiB from INFO memory |
| Container memory/CPU | Point-in-time values in docker-resources.txt; not a peak/load benchmark |
| DB connections | 10 idle application connections plus active diagnostic query in initial sample |
| Slowest tested warm API median | Laptop facets 11.71ms / five local samples |
| INP / Lighthouse scores | Not measured |

Java RSS is an idle sample, not startup peak, container usage, retained-heap measurement or proof a 512MB hosted deployment is safe. Local host RAM/CPU and debug SQL differ from Render. Browser JavaScript heap medians are roughly 5–6MiB on measured pages; this excludes browser process, decoded images, GPU and native memory. No long-duration leak diagnosis was performed.

## Background resource behavior

Notification count polls every 120 seconds; hidden-tab polling requires browser-alert opt-in. Wallet and tracking have no dedicated frequent polling. Search placeholder rotates every 3.5 seconds; hero rotates every 7 seconds with cleanup/interaction/reduced-motion handling. Guest cleanup runs daily and demo stock replenishment every 15 minutes. These cadences do not justify WebSockets or a scheduler rewrite. BE-003 is the actionable background waste: unchanged notification polls can repeatedly write deleted milestones.

## Docker and deployment

Backend build passed using the existing multistage Dockerfile, Maven cache mounts, extracted jar layers, non-root JRE runtime and backend-only context. Runtime does not include Maven/node_modules or the build JDK. `.dockerignore` excludes target, IDE metadata and .env. No oversized frontend dependency tree was copied into that image. Retain debuggability/security; an unmeasured distroless/base-image migration is not a quick-win finding.

Render blueprint config sets pool size 5 and overrides JVM options for its declared small plan; the Docker default and local audit JVM settings differ. Comments claiming historical stress results were not independently accepted. This audit did not repeat a hosted 40-shopper memory test or verify current hosting quotas, so no arbitrary heap/pool values are proposed.

Vercel deploys static assets and proxies API requests. Production compression, CDN cache-hit rates, service cold starts and outgoing traffic were not measured. The 20.3MiB identical-image opportunity is build/deployment storage first; old URLs may be referenced by long-lived cached JSON. Coordinate compatibility instead of deleting assets immediately.

## Highest-value reductions

Prioritize DB-001, BE-002, BE-003, FE-001 and BE-001: query work, repeated writes and unused data. IMG-001 is lower priority because current images are already small and quality-preserving. No recommendation removes shopping functionality, high-resolution detail sources, useful notifications, security checks or transactional guarantees.
''')
write('SECURITY_OPTIMIZATION_RISKS.md','''# Security and correctness constraints for optimization

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
''')

# Test counts distinguish suite cases from observations and manual probes.
security=json.loads((E/'security-checks.json').read_text()); sec=[x for x in security if 'pass' in x]
write('TEST_RESULTS.md','''# Commands, tests and verification results

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
''')

bug='''# Bugs found

This index separates defects from pure optimization opportunities. Full reproduction, expected/actual behavior, files/lines, likely cause, risk and required tests live in the linked canonical finding. No bug has been fixed.

| ID | Severity | Defect | Reproduction evidence | Required regression |
|---|---|---|---|---|
'''
for f in findings:
 if f['bug']:bug+=f'| {link(f)} | {f["priority"]} | {f["title"]} | {f["evidence"]} | {f["test"]} |\n'
bug+='''
Pure optimization opportunities (not functional bugs): FE-001, DB-001, BE-001, BE-002 and IMG-001. QA-001/002 are regression-protection gaps rather than currently failing shopping features. BE-003 is a persistence algorithm defect with resource impact even though the visible count stays capped.

No P0 defect was demonstrated. The highest-priority bug is SEC-001. Existing repository tests all passed; the independent security assertion failed. See TEST_RESULTS.md for the distinction and remaining limits.
'''
write('BUGS_FOUND.md',bug)

byid={f['id']:f for f in findings}
plan='''# TrustKart Optimization Implementation Plan

## Current Verified State

Read AUDIT_SUMMARY.md, OPTIMIZATION_FINDINGS.md and TEST_RESULTS.md before editing. Baseline is `177fb10` on `trustkart-rebuild`; origin was fetched and this branch is current with its upstream. At audit time origin/main has an identical source tree with different merge history. Verify your current commit and diff first: another coding process may have changed the project since this audit.

The production build and 169 existing executed tests passed. Sixteen independent security assertions had fifteen passes and one failure (SEC-001). The audit inspected 27 route cases, 207 route/viewport observations, all 14 requested sizes, 54 axe scans and 30 measured cold loads. There are 18 prioritized findings, not a mandate to rewrite the application. Detailed files/lines and reproducible evidence are linked below.

## Rules

1. Verify each referenced finding still exists before changing it. Record obsolete/already-fixed findings instead of reintroducing an old fix.
2. Inspect status and concurrent work. Keep unrelated edits and untracked scripts/renders. Do not reset, stash, clean or switch over active work. Create an isolated worktree if useful with the user's normal workflow.
3. Preserve premium imagery, dark retail identity, desktop/tablet/mobile usability, fast search, accessibility and useful feedback. Do not redesign unrelated areas.
4. Preserve server pricing, wallet/order/ledger atomicity, stock checks, idempotency, ownership, CSRF, rate limits and session security. PostgreSQL stays authoritative.
5. Use local/dev services and synthetic data. Audit harnesses have temporary paths; read them before reuse. Never run synthetic fixture or key-deletion probes against production.
6. Establish the relevant pre-change failure/measurement, make one focused change, then run its targeted regression plus phase checks. Keep actual before/after numbers with identical fixtures; no invented percentages.
7. Treat migrations/cache semantics separately from cosmetic changes. Use additive, reversible transitions where possible. Commit/push/deploy only under the user's authorization for that implementation session.
8. The tasks below are future work. This audit has not implemented any of them.

## P0

No P0 was demonstrated. Do not manufacture emergency changes to fill this section. If new evidence reveals critical breakage, pause the optimization sequence and assess it.

## P1 — Address security before optimization

'''

def task(id,perfcheck=None,rollback=None):
 f=byid[id]
 return f'''### {id}: {f['title']}

**Verify first:** Read [{id}](OPTIMIZATION_FINDINGS.md#{id.lower()}) and reproduce it on the current branch. {f['repro']}

**Files likely involved:** {refs(f['files'])}.

**Exact problem:** {f['cause']}

**Recommended implementation:** {f['implement']}

**What NOT to change / UI verification:** {f['ux']} Recheck the affected route/interaction at the sizes specified by the canonical finding; backend-only work must produce the same visible cards, amounts and actions.

**Implementation risk:** {f['risk']}

**Pre-change test:** Capture the reproduction result and relevant evidence before editing; retain the current passing tests as the baseline.

**Post-change test:** {f['test']}

**Performance verification:** {perfcheck or f['resource']+' Compare the same fixture before and after; a correctness/UI fix needs no fabricated latency target.'}

**Security verification:** Apply the row for {id} in SECURITY_OPTIMIZATION_RISKS.md; re-run affected ownership/validation/cache/session tests with real middleware enabled.

**Rollback considerations:** {rollback or 'Keep the change isolated so its application diff can be reverted without reverting unrelated work. Preserve prior data and API contracts; do not delete migration history or production user state.'}

'''
plan+=task('SEC-001','Measure authenticated request SQL/latency after the fix; the security guarantee takes precedence over saving a single indexed read.','Do not restore the known fail-open Redis-miss behavior as a performance rollback. Retain an authoritative fallback; disable only optional caching if needed.')
plan+='## Phase 1: Safe Quick Wins\n\n'
for id in ['API-001','A11Y-001']:plan+=task(id)
plan+='## Phase 2: Responsive/UI Fixes\n\n'
for id in ['UX-001','UX-002','UX-003','UX-004']:plan+=task(id)
plan+='## Phase 3: Frontend Performance\n\n'+task('FE-001','Compare home body bytes and cache-cold tile-query count; preserve screenshots with and without recent history. No need for global memoization or extra lazy boundaries.')
plan+='## Phase 4: Images and Network\n\n'+task('QA-001')+task('IMG-001','Measure build/public bytes and actual repeat-image requests after canonicalization; compare rendered images at DPR 1 and 3. Do not call the 20.3MiB storage upper bound per-page savings.','Retain old files while deployed cached manifests can reference them; roll back manifest mappings independently. Do not delete provenance.')
plan+='## Phase 5: API Optimization\n\n'+task('API-002','Coordinate count correctness tests with DB-001 in Phase 7. Compare SQL and latency on ordinary and empty pages; accept a bounded count query if needed for correctness.')
plan+='## Phase 6: Backend/JPA\n\n'
for id in ['BE-001','BE-002','BE-003']:
 plan+=task(id,rollback='Prefer additive durable metadata if the fix needs schema changes. Preserve prior notification history, preferences and read status. Roll back code as a coordinated unit; do not drop migration records.' if id=='BE-003' else None)
plan+='## Phase 7: PostgreSQL\n\n'+task('DB-001','Run the stored EXPLAIN on the same seed before/after. Image probe loops should approach returned page size rather than 2,201. Compare all returned IDs, counts, sorts and timing; do not require an arbitrary percentage gain.','Restore the previous query if equivalence fails, retaining a separately verified API-002 count correction. No speculative index migration is required by this finding.')
plan+='''## Phase 8: Redis

Re-verify SEC-001's fix under missing-key and outage/recovery cases after all backend changes. This phase has no independent cache-everything task. Keep rate limiting and OAuth state TTLs; PostgreSQL remains the authority for commerce/session revocation. Relevant files: SessionRevocation.java, JwtConfig.java and existing Redis/rate-limit configuration.

Pre-change: capture current Redis key TTL/memory and authenticated SQL behavior in isolated services. Post-change: valid sessions work; revoked/unknown sessions fail even after lost keys. UI: no login loop. Performance: measure the cost of authoritative reads, rather than skipping them. Security: retain fail-closed authorization. Rollback: remove optional cache enhancements, never restore the known missing-key bypass.

'''
plan+='## Phase 9: Accessibility\n\n'
for id in ['A11Y-002','A11Y-003']:plan+=task(id)
plan+='''Recheck A11Y-001 from Phase 1 and UX-001/002 focus/operability after keyboard changes. Use both automated scans and manual keyboard behavior; scanner scores alone are insufficient.

## Phase 10: Docker/Deployment

'''+task('QA-002','Record CI duration and avoid redundant stacks/workers. Existing Docker multistage runtime is sound; image size alone does not justify a base-image replacement.','Keep workflow additions in a separate diff; if orchestration flakes, repair readiness/isolation without disabling the existing build/security jobs or silently ignoring E2E failures.')
plan+='''No other Docker change is required by current evidence. Retain non-root JRE, layered builds, .dockerignore and current security headers. Do not copy the audit DEBUG flags or local insecure-cookie setting into production. Before any later deployment verify real environment/limits separately; this plan does not authorize a deployment.

## Phase 11: Final Regression Testing

Follow VERIFICATION_CHECKLIST.md. Run backend verify, frontend lint/typecheck/format/tests/build, expanded image validation and existing E2E on an isolated stack. Re-run the independent reproductions for all changed IDs, route/viewport screenshots, keyboard/axe, console/network and targeted SQL plans.

Required end-to-end behaviors: guest and registered search/product/cart; priced variants; wallet funding/mode; checkout with server quote and repeated idempotency key; receipt/tracking/refund; collection/wishlist; notifications after 30 and 200 records; leaderboard privacy/ranks; account/session revocation; normal-user/admin ownership boundaries. Preserve the premium visual identity.

Compare results with this audit's measured baseline using the same viewport, DPR, fixture, cache state and throttling. Record unmapped/untested states honestly. Review the final Git diff for unrelated source/config/catalog/secret changes and document each resolved ID with evidence. Do not claim all findings fixed merely because lint/build pass.
'''
write('CLAUDE_IMPLEMENTATION_PLAN.md',plan)

check='''# Reusable optimization verification checklist

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
'''
for f in findings:check+=f'| {link(f)} | [ ] | [ ] | [ ] | [ ] | Not implemented by this audit |\n'
write('VERIFICATION_CHECKLIST.md',check)

write('AUDIT_SUMMARY.md','''# TrustKart independent audit summary

**Audit complete; implementation has not started.** Baseline: `177fb10` on `trustkart-rebuild`, September 30–October 1, 2026. Local origin was fetched and the branch fast-forward checked: already current with origin/trustkart-rebuild. Origin/main has newer merge history but an identical source tree. Existing untracked render scripts and concurrent coding processes were preserved. Only `codex/` was added; no application source/config/tests/migrations/docs were changed, committed or pushed.

## Verified state and overall health

TrustKart is a working React/TypeScript/Vite retail SPA backed by Java 21/Spring Boot, PostgreSQL and Redis. It contains **2,201 products, 198 brands and 112 categories**, with catalog/search, comparison, wishlist/cart, simulated wallet/checkout, seven-day tracking, collection, notifications, rankings, account/security and optional Google login. Smart/semantic AI search and an admin dashboard are not implemented; protected admin ranking APIs exist. Read CODEBASE_ARCHITECTURE.md before assuming planned features are live.

The existing build/test baseline is healthy and the premium design is coherent. Targeted inspection nevertheless exposed one important session-security defect, several concrete UI/accessibility defects and avoidable database/payload work. This is neither a clean bill of health nor evidence that the architecture needs a rewrite.

## Scope and executed verification

Repository structure, all module areas, 13 migrations, catalog/image manifests, Docker/CI/deployment/config examples, existing docs and tests were inspected. Builds ran in a temporary commit snapshot; app services used isolated local PostgreSQL/Redis and synthetic accounts. Frontend production build, lint, typecheck, formatting, tests; backend compile/package/verify; existing Playwright; image validation; npm production audit; compose validation and Docker build completed.

Browser evidence covers **27 route cases**, **207 route/viewport observations**, **all 14 requested viewport sizes**, **54 axe scans**, selected manual keyboard/overlay interactions and **30 cold production-build lab loads**. Twenty-one APIs were timed five times each; request-correlated SQL and four read-only EXPLAIN plans were captured. Targeted fixtures exposed wishlist query growth and notification retention churn. Routes were inspected visually through full captures and desktop/tablet/mobile contact sheets.

## Findings by priority

| Priority | Count | Summary |
|---|---:|---|
| P0 | 0 | No critical breakage/exploit demonstrated |
| P1 | 1 | SEC-001: revoked access JWT accepted after its Redis denylist key disappears |
| P2 | 16 | UI/keyboard defects, malformed-input/page-count bugs, query/payload waste, notification churn and test-coverage gaps |
| P3 | 1 | IMG-001: byte-identical images duplicated across static paths |
| **Total** | **18** | Canonical details and evidence in OPTIMIZATION_FINDINGS.md |

SEC-001 was reproduced: old cookie returned 401 after logout, then 200 after deleting only that synthetic session's Redis revocation key while PostgreSQL still recorded revocation. The affected window is the remaining short-lived access token lifetime. Fix the missing-key authorization semantics first; do not suppress database checks for speed.

## Test status

Existing tests: **169 executed, 169 passed, zero failed** (136 backend, 11 Vitest, 22 Playwright); eight intentional Playwright project skips. Independent security assertions: **16 executed, 15 passed, one failed** (SEC-001). Combined counted tests/assertions: **185 executed, 184 passed, one failed**. Browser scans/manual probes are reported separately rather than inflating that count. TEST_RESULTS.md explains infrastructure retries and measurement limits.

## Major UI and responsive issues

1. UX-001: notification panel starts 34px outside the left edge at 320/390 widths.
2. UX-002: comparison tray overlaps the phone product purchase bar by 45px.
3. UX-003: tablet account summary truncates Unlimited mode and large monetary values.
4. UX-004: only the first 30 notifications are reachable despite 200 retained entries.
5. A11Y-003: recent-search deletion is pointer-only; A11Y-001/002 additionally cover inline-link cues and leaderboard keyboard tabs.

Root-page overflow checks passed, illustrating why they are insufficient by themselves. Search typing/keyboard submission and settled wallet/filter Escape behavior worked. Retain the premium image wells, dark theme, intentional shelves and desktop whitespace.

## Largest safe optimization opportunities

| Finding | Measured waste | Recommended direction |
|---|---|---|
| DB-001 | 2,201 primary-image probes for 24 returned products | Page before image hydration; preserve sort/count/filter semantics |
| BE-003 | 40 INSERTs plus retention DELETE on each unchanged 60-order notification poll | Preserve durable milestone-generation knowledge beyond display retention |
| BE-002 | 4/6/8 SQL statements for 1/2/3 wishlists | Batch owned associations and one distinct-product lookup |
| FE-001 | At least 20 unused home cards / 15,331 JSON bytes plus five cache-rebuild queries | Return/build only the visible home tile contract |
| BE-001 | Repeated full category metadata query/rebuild on list/search/suggest | Reuse immutable metadata with finite freshness and invalidation |

IMG-001 offers a lower-priority **20.3MiB storage upper bound** from exact duplicates. Current 400/800px WebP assets are already small; the largest product image is 196,998 bytes. Do not sacrifice sharpness to chase a score.

## Measured performance/resource snapshot

Desktop home initially transferred ~948.2KiB across 83 requests, including 145,007 bytes JS and 20,230 bytes CSS. Median observed home LCP was 568ms desktop and 884ms under the stated mobile simulation; these are local lab values, not production percentiles. The complete raw/gzip bundle and other vitals are in FRONTEND_PERFORMANCE.md. Slowest tested warm API median was facets at 11.71ms. Backend Docker image displayed 459MB locally. Frontend Docker image is not applicable. Lighthouse scores and INP: **Not measured**.

Backend/database changes should remove demonstrated work, not presume local millisecond requests are already harming production users. SQL plans used the current 2,201-product catalog; leaderboard history was small. No arbitrary index, pool-size, heap-size or package replacement is justified by this evidence.

## Things Claude should not change

Preserve authoritative server pricing, wallet/ledger/order transactions, stock checks, idempotency, ownership, CSRF and password/session protections. Keep PostgreSQL authoritative; fix Redis revocation correctness rather than caching more security state blindly. Preserve timestamp-derived tracking, mutation-driven wallet/cart updates, two-minute notification polling, fast 150ms cancellable search suggestions, existing lazy routes and responsive high-quality images.

## Limits and handoff

No production services were contacted, no real Google consent tested, no hosted load/CPU profiling run, and no complete screen-reader/Safari/Firefox/light-theme campaign performed. React commit profiling and field Web Vitals were not available. Some networkidle waits timed out during the broad harness; settled captures and later measurements are identified explicitly. A green test count is not a security certificate.

Start implementation with [CLAUDE_IMPLEMENTATION_PLAN.md](CLAUDE_IMPLEMENTATION_PLAN.md), verify each finding still exists, and follow [VERIFICATION_CHECKLIST.md](VERIFICATION_CHECKLIST.md). Evidence and screenshots remain in this folder.

Implementation has NOT been started. Review codex/CLAUDE_IMPLEMENTATION_PLAN.md before making changes.
''')
print('Wrote',len(list(R.glob('*.md'))),'reports')

# Completed audit lifecycle, recorded separately from application findings.
for filename in ['README.md','TEST_RESULTS.md','RESOURCE_USAGE_AUDIT.md']:
 p=R/filename
 p.write_text(p.read_text()+'''\n## Audit closeout\n\nThe named audit browser session, temporary Java API and Vite preview processes were stopped. Only `codex-trustkart-audit-postgres-1` and `codex-trustkart-audit-redis-1` were stopped; existing unrelated containers/processes were not stopped. The temporary snapshot, stopped isolated fixture containers/volume and local audit Docker image were retained for reproducibility; they consume disk but no running application CPU. Final Git checks showed no tracked or staged application diff, with only `codex/` plus pre-existing `scripts/renders/` untracked. No commits, pushes or deployments occurred.\n''')
