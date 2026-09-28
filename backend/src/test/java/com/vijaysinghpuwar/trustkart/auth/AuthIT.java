package com.vijaysinghpuwar.trustkart.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.vijaysinghpuwar.trustkart.support.Browser;
import com.vijaysinghpuwar.trustkart.support.IntegrationTest;
import com.vijaysinghpuwar.trustkart.support.MutableClock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class AuthIT {

    static final String PASSWORD = "correct horse battery staple";

    @Autowired
    MockMvc mvc;

    @Autowired
    MutableClock clock;

    @Autowired
    JdbcClient jdbc;

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    static String email() {
        return "user-" + UUID.randomUUID() + "@example.test";
    }

    static String registerJson(String email) {
        return """
                {"email":"%s","password":"%s","displayName":"Maya Chen"}""".formatted(email, PASSWORD);
    }

    Browser registered(String email) throws Exception {
        Browser b = new Browser(mvc);
        b.post("/api/v1/auth/register", registerJson(email)).andExpect(status().isCreated());
        return b;
    }

    @Test
    void registrationSignsInWithHttpOnlyCookies() throws Exception {
        Browser b = new Browser(mvc);
        b.post("/api/v1/auth/register", registerJson(email()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.displayName").value("Maya Chen"))
                .andExpect(header().stringValues("Set-Cookie", hasItem(containsString("tk_at="))))
                .andExpect(header().stringValues("Set-Cookie", hasItem(containsString("HttpOnly"))))
                .andExpect(header().stringValues("Set-Cookie", hasItem(containsString("Path=/api/v1/auth"))));

        b.get("/api/v1/me")
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.profile.roles", contains("CUSTOMER")))
                .andExpect(jsonPath("$.profile.permissions", hasSize(0)));
    }

    @Test
    void passwordsAreStoredAsArgon2idHashes() throws Exception {
        String email = email();
        registered(email);
        String hash = jdbc.sql("SELECT password_hash FROM app_user WHERE email = :e").param("e", email).query(String.class).single();
        assertThat(hash).startsWith("{argon2}$argon2id$").doesNotContain(PASSWORD);
    }

    @Test
    void weakPasswordsAreRejectedWithFieldErrors() throws Exception {
        new Browser(mvc).post("/api/v1/auth/register", """
                        {"email":"%s","password":"short","displayName":"Maya"}""".formatted(email()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("password"));
    }

    @Test
    void massAssignmentOfRolesIsRejected() throws Exception {
        new Browser(mvc).post("/api/v1/auth/register", """
                        {"email":"%s","password":"%s","displayName":"Mallory","roles":["ADMIN"]}"""
                        .formatted(email(), PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void wrongPasswordAndUnknownEmailLookIdentical() throws Exception {
        String email = email();
        registered(email);
        String wrong = new Browser(mvc).post("/api/v1/auth/login", """
                        {"email":"%s","password":"not the password at all"}""".formatted(email))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String unknown = new Browser(mvc).post("/api/v1/auth/login", """
                        {"email":"%s","password":"not the password at all"}""".formatted(email()))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(wrong, "$.code")).isEqualTo(JsonPath.read(unknown, "$.code"));
        assertThat((String) JsonPath.read(wrong, "$.message")).isEqualTo(JsonPath.read(unknown, "$.message"));
    }

    @Test
    void accountLocksAfterRepeatedFailures() throws Exception {
        String email = email();
        registered(email);
        Browser attacker = new Browser(mvc);
        for (int i = 0; i < 5; i++) {
            attacker.post("/api/v1/auth/login", """
                    {"email":"%s","password":"guess number %d xx"}""".formatted(email, i));
        }
        // Even the correct password is refused while locked.
        new Browser(mvc).post("/api/v1/auth/login", """
                        {"email":"%s","password":"%s"}""".formatted(email, PASSWORD))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));

        clock.advance(Duration.ofMinutes(16));
        new Browser(mvc).post("/api/v1/auth/login", """
                        {"email":"%s","password":"%s"}""".formatted(email, PASSWORD))
                .andExpect(status().isOk());
    }

    @Test
    void loginAttemptsAreRateLimitedPerIp() throws Exception {
        Browser b = new Browser(mvc).from("198.51.100.77");
        int limited = 0;
        for (int i = 0; i < 25; i++) {
            int code = b.post("/api/v1/auth/login", """
                    {"email":"%s","password":"whatever-password"}""".formatted(email())).andReturn().getResponse().getStatus();
            if (code == 429) {
                limited++;
            }
        }
        assertThat(limited).isGreaterThan(0);
        b.post("/api/v1/auth/login", "{\"email\":\"a@b.co\",\"password\":\"x\"}")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void refreshRotatesTheToken() throws Exception {
        Browser b = registered(email());
        String before = b.cookie("tk_rt");
        b.post("/api/v1/auth/refresh").andExpect(status().isNoContent());
        assertThat(b.cookie("tk_rt")).isNotNull().isNotEqualTo(before);
        b.get("/api/v1/me").andExpect(jsonPath("$.authenticated").value(true));
    }

    @Test
    void replayingARotatedRefreshTokenRevokesTheSession() throws Exception {
        Browser victim = registered(email());
        String stolen = victim.cookie("tk_rt");
        victim.post("/api/v1/auth/refresh").andExpect(status().isNoContent());

        clock.advance(Duration.ofMinutes(1));
        Browser attacker = new Browser(mvc);
        attacker.setCookie("tk_rt", stolen, "/api/v1/auth");
        attacker.post("/api/v1/auth/refresh").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));

        // The legitimate holder is signed out too: the whole session is burned.
        victim.post("/api/v1/auth/refresh").andExpect(status().isUnauthorized());
        victim.get("/api/v1/me/sessions").andExpect(status().isUnauthorized());
    }

    @Test
    void concurrentRefreshWithinGraceWindowIsNotTreatedAsTheft() throws Exception {
        Browser tabA = registered(email());
        String shared = tabA.cookie("tk_rt");
        tabA.post("/api/v1/auth/refresh").andExpect(status().isNoContent());

        Browser tabB = new Browser(mvc);
        tabB.setCookie("tk_rt", shared, "/api/v1/auth");
        tabB.post("/api/v1/auth/refresh").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REFRESH_IN_PROGRESS"));
        tabA.post("/api/v1/auth/refresh").andExpect(status().isNoContent());
    }

    @Test
    void logoutRevokesTheAccessTokenImmediately() throws Exception {
        Browser b = registered(email());
        String access = b.cookie("tk_at");
        b.post("/api/v1/auth/logout").andExpect(status().isNoContent());
        assertThat(b.cookie("tk_at")).isNull();

        Browser replay = new Browser(mvc);
        replay.setCookie("tk_at", access, "/api");
        replay.get("/api/v1/me/sessions").andExpect(status().isUnauthorized());
    }

    @Test
    void forgedOrTamperedTokensAreRejected() throws Exception {
        Browser b = registered(email());
        String token = b.cookie("tk_at");
        String[] parts = token.split("\\.");
        String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]))
                .replace("\"roles\":[\"CUSTOMER\"]", "\"roles\":[\"ADMIN\"]");
        String tampered = parts[0] + "." + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes())
                + "." + parts[2];
        Browser forger = new Browser(mvc);
        forger.setCookie("tk_at", tampered, "/api");
        forger.get("/api/v1/me/sessions").andExpect(status().isUnauthorized());

        forger.setCookie("tk_at", "not-a-jwt", "/api");
        forger.get("/api/v1/me/sessions").andExpect(status().isUnauthorized());
    }

    @Test
    void usersCannotRevokeSomeoneElsesSession() throws Exception {
        Browser alice = registered(email());
        Browser bob = registered(email());
        String aliceSessions = alice.get("/api/v1/me/sessions").andReturn().getResponse().getContentAsString();
        String aliceSessionId = JsonPath.read(aliceSessions, "$[0].id");

        bob.delete("/api/v1/me/sessions/" + aliceSessionId).andExpect(status().isNotFound());
        alice.get("/api/v1/me/sessions").andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void sessionListShowsDevicesAndMarksCurrent() throws Exception {
        String email = email();
        Browser laptop = registered(email);
        Browser phone = new Browser(mvc);
        phone.post("/api/v1/auth/login", """
                {"email":"%s","password":"%s"}""".formatted(email, PASSWORD)).andExpect(status().isOk());

        laptop.get("/api/v1/me/sessions")
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.current == true)]", hasSize(1)))
                .andExpect(jsonPath("$[0].device").value("Chrome on macOS"));

        laptop.post("/api/v1/me/sessions/revoke-others").andExpect(jsonPath("$.count").value(1));
        phone.get("/api/v1/me/sessions").andExpect(status().isUnauthorized());
    }

    @Test
    void loginHistoryRecordsSuccessesAndFailures() throws Exception {
        String email = email();
        Browser b = registered(email);
        new Browser(mvc).post("/api/v1/auth/login", """
                {"email":"%s","password":"wrong password here"}""".formatted(email));
        b.get("/api/v1/me/login-events")
                .andExpect(jsonPath("$[*].outcome", hasItem("BAD_CREDENTIALS")));
    }

    @Test
    void changingPasswordRequiresCurrentPasswordAndSignsOutOthers() throws Exception {
        String email = email();
        Browser main = registered(email);
        Browser other = new Browser(mvc);
        other.post("/api/v1/auth/login", """
                {"email":"%s","password":"%s"}""".formatted(email, PASSWORD));

        main.post("/api/v1/me/password", """
                        {"currentPassword":"wrong current password","newPassword":"a brand new long passphrase"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("currentPassword"));
        main.post("/api/v1/me/password", """
                        {"currentPassword":"%s","newPassword":"a brand new long passphrase"}""".formatted(PASSWORD))
                .andExpect(status().isNoContent());

        other.get("/api/v1/me/sessions").andExpect(status().isUnauthorized());
        main.get("/api/v1/me/sessions").andExpect(status().isOk());
    }

    @Test
    void stateChangingRequestsRequireCsrfToken() throws Exception {
        Browser b = registered(email());
        b.forget("XSRF-TOKEN");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/me/sessions/revoke-others")
                        .cookie(new jakarta.servlet.http.Cookie("tk_at", b.cookie("tk_at"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void customersCannotReachAdminApis() throws Exception {
        Browser customer = registered(email());
        customer.get("/api/v1/admin/audit-log").andExpect(status().is(not(200)));
    }
}
