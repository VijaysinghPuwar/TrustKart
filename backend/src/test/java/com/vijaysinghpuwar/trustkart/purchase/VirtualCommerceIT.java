package com.vijaysinghpuwar.trustkart.purchase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.vijaysinghpuwar.trustkart.support.Browser;
import com.vijaysinghpuwar.trustkart.support.IntegrationTest;
import com.vijaysinghpuwar.trustkart.support.ShoppingFixtures;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class VirtualCommerceIT {

    static final String CHECKOUT = "{\"deliveryPreset\":\"DREAM_SETUP\"}";

    @Autowired
    MockMvc mvc;

    @Autowired
    ShoppingFixtures fixtures;

    @Autowired
    JdbcClient jdbc;

    static String key() {
        return UUID.randomUUID().toString();
    }

    static String add(long productId, int qty) {
        return "{\"productId\":%d,\"quantity\":%d}".formatted(productId, qty);
    }

    static BigDecimal balance(Browser b) throws Exception {
        return new BigDecimal((String) JsonPath.read(b.get("/api/v1/wallet").andReturn().getResponse().getContentAsString(), "$.balance"));
    }

    // ---- Wallet ----------------------------------------------------------------------------------------------

    @Test
    void newShoppersSeeAStartingBalanceWithoutCreatingAWallet() throws Exception {
        new Browser(mvc).get("/api/v1/wallet")
                .andExpect(jsonPath("$.balance").value("100000.00"))
                .andExpect(jsonPath("$.exists").value(false));
    }

    @Test
    void addingVirtualFundsIsLedgeredAndIdempotent() throws Exception {
        Browser b = new Browser(mvc);
        String k = key();
        b.post("/api/v1/wallet/credits", "{\"amount\":\"500000.00\"}", "Idempotency-Key", k)
                .andExpect(status().isOk()).andExpect(jsonPath("$.balance").value("600000.00"));
        b.post("/api/v1/wallet/credits", "{\"amount\":\"500000.00\"}", "Idempotency-Key", k)
                .andExpect(jsonPath("$.balance").value("600000.00"));

        b.get("/api/v1/wallet/transactions")
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].type").value("CREDIT"))
                .andExpect(jsonPath("$.items[0].balanceBefore").value("100000.00"))
                .andExpect(jsonPath("$.items[0].balanceAfter").value("600000.00"));
    }

    @Test
    void invalidCreditAmountsAreRejected() throws Exception {
        Browser b = new Browser(mvc);
        for (String bad : new String[] {"-100", "0", "0.001", "10000000.01", "1e30", "\"abc\"", "99999999999999999999"}) {
            b.post("/api/v1/wallet/credits", "{\"amount\":" + bad + "}", "Idempotency-Key", key())
                    .andExpect(status().isBadRequest());
        }
        assertThat(balance(b)).isEqualByComparingTo("100000.00");
    }

    @Test
    void creditsRequireAnIdempotencyKey() throws Exception {
        new Browser(mvc).post("/api/v1/wallet/credits", "{\"amount\":\"10.00\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    void balanceCannotBeSetDirectly() throws Exception {
        Browser b = new Browser(mvc);
        b.put("/api/v1/wallet/mode", "{\"mode\":\"BUDGET\",\"balance\":\"999999999\"}").andExpect(status().isBadRequest());
        b.patch("/api/v1/wallet", "{\"balance\":\"999999999\"}").andExpect(status().is4xxClientError());
        assertThat(balance(b)).isEqualByComparingTo("100000.00");
    }

    @Test
    void resetRestoresTheStartingBalance() throws Exception {
        Browser b = new Browser(mvc);
        b.post("/api/v1/wallet/credits", "{\"amount\":\"25000\"}", "Idempotency-Key", key());
        b.post("/api/v1/wallet/reset").andExpect(jsonPath("$.balance").value("100000.00"));
        b.get("/api/v1/wallet/transactions").andExpect(jsonPath("$.items[0].type").value("RESET"))
                .andExpect(jsonPath("$.items[0].amount").value("-25000.00"));
    }

    // ---- Checkout --------------------------------------------------------------------------------------------

    @Test
    void purchaseDeductsExactlyTheServerComputedTotal() throws Exception {
        long p = fixtures.product("33.33", 20);
        Browser b = new Browser(mvc);
        b.post("/api/v1/cart/items", add(p, 3)).andExpect(jsonPath("$.subtotal").value("99.99"));

        b.get("/api/v1/checkout/quote")
                .andExpect(jsonPath("$.total").value("99.99"))
                .andExpect(jsonPath("$.balanceAfter").value("99900.01"))
                .andExpect(jsonPath("$.canPlace").value(true));

        b.post("/api/v1/purchases", CHECKOUT, "Idempotency-Key", key())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderNumber").value(org.hamcrest.Matchers.matchesPattern("TK-\\d{8}-\\d{4,}")))
                .andExpect(jsonPath("$.total").value("99.99"))
                .andExpect(jsonPath("$.balanceBefore").value("100000.00"))
                .andExpect(jsonPath("$.balanceAfter").value("99900.01"))
                .andExpect(jsonPath("$.simulation").value(true));

        assertThat(balance(b)).isEqualByComparingTo("99900.01");
        assertThat(fixtures.stock(p)).isEqualTo(17);
        b.get("/api/v1/cart").andExpect(jsonPath("$.items", hasSize(0)));
        b.get("/api/v1/collection").andExpect(jsonPath("$.items[0].quantity").value(3))
                .andExpect(jsonPath("$.stats.totalVirtualSpend").value("99.99"));
    }

    @Test
    void clientCannotSendPricesOrTotals() throws Exception {
        long p = fixtures.product("500.00", 5);
        Browser b = new Browser(mvc);
        b.post("/api/v1/cart/items", "{\"productId\":%d,\"quantity\":1,\"price\":\"0.01\"}".formatted(p))
                .andExpect(status().isBadRequest());
        b.post("/api/v1/cart/items", add(p, 1));
        b.post("/api/v1/purchases", "{\"deliveryPreset\":\"HOME\",\"total\":\"0.01\"}", "Idempotency-Key", key())
                .andExpect(status().isBadRequest());
        // A stale or tampered expectedTotal never becomes the charge; it just triggers a fresh quote.
        b.post("/api/v1/purchases", "{\"deliveryPreset\":\"HOME\",\"expectedTotal\":\"0.01\"}", "Idempotency-Key", key())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRICE_CHANGED"))
                .andExpect(jsonPath("$.details.quote.total").value("500.00"));
        assertThat(balance(b)).isEqualByComparingTo("100000.00");
    }

    @Test
    void quantitiesAreValidated() throws Exception {
        long p = fixtures.product("10.00", 3);
        Browser b = new Browser(mvc);
        b.post("/api/v1/cart/items", add(p, -1)).andExpect(status().isBadRequest());
        b.post("/api/v1/cart/items", add(p, 0)).andExpect(status().isBadRequest());
        b.post("/api/v1/cart/items", add(p, 2147483647)).andExpect(status().isBadRequest());
        b.post("/api/v1/cart/items", add(p, 4)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OUT_OF_STOCK")).andExpect(jsonPath("$.details.maxQuantity").value(3));
    }

    @Test
    void insufficientFundsChangesNothingAndReportsTheShortfall() throws Exception {
        long p = fixtures.product("60000.00", 5);
        Browser b = new Browser(mvc);
        b.post("/api/v1/cart/items", add(p, 2));
        b.post("/api/v1/purchases", CHECKOUT, "Idempotency-Key", key())
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_VIRTUAL_FUNDS"))
                .andExpect(jsonPath("$.details.shortfall").value("20000.00"))
                .andExpect(jsonPath("$.message").value("You need $20,000.00 more virtual funds for this purchase."));

        assertThat(balance(b)).isEqualByComparingTo("100000.00");
        assertThat(fixtures.stock(p)).isEqualTo(5);
        b.get("/api/v1/cart").andExpect(jsonPath("$.items", hasSize(1)));
    }

    @Test
    void unlimitedModeAlwaysSucceedsWithoutTouchingTheBalance() throws Exception {
        long p = fixtures.product("250000.00", 5);
        Browser b = new Browser(mvc);
        b.put("/api/v1/wallet/mode", "{\"mode\":\"UNLIMITED\"}").andExpect(jsonPath("$.mode").value("UNLIMITED"));
        b.post("/api/v1/cart/items", add(p, 2));
        b.post("/api/v1/purchases", CHECKOUT, "Idempotency-Key", key())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.walletMode").value("UNLIMITED"))
                .andExpect(jsonPath("$.total").value("500000.00"))
                .andExpect(jsonPath("$.balanceAfter").doesNotExist());
        assertThat(balance(b)).isEqualByComparingTo("100000.00");
    }

    @Test
    void retriedPurchaseWithSameKeyDeductsOnce() throws Exception {
        long p = fixtures.product("1000.00", 10);
        Browser b = new Browser(mvc);
        b.post("/api/v1/cart/items", add(p, 1));
        String k = key();
        String first = b.post("/api/v1/purchases", CHECKOUT, "Idempotency-Key", k).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String second = b.post("/api/v1/purchases", CHECKOUT, "Idempotency-Key", k).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(second, "$.orderNumber")).isEqualTo(JsonPath.read(first, "$.orderNumber"));
        assertThat(balance(b)).isEqualByComparingTo("99000.00");
        assertThat(fixtures.stock(p)).isEqualTo(9);
    }

    @Test
    void reusingAKeyForADifferentRequestIsRejected() throws Exception {
        long p = fixtures.product("10.00", 10);
        Browser b = new Browser(mvc);
        b.post("/api/v1/cart/items", add(p, 1));
        String k = key();
        b.post("/api/v1/purchases", CHECKOUT, "Idempotency-Key", k).andExpect(status().isCreated());
        b.post("/api/v1/purchases", "{\"deliveryPreset\":\"OFFICE\"}", "Idempotency-Key", k)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void emptyCartCannotBeCheckedOut() throws Exception {
        new Browser(mvc).post("/api/v1/purchases", CHECKOUT, "Idempotency-Key", key())
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("CART_EMPTY"));
    }

    @Test
    void cancellingBeforeShipmentRestoresBalanceStockAndCollectionAndIsIdempotent() throws Exception {
        long p = fixtures.product("2500.00", 4);
        Browser b = new Browser(mvc);
        b.post("/api/v1/cart/items", add(p, 2));
        String id = JsonPath.read(b.post("/api/v1/purchases", CHECKOUT, "Idempotency-Key", key())
                .andReturn().getResponse().getContentAsString(), "$.id");
        assertThat(balance(b)).isEqualByComparingTo("95000.00");

        b.post("/api/v1/purchases/" + id + "/refund").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        b.post("/api/v1/purchases/" + id + "/refund").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(balance(b)).isEqualByComparingTo("100000.00");
        assertThat(fixtures.stock(p)).isEqualTo(4);
        b.get("/api/v1/collection").andExpect(jsonPath("$.items", hasSize(0)));
        b.get("/api/v1/wallet/transactions").andExpect(jsonPath("$.items[0].type").value("REFUND"));
    }

    @Test
    void instantVirtualBuySkipsTheCart() throws Exception {
        long inCart = fixtures.product("5.00", 10);
        long instant = fixtures.product("75.50", 10);
        Browser b = new Browser(mvc);
        b.post("/api/v1/cart/items", add(inCart, 1));
        b.post("/api/v1/purchases", "{\"deliveryPreset\":\"HOME\",\"instant\":{\"productId\":%d,\"quantity\":2}}".formatted(instant),
                        "Idempotency-Key", key())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value("151.00"))
                .andExpect(jsonPath("$.items", hasSize(1)));
        b.get("/api/v1/cart").andExpect(jsonPath("$.items", hasSize(1)));
    }

    @Test
    void backorderItemsCanBeBoughtWithoutTouchingStock() throws Exception {
        long p = fixtures.product("40.00", 0, true);
        Browser b = new Browser(mvc);
        b.post("/api/v1/cart/items", add(p, 2)).andExpect(status().isOk());
        b.post("/api/v1/purchases", CHECKOUT, "Idempotency-Key", key()).andExpect(status().isCreated());
        assertThat(fixtures.stock(p)).isZero();
    }

    // ---- Ownership (IDOR/BOLA) -------------------------------------------------------------------------------

    @Test
    void shoppersCannotSeeOrTouchEachOthersData() throws Exception {
        long p = fixtures.product("10.00", 10);
        Browser alice = new Browser(mvc);
        Browser bob = new Browser(mvc);
        String cartItemId = JsonPath.read(alice.post("/api/v1/cart/items", add(p, 1)).andReturn().getResponse().getContentAsString(),
                "$.items[0].id");
        String purchaseId = JsonPath.read(alice.post("/api/v1/purchases", CHECKOUT, "Idempotency-Key", key())
                .andReturn().getResponse().getContentAsString(), "$.id");
        alice.post("/api/v1/cart/items", add(p, 1));
        cartItemId = JsonPath.read(alice.get("/api/v1/cart").andReturn().getResponse().getContentAsString(), "$.items[0].id");

        bob.get("/api/v1/purchases/" + purchaseId).andExpect(status().isNotFound());
        bob.post("/api/v1/purchases/" + purchaseId + "/refund").andExpect(status().isNotFound());
        bob.delete("/api/v1/cart/items/" + cartItemId).andExpect(status().isNotFound());
        bob.patch("/api/v1/cart/items/" + cartItemId, "{\"quantity\":5}").andExpect(status().isNotFound());
        bob.get("/api/v1/purchases").andExpect(jsonPath("$.items", hasSize(0)));
        bob.get("/api/v1/collection").andExpect(jsonPath("$.items", hasSize(0)));
        alice.get("/api/v1/purchases/" + purchaseId).andExpect(status().isOk());
    }

    @Test
    void guestCartAndPurchasesFollowTheShopperIntoTheirAccount() throws Exception {
        long p = fixtures.product("20.00", 10);
        Browser guest = new Browser(mvc);
        guest.post("/api/v1/cart/items", add(p, 2));
        guest.post("/api/v1/auth/register", """
                {"email":"merge-%s@example.test","password":"a long enough passphrase","displayName":"Guest"}"""
                .formatted(UUID.randomUUID())).andExpect(status().isCreated());
        guest.get("/api/v1/cart").andExpect(jsonPath("$.items[0].quantity").value(2));
    }
}
