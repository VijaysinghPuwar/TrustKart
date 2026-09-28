# Deploying TrustKart

TrustKart is two deployables:

| Part | What it is | Where it runs |
|---|---|---|
| `frontend/` | React + Vite single-page app, static files | **Vercel** |
| `backend/` | Spring Boot 4 on Java 21, needs PostgreSQL (pg_trgm, pgvector) and Redis | Any container host: Render, Fly.io, Railway, Google Cloud Run… |

Vercel cannot run a JVM service, so the API lives on a container host. The browser still only ever talks to the
Vercel domain: `vercel.json` rewrites `/api/*` to the backend. That keeps the auth cookies first-party
(`SameSite=Lax/Strict`, `HttpOnly`, `Secure`), avoids CORS entirely and matches local development, where Vite
proxies `/api` the same way.

```
browser ──► https://trustkart.vercel.app ──┬─ static files (Vercel CDN)
                                           └─ /api/* ──rewrite──► https://<your-api-host>/api/*
                                                                   ├─ PostgreSQL 17 + pgvector
                                                                   └─ Redis (rate limits, sessions)
```

## 1. Database and Redis

- **PostgreSQL 17** with the `vector` and `pg_trgm` extensions: Neon, Supabase and most managed Postgres offer both.
  Flyway creates the extensions and the schema on first start (`V1__extensions.sql`), so the database user needs
  permission to `CREATE EXTENSION`, or create them once by hand.
- **Redis 7+**: e.g. Upstash. Hosted Redis usually requires TLS: set `TRUSTKART_REDIS_SSL=true`.

## 2. Backend (container host)

Build from `backend/Dockerfile` (multi-stage, non-root, layered jar). Health check: `GET /actuator/health`.
The app listens on `$PORT` (default 8080).

Environment variables (never commit real values):

| Variable | Required | Example / notes |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | yes | `prod` |
| `TRUSTKART_DB_URL` | yes | `jdbc:postgresql://host:5432/trustkart?sslmode=require` |
| `TRUSTKART_DB_USER`, `TRUSTKART_DB_PASSWORD` | yes | |
| `TRUSTKART_DB_POOL_SIZE` | no | Default 10. Keep within your database plan's connection limit. |
| `TRUSTKART_REDIS_HOST`, `TRUSTKART_REDIS_PORT` | yes | |
| `TRUSTKART_REDIS_USERNAME`, `TRUSTKART_REDIS_PASSWORD` | if set | Upstash: username `default` |
| `TRUSTKART_REDIS_SSL` | hosted Redis | `true` |
| `TRUSTKART_JWT_SECRET` | yes | 32+ random bytes, base64: `openssl rand -base64 48` |
| `TRUSTKART_PUBLIC_ORIGIN` | yes | `https://trustkart.vercel.app`: the origin browsers use; pins the Google sign-in callback |
| `TRUSTKART_ALLOWED_ORIGINS` | yes | Same origin as above (comma-separated if several) |
| `TRUSTKART_SEED_CATALOG` | first deploy | `true` loads the demo catalog (905 products) on startup; safe to leave on, seeding is idempotent |
| `TRUSTKART_TRUSTED_PROXIES` | see below | Regex of proxy addresses whose `X-Forwarded-*` headers are trusted |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | optional | Enables "Sign in with Google" |
| `TRUSTKART_API_DOCS_ENABLED` | no | `false` in production |

`TRUSTKART_SECURE_COOKIES` defaults to `true`; only local plain-http development turns it off.

### Client IPs behind the Vercel rewrite

Rate limits (login, search, checkout) key on the client IP. Behind a rewrite, the backend's TCP peer is Vercel's
edge, and the real client is in `X-Forwarded-For`. The backend only believes that header from addresses matching
`TRUSTKART_TRUSTED_PROXIES` (default: private ranges and localhost, which is right for local and Docker setups).

- If your container host puts its own load balancer in front of the app, its address is usually already in the
  private ranges and nothing needs changing.
- If the backend sees Vercel's public edge addresses, every visitor shares a handful of IPs and rate limits trip
  early. Either restrict the backend so it is reachable only through Vercel and set `TRUSTKART_TRUSTED_PROXIES=.*`,
  or leave the default and accept coarser rate limiting. **Never trust `.*` on a backend that is publicly
  reachable**: anyone could then spoof their IP to dodge rate limits and account lockout.

## 3. Frontend (Vercel)

1. Import the repository in Vercel and leave **Root Directory** at the repository root. The root `vercel.json`
   installs and builds `frontend/` (`npm ci --prefix frontend`, `npm run build --prefix frontend`) and serves
   `frontend/dist`.
2. In `vercel.json`, replace `https://api.trustkart.example.com` in the `/api/:path*` rewrite with your
   backend's URL. Rewrites are static configuration; they can't read environment variables.
3. Deploy. Then set `TRUSTKART_PUBLIC_ORIGIN` and `TRUSTKART_ALLOWED_ORIGINS` on the backend to the Vercel URL
   (or your custom domain) and restart it.

What `vercel.json` sets up:

- `/api/*` → backend; any other path without a file → `index.html` (client-side routing).
- `/assets/*` (content-hashed JS/CSS): `Cache-Control: public, max-age=31536000, immutable`.
- `/images/*`, `/brand/*`: one week, then stale-while-revalidate.
- `index.html`: always revalidated, so a new deploy is picked up immediately.
- Security headers on every response: a strict Content-Security-Policy (no inline or third-party scripts; images
  only from this origin plus Google profile photos), HSTS, `nosniff`, `Referrer-Policy`, `Permissions-Policy`,
  `Cross-Origin-Opener-Policy`, and `frame-ancestors 'none'`. The CSP was checked against the production build on
  the main pages with zero violations.
- Source maps are generated but not referenced from the bundles (`sourcemap: 'hidden'`).

Until a backend is deployed and the `/api` rewrite points at it, the site loads but every data request fails, so
the home page shows "The store didn't load".

## 4. Google sign-in (optional)

In Google Cloud Console → Credentials, add this authorized redirect URI:

```
https://<your-vercel-domain>/api/v1/auth/oauth2/callback/google
```

Set `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` and `TRUSTKART_PUBLIC_ORIGIN` on the backend. Without the
credentials the sign-in page simply doesn't offer Google.

## 5. Verify a deployment

```bash
curl -fsS https://<vercel-domain>/api/v1/catalog/home > /dev/null && echo "API through Vercel: ok"
curl -sI https://<vercel-domain>/ | grep -i content-security-policy
```

Then, with a staging database, run the load and consistency test against the API host:

```bash
python3 scripts/stress_test.py --base https://<api-host> --db-container <local tunnel or skip>
```

The stress test drives concurrent browsing, a rate-limit flood, 60 parallel checkouts with double-submits, an
oversell race, order tracking and notifications, then checks ledger, stock and idempotency invariants in the
database. Run it against staging, never production: it places real (virtual) orders.
