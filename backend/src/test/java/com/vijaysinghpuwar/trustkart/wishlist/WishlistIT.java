package com.vijaysinghpuwar.trustkart.wishlist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Wishlists are read in two queries however many lists there are; these pin down what that read must return. */
@IntegrationTest
class WishlistIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    ShoppingFixtures fixtures;

    @Autowired
    JdbcClient jdbc;

    String createList(Browser b, String name) throws Exception {
        String body = b.post("/api/v1/wishlist/lists", "{\"name\":\"" + name + "\"}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    void add(Browser b, long productId, String listId) throws Exception {
        b.post("/api/v1/wishlist/items", "{\"productId\":" + productId + (listId == null ? "" : ",\"listId\":\"" + listId + "\"") + "}")
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    void severalListsKeepTheirOwnItemsInNewestFirstOrder() throws Exception {
        long a = fixtures.product("10.00", 5);
        long b = fixtures.product("20.00", 5);
        long c = fixtures.product("30.00", 5);
        Browser shopper = new Browser(mvc);
        shopper.get("/api/v1/wishlist").andExpect(jsonPath("$", hasSize(0)));

        add(shopper, a, null); // creates the default list
        add(shopper, b, null);
        String lab = createList(shopper, "Dream Homelab");
        add(shopper, b, lab); // the same product in two lists
        add(shopper, c, lab);
        createList(shopper, "Empty for now");
        jdbc.sql("UPDATE wishlist_item SET added_at = now() + (product_id - :a) * interval '1 second' WHERE product_id IN (:ids)")
                .param("a", a).param("ids", java.util.List.of(a, b, c)).update(); // later ids were "added" later

        shopper.get("/api/v1/wishlist")
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].isDefault").value(true))
                .andExpect(jsonPath("$[0].items[*].id", contains((int) b, (int) a)))
                .andExpect(jsonPath("$[1].name").value("Dream Homelab"))
                .andExpect(jsonPath("$[1].items[*].id", contains((int) c, (int) b)))
                .andExpect(jsonPath("$[2].items", hasSize(0)));

        // A drafted product drops out of every list without disturbing the others.
        jdbc.sql("UPDATE product SET status = 'DRAFT' WHERE id = :id").param("id", b).update();
        shopper.get("/api/v1/wishlist")
                .andExpect(jsonPath("$[0].items[*].id", contains((int) a)))
                .andExpect(jsonPath("$[1].items[*].id", contains((int) c)));
    }

    @Test
    void listsBelongToTheirShopper() throws Exception {
        long product = fixtures.product("15.00", 5);
        Browser alice = new Browser(mvc);
        Browser bob = new Browser(mvc);
        String alicesList = createList(alice, "Alice's picks");
        add(alice, product, alicesList);

        bob.post("/api/v1/wishlist/items", "{\"productId\":" + product + ",\"listId\":\"" + alicesList + "\"}")
                .andExpect(status().isNotFound());
        bob.get("/api/v1/wishlist").andExpect(jsonPath("$", hasSize(0)));
        alice.get("/api/v1/wishlist").andExpect(jsonPath("$[0].items", hasSize(1)));
    }

    /** Hearting several products at once used to make every request but one fail creating the default list. */
    @Test
    void heartsTappedAtOnceAllLand() throws Exception {
        List<Long> products = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            products.add(fixtures.product("9.00", 5));
        }
        Browser shopper = new Browser(mvc);
        shopper.post("/api/v1/cart/items", "{\"productId\":" + products.getFirst() + ",\"quantity\":1}"); // a shopper, no lists yet
        Cookie guest = new Cookie("tk_guest", shopper.cookie("tk_guest"));
        Cookie xsrf = new Cookie("XSRF-TOKEN", shopper.cookie("XSRF-TOKEN"));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(products.size());
        List<Future<Integer>> results = new ArrayList<>();
        for (long product : products) {
            results.add(pool.submit(() -> {
                start.await();
                return mvc.perform(MockMvcRequestBuilders.post("/api/v1/wishlist/items")
                                .contentType(MediaType.APPLICATION_JSON).content("{\"productId\":" + product + "}")
                                .cookie(guest, xsrf).header("X-XSRF-TOKEN", xsrf.getValue()))
                        .andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        for (Future<Integer> f : results) {
            assertThat(f.get()).isLessThan(300);
        }
        pool.shutdown();
        shopper.get("/api/v1/wishlist").andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].items", hasSize(6)));
    }
}
