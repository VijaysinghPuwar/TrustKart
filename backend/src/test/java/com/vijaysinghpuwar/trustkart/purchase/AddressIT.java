package com.vijaysinghpuwar.trustkart.purchase;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.vijaysinghpuwar.trustkart.support.Browser;
import com.vijaysinghpuwar.trustkart.support.IntegrationTest;
import com.vijaysinghpuwar.trustkart.support.ShoppingFixtures;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class AddressIT {

    static final String HOME = """
            {"label":"Home","fullName":"Maya Chen","line1":"1 Infinite Loop","line2":"Apt 4","city":"Springfield",
             "region":"IL","postalCode":"62701","country":"US"}""";

    @Autowired
    MockMvc mvc;

    @Autowired
    ShoppingFixtures fixtures;

    String create(Browser b, String json) throws Exception {
        return JsonPath.read(b.post("/api/v1/addresses", json).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    @Test
    void firstAddressBecomesDefaultAndDefaultsStayUnique() throws Exception {
        Browser b = new Browser(mvc);
        String home = create(b, HOME);
        String office = create(b, HOME.replace("Home", "Office"));
        b.get("/api/v1/addresses").andExpect(jsonPath("$", hasSize(2))).andExpect(jsonPath("$[0].id").value(home))
                .andExpect(jsonPath("$[0].isDefault").value(true)).andExpect(jsonPath("$[1].isDefault").value(false));

        b.post("/api/v1/addresses/" + office + "/default").andExpect(jsonPath("$.isDefault").value(true));
        b.get("/api/v1/addresses").andExpect(jsonPath("$[?(@.isDefault == true)]", hasSize(1)));

        b.delete("/api/v1/addresses/" + office).andExpect(status().isNoContent());
        b.get("/api/v1/addresses").andExpect(jsonPath("$[0].isDefault").value(true));
    }

    @Test
    void addressesAreValidated() throws Exception {
        Browser b = new Browser(mvc);
        b.post("/api/v1/addresses", HOME.replace("\"US\"", "\"usa\"")).andExpect(status().isBadRequest());
        b.post("/api/v1/addresses", HOME.replace("62701", "<script>")).andExpect(status().isBadRequest());
        b.post("/api/v1/addresses", HOME.replace("\"Maya Chen\"", "\"\"")).andExpect(status().isBadRequest());
        b.post("/api/v1/addresses", HOME.replace("\"Home\"", "\"" + "x".repeat(41) + "\"")).andExpect(status().isBadRequest());
    }

    @Test
    void checkoutWithSavedAddressSnapshotsIt() throws Exception {
        long p = fixtures.product("10.00", 10);
        Browser b = new Browser(mvc);
        String id = create(b, HOME);
        b.post("/api/v1/cart/items", "{\"productId\":%d,\"quantity\":1}".formatted(p));
        String purchase = JsonPath.read(b.post("/api/v1/purchases", "{\"deliveryPreset\":\"ADDRESS\",\"addressId\":\"%s\"}".formatted(id),
                "Idempotency-Key", UUID.randomUUID().toString()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.simulationAddress.fullName").value("Maya Chen"))
                .andExpect(jsonPath("$.simulationAddress.country").value("US"))
                .andReturn().getResponse().getContentAsString(), "$.id");

        // Editing the address later doesn't rewrite the receipt.
        b.put("/api/v1/addresses/" + id, HOME.replace("Maya Chen", "Someone Else")).andExpect(status().isOk());
        b.get("/api/v1/purchases/" + purchase).andExpect(jsonPath("$.simulationAddress.fullName").value("Maya Chen"));
    }

    @Test
    void checkoutWithInlineAddressRequiresTheEssentials() throws Exception {
        long p = fixtures.product("10.00", 10);
        Browser b = new Browser(mvc);
        b.post("/api/v1/cart/items", "{\"productId\":%d,\"quantity\":1}".formatted(p));
        b.post("/api/v1/purchases", "{\"deliveryPreset\":\"ADDRESS\",\"simulationAddress\":{\"label\":\"Somewhere\"}}",
                        "Idempotency-Key", UUID.randomUUID().toString())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("simulationAddress"));
        b.post("/api/v1/purchases", "{\"deliveryPreset\":\"ADDRESS\",\"simulationAddress\":" + HOME + "}",
                        "Idempotency-Key", UUID.randomUUID().toString())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.simulationAddress.city").value("Springfield"));
    }

    @Test
    void shoppersCannotReadEditOrShipToSomeoneElsesAddress() throws Exception {
        long p = fixtures.product("10.00", 10);
        Browser alice = new Browser(mvc);
        Browser bob = new Browser(mvc);
        String aliceAddress = create(alice, HOME);
        bob.post("/api/v1/cart/items", "{\"productId\":%d,\"quantity\":1}".formatted(p));

        bob.put("/api/v1/addresses/" + aliceAddress, HOME).andExpect(status().isNotFound());
        bob.delete("/api/v1/addresses/" + aliceAddress).andExpect(status().isNotFound());
        bob.post("/api/v1/addresses/" + aliceAddress + "/default").andExpect(status().isNotFound());
        bob.post("/api/v1/purchases", "{\"deliveryPreset\":\"ADDRESS\",\"addressId\":\"%s\"}".formatted(aliceAddress),
                "Idempotency-Key", UUID.randomUUID().toString()).andExpect(status().isNotFound());
        bob.get("/api/v1/addresses").andExpect(jsonPath("$", hasSize(0)));
        alice.get("/api/v1/addresses").andExpect(jsonPath("$", hasSize(1)));
    }
}
