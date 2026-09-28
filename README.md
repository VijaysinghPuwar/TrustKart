# TrustKart

**Shop everything. Spend nothing.**

TrustKart is an online technology store that looks and works like a real one. You can browse over a thousand
products, from iPhones and gaming laptops to GPUs and rack servers, fill a cart and check out. Instead of a card
you pay with a **TrustKart Wallet** full of virtual money, so nothing real is ever charged or shipped.

🔗 **Live site: [trustkart-xi.vercel.app](https://trustkart-xi.vercel.app)**

---

## What you can do

- **Browse 1,192 real products** across 18 departments (phones, laptops, PC parts, servers, networking, TVs,
  cameras, smart home and more), each with an official product photo and full specs.
- **Search like you talk.** Type "gaming laptop under $2000" and the store understands the budget and category.
- **Pick your configuration.** Choose storage, colour or model and watch the price update, just like on Apple's
  or Samsung's site.
- **Check out in four steps** (cart, delivery, payment, review) with a saved address book.
- **Track your order** through a seven-day delivery timeline, from "Order placed" to "Delivered", with a
  tracking number and shipment history. Cancel before it ships, or return it within 30 days.
- **Get notified.** A bell in the header tells you when an order ships, goes out for delivery and arrives.
- **Build a collection.** Everything you buy lands in *My Collection*, valued at today's prices.
- **Unlock 76 achievements**, from *First purchase* to *Centibillionaire* (spend $100 billion), with bronze,
  silver, gold, platinum and legendary tiers.
- **Compare products** side by side, keep wishlists, and switch between light and dark mode.
- **Sign in with email or Google**, or just shop as a guest; your cart follows you when you sign in.

## How it's built

| Part | Technology |
|---|---|
| Website | React 19, TypeScript, Vite, Tailwind CSS, TanStack Query |
| Server | Java 21, Spring Boot 4, Spring Security |
| Data | PostgreSQL 17 (full-text search, pgvector), Redis |
| Testing | JUnit, Testcontainers, Vitest, Playwright, a load/stress test script |
| Hosting | Vercel (website) + a container host (server) |

A few things worth knowing:

- **Prices always come from the server.** The browser only ever sends product IDs and quantities, so no one can
  change a price by editing a request.
- **Checkout is safe under pressure.** Stock is reserved atomically, double-clicking "Place order" never creates
  two orders, and two shoppers can't buy the last unit twice. A stress test runs 100 simultaneous checkouts and
  then checks that the wallet ledger, stock and order records all still add up.
- **Security is built in**: Argon2 password hashing, short-lived session tokens in secure cookies, CSRF
  protection, rate limiting, account lockout and a strict Content Security Policy.

## Run it on your computer

You need **Docker**, **Java 21** and **Node.js 22**.

```bash
# 1. Start the database and Redis
cp .env.example .env          # then fill in the values (the file explains each one)
docker compose up -d

# 2. Start the server (loads the product catalog on first run)
set -a; source .env; set +a   # make the settings visible to the server
cd backend
./mvnw spring-boot:run

# 3. In a second terminal, start the website
cd frontend
npm install
npm run dev
```

Open **http://localhost:5173** and start shopping. Every new visitor gets $100,000 in virtual funds.

### Tests

```bash
cd backend && ./mvnw verify                  # server tests (uses Docker)
cd frontend && npm test && npx playwright test   # website unit and browser tests
python3 scripts/stress_test.py               # load and consistency test against a running server
```

## Project layout

```
backend/     Spring Boot server: catalog, cart, checkout, wallet, orders, notifications
frontend/    React website
catalog-data/  Product data and image sources used to build the catalog
scripts/     Image validation, stress test and catalog tools
docs/        Deployment guide, feature list and image credits
```

## More

- [Deployment guide](docs/DEPLOYMENT.md): putting TrustKart online
- [Feature list](docs/FEATURE_MATRIX.md): what's done and what's planned
- [Image credits](docs/ASSET_SOURCES.md): where every product photo comes from

Product names, logos and photos belong to their respective owners and are used here only to illustrate a
non-commercial portfolio project. No affiliation or endorsement is implied, and nothing on TrustKart can
actually be bought.
