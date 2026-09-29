package com.vijaysinghpuwar.trustkart.config;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vijaysinghpuwar.trustkart.catalog.seed.DemoCatalogSeeder;
import com.vijaysinghpuwar.trustkart.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Public catalog and search responses are shared through the CDN, so they must be cacheable and must never set a
 * cookie. Everything else keeps issuing the CSRF cookie, and state-changing requests still need the token.
 */
@IntegrationTest
class PublicCacheIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    DemoCatalogSeeder seeder;

    @BeforeEach
    void seed() {
        seeder.seed();
    }

    @Test
    void publicCatalogAndSearchReadsAreCdnCacheableAndCookieFree() throws Exception {
        for (String path : new String[] {"/api/v1/catalog/home", "/api/v1/catalog/categories",
                "/api/v1/catalog/products?size=2", "/api/v1/search?q=laptop", "/api/v1/search/suggest?q=ma"}) {
            mvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", containsString("public")))
                    .andExpect(header().string("Cache-Control", containsString("s-maxage=300")))
                    .andExpect(header().string("Cache-Control", containsString("stale-while-revalidate")))
                    .andExpect(header().doesNotExist("Set-Cookie"));
        }
    }

    @Test
    void privateEndpointsStillIssueTheCsrfCookieAndWritesStillNeedTheToken() throws Exception {
        mvc.perform(get("/api/v1/auth/csrf")).andExpect(cookie().exists("XSRF-TOKEN"));
        mvc.perform(post("/api/v1/cart/items").contentType(MediaType.APPLICATION_JSON).content("{\"productId\":1,\"quantity\":1}"))
                .andExpect(status().isForbidden());
    }
}
