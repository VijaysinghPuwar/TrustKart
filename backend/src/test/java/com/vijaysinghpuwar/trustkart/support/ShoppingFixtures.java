package com.vijaysinghpuwar.trustkart.support;

import com.vijaysinghpuwar.trustkart.catalog.seed.DemoCatalogSeeder;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Creates isolated test products so purchase tests never fight over the shared demo catalog's stock. */
@Component
public class ShoppingFixtures {

    private final JdbcClient jdbc;
    private final DemoCatalogSeeder seeder;

    public ShoppingFixtures(JdbcClient jdbc, DemoCatalogSeeder seeder) {
        this.jdbc = jdbc;
        this.seeder = seeder;
    }

    public long product(String price, int stock) {
        return product(price, stock, false);
    }

    public long product(String price, int stock, boolean backorder) {
        seeder.seed();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        long id = jdbc.sql("""
                        INSERT INTO product (sku, slug, name, brand_id, category_id, summary, description, price, specs, keywords)
                        VALUES (:sku, :slug, :name, (SELECT id FROM brand ORDER BY id LIMIT 1),
                                (SELECT id FROM category WHERE slug = 'cables'), 'Test item', 'Test item', :price, '{}', '')
                        RETURNING id""")
                .param("sku", "TEST-" + suffix).param("slug", "test-" + suffix).param("name", "Test product " + suffix)
                .param("price", new BigDecimal(price)).query(Long.class).single();
        jdbc.sql("INSERT INTO inventory (product_id, available, low_stock_threshold, backorder_allowed) VALUES (:id, :a, 0, :b)")
                .param("id", id).param("a", stock).param("b", backorder).update();
        return id;
    }

    public int stock(long productId) {
        return jdbc.sql("SELECT available FROM inventory WHERE product_id = :id").param("id", productId).query(Integer.class).single();
    }
}
