package com.vijaysinghpuwar.trustkart.collection;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.vijaysinghpuwar.trustkart.common.money.MoneyWire;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "My Collection" is derived, not stored: it is exactly the items in the shopper's completed (non-refunded)
 * virtual purchases. So it can never disagree with purchase history, and a refund removes items automatically.
 * All values are virtual catalog values.
 */
@Service
public class CollectionService {

    public record OwnedItem(long productId, String slug, String name, String categoryName, String imageUrl, int quantity,
            String virtualSpend, String currentValue, Instant firstAcquired, Instant lastAcquired) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Stats(int productsOwned, int distinctProducts, int purchases, String totalVirtualSpend, String collectionValue,
            Highlight mostExpensive, Highlight largestPurchase, String favoriteCategory) {}

    public record Highlight(String label, String amount) {}

    public record CollectionView(List<OwnedItem> items, Stats stats, List<Achievements.Achievement> achievements,
            boolean simulation) {}

    private final JdbcClient jdbc;
    private final Achievements achievements;

    public CollectionService(JdbcClient jdbc, Achievements achievements) {
        this.jdbc = jdbc;
        this.achievements = achievements;
    }

    @Transactional(readOnly = true)
    public CollectionView view(long shopperId) {
        List<OwnedItem> items = jdbc.sql("""
                        SELECT pi.product_id, max(pi.product_slug) AS slug, max(pi.product_name) AS name,
                               max(pi.category_name) AS category_name, max(pi.image_url) AS image_url,
                               sum(pi.quantity) AS qty, sum(pi.line_total) AS spent,
                               sum(pi.quantity) * max(pr.price) AS value,
                               min(vp.created_at) AS first_at, max(vp.created_at) AS last_at
                        FROM virtual_purchase_item pi
                        JOIN virtual_purchase vp ON vp.id = pi.purchase_id
                        JOIN product pr ON pr.id = pi.product_id
                        WHERE vp.shopper_id = :s AND vp.status = 'COMPLETED'
                        GROUP BY pi.product_id ORDER BY max(vp.created_at) DESC, pi.product_id""")
                .param("s", shopperId)
                .query((rs, i) -> new OwnedItem(rs.getLong("product_id"), rs.getString("slug"), rs.getString("name"),
                        rs.getString("category_name"), rs.getString("image_url"), rs.getInt("qty"),
                        MoneyWire.format(rs.getBigDecimal("spent")), MoneyWire.format(rs.getBigDecimal("value")),
                        rs.getTimestamp("first_at").toInstant(), rs.getTimestamp("last_at").toInstant()))
                .list();
        Stats stats = stats(shopperId, items);
        return new CollectionView(items, stats, achievements.evaluate(shopperId, new BigDecimal(stats.collectionValue()),
                stats.productsOwned(), stats.distinctProducts()), true);
    }

    private Stats stats(long shopperId, List<OwnedItem> items) {
        record Totals(int purchases, BigDecimal spend) {}
        Totals totals = jdbc.sql("""
                        SELECT count(*) AS n, coalesce(sum(total), 0) AS spend FROM virtual_purchase
                        WHERE shopper_id = :s AND status = 'COMPLETED'""")
                .param("s", shopperId)
                .query((rs, i) -> new Totals(rs.getInt("n"), rs.getBigDecimal("spend"))).single();
        BigDecimal value = items.stream().map(i -> new BigDecimal(i.currentValue())).reduce(BigDecimal.ZERO, BigDecimal::add);

        Highlight mostExpensive = jdbc.sql("""
                        SELECT pi.product_name, pi.unit_price FROM virtual_purchase_item pi
                        JOIN virtual_purchase vp ON vp.id = pi.purchase_id
                        WHERE vp.shopper_id = :s AND vp.status = 'COMPLETED' ORDER BY pi.unit_price DESC LIMIT 1""")
                .param("s", shopperId)
                .query((rs, i) -> new Highlight(rs.getString(1), MoneyWire.format(rs.getBigDecimal(2)))).optional().orElse(null);
        Highlight largest = jdbc.sql("""
                        SELECT order_number, total FROM virtual_purchase
                        WHERE shopper_id = :s AND status = 'COMPLETED' ORDER BY total DESC LIMIT 1""")
                .param("s", shopperId)
                .query((rs, i) -> new Highlight(rs.getString(1), MoneyWire.format(rs.getBigDecimal(2)))).optional().orElse(null);
        String favorite = jdbc.sql("""
                        SELECT pi.category_name FROM virtual_purchase_item pi JOIN virtual_purchase vp ON vp.id = pi.purchase_id
                        WHERE vp.shopper_id = :s AND vp.status = 'COMPLETED'
                        GROUP BY pi.category_name ORDER BY sum(pi.quantity) DESC, pi.category_name LIMIT 1""")
                .param("s", shopperId).query(String.class).optional().orElse(null);

        return new Stats(items.stream().mapToInt(OwnedItem::quantity).sum(), items.size(), totals.purchases(),
                MoneyWire.format(totals.spend()), MoneyWire.format(value), mostExpensive, largest, favorite);
    }
}
