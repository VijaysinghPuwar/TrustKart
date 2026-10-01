package com.vijaysinghpuwar.trustkart.cart;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.vijaysinghpuwar.trustkart.support.Browser;
import com.vijaysinghpuwar.trustkart.support.IntegrationTest;
import com.vijaysinghpuwar.trustkart.support.ShoppingFixtures;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Adding to the cart increments a line's quantity. Concurrent adds (a double-tapped button, two tabs) used to read the
 * same old quantity and overwrite each other, so eight adds of one could leave a quantity of 2.
 */
@IntegrationTest
class CartConcurrencyIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    ShoppingFixtures fixtures;

    private List<Integer> addConcurrently(Browser shopper, long productId, int n) throws Exception {
        Cookie guest = new Cookie("tk_guest", shopper.cookie("tk_guest"));
        Cookie xsrf = new Cookie("XSRF-TOKEN", shopper.cookie("XSRF-TOKEN"));
        String body = "{\"productId\":%d,\"quantity\":1}".formatted(productId);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(n);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return mvc.perform(MockMvcRequestBuilders.post("/api/v1/cart/items")
                                .contentType(MediaType.APPLICATION_JSON).content(body)
                                .cookie(guest, xsrf).header("X-XSRF-TOKEN", xsrf.getValue()))
                        .andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        List<Integer> codes = new ArrayList<>();
        for (Future<Integer> f : results) {
            codes.add(f.get());
        }
        pool.shutdown();
        return codes;
    }

    @Test
    void concurrentAddsToAnExistingLineAreAllCounted() throws Exception {
        long product = fixtures.product("12.00", 100);
        Browser shopper = new Browser(mvc);
        shopper.post("/api/v1/cart/items", "{\"productId\":%d,\"quantity\":1}".formatted(product));

        assertThat(addConcurrently(shopper, product, 8)).containsOnly(200);

        String cart = shopper.get("/api/v1/cart").andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Integer>>read(cart, "$.items[*].quantity")).containsExactly(9);
    }

    @Test
    void concurrentFirstAddsCreateOneLine() throws Exception {
        long product = fixtures.product("12.00", 100);
        Long other = fixtures.product("5.00", 100);
        Browser shopper = new Browser(mvc);
        shopper.post("/api/v1/cart/items", "{\"productId\":%d,\"quantity\":1}".formatted(other)); // creates the shopper

        assertThat(addConcurrently(shopper, product, 6)).containsOnly(200);

        String cart = shopper.get("/api/v1/cart").andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Integer>>read(cart, "$.items[?(@.product.id == %d)].quantity".formatted(product)))
                .containsExactly(6);
    }
}
