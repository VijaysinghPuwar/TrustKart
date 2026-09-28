package com.vijaysinghpuwar.trustkart.common;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vijaysinghpuwar.trustkart.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class PlatformIT {

    @Autowired
    MockMvc mvc;

    @Test
    void healthIsPublicAndUp() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                // Component details (DB, Redis) are only shown to admins.
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void everyResponseCarriesAGeneratedRequestId() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(header().string("X-Request-ID", matchesPattern("[0-9A-Z]{10}")));
    }

    @Test
    void wellFormedIncomingRequestIdIsReused() throws Exception {
        mvc.perform(get("/actuator/health").header("X-Request-ID", "client-abc-123"))
                .andExpect(header().string("X-Request-ID", "client-abc-123"));
    }

    @Test
    void malformedIncomingRequestIdIsReplaced() throws Exception {
        mvc.perform(get("/actuator/health").header("X-Request-ID", "bad\nid<script>"))
                .andExpect(header().string("X-Request-ID", matchesPattern("[0-9A-Z]{10}")));
    }

    @Test
    void unknownApiRouteReturnsStructured404WithoutInternals() throws Exception {
        mvc.perform(get("/api/v1/catalog/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist());
    }

    @Test
    void protectedRouteRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/admin/anything"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void stateChangingRequestWithoutCsrfTokenIsRejected() throws Exception {
        mvc.perform(post("/api/v1/cart/items").contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void securityHeadersArePresent() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(header().string("Content-Security-Policy", matchesPattern(".*default-src 'none'.*")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"));
    }
}
