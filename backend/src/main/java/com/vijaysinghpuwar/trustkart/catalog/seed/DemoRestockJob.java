package com.vijaysinghpuwar.trustkart.catalog.seed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The demo catalog is shared by every visitor, and virtual purchases really decrement stock. This job tops
 * low items back up so the store never empties. Products seeded as sold out (restock_target = 0) stay sold out.
 */
@Component
@ConditionalOnBooleanProperty("trustkart.demo.seed-catalog")
class DemoRestockJob {

    private static final Logger log = LoggerFactory.getLogger(DemoRestockJob.class);

    private final JdbcClient jdbc;

    DemoRestockJob(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Scheduled(fixedDelayString = "${trustkart.demo.restock-interval:PT15M}", initialDelayString = "PT1M")
    void restock() {
        int updated = jdbc.sql("""
                UPDATE inventory SET available = restock_target, updated_at = now(), version = version + 1
                WHERE restock_target > 0 AND available <= low_stock_threshold AND available < restock_target""")
                .update();
        if (updated > 0) {
            log.info("Demo restock topped up {} products", updated);
        }
    }
}
