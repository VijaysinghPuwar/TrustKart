package com.vijaysinghpuwar.trustkart.catalog.infra;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Stock movements as single conditional UPDATEs. The WHERE clause is the oversell guard: two concurrent
 * purchases of the last unit can't both succeed because only one UPDATE matches, with no read-then-write race.
 */
@Repository
public class InventoryRepository {

    private final JdbcClient jdbc;

    public InventoryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Takes {@code quantity} units if that many are sellable. Returns false (and changes nothing) otherwise. */
    public boolean tryCommit(long productId, int quantity) {
        return jdbc.sql("""
                        UPDATE inventory SET available = available - :q, updated_at = now(), version = version + 1
                        WHERE product_id = :id AND available - reserved >= :q""")
                .param("q", quantity).param("id", productId).update() == 1;
    }

    public void release(long productId, int quantity) {
        jdbc.sql("UPDATE inventory SET available = available + :q, updated_at = now(), version = version + 1 WHERE product_id = :id")
                .param("q", quantity).param("id", productId).update();
    }
}
