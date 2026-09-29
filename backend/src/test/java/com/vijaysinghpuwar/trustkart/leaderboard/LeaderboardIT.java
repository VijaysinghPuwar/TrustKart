package com.vijaysinghpuwar.trustkart.leaderboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.vijaysinghpuwar.trustkart.support.Browser;
import com.vijaysinghpuwar.trustkart.support.IntegrationTest;
import com.vijaysinghpuwar.trustkart.support.MutableClock;
import com.vijaysinghpuwar.trustkart.support.ShoppingFixtures;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Leaderboards against real PostgreSQL. The test database is shared with other suites, so each monthly scenario runs
 * in its own far-future month (via the injected clock): rankings for that month contain only this test's data.
 */
@IntegrationTest
class LeaderboardIT {

    static final String PASSWORD = "correct-horse-battery-staple-42";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    MutableClock clock;

    @Autowired
    ShoppingFixtures fixtures;

    @Autowired
    LeaderboardService leaderboards;

    @Autowired
    ApplicationEventPublisher events;

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    // ---- Fixtures -------------------------------------------------------------------------------------------------

    private void clockAt(String instant) {
        clock.reset();
        clock.advance(Duration.between(Instant.now(), Instant.parse(instant)));
    }

    /** An account with a shopper, inserted directly (fast enough to create hundreds). */
    private long account(String... extraRoles) {
        String email = "lb-" + UUID.randomUUID() + "@example.test";
        long userId = jdbc.sql("""
                        INSERT INTO app_user (public_id, email, display_name, password_hash) VALUES (:p, :e, 'Private Name', 'x')
                        RETURNING id""")
                .param("p", UUID.randomUUID()).param("e", email).query(Long.class).single();
        jdbc.sql("INSERT INTO user_role (user_id, role_id) SELECT :u, id FROM role WHERE name = 'CUSTOMER'").param("u", userId).update();
        for (String role : extraRoles) {
            jdbc.sql("INSERT INTO user_role (user_id, role_id) SELECT :u, id FROM role WHERE name = :r")
                    .param("u", userId).param("r", role).update();
        }
        jdbc.sql("INSERT INTO shopper (public_id, user_id) VALUES (:p, :u)").param("p", UUID.randomUUID()).param("u", userId).update();
        return userId;
    }

    private void order(long userId, String total, String at, String status) {
        jdbc.sql("""
                        INSERT INTO virtual_purchase (public_id, order_number, shopper_id, status, item_count, subtotal, shipping,
                                                      total, wallet_mode, delivery_preset, idempotency_key, request_hash,
                                                      created_at, refunded_at)
                        VALUES (:p, :n, (SELECT id FROM shopper WHERE user_id = :u), :s, 1, :t, 0, :t, 'UNLIMITED', 'HOME',
                                :k, 'h', :at, CASE WHEN :s = 'COMPLETED' THEN NULL ELSE CAST(:at AS timestamptz) END)""")
                .param("p", UUID.randomUUID()).param("n", "LB-" + UUID.randomUUID().toString().substring(0, 18))
                .param("u", userId).param("s", status).param("t", new BigDecimal(total)).param("k", UUID.randomUUID().toString())
                .param("at", Timestamp.from(Instant.parse(at))).update();
        events.publishEvent(new LeaderboardChanged());
    }

    private void order(long userId, String total, String at) {
        order(userId, total, at, "COMPLETED");
    }

    private void profile(long userId, String name, boolean visible) {
        jdbc.sql("INSERT INTO leaderboard_profile (user_id, display_name, visible) VALUES (:u, :n, :v)")
                .param("u", userId).param("n", name).param("v", visible).update();
        events.publishEvent(new LeaderboardChanged());
    }

    private Browser signedUp() throws Exception {
        Browser b = new Browser(mvc);
        b.post("/api/v1/auth/register", """
                {"email":"%s","password":"%s","displayName":"Private Person"}"""
                .formatted("lb-" + UUID.randomUUID() + "@example.test", PASSWORD)).andExpect(status().isCreated());
        return b;
    }

    private static String unique() {
        return "U" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    // ---- Periods --------------------------------------------------------------------------------------------------

    @Test
    void aNewMonthStartsEmptyWithoutTouchingThePreviousMonth() throws Exception {
        clockAt("2031-09-30T23:59:00Z");
        long a = account();
        order(a, "1200.00", "2031-09-30T23:58:00Z");
        new Browser(mvc).get("/api/v1/leaderboards/monthly")
                .andExpect(jsonPath("$.period").value("2031-09"))
                .andExpect(jsonPath("$.periodLabel").value("September 2031"))
                .andExpect(jsonPath("$.entries", hasSize(1)))
                .andExpect(jsonPath("$.entries[0].virtualSpend").value("1200.00"));

        clockAt("2031-10-01T00:00:30Z");
        new Browser(mvc).get("/api/v1/leaderboards/monthly")
                .andExpect(jsonPath("$.period").value("2031-10"))
                .andExpect(jsonPath("$.entries", hasSize(0)))
                .andExpect(jsonPath("$.rankedCount").value(0));
        // September is still there, derived from the same orders.
        assertThat(leaderboards.board(LeaderboardPeriod.month(YearMonth.of(2031, 9)), null).entries()).hasSize(1);
    }

    @Test
    void tiesShareARankAndBreakDeterministically() {
        clockAt("2032-02-20T12:00:00Z");
        long first = account(), tieEarly = account(), tieLate = account(), last = account();
        order(first, "500.00", "2032-02-03T10:00:00Z");
        order(tieLate, "300.00", "2032-02-09T10:00:00Z");
        order(tieEarly, "300.00", "2032-02-05T10:00:00Z");
        order(last, "100.00", "2032-02-04T10:00:00Z");
        profile(tieEarly, unique(), true);
        profile(tieLate, unique(), true);

        LeaderboardService.Board board = leaderboards.board(LeaderboardPeriod.currentMonth(clock), null);
        assertThat(board.entries()).extracting(LeaderboardService.Entry::rank).containsExactly(1, 2, 2, 4);
        // Same total: the one who got there first is listed first.
        assertThat(leaderboards.board(LeaderboardPeriod.currentMonth(clock), tieEarly).entries().get(1).currentUser()).isTrue();
        assertThat(leaderboards.board(LeaderboardPeriod.currentMonth(clock), tieLate).entries().get(2).currentUser()).isTrue();
    }

    @Test
    void monthlyShowsAtMostFiftyAndEveryoneStillHasAPosition() throws Exception {
        clockAt("2033-03-15T12:00:00Z");
        long[] users = new long[51];
        for (int i = 0; i < 51; i++) {
            users[i] = account();
            order(users[i], (1000 + i) + ".00", "2033-03-02T10:00:00Z");
        }
        new Browser(mvc).get("/api/v1/leaderboards/monthly")
                .andExpect(jsonPath("$.limit").value(50))
                .andExpect(jsonPath("$.entries", hasSize(50)))
                .andExpect(jsonPath("$.rankedCount").value(51));
        LeaderboardService.Position last = leaderboards.position(LeaderboardPeriod.currentMonth(clock), users[0]).orElseThrow();
        assertThat(last.rank()).isEqualTo(51);
        assertThat(last.inTopList()).isFalse();
        assertThat(last.gapToTopList()).isEqualTo("1.00");
    }

    @Test
    void allTimeShowsAtMostOneHundred() throws Exception {
        clockAt("2034-05-15T12:00:00Z");
        for (int i = 0; i < 101; i++) {
            order(account(), "9000000" + String.format("%03d", i) + ".00", "2034-05-02T10:00:00Z");
        }
        new Browser(mvc).get("/api/v1/leaderboards/all-time")
                .andExpect(jsonPath("$.limit").value(100))
                .andExpect(jsonPath("$.entries", hasSize(100)))
                .andExpect(jsonPath("$.entries[0].rank").value(1))
                .andExpect(jsonPath("$.entries[99].rank").value(100));
    }

    // ---- Eligibility ------------------------------------------------------------------------------------------------

    @Test
    void onlyCompletedOrdersFromEligibleAccountsCount() {
        clockAt("2035-06-20T12:00:00Z");
        long customer = account(), staff = account("SUPPORT"), excluded = account(), cancelled = account();
        order(customer, "250.00", "2035-06-02T10:00:00Z");
        order(customer, "999.00", "2035-06-03T10:00:00Z", "CANCELLED");
        order(customer, "50.00", "2035-06-04T10:00:00Z", "REFUNDED");
        order(staff, "80000.00", "2035-06-02T10:00:00Z");
        order(excluded, "70000.00", "2035-06-02T10:00:00Z");
        jdbc.sql("INSERT INTO leaderboard_profile (user_id, display_name, eligible) VALUES (:u, :n, FALSE)")
                .param("u", excluded).param("n", unique()).update();
        order(cancelled, "60000.00", "2035-06-02T10:00:00Z", "CANCELLED");
        events.publishEvent(new LeaderboardChanged());

        LeaderboardService.Board board = leaderboards.board(LeaderboardPeriod.currentMonth(clock), customer);
        assertThat(board.entries()).hasSize(1);
        assertThat(board.entries().getFirst().virtualSpend()).isEqualTo("250.00");
        assertThat(board.entries().getFirst().orderCount()).isEqualTo(1);
        assertThat(board.entries().getFirst().currentUser()).isTrue();
    }

    @Test
    void realCheckoutCancellationAndDuplicatesAreHandledEndToEnd() throws Exception {
        clockAt("2036-07-02T12:00:00Z");
        long product = fixtures.product("24500.00", 10);
        Browser b = signedUp();
        // Adding virtual funds never counts.
        b.post("/api/v1/wallet/credits", "{\"amount\":\"1000000.00\"}", "Idempotency-Key", UUID.randomUUID().toString())
                .andExpect(status().isOk());
        b.get("/api/v1/leaderboards/me").andExpect(jsonPath("$.monthly").doesNotExist());

        String body = "{\"deliveryPreset\":\"HOME\",\"simulationAddress\":{\"label\":\"Home\"},\"instant\":{\"productId\":"
                + product + ",\"quantity\":1}}";
        String key = UUID.randomUUID().toString();
        String id = JsonPath.read(b.post("/api/v1/purchases", body, "Idempotency-Key", key)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        b.post("/api/v1/purchases", body, "Idempotency-Key", key).andExpect(status().isCreated());
        b.get("/api/v1/leaderboards/me")
                .andExpect(jsonPath("$.monthly.virtualSpend").value("24500.00"))
                .andExpect(jsonPath("$.monthly.orderCount").value(1))
                .andExpect(jsonPath("$.monthly.rank").value(1))
                .andExpect(jsonPath("$.allTime.virtualSpend").exists());
        b.get("/api/v1/leaderboards/monthly").andExpect(jsonPath("$.entries[0].currentUser").value(true));

        // Cancelled before shipping: the contribution disappears from both boards.
        b.post("/api/v1/purchases/" + id + "/refund").andExpect(jsonPath("$.status").value("CANCELLED"));
        b.get("/api/v1/leaderboards/me").andExpect(jsonPath("$.monthly").doesNotExist());
        b.get("/api/v1/leaderboards/monthly").andExpect(jsonPath("$.entries", hasSize(0)));
    }

    @Test
    void guestPurchasesDoNotCountUntilTheyBelongToAnAccount() throws Exception {
        clockAt("2037-08-02T12:00:00Z");
        long product = fixtures.product("300.00", 10);
        Browser guest = new Browser(mvc);
        guest.post("/api/v1/purchases", "{\"deliveryPreset\":\"HOME\",\"simulationAddress\":{\"label\":\"Home\"},"
                + "\"instant\":{\"productId\":" + product + ",\"quantity\":1}}", "Idempotency-Key", UUID.randomUUID().toString())
                .andExpect(status().isCreated());
        guest.get("/api/v1/leaderboards/monthly").andExpect(jsonPath("$.entries", hasSize(0)));
        guest.get("/api/v1/leaderboards/me").andExpect(status().isUnauthorized());

        guest.post("/api/v1/auth/register", """
                {"email":"%s","password":"%s","displayName":"Private Person"}"""
                .formatted("lb-" + UUID.randomUUID() + "@example.test", PASSWORD)).andExpect(status().isCreated());
        guest.get("/api/v1/leaderboards/me").andExpect(jsonPath("$.monthly.virtualSpend").value("300.00"));
    }

    // ---- Privacy -----------------------------------------------------------------------------------------------------

    @Test
    void accountsAreAnonymousUntilTheyOptInAndEmailsNeverAppear() throws Exception {
        clockAt("2038-09-02T12:00:00Z");
        long product = fixtures.product("777.00", 10);
        Browser b = signedUp();
        b.post("/api/v1/purchases", "{\"deliveryPreset\":\"HOME\",\"simulationAddress\":{\"label\":\"Home\"},"
                + "\"instant\":{\"productId\":" + product + ",\"quantity\":1}}", "Idempotency-Key", UUID.randomUUID().toString())
                .andExpect(status().isCreated());

        b.get("/api/v1/leaderboards/monthly")
                .andExpect(jsonPath("$.entries[0].displayName").value(LeaderboardService.ANONYMOUS))
                .andExpect(jsonPath("$.entries[0].anonymous").value(true))
                .andExpect(content().string(not(containsString("@example.test"))))
                .andExpect(content().string(not(containsString("Private Person"))));
        b.get("/api/v1/leaderboards/profile")
                .andExpect(jsonPath("$.displayName").value(org.hamcrest.Matchers.matchesPattern("Collector\\d{5}")))
                .andExpect(jsonPath("$.visible").value(false));

        String name = unique();
        b.put("/api/v1/leaderboards/profile", "{\"displayName\":\"" + name + "\",\"visible\":true,\"showAvatar\":false}")
                .andExpect(status().isOk());
        new Browser(mvc).get("/api/v1/leaderboards/monthly")
                .andExpect(jsonPath("$.entries[0].displayName").value(name))
                .andExpect(jsonPath("$.entries[0].currentUser").value(false))
                .andExpect(content().string(not(containsString("userId"))));
    }

    @Test
    void publicNamesAreValidatedAndClientsCannotSendTotals() throws Exception {
        Browser b = signedUp();
        for (String bad : new String[] {"ab", "has space", "<script>", "a".repeat(21), "Admin_1", "TrustKart"}) {
            b.put("/api/v1/leaderboards/profile", "{\"displayName\":\"" + bad + "\",\"visible\":true,\"showAvatar\":false}")
                    .andExpect(status().isBadRequest());
        }
        String taken = unique();
        signedUp().put("/api/v1/leaderboards/profile", "{\"displayName\":\"" + taken + "\",\"visible\":true,\"showAvatar\":false}")
                .andExpect(status().isOk());
        b.put("/api/v1/leaderboards/profile", "{\"displayName\":\"" + taken.toLowerCase() + "\",\"visible\":true,\"showAvatar\":false}")
                .andExpect(status().isBadRequest());
        // Unknown fields are rejected outright: there is no way to submit a spend or rank.
        b.put("/api/v1/leaderboards/profile", "{\"displayName\":\"" + unique() + "\",\"visible\":true,\"showAvatar\":false,"
                + "\"virtualSpend\":\"999999999\",\"rank\":1}").andExpect(status().isBadRequest());
        new Browser(mvc).put("/api/v1/leaderboards/profile", "{\"displayName\":\"" + unique() + "\",\"visible\":true,\"showAvatar\":false}")
                .andExpect(status().isUnauthorized());
    }

    // ---- Notifications ----------------------------------------------------------------------------------------------

    @Test
    void rankMilestonesAndMonthlyResultsNotifyOnce() throws Exception {
        clockAt("2039-10-10T12:00:00Z");
        long product = fixtures.product("5000.00", 10);
        Browser b = signedUp();
        String email = JsonPath.read(b.get("/api/v1/me").andReturn().getResponse().getContentAsString(), "$.profile.email");
        b.post("/api/v1/purchases", "{\"deliveryPreset\":\"HOME\",\"simulationAddress\":{\"label\":\"Home\"},"
                + "\"instant\":{\"productId\":" + product + ",\"quantity\":1}}", "Idempotency-Key", UUID.randomUUID().toString())
                .andExpect(status().isCreated());
        for (int i = 0; i < 3; i++) {
            b.get("/api/v1/notifications");
        }
        String json = b.get("/api/v1/notifications").andReturn().getResponse().getContentAsString();
        List<String> titles = JsonPath.read(json, "$.items[?(@.type == 'LEADERBOARD_MILESTONE')].title");
        assertThat(titles).containsOnlyOnce("You're #1 this month");
        assertThat(titles).noneMatch(t -> t.startsWith("You're in this month's Top"));

        // Next month: the final result for October arrives once. (Sign in again: the access token has expired.)
        clockAt("2039-11-02T09:00:00Z");
        b.post("/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}");
        b.get("/api/v1/notifications");
        String after = b.get("/api/v1/notifications").andReturn().getResponse().getContentAsString();
        List<String> results = JsonPath.read(after, "$.items[?(@.type == 'LEADERBOARD_RESULT')].title");
        assertThat(results).containsExactly("Your October 2039 result");
    }

    // ---- Admin ------------------------------------------------------------------------------------------------------

    @Test
    void onlyOperatorsSeeAdminToolsAndReconciliationFindsNoDrift() throws Exception {
        signedUp().get("/api/v1/admin/leaderboards/reconcile").andExpect(status().isForbidden());
        new Browser(mvc).get("/api/v1/admin/leaderboards").andExpect(status().isUnauthorized());
        signedUp().post("/api/v1/admin/leaderboards/refresh").andExpect(status().isForbidden());

        Browser admin = signedUp();
        String email = JsonPath.read(admin.get("/api/v1/me").andReturn().getResponse().getContentAsString(), "$.profile.email");
        jdbc.sql("INSERT INTO user_role (user_id, role_id) SELECT u.id, r.id FROM app_user u, role r WHERE u.email = :e AND r.name = 'ADMIN'")
                .param("e", email).update();
        admin.post("/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}");
        admin.get("/api/v1/admin/leaderboards").andExpect(status().isOk()).andExpect(jsonPath("$.month").exists());
        admin.get("/api/v1/admin/leaderboards/reconcile").andExpect(status().isOk())
                .andExpect(jsonPath("$.mismatches", hasSize(0)));
    }
}
