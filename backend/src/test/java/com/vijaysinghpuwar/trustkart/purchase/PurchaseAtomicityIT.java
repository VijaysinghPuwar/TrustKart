package com.vijaysinghpuwar.trustkart.purchase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.vijaysinghpuwar.trustkart.cart.CartService;
import com.vijaysinghpuwar.trustkart.support.Browser;
import com.vijaysinghpuwar.trustkart.support.IntegrationTest;
import com.vijaysinghpuwar.trustkart.support.ShoppingFixtures;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Proves the purchase is all-or-nothing. The failure is injected at the very last step (clearing the cart),
 * after stock was committed, the purchase row inserted and the wallet debited in the same transaction.
 */
@IntegrationTest
class PurchaseAtomicityIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    ShoppingFixtures fixtures;

    @Autowired
    JdbcClient jdbc;

    @MockitoSpyBean
    CartService cartService;

    @Test
    void failureAfterDebitRollsBackEverything() throws Exception {
        long product = fixtures.product("1234.56", 10);
        Browser b = new Browser(mvc);
        b.post("/api/v1/cart/items", "{\"productId\":%d,\"quantity\":2}".formatted(product));
        long purchasesBefore = jdbc.sql("SELECT count(*) FROM virtual_purchase").query(Long.class).single();

        doThrow(new IllegalStateException("simulated failure while clearing the cart")).when(cartService).removeLines(anyList());
        b.post("/api/v1/purchases", "{\"deliveryPreset\":\"HOME\"}", "Idempotency-Key", UUID.randomUUID().toString())
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Something went wrong on our side."));

        String wallet = b.get("/api/v1/wallet").andReturn().getResponse().getContentAsString();
        assertThat(new BigDecimal((String) JsonPath.read(wallet, "$.balance"))).isEqualByComparingTo("100000.00");
        assertThat(fixtures.stock(product)).isEqualTo(10);
        assertThat(jdbc.sql("SELECT count(*) FROM virtual_purchase").query(Long.class).single()).isEqualTo(purchasesBefore);
        b.get("/api/v1/cart").andExpect(jsonPath("$.items", hasSize(1)));
        b.get("/api/v1/wallet/transactions").andExpect(jsonPath("$.items[*].type", org.hamcrest.Matchers.everyItem(
                org.hamcrest.Matchers.not("PURCHASE"))));
    }
}
