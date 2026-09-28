#!/usr/bin/env python3
"""
Load and stress test for a running TrustKart backend (default http://localhost:8080).

Phases:
  1. browse   - N workers, each a different client IP, hammer home, category listings, product pages,
                search and suggestions for a fixed duration. Reports status mix and latency percentiles.
  2. abuse    - one IP floods search; expects clean 429s (never 5xx).
  3. checkout - M guest shoppers buy concurrently: cart add, quote, place order. Each shopper also fires the
                same Idempotency-Key twice at once (must produce one order), and all shoppers race for a
                low-stock product (must never oversell).
  4. tracking - order detail, notifications and unread counts under concurrency.
  5. invariants - database checks: ledger arithmetic, balances, stock never negative, one order per key.

Exit code is non-zero if any check fails. Stdlib only.

  python3 scripts/stress_test.py [--base URL] [--workers 40] [--seconds 45] [--shoppers 60]
"""
from __future__ import annotations

import argparse
import http.cookiejar
import json
import random
import statistics
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.request
import uuid
from collections import Counter
from concurrent.futures import ThreadPoolExecutor

FAILURES: list[str] = []


def fail(msg: str) -> None:
    FAILURES.append(msg)
    print("  FAIL " + msg)


class Client:
    """A browser-like client: its own cookie jar, CSRF header and a stable fake client IP."""

    def __init__(self, base: str, ip: str):
        self.base = base
        self.ip = ip
        self.jar = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar))

    def _xsrf(self) -> str | None:
        return next((c.value for c in self.jar if c.name == "XSRF-TOKEN"), None)

    def call(self, method: str, path: str, body=None, headers=None, timeout=30):
        data = None if body is None else json.dumps(body).encode()
        h = {"X-Forwarded-For": self.ip, "Accept": "application/json"}
        if data is not None:
            h["Content-Type"] = "application/json"
        if method != "GET":
            if self._xsrf() is None:
                self.call("GET", "/api/v1/auth/csrf")
            h["X-XSRF-TOKEN"] = self._xsrf() or ""
        h.update(headers or {})
        req = urllib.request.Request(self.base + path, data=data, headers=h, method=method)
        t0 = time.perf_counter()
        try:
            with self.opener.open(req, timeout=timeout) as r:
                raw = r.read()
                status = r.status
        except urllib.error.HTTPError as e:
            raw = e.read()
            status = e.code
        except Exception as e:  # connection reset, timeout
            return 0, {"error": str(e)}, (time.perf_counter() - t0) * 1000
        ms = (time.perf_counter() - t0) * 1000
        try:
            payload = json.loads(raw) if raw else None
        except ValueError:
            payload = None
        return status, payload, ms


def pct(values: list[float], p: float) -> float:
    if not values:
        return 0.0
    s = sorted(values)
    return s[min(len(s) - 1, int(round(p / 100 * (len(s) - 1))))]


def report(name: str, statuses: Counter, lat: list[float]) -> None:
    total = sum(statuses.values())
    print(f"  {name}: {total} requests, status {dict(statuses)}")
    if lat:
        print(f"    latency ms  p50 {pct(lat, 50):.0f}  p95 {pct(lat, 95):.0f}  p99 {pct(lat, 99):.0f}  max {max(lat):.0f}")


# ---- 1. browse ------------------------------------------------------------------------------------------------

def phase_browse(base: str, workers: int, seconds: int) -> None:
    print(f"\n[1] browse: {workers} clients for {seconds}s")
    c = Client(base, "10.200.0.1")
    _, cats, _ = c.call("GET", "/api/v1/catalog/categories")
    slugs = [x["slug"] for x in cats] + [ch["slug"] for x in cats for ch in x["children"]]
    products = []
    for page in range(5):
        _, d, _ = c.call("GET", f"/api/v1/catalog/products?page={page}&size=48")
        products += [p["slug"] for p in d["items"]]
    queries = ["iphone", "laptop", "rtx 5090", "nas", "4k monitor", "gaming laptop under 2000", "ssd 2tb",
               "mechanical keyboard", "router wifi 7", "ecc memory", "zzzz-no-match", "'; drop table--", "ü💥"]

    statuses: Counter = Counter()
    lat: list[float] = []
    lock = threading.Lock()
    stop = time.time() + seconds

    def worker(i: int) -> None:
        # Each request comes from one of ~5,000 visitors, so per-IP limits see realistic traffic.
        cl = Client(base, "")
        rnd = random.Random(i)
        while time.time() < stop:
            cl.ip = f"10.201.{rnd.randint(0, 19)}.{rnd.randint(1, 250)}"
            path = rnd.choice([
                "/api/v1/catalog/home",
                f"/api/v1/catalog/products?category={rnd.choice(slugs)}&page={rnd.randint(0, 2)}&size=24",
                f"/api/v1/catalog/products/{rnd.choice(products)}",
                f"/api/v1/search?q={urllib.request.quote(rnd.choice(queries))}",
                f"/api/v1/search/suggest?q={urllib.request.quote(rnd.choice(queries)[:3])}",
                f"/api/v1/catalog/categories/{rnd.choice(slugs)}/facets",
            ])
            s, _, ms = cl.call("GET", path)
            with lock:
                statuses[s] += 1
                lat.append(ms)
                if s >= 500 or s == 0:
                    fail(f"browse {path} -> {s}")

    with ThreadPoolExecutor(workers) as ex:
        list(ex.map(worker, range(workers)))
    report("browse", statuses, lat)
    if statuses[429]:
        fail(f"browse hit {statuses[429]} rate limits despite distinct client IPs")
    if pct(lat, 95) > 2000:
        fail(f"browse p95 latency {pct(lat, 95):.0f} ms exceeds 2000 ms")


# ---- 2. abuse -------------------------------------------------------------------------------------------------

def phase_abuse(base: str) -> None:
    print("\n[2] abuse: one IP floods search (limit 120/min)")
    cl = Client(base, "10.202.0.1")
    statuses: Counter = Counter()
    with ThreadPoolExecutor(20) as ex:
        for s, _, _ in ex.map(lambda _: cl.call("GET", "/api/v1/search?q=ssd"), range(300)):
            statuses[s] += 1
    report("abuse", statuses, [])
    if not statuses[429]:
        fail("search flood from one IP was never rate limited")
    if any(s >= 500 or s == 0 for s in statuses):
        fail(f"search flood produced server errors: {dict(statuses)}")


# ---- 3. checkout ----------------------------------------------------------------------------------------------

ADDRESS = {"label": "Home", "fullName": "Load Tester", "line1": "1 Market St", "city": "San Francisco",
           "region": "CA", "postalCode": "94105", "country": "US"}


def place(cl: Client, body: dict, key: str):
    return cl.call("POST", "/api/v1/purchases", body, {"Idempotency-Key": key})


def phase_checkout(base: str, shoppers: int) -> list[tuple[Client, str]]:
    print(f"\n[3] checkout: {shoppers} guest shoppers in parallel")
    c = Client(base, "10.203.0.1")
    _, d, _ = c.call("GET", "/api/v1/catalog/products?page=0&size=48&sort=PRICE_ASC")
    # Well-stocked items, so a sell-out doesn't masquerade as a failure (the oversell phase covers that).
    cheap = [p for p in d["items"] if p["maxQuantity"] >= 2 and (p.get("stockLeft") is None)][:10]
    statuses: Counter = Counter()
    lat: list[float] = []
    orders: list[tuple[Client, str]] = []
    lock = threading.Lock()

    def shopper(i: int) -> None:
        cl = Client(base, f"10.204.{i // 250}.{i % 250 + 1}")
        rnd = random.Random(1000 + i)
        for p in rnd.sample(cheap, 2):
            s, _, ms = cl.call("POST", "/api/v1/cart/items", {"productId": p["id"], "quantity": 1})
            with lock:
                statuses[s] += 1; lat.append(ms)
        s, quote, ms = cl.call("GET", "/api/v1/checkout/quote")
        with lock:
            statuses[s] += 1; lat.append(ms)
        if s != 200:
            fail(f"quote -> {s} {quote}")
            return
        body = {"deliveryPreset": "ADDRESS", "simulationAddress": ADDRESS, "expectedTotal": quote["total"]}
        key = str(uuid.uuid4())
        # Double submit with the same key at the same moment: must yield exactly one order.
        with ThreadPoolExecutor(2) as ex:
            results = list(ex.map(lambda _: place(cl, body, key), range(2)))
        ids = set()
        for s, r, ms in results:
            with lock:
                statuses[s] += 1; lat.append(ms)
            if s == 201:
                ids.add(r["id"])
            elif s >= 500 or s == 0:
                fail(f"place -> {s} {r}")
        if len(ids) != 1:
            codes = [(x[0], (x[1] or {}).get("code")) for x in results]
            fail(f"double submit produced {len(ids)} orders: {codes}")
            return
        with lock:
            orders.append((cl, ids.pop()))

    with ThreadPoolExecutor(min(shoppers, 32)) as ex:
        list(ex.map(shopper, range(shoppers)))
    report("checkout", statuses, lat)
    print(f"  orders placed: {len(orders)}/{shoppers}")
    if len(orders) != shoppers:
        fail(f"only {len(orders)} of {shoppers} shoppers completed checkout")
    return orders


def phase_oversell(base: str, db) -> None:
    print("\n[3b] oversell race: 40 shoppers, 1 product, limited stock")
    row = db("SELECT p.id, i.available - i.reserved FROM product p JOIN inventory i ON i.product_id = p.id "
             "WHERE p.status = 'ACTIVE' AND NOT i.backorder_allowed AND i.available - i.reserved BETWEEN 3 AND 15 "
             "ORDER BY p.id LIMIT 1")
    if not row:
        print("  skipped: no product with 3-15 units in stock")
        return
    pid, stock = (int(x) for x in row[0])
    sold: Counter = Counter()
    lock = threading.Lock()

    def buyer(i: int) -> None:
        cl = Client(base, f"10.205.0.{i + 1}")
        s, r, _ = place(cl, {"deliveryPreset": "HOME", "simulationAddress": {"label": "Home"},
                             "instant": {"productId": pid, "quantity": 1}}, str(uuid.uuid4()))
        with lock:
            sold[s] += 1
        if s >= 500 or s == 0:
            fail(f"oversell buyer -> {s} {r}")

    with ThreadPoolExecutor(40) as ex:
        list(ex.map(buyer, range(40)))
    left = int(db(f"SELECT available - reserved FROM inventory WHERE product_id = {pid}")[0][0])
    print(f"  stock {stock} -> {left}; results {dict(sold)}")
    if sold[201] != stock or left != 0:
        fail(f"oversell: {sold[201]} orders for {stock} units, {left} left")
    if left < 0:
        fail("inventory went negative")


# ---- 4. tracking and notifications ------------------------------------------------------------------------------

def phase_tracking(orders: list[tuple[Client, str]]) -> None:
    print(f"\n[4] tracking and notifications for {len(orders)} shoppers")
    statuses: Counter = Counter()
    lock = threading.Lock()

    def check(o) -> None:
        cl, oid = o
        for path in (f"/api/v1/purchases/{oid}", "/api/v1/notifications", "/api/v1/notifications/unread-count",
                     "/api/v1/purchases?page=0&size=10"):
            s, r, _ = cl.call("GET", path)
            with lock:
                statuses[s] += 1
            if s != 200:
                fail(f"{path} -> {s}")
            elif path.endswith(oid) and r["tracking"]["stage"] != "PLACED":
                fail(f"new order stage {r['tracking']['stage']}")
            elif path.endswith("unread-count") and r["count"] < 1:
                fail("no notification for a placed order")
        # Another shopper must not see this order.
        s, _, _ = Client(cl.base, "10.206.0.1").call("GET", f"/api/v1/purchases/{oid}")
        if s != 404:
            fail(f"foreign shopper read an order -> {s}")

    with ThreadPoolExecutor(32) as ex:
        list(ex.map(check, orders))
    report("tracking", statuses, [])

    # Cancel half concurrently, twice each: must be idempotent and refund exactly once.
    half = orders[: len(orders) // 2]

    def cancel(o) -> None:
        cl, oid = o
        with ThreadPoolExecutor(2) as ex:
            rs = list(ex.map(lambda _: cl.call("POST", f"/api/v1/purchases/{oid}/refund"), range(2)))
        if {r[1]["status"] for r in rs if r[0] == 200} != {"CANCELLED"} or any(r[0] != 200 for r in rs):
            fail(f"concurrent cancel -> {[r[0] for r in rs]}")

    with ThreadPoolExecutor(16) as ex:
        list(ex.map(cancel, half))
    print(f"  cancelled {len(half)} orders with duplicate concurrent requests")


# ---- 5. invariants --------------------------------------------------------------------------------------------

def phase_invariants(db) -> None:
    print("\n[5] database invariants")
    checks = {
        "ledger arithmetic": "SELECT count(*) FROM virtual_transaction WHERE amount <> balance_after - balance_before",
        "wallet balance = last ledger entry":
            "SELECT count(*) FROM virtual_wallet w JOIN LATERAL (SELECT balance_after FROM virtual_transaction t "
            "WHERE t.wallet_id = w.id ORDER BY t.id DESC LIMIT 1) last ON TRUE WHERE last.balance_after <> w.balance",
        "negative stock": "SELECT count(*) FROM inventory WHERE available < 0 OR reserved < 0 OR reserved > available",
        "duplicate idempotency keys":
            "SELECT count(*) FROM (SELECT shopper_id, idempotency_key FROM virtual_purchase "
            "GROUP BY 1, 2 HAVING count(*) > 1) d",
        "cancelled orders without a refund":
            "SELECT count(*) FROM virtual_purchase vp WHERE vp.status IN ('CANCELLED', 'REFUNDED') "
            "AND vp.wallet_mode = 'BUDGET' AND NOT EXISTS (SELECT 1 FROM virtual_transaction t "
            "WHERE t.type = 'REFUND' AND t.reference = vp.order_number)",
        "orders refunded twice":
            "SELECT count(*) FROM (SELECT reference FROM virtual_transaction WHERE type = 'REFUND' "
            "GROUP BY reference HAVING count(*) > 1) d",
        "duplicate notifications":
            "SELECT count(*) FROM (SELECT shopper_id, dedupe_key FROM notification GROUP BY 1, 2 HAVING count(*) > 1) d",
    }
    for name, sql in checks.items():
        try:
            n = int(db(sql)[0][0])
        except Exception as e:
            fail(f"{name}: query failed ({e})")
            continue
        print(f"  {name}: {n}")
        if n:
            fail(f"invariant violated: {name} ({n} rows)")


def make_db(container: str, user: str, name: str):
    def db(sql: str):
        out = subprocess.run(["docker", "exec", container, "psql", "-U", user, "-d", name, "-At", "-F", "\t", "-c", sql],
                             capture_output=True, text=True, check=True).stdout.strip()
        return [line.split("\t") for line in out.splitlines() if line]
    return db


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default="http://localhost:8080")
    ap.add_argument("--workers", type=int, default=40)
    ap.add_argument("--seconds", type=int, default=45)
    ap.add_argument("--shoppers", type=int, default=60)
    ap.add_argument("--db-container", default="trustkart-postgres-1")
    ap.add_argument("--db-user", default="trustkart")
    ap.add_argument("--db-name", default="trustkart")
    a = ap.parse_args()
    db = make_db(a.db_container, a.db_user, a.db_name)

    t0 = time.time()
    phase_browse(a.base, a.workers, a.seconds)
    phase_abuse(a.base)
    orders = phase_checkout(a.base, a.shoppers)
    phase_oversell(a.base, db)
    phase_tracking(orders)
    phase_invariants(db)
    print(f"\n{'PASSED' if not FAILURES else f'FAILED ({len(FAILURES)})'} in {time.time() - t0:.0f}s")
    for f in FAILURES[:30]:
        print(" - " + f)
    return 1 if FAILURES else 0


if __name__ == "__main__":
    sys.exit(main())
