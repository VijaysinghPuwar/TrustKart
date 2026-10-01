# How TrustKart stays fast

A short guide to the performance decisions in this codebase and where to find them. Measured results are in
[PERFORMANCE_AUDIT.md](PERFORMANCE_AUDIT.md).

## Loading the site

- **Route code splitting.** Every page except Home and 404 is a `React.lazy` chunk (`frontend/src/app/router.tsx`),
  so a visitor on the home page never downloads checkout, account or rankings code. The entry chunk is 85 KB
  gzipped.
- **Early data for the home page.** `public/theme-init.js` already runs in `<head>` (it sets the theme before first
  paint). On `/` it also starts the home catalog request and, once that arrives, preloads the first banner photo
  with the same `srcset`/`sizes` the banner uses. The app picks the request up instead of making its own. This keeps
  the strict CSP (`script-src 'self'`) because nothing is inline.
- **No layout shift.** `<main>` is at least one screen tall, so the footer never appears and then jumps while a page
  loads. Placeholders share their real component's sizing (the home banner reuses the same class constants), the
  banner reserves the same lines on every slide, and images carry width and height.
- **Caching headers.** Hashed assets under `/assets` are `immutable` for a year; HTML is revalidated on every visit;
  product photos are cached for a week (`vercel.json`).
- **The catalog doesn't wait for a sleeping server.** The API runs on a free instance that sleeps after 15 idle
  minutes and takes minutes to wake. Public catalog and search responses are sent with
  `max-age=60, s-maxage=300, stale-while-revalidate=604800, stale-if-error=604800` (`common/web/PublicCache.java`),
  so Vercel's edge serves its last copy instantly and refreshes it in the background; the visit itself wakes the
  API. These responses never set cookies (the CSRF cookie is skipped for them; writes are still checked), so they
  are safe to share. Only small personal header data (account, balance, cart count) waits for the API, and the
  "starting up" notice ignores it.

## Images

Product photos are pre-built WebP files at 400 and 800 px (about 9 KB and 30 KB on average). `ProductImage`
(`frontend/src/components/commerce/ProductImage.tsx`) sets `srcset`, `sizes`, intrinsic width and height,
`loading="lazy"` below the fold, `decoding="async"`, `fetchpriority="high"` for the first cards, and falls back
once to a neutral placeholder if a file fails, without retrying.

## Talking to the API

- **TanStack Query** (`frontend/src/app/App.tsx`): data is fresh for 30 s by default, no refetch on window focus,
  and retries only for server and network errors (never 4xx).
- **Queries run only when needed.** The "Buy now" quote runs only while its dialog is open; search suggestions start
  at two characters (an empty box shows local examples); the wallet and cart are invalidated by the mutations that
  change them rather than polled.
- **Notifications.** Order milestones happen over days, so the bell refreshes every two minutes while the tab is
  visible, when the tab regains focus, and in the background only if the shopper turned on browser alerts. Polling
  was chosen over WebSockets or server-sent events: one small request every two minutes is cheaper than holding a
  connection open per visitor on a small server.
- **Order tracking is derived, not polled.** Tracking stages are computed from the order time and the clock
  (`OrderTracking`), so no background job advances orders and the page doesn't need to poll.
- **Pagination everywhere it can grow.** Product listings and search are paginated (at most 60 per page), orders,
  notifications and wallet transactions too; leaderboards are capped at 50 and 100.

## Server

- **Card-sized payloads.** Listings return `ProductCardDto` (name, brand, price, stock, one image); descriptions,
  specs, the gallery and options are only in the product detail response.
- **No N+1 on the hot paths.** Listings are one SQL query that picks the page of products first and only then joins
  each card's first image (so a 24-card page looks up 24 images, not one per matching product); the cart and checkout
  load every product in one query to price option choices; the orders list batch-loads its items; wishlists load
  every list's items and cards in two queries however many lists there are.
- **In-process caches, not Redis, for public catalog data.** Home (60 s, built from only the six collection tiles
  it shows) and the category tree (5 min, also used to resolve every listing, search and suggestion) are
  identical for everyone and small, so they live in memory (`common/cache/Memo.java`): one caller rebuilds an
  expired value while others wait, and seeding the catalog clears them. The leaderboard caches each board for 30 s
  and drops it when an order commits. PostgreSQL stays the source of truth for all of it.
- **Redis is for shared, short-lived state only:** rate-limit buckets and OAuth sign-in state, both with TTLs. If
  Redis is down, rate limits fail open and the store keeps working. Sign-out is not one of them: every authenticated
  request checks its session row in PostgreSQL (one primary-key lookup), so a lost cache entry can never bring a
  signed-out token back.
- **Reads don't lock.** Viewing the wallet or a checkout quote reads the balance without `SELECT ... FOR UPDATE`;
  only placing an order or changing funds takes the row lock.
- **Notifications poll cheaply.** A poll where nothing changed runs read-only queries: it checks which milestone
  keys already exist before inserting, reads ranks from the cached leaderboards, and trims old rows only after an
  insert. Once a shopper has the 200 notifications retention keeps, older milestones that were trimmed are not
  inserted again (they would only be trimmed again on every poll).
- **Indexes follow queries.** Full-text search (GIN), category and price, product images by product, orders by
  shopper and date, a partial index on completed orders for rankings, and `product_collection(product_id)` for a
  product's collection tags.
- **Fast boots.** The catalog seeder stores a fingerprint of its data files and skips work when they haven't
  changed; when they have, it writes in 100-product transactions so the persistence context stays small.

## Budgets

Set from the measured state so a regression is visible, not aspirational:

| Budget | Current | Limit |
|---|---|---|
| Entry JavaScript (gzip) | 85 KB | 110 KB |
| CSS (gzip) | 19 KB | 30 KB |
| Home, phone, total transfer after scrolling | 1.37 MB | 1.6 MB |
| CLS on any measured page | ≤ 0.005 | 0.05 |
| SQL statements for an unread-count poll | 13 | 16 |

## Guards

- `frontend/e2e/responsive.spec.ts`: no horizontal scroll on key pages from 320 to 1920 px, a stable banner height,
  and a one-row phone header.
- `frontend/scripts/measure.mjs` and `frontend/scripts/responsive-audit.mjs` reproduce the measurements.
- `frontend/e2e/overlays-and-keyboard.spec.ts`: the phone notification panel and compare tray stay on screen and out
  of each other's way, account values never truncate, and the keyboard paths for rankings tabs and recent searches.
- Backend integration tests cover notifications, leaderboards, cart and checkout behaviour after the query changes,
  including concurrent cart and wishlist writes.
