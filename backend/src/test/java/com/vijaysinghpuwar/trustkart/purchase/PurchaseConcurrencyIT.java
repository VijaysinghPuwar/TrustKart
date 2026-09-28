package com.vijaysinghpuwar.trustkart.purchase;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.vijaysinghpuwar.trustkart.support.Browser;
import com.vijaysinghpuwar.trustkart.support.IntegrationTest;
import com.vijaysinghpuwar.trustkart.support.ShoppingFixtures;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Real concurrent requests against real PostgreSQL row locks. */
@IntegrationTest
class PurchaseConcurrencyIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    ShoppingFixtures fixtures;

    @Autowired
    JdbcClient jdbc;

    /** Fires {@code n} purchase requests for the same shopper at the same instant and returns their status codes. */
    private List<Integer> fireConcurrently(Browser shopper, int n, java.util.function.IntFunction<String> keyFor, String body)
            throws Exception {
        Cookie guest = new Cookie("tk_guest", shopper.cookie("tk_guest"));
        Cookie xsrf = new Cookie("XSRF-TOKEN", shopper.cookie("XSRF-TOKEN"));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(n);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int index = i;
            Callable<Integer> call = () -> {
                start.await();
                return mvc.perform(MockMvcRequestBuilders.post("/api/v1/purchases")
                                .contentType(MediaType.APPLICATION_JSON).content(body)
                                .cookie(guest, xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
                                .header("Idempotency-Key", keyFor.apply(index)))
                        .andReturn().getResponse().getStatus();
            };
            results.add(pool.submit(call));
        }
        start.countDown();
        List<Integer> codes = new ArrayList<>();
        for (Future<Integer> f : results) {
            codes.add(f.get());
        }
        pool.shutdown();
        return codes;
    }

    private BigDecimal balance(Browser b) throws Exception {
        return new BigDecimal((String) JsonPath.read(b.get("/api/v1/wallet").andReturn().getResponse().getContentAsString(), "$.balance"));
    }

    @Test
    void parallelPurchasesNeverOverspendTheWallet() throws Exception {
        long product = fixtures.product("60000.00", 50);
        Browser shopper = new Browser(mvc);
        shopper.post("/api/v1/wallet/reset");
        String body = "{\"deliveryPreset\":\"HOME\",\"instant\":{\"productId\":%d,\"quantity\":1}}".formatted(product);

        List<Integer> codes = fireConcurrently(shopper, 10, i -> UUID.randomUUID().toString(), body);

        assertThat(codes).filteredOn(c -> c == 201).hasSize(1);
        assertThat(codes).filteredOn(c -> c == 422).hasSize(9);
        assertThat(balance(shopper)).isEqualByComparingTo("40000.00");
        assertThat(fixtures.stock(product)).isEqualTo(49);
    }

    @Test
    void doubleClickedPlaceOrderCreatesOnePurchase() throws Exception {
        long product = fixtures.product("100.00", 50);
        Browser shopper = new Browser(mvc);
        shopper.post("/api/v1/cart/items", "{\"productId\":%d,\"quantity\":1}".formatted(product));
        String sameKey = UUID.randomUUID().toString();

        List<Integer> codes = fireConcurrently(shopper, 8, i -> sameKey, "{\"deliveryPreset\":\"HOME\"}");

        assertThat(codes).allMatch(c -> c == 201);
        long purchases = jdbc.sql("SELECT count(*) FROM virtual_purchase WHERE idempotency_key = :k").param("k", sameKey)
                .query(Long.class).single();
        assertThat(purchases).isEqualTo(1);
        assertThat(balance(shopper)).isEqualByComparingTo("99900.00");
        assertThat(fixtures.stock(product)).isEqualTo(49);
    }

    @Test
    void lastUnitCannotBeSoldTwice() throws Exception {
        long product = fixtures.product("10.00", 1);
        List<Browser> shoppers = List.of(new Browser(mvc), new Browser(mvc), new Browser(mvc), new Browser(mvc));
        for (Browser s : shoppers) {
            s.post("/api/v1/wallet/reset");
        }
        String body = "{\"deliveryPreset\":\"HOME\",\"instant\":{\"productId\":%d,\"quantity\":1}}".formatted(product);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(shoppers.size());
        List<Future<Integer>> results = new ArrayList<>();
        for (Browser s : shoppers) {
            Cookie guest = new Cookie("tk_guest", s.cookie("tk_guest"));
            Cookie xsrf = new Cookie("XSRF-TOKEN", s.cookie("XSRF-TOKEN"));
            results.add(pool.submit(() -> {
                start.await();
                return mvc.perform(MockMvcRequestBuilders.post("/api/v1/purchases").contentType(MediaType.APPLICATION_JSON)
                                .content(body).cookie(guest, xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
                                .header("Idempotency-Key", UUID.randomUUID().toString()))
                        .andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        List<Integer> codes = new ArrayList<>();
        for (Future<Integer> f : results) {
            codes.add(f.get());
        }
        pool.shutdown();

        assertThat(codes).filteredOn(c -> c == 201).hasSize(1);
        assertThat(codes).filteredOn(c -> c == 409).hasSize(3);
        assertThat(fixtures.stock(product)).isZero();
    }

    /**
     * Regression: shoppers whose carts hold the same products in opposite order used to lock inventory rows in
     * opposite order and deadlock (500 after PostgreSQL's 1 s detection). Stock is now committed in product-id order.
     */
    @Test
    void crossedCartsNeverDeadlock() throws Exception {
        long a = fixtures.product("10.00", 500);
        long b = fixtures.product("12.00", 500);
        List<Browser> shoppers = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            Browser s = new Browser(mvc);
            long first = i % 2 == 0 ? a : b;
            long second = i % 2 == 0 ? b : a;
            s.post("/api/v1/cart/items", "{\"productId\":%d,\"quantity\":1}".formatted(first));
            s.post("/api/v1/cart/items", "{\"productId\":%d,\"quantity\":1}".formatted(second));
            shoppers.add(s);
        }
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(shoppers.size());
        List<Future<Integer>> results = new ArrayList<>();
        for (Browser s : shoppers) {
            results.add(pool.submit(() -> {
                start.await();
                return s.post("/api/v1/purchases", "{\"deliveryPreset\":\"HOME\"}", "Idempotency-Key", UUID.randomUUID().toString())
                        .andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        List<Integer> codes = new ArrayList<>();
        for (Future<Integer> f : results) {
            codes.add(f.get());
        }
        pool.shutdown();
        assertThat(codes).containsOnly(201);
        assertThat(fixtures.stock(a)).isEqualTo(500 - shoppers.size());
        assertThat(fixtures.stock(b)).isEqualTo(500 - shoppers.size());
    }
}
