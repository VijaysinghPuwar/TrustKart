package com.vijaysinghpuwar.trustkart.collection;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.vijaysinghpuwar.trustkart.catalog.application.CatalogService;
import com.vijaysinghpuwar.trustkart.catalog.application.CategoryTree;
import com.vijaysinghpuwar.trustkart.common.money.MoneyWire;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
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

    public record Achievement(String code, String title, String description, boolean unlocked, String progress) {}

    public record CollectionView(List<OwnedItem> items, Stats stats, List<Achievement> achievements, boolean simulation) {}

    private static final BigDecimal DATACENTER_DREAMER = new BigDecimal("100000");
    private static final BigDecimal MILLION = new BigDecimal("1000000");

    private final JdbcClient jdbc;
    private final CatalogService catalog;

    public CollectionService(JdbcClient jdbc, CatalogService catalog) {
        this.jdbc = jdbc;
        this.catalog = catalog;
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
        return new CollectionView(items, stats, achievements(shopperId, stats), true);
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

    /** Achievements are computed from purchase history on read: nothing to unlock, sell or manipulate. */
    private List<Achievement> achievements(long shopperId, Stats stats) {
        CategoryTree tree = catalog.tree();
        int gpus = unitsIn(shopperId, subtree(tree, "gpus"));
        int servers = unitsIn(shopperId, subtree(tree, "servers"));
        int securityKeys = unitsIn(shopperId, subtree(tree, "security-keys"));
        BigDecimal value = new BigDecimal(stats.collectionValue());
        BigDecimal lifetime = jdbc.sql("SELECT coalesce(sum(total), 0) FROM virtual_purchase WHERE shopper_id = :s AND status = 'COMPLETED'")
                .param("s", shopperId).query(BigDecimal.class).single();
        return List.of(
                new Achievement("FIRST_PURCHASE", "First purchase", "Complete your first virtual order.",
                        stats.purchases() > 0, Math.min(stats.purchases(), 1) + " / 1"),
                new Achievement("GPU_COLLECTOR", "GPU collector", "Own five graphics cards.", gpus >= 5, Math.min(gpus, 5) + " / 5"),
                new Achievement("HOMELAB_STARTER", "Homelab starter", "Buy your first server.", servers >= 1, Math.min(servers, 1) + " / 1"),
                new Achievement("KEY_HOLDER", "Key holder", "Own a hardware security key.", securityKeys >= 1, Math.min(securityKeys, 1) + " / 1"),
                new Achievement("DATACENTER_DREAMER", "Datacenter dreamer", "Build a collection worth $100,000.",
                        value.compareTo(DATACENTER_DREAMER) >= 0, "$" + value.min(DATACENTER_DREAMER).toBigInteger() + " / $100000"),
                new Achievement("MILLION_DOLLAR_CART", "Million dollar cart", "Complete $1,000,000 in virtual purchases.",
                        lifetime.compareTo(MILLION) >= 0, "$" + lifetime.min(MILLION).toBigInteger() + " / $1000000"));
    }

    private static Set<Long> subtree(CategoryTree tree, String slug) {
        return tree.bySlug(slug).map(n -> Set.copyOf(tree.selfAndDescendantIds(n.id()))).orElse(Set.of());
    }

    private int unitsIn(long shopperId, Collection<Long> categoryIds) {
        if (categoryIds.isEmpty()) {
            return 0;
        }
        return jdbc.sql("""
                        SELECT coalesce(sum(pi.quantity), 0) FROM virtual_purchase_item pi
                        JOIN virtual_purchase vp ON vp.id = pi.purchase_id JOIN product p ON p.id = pi.product_id
                        WHERE vp.shopper_id = :s AND vp.status = 'COMPLETED' AND p.category_id IN (:c)""")
                .param("s", shopperId).param("c", categoryIds).query(Integer.class).single();
    }
}
