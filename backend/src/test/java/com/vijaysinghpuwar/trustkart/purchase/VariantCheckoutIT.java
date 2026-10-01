package com.vijaysinghpuwar.trustkart.purchase;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.vijaysinghpuwar.trustkart.catalog.infra.ProductRepository;
import com.vijaysinghpuwar.trustkart.catalog.seed.DemoCatalogSeeder;
import com.vijaysinghpuwar.trustkart.support.Browser;
import com.vijaysinghpuwar.trustkart.support.IntegrationTest;
import com.vijaysinghpuwar.trustkart.support.ShoppingFixtures;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/** Selectable options (storage, colour…) through cart, quote and checkout. Prices always come from the server. */
@IntegrationTest
class VariantCheckoutIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    ProductRepository products;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    DemoCatalogSeeder seeder;

    @Autowired
    ShoppingFixtures fixtures;

    long iphone;

    @BeforeEach
    void findProduct() {
        seeder.seed();
        iphone = products.findWithDetailsBySlug("apple-iphone-18-pro").orElseThrow().getId();
    }

    private String line(String options) {
        return "{\"productId\":" + iphone + ",\"quantity\":1" + (options == null ? "" : ",\"options\":" + options) + "}";
    }

    @Test
    void eachConfigurationIsItsOwnCartLinePricedByTheServer() throws Exception {
        Browser b = new Browser(mvc);
        b.post("/api/v1/cart/items", line(null)).andExpect(status().isOk());
        b.post("/api/v1/cart/items", line("{\"Storage\":\"1TB\",\"Color\":\"Silver\"}")).andExpect(status().isOk());
        // Same configuration again merges into its line.
        b.post("/api/v1/cart/items", line("{\"Color\":\"Silver\",\"Storage\":\"1TB\"}"))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].unitPrice").value("1199.00"))
                .andExpect(jsonPath("$.items[0].optionsLabel").value(org.hamcrest.Matchers.startsWith("256GB")))
                .andExpect(jsonPath("$.items[1].unitPrice").value("1799.00"))
                .andExpect(jsonPath("$.items[1].quantity").value(2))
                .andExpect(jsonPath("$.items[1].optionsLabel").value("1TB · Silver"))
                .andExpect(jsonPath("$.items[1].optionImage").value(org.hamcrest.Matchers.containsString("--silver-")))
                .andExpect(jsonPath("$.subtotal").value("4797.00"));

        String quote = b.get("/api/v1/checkout/quote").andExpect(jsonPath("$.total").value("4797.00"))
                .andReturn().getResponse().getContentAsString();
        String total = JsonPath.read(quote, "$.total");
        b.post("/api/v1/purchases", "{\"deliveryPreset\":\"HOME\",\"simulationAddress\":{\"label\":\"Home\"},\"expectedTotal\":\""
                        + total + "\"}", "Idempotency-Key", UUID.randomUUID().toString())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[1].optionsLabel").value("1TB · Silver"))
                .andExpect(jsonPath("$.items[1].imageUrl").value(org.hamcrest.Matchers.containsString("--silver-")))
                .andExpect(jsonPath("$.items[1].unitPrice").value("1799.00"))
                .andExpect(jsonPath("$.total").value("4797.00"));
    }

    @Test
    void unknownOptionsAreRejected() throws Exception {
        Browser b = new Browser(mvc);
        b.post("/api/v1/cart/items", line("{\"Storage\":\"64TB\"}")).andExpect(status().isBadRequest());
        b.post("/api/v1/cart/items", line("{\"Engraving\":\"yes\"}")).andExpect(status().isBadRequest());
        // A client-supplied price is not a known field at all.
        b.post("/api/v1/cart/items", "{\"productId\":" + iphone + ",\"quantity\":1,\"price\":\"1.00\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void instantBuyQuotesAndChargesTheChosenConfiguration() throws Exception {
        Browser b = new Browser(mvc);
        String opts = URLEncoder.encode("{\"Storage\":\"1TB\"}", StandardCharsets.UTF_8);
        b.get(java.net.URI.create("/api/v1/checkout/quote?productId=" + iphone + "&quantity=1&options=" + opts))
                .andExpect(jsonPath("$.total").value("1799.00"))
                .andExpect(jsonPath("$.lines[0].optionsLabel").value(org.hamcrest.Matchers.startsWith("1TB")));
        b.get("/api/v1/checkout/quote?productId=" + iphone + "&quantity=1&options=not-json").andExpect(status().isBadRequest());
        // JSON null used to decode to a null map and fail with a 500.
        for (String malformed : List.of("null", "[]", "1", "%7B%22a%22%3A%7B%22b%22%3A1%7D%7D")) {
            b.get(java.net.URI.create("/api/v1/checkout/quote?productId=" + iphone + "&options=" + malformed))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("options"));
        }

        String body = "{\"deliveryPreset\":\"HOME\",\"simulationAddress\":{\"label\":\"Home\"},\"instant\":{\"productId\":" + iphone
                + ",\"quantity\":1,\"options\":{\"Storage\":\"1TB\"}}}";
        String key = UUID.randomUUID().toString();
        b.post("/api/v1/purchases", body, "Idempotency-Key", key)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.total").value("1799.00"));
        // Same key, different configuration: a different request, so the key can't be replayed for it.
        b.post("/api/v1/purchases", body.replace("1TB", "512GB"), "Idempotency-Key", key)
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void aRetiredConfigurationBlocksCheckoutWithAReason() throws Exception {
        Browser b = new Browser(mvc);
        b.post("/api/v1/cart/items", line("{\"Storage\":\"512GB\"}")).andExpect(status().isOk());
        jdbc.sql("UPDATE cart_item SET options = '{\"Storage\":\"3TB\"}'::jsonb WHERE product_id = :p AND options ->> 'Storage' = '512GB'")
                .param("p", iphone).update();
        b.get("/api/v1/cart").andExpect(jsonPath("$.items[0].issue").value("OPTION_UNAVAILABLE"));
        b.get("/api/v1/checkout/quote")
                .andExpect(jsonPath("$.canPlace").value(false))
                .andExpect(jsonPath("$.lines[0].issue").value("OPTION_UNAVAILABLE"));
    }

    @Test
    void aProductWithdrawnAfterItWasAddedDoesNotBlockCheckout() throws Exception {
        Browser b = new Browser(mvc);
        long gone = fixtures.product("25.00", 5);
        b.post("/api/v1/cart/items", "{\"productId\":" + gone + ",\"quantity\":1}").andExpect(status().isOk());
        b.post("/api/v1/cart/items", line(null)).andExpect(status().isOk());
        jdbc.sql("UPDATE product SET status = 'DRAFT' WHERE id = :p").param("p", gone).update();

        b.get("/api/v1/cart").andExpect(jsonPath("$.items", hasSize(1)));
        String quote = b.get("/api/v1/checkout/quote").andExpect(status().isOk())
                .andExpect(jsonPath("$.lines", hasSize(1))).andExpect(jsonPath("$.canPlace").value(true))
                .andReturn().getResponse().getContentAsString();
        b.post("/api/v1/purchases", "{\"deliveryPreset\":\"HOME\",\"simulationAddress\":{\"label\":\"Home\"},\"expectedTotal\":\""
                        + JsonPath.read(quote, "$.total") + "\"}", "Idempotency-Key", UUID.randomUUID().toString())
                .andExpect(status().isCreated()).andExpect(jsonPath("$.items", hasSize(1)));
    }
}
