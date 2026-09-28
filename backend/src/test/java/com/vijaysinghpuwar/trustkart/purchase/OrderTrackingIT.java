package com.vijaysinghpuwar.trustkart.purchase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.vijaysinghpuwar.trustkart.support.Browser;
import com.vijaysinghpuwar.trustkart.support.IntegrationTest;
import com.vijaysinghpuwar.trustkart.support.MutableClock;
import com.vijaysinghpuwar.trustkart.support.ShoppingFixtures;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class OrderTrackingIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    ShoppingFixtures fixtures;

    @Autowired
    MutableClock clock;

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    private String order(Browser b, long productId) throws Exception {
        String body = "{\"deliveryPreset\":\"ADDRESS\",\"simulationAddress\":{\"label\":\"Home\",\"fullName\":\"Ada L\","
                + "\"line1\":\"1 Market St\",\"city\":\"San Francisco\",\"region\":\"CA\",\"postalCode\":\"94105\",\"country\":\"US\"},"
                + "\"instant\":{\"productId\":" + productId + ",\"quantity\":1}}";
        return JsonPath.read(b.post("/api/v1/purchases", body, "Idempotency-Key", UUID.randomUUID().toString())
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private BigDecimal balance(Browser b) throws Exception {
        return new BigDecimal((String) JsonPath.read(b.get("/api/v1/wallet").andReturn().getResponse().getContentAsString(), "$.balance"));
    }

    @Test
    void anOrderMovesThroughTheSevenDayTimeline() throws Exception {
        Browser b = new Browser(mvc);
        String id = order(b, fixtures.product("120.00", 5));

        b.get("/api/v1/purchases/" + id)
                .andExpect(jsonPath("$.tracking.stage").value("PLACED"))
                .andExpect(jsonPath("$.tracking.trackingNumber").value(org.hamcrest.Matchers.matchesPattern("TK1Z[0-9A-F]{14}")))
                .andExpect(jsonPath("$.tracking.canCancel").value(true))
                .andExpect(jsonPath("$.tracking.events", hasSize(8)))
                .andExpect(jsonPath("$.tracking.events[0].done").value(true))
                .andExpect(jsonPath("$.tracking.events[7].done").value(false));

        clock.advance(Duration.ofDays(2));
        b.get("/api/v1/purchases/" + id)
                .andExpect(jsonPath("$.tracking.stage").value("IN_TRANSIT"))
                .andExpect(jsonPath("$.tracking.canCancel").value(false));
        b.get("/api/v1/purchases").andExpect(jsonPath("$.items[0].stage").value("IN_TRANSIT"));

        clock.advance(Duration.ofDays(4).plusHours(21));
        b.get("/api/v1/purchases/" + id)
                .andExpect(jsonPath("$.tracking.stage").value("OUT_FOR_DELIVERY"))
                .andExpect(jsonPath("$.tracking.events[6].location").value("San Francisco, CA"));

        clock.advance(Duration.ofDays(1));
        b.get("/api/v1/purchases/" + id)
                .andExpect(jsonPath("$.tracking.stage").value("DELIVERED"))
                .andExpect(jsonPath("$.tracking.progress").value(100))
                .andExpect(jsonPath("$.tracking.canReturn").value(true));
    }

    @Test
    void shippedOrdersCannotBeCancelledButDeliveredOnesCanBeReturned() throws Exception {
        long p = fixtures.product("300.00", 3);
        Browser b = new Browser(mvc);
        String id = order(b, p);

        clock.advance(Duration.ofDays(3));
        b.post("/api/v1/purchases/" + id + "/refund")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ORDER_IN_TRANSIT"));
        assertThat(balance(b)).isEqualByComparingTo("99700.00");

        clock.advance(Duration.ofDays(5));
        b.post("/api/v1/purchases/" + id + "/refund")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"))
                .andExpect(jsonPath("$.tracking.stage").value("REFUNDED"))
                .andExpect(jsonPath("$.tracking.canReturn").value(false));
        assertThat(balance(b)).isEqualByComparingTo("100000.00");
        assertThat(fixtures.stock(p)).isEqualTo(3);
    }

    @Test
    void returnsCloseThirtyDaysAfterDelivery() throws Exception {
        Browser b = new Browser(mvc);
        String id = order(b, fixtures.product("50.00", 3));
        clock.advance(Duration.ofDays(38));
        b.post("/api/v1/purchases/" + id + "/refund")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RETURN_WINDOW_CLOSED"));
    }

    @Test
    void cancelledOrdersStopMoving() throws Exception {
        Browser b = new Browser(mvc);
        String id = order(b, fixtures.product("80.00", 3));
        b.post("/api/v1/purchases/" + id + "/refund").andExpect(jsonPath("$.status").value("CANCELLED"));
        clock.advance(Duration.ofDays(10));
        b.get("/api/v1/purchases/" + id)
                .andExpect(jsonPath("$.tracking.stage").value("CANCELLED"))
                .andExpect(jsonPath("$.tracking.deliveredAt").doesNotExist())
                .andExpect(jsonPath("$.tracking.events[-1].stage").value("CANCELLED"));
    }

    // ---- Notifications -----------------------------------------------------------------------------------------

    @Test
    void milestonesBecomeNotificationsExactlyOnce() throws Exception {
        Browser b = new Browser(mvc);
        order(b, fixtures.product("99.00", 3));
        b.get("/api/v1/notifications/unread-count").andExpect(jsonPath("$.count").value(1));

        clock.advance(Duration.ofDays(8));
        for (int i = 0; i < 3; i++) {
            b.get("/api/v1/notifications").andExpect(jsonPath("$.items", hasSize(4))).andExpect(jsonPath("$.unreadCount").value(4));
        }
        String json = b.get("/api/v1/notifications").andReturn().getResponse().getContentAsString();
        List<String> types = JsonPath.read(json, "$.items[*].type");
        assertThat(types).containsExactly("DELIVERED", "OUT_FOR_DELIVERY", "ORDER_SHIPPED", "ORDER_PLACED");

        String first = JsonPath.read(json, "$.items[0].id");
        b.post("/api/v1/notifications/" + first + "/read").andExpect(status().isNoContent());
        b.get("/api/v1/notifications/unread-count").andExpect(jsonPath("$.count").value(3));
        b.post("/api/v1/notifications/read-all").andExpect(status().isNoContent());
        b.get("/api/v1/notifications/unread-count").andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void preferencesSuppressDeliveryUpdates() throws Exception {
        Browser b = new Browser(mvc);
        order(b, fixtures.product("45.00", 3));
        b.put("/api/v1/notifications/preferences", "{\"orderUpdates\":true,\"deliveryUpdates\":false}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.deliveryUpdates").value(false));
        clock.advance(Duration.ofDays(8));
        String json = b.get("/api/v1/notifications").andReturn().getResponse().getContentAsString();
        List<String> types = JsonPath.read(json, "$.items[*].type");
        assertThat(types).containsExactly("ORDER_SHIPPED", "ORDER_PLACED");
    }

    @Test
    void notificationsArePrivateToTheirShopper() throws Exception {
        Browser alice = new Browser(mvc);
        order(alice, fixtures.product("10.00", 3));
        String id = JsonPath.read(alice.get("/api/v1/notifications").andReturn().getResponse().getContentAsString(), "$.items[0].id");

        Browser bob = new Browser(mvc);
        bob.get("/api/v1/notifications").andExpect(jsonPath("$.items", hasSize(0)));
        bob.get("/api/v1/wallet");
        bob.post("/api/v1/notifications/" + id + "/read").andExpect(status().isNotFound());
        alice.get("/api/v1/notifications/unread-count").andExpect(jsonPath("$.count").value(1));
    }
}
