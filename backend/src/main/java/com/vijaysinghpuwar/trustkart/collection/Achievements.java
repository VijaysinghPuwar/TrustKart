package com.vijaysinghpuwar.trustkart.collection;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.vijaysinghpuwar.trustkart.catalog.application.CatalogService;
import com.vijaysinghpuwar.trustkart.catalog.application.CategoryTree;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Achievement catalog. Every achievement is a threshold over facts derived from the shopper's own history
 * (completed orders, wishlist, address book) at read time: nothing is stored, so nothing can be granted,
 * duplicated or lost, and a refund can take progress back.
 *
 * <p>Tiers run bronze → silver → gold → platinum → legendary so there is always a next goal in sight.
 */
@Component
public class Achievements {

    enum Tier { BRONZE, SILVER, GOLD, PLATINUM, LEGENDARY }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Achievement(String code, String title, String description, String group, String tier, boolean unlocked,
            String progress, int percent) {}

    /** Everything the thresholds are measured against, gathered in a handful of queries. */
    record Facts(BigDecimal lifetimeSpend, int orders, int units, int distinctProducts, BigDecimal collectionValue,
            BigDecimal biggestOrder, BigDecimal priciestItem, int departments, int brands, int delivered,
            int configuredItems, int biggestLine, int wishlistItems, int addresses, int cancelledOrReturned,
            Map<String, Integer> unitsBySlug) {

        int units(String slug) {
            return unitsBySlug.getOrDefault(slug, 0);
        }
    }

    private record Def(String code, String title, String description, String group, Tier tier, boolean money,
            BigDecimal target, Function<Facts, BigDecimal> current) {}

    /** Categories with a specialist achievement; their units include all subcategories. */
    private static final List<String> TRACKED = List.of("gpus", "cpus", "servers", "ai-systems", "security-keys", "phones",
            "laptops", "monitors", "nas", "networking", "audio", "cameras-drones", "gaming", "smart-home", "wearables",
            "tvs", "printers", "vr", "drones", "keyboards");

    private static final List<Def> DEFS = defs();

    private final JdbcClient jdbc;
    private final CatalogService catalog;
    private final Clock clock;

    Achievements(JdbcClient jdbc, CatalogService catalog, Clock clock) {
        this.jdbc = jdbc;
        this.catalog = catalog;
        this.clock = clock;
    }

    /** Every achievement, all locked: what a visitor with no history sees, so the goals are visible from day one. */
    public List<Achievement> preview() {
        return score(new Facts(BigDecimal.ZERO, 0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, 0, 0, 0, 0, 0,
                0, Map.of()));
    }

    List<Achievement> evaluate(long shopperId, BigDecimal collectionValue, int units, int distinctProducts) {
        return score(facts(shopperId, collectionValue, units, distinctProducts));
    }

    private static List<Achievement> score(Facts facts) {
        List<Achievement> out = new ArrayList<>(DEFS.size());
        for (Def d : DEFS) {
            BigDecimal now = d.current().apply(facts).max(BigDecimal.ZERO);
            boolean unlocked = now.compareTo(d.target()) >= 0;
            int percent = unlocked ? 100 : now.multiply(BigDecimal.valueOf(100)).divide(d.target(), 0, RoundingMode.DOWN).intValue();
            String progress = d.money() ? usd(now.min(d.target())) + " / " + usd(d.target())
                    : now.min(d.target()).toBigInteger() + " / " + d.target().toBigInteger();
            out.add(new Achievement(d.code(), d.title(), d.description(), d.group(), d.tier().name(), unlocked, progress, percent));
        }
        return out;
    }

    static int count() {
        return DEFS.size();
    }

    private Facts facts(long shopperId, BigDecimal collectionValue, int units, int distinctProducts) {
        record Orders(int n, BigDecimal spend, BigDecimal biggest, int delivered) {}
        Orders orders = jdbc.sql("""
                        SELECT count(*), coalesce(sum(total), 0), coalesce(max(total), 0),
                               count(*) FILTER (WHERE created_at <= :deliveredBefore)
                        FROM virtual_purchase WHERE shopper_id = :s AND status = 'COMPLETED'""")
                .param("s", shopperId)
                .param("deliveredBefore", Timestamp.from(clock.instant().minus(Duration.ofDays(7))))
                .query((rs, i) -> new Orders(rs.getInt(1), rs.getBigDecimal(2), rs.getBigDecimal(3), rs.getInt(4))).single();

        record Items(BigDecimal priciest, int departments, int brands, int configured, int biggestLine) {}
        Items items = jdbc.sql("""
                        WITH RECURSIVE owned AS (
                            SELECT pi.product_id, pi.unit_price, pi.quantity, pi.options_label, p.brand_id, p.category_id
                            FROM virtual_purchase_item pi
                            JOIN virtual_purchase vp ON vp.id = pi.purchase_id
                            JOIN product p ON p.id = pi.product_id
                            WHERE vp.shopper_id = :s AND vp.status = 'COMPLETED'),
                        up AS (
                            SELECT c.id AS start_id, c.id, c.parent_id FROM category c
                            WHERE c.id IN (SELECT category_id FROM owned)
                            UNION ALL
                            SELECT up.start_id, c.id, c.parent_id FROM up JOIN category c ON c.id = up.parent_id)
                        SELECT coalesce(max(unit_price), 0),
                               (SELECT count(DISTINCT up.id) FROM up WHERE up.parent_id IS NULL),
                               count(DISTINCT brand_id),
                               coalesce(sum(quantity) FILTER (WHERE options_label IS NOT NULL), 0),
                               coalesce(max(quantity), 0)
                        FROM owned""")
                .param("s", shopperId)
                .query((rs, i) -> new Items(rs.getBigDecimal(1), rs.getInt(2), rs.getInt(3), rs.getInt(4), rs.getInt(5)))
                .single();

        Map<Long, Integer> byCategory = new HashMap<>();
        jdbc.sql("""
                        SELECT p.category_id, sum(pi.quantity) FROM virtual_purchase_item pi
                        JOIN virtual_purchase vp ON vp.id = pi.purchase_id JOIN product p ON p.id = pi.product_id
                        WHERE vp.shopper_id = :s AND vp.status = 'COMPLETED' GROUP BY p.category_id""")
                .param("s", shopperId)
                .query((rs, i) -> byCategory.put(rs.getLong(1), rs.getInt(2))).list();
        CategoryTree tree = catalog.tree();
        Map<String, Integer> bySlug = new HashMap<>();
        for (String slug : TRACKED) {
            tree.bySlug(slug).ifPresent(n -> bySlug.put(slug,
                    tree.selfAndDescendantIds(n.id()).stream().mapToInt(id -> byCategory.getOrDefault(id, 0)).sum()));
        }

        int wishlist = jdbc.sql("""
                        SELECT count(*) FROM wishlist_item wi JOIN wishlist w ON w.id = wi.wishlist_id WHERE w.shopper_id = :s""")
                .param("s", shopperId).query(Integer.class).single();
        int addresses = jdbc.sql("SELECT count(*) FROM shopper_address WHERE shopper_id = :s")
                .param("s", shopperId).query(Integer.class).single();
        int closed = jdbc.sql("""
                        SELECT count(*) FROM virtual_purchase WHERE shopper_id = :s AND status IN ('CANCELLED', 'REFUNDED')""")
                .param("s", shopperId).query(Integer.class).single();

        return new Facts(orders.spend(), orders.n(), units, distinctProducts, collectionValue, orders.biggest(),
                items.priciest(), items.departments(), items.brands(), orders.delivered(), items.configured(),
                items.biggestLine(), wishlist, addresses, closed, bySlug);
    }

    // ---- The catalog ----------------------------------------------------------------------------------------------

    private static List<Def> defs() {
        List<Def> d = new ArrayList<>();
        // Lifetime spend: the headline ladder, from a first splurge to a hundred billion.
        Object[][] spend = {
                {"SPEND_1K", "Window shopper no more", "Spend $1,000 in total.", "1000", Tier.BRONZE},
                {"SPEND_10K", "Big spender", "Spend $10,000 in total.", "10000", Tier.BRONZE},
                {"SPEND_100K", "Six figures", "Spend $100,000 in total.", "100000", Tier.SILVER},
                {"SPEND_1M", "Millionaire", "Spend $1 million in total.", "1000000", Tier.GOLD},
                {"SPEND_10M", "Eight-figure empire", "Spend $10 million in total.", "10000000", Tier.GOLD},
                {"SPEND_100M", "Hundred-million club", "Spend $100 million in total.", "100000000", Tier.PLATINUM},
                {"SPEND_1B", "Billionaire", "Spend $1 billion in total.", "1000000000", Tier.PLATINUM},
                {"SPEND_10B", "Deca-billionaire", "Spend $10 billion in total.", "10000000000", Tier.LEGENDARY},
                {"SPEND_100B", "Centibillionaire", "Spend $100 billion in total.", "100000000000", Tier.LEGENDARY},
        };
        for (Object[] s : spend) {
            d.add(money((String) s[0], (String) s[1], (String) s[2], "Spending", (Tier) s[4], (String) s[3], Facts::lifetimeSpend));
        }
        // Collection value: what you own right now, at today's prices.
        d.add(money("VALUE_10K", "Starter shelf", "Own a collection worth $10,000.", "Collection", Tier.BRONZE, "10000", Facts::collectionValue));
        d.add(money("VALUE_100K", "Datacenter dreamer", "Own a collection worth $100,000.", "Collection", Tier.SILVER, "100000", Facts::collectionValue));
        d.add(money("VALUE_1M", "Vault keeper", "Own a collection worth $1 million.", "Collection", Tier.GOLD, "1000000", Facts::collectionValue));
        d.add(money("VALUE_10M", "Private museum", "Own a collection worth $10 million.", "Collection", Tier.PLATINUM, "10000000", Facts::collectionValue));
        d.add(money("VALUE_100M", "Hyperscaler", "Own a collection worth $100 million.", "Collection", Tier.LEGENDARY, "100000000", Facts::collectionValue));
        // Orders.
        d.add(count("FIRST_PURCHASE", "First purchase", "Complete your first order.", "Orders", Tier.BRONZE, 1, Facts::orders));
        d.add(count("ORDERS_5", "Regular", "Complete 5 orders.", "Orders", Tier.BRONZE, 5, Facts::orders));
        d.add(count("ORDERS_10", "Loyal customer", "Complete 10 orders.", "Orders", Tier.SILVER, 10, Facts::orders));
        d.add(count("ORDERS_25", "Frequent flyer", "Complete 25 orders.", "Orders", Tier.SILVER, 25, Facts::orders));
        d.add(count("ORDERS_50", "VIP", "Complete 50 orders.", "Orders", Tier.GOLD, 50, Facts::orders));
        d.add(count("ORDERS_100", "Centurion", "Complete 100 orders.", "Orders", Tier.PLATINUM, 100, Facts::orders));
        d.add(count("ORDERS_250", "TrustKart legend", "Complete 250 orders.", "Orders", Tier.LEGENDARY, 250, Facts::orders));
        d.add(count("DELIVERED_1", "Special delivery", "Have an order delivered.", "Orders", Tier.BRONZE, 1, Facts::delivered));
        d.add(count("DELIVERED_10", "Porch pro", "Have 10 orders delivered.", "Orders", Tier.SILVER, 10, Facts::delivered));
        // Big single moments.
        d.add(money("ORDER_10K", "Treat yourself", "Place a single order worth $10,000.", "Big moments", Tier.BRONZE, "10000", Facts::biggestOrder));
        d.add(money("ORDER_100K", "Whale", "Place a single order worth $100,000.", "Big moments", Tier.SILVER, "100000", Facts::biggestOrder));
        d.add(money("ORDER_1M", "Million dollar cart", "Place a single order worth $1 million.", "Big moments", Tier.GOLD, "1000000", Facts::biggestOrder));
        d.add(money("ORDER_10M", "Procurement department", "Place a single order worth $10 million.", "Big moments", Tier.PLATINUM, "10000000", Facts::biggestOrder));
        d.add(money("ITEM_10K", "Luxury taste", "Buy a single product priced $10,000 or more.", "Big moments", Tier.SILVER, "10000", Facts::priciestItem));
        d.add(money("ITEM_100K", "Enterprise grade", "Buy a single product priced $100,000 or more.", "Big moments", Tier.GOLD, "100000", Facts::priciestItem));
        d.add(money("ITEM_1M", "Crown jewel", "Buy a single product priced $1 million or more.", "Big moments", Tier.PLATINUM, "1000000", Facts::priciestItem));
        d.add(count("BULK_10", "Bulk buyer", "Buy 10 of one product in a single order.", "Big moments", Tier.SILVER, 10, Facts::biggestLine));
        // Collector: breadth and volume.
        d.add(count("UNITS_10", "Hoarder", "Own 10 items.", "Collector", Tier.BRONZE, 10, Facts::units));
        d.add(count("UNITS_50", "Stockpile", "Own 50 items.", "Collector", Tier.SILVER, 50, Facts::units));
        d.add(count("UNITS_100", "Warehouse", "Own 100 items.", "Collector", Tier.GOLD, 100, Facts::units));
        d.add(count("UNITS_500", "Distribution center", "Own 500 items.", "Collector", Tier.PLATINUM, 500, Facts::units));
        d.add(count("UNITS_1000", "Fulfillment empire", "Own 1,000 items.", "Collector", Tier.LEGENDARY, 1000, Facts::units));
        d.add(count("DISTINCT_10", "Curator", "Own 10 different products.", "Collector", Tier.BRONZE, 10, Facts::distinctProducts));
        d.add(count("DISTINCT_50", "Connoisseur", "Own 50 different products.", "Collector", Tier.SILVER, 50, Facts::distinctProducts));
        d.add(count("DISTINCT_100", "Encyclopedia", "Own 100 different products.", "Collector", Tier.GOLD, 100, Facts::distinctProducts));
        d.add(count("DISTINCT_250", "The whole catalog (almost)", "Own 250 different products.", "Collector", Tier.LEGENDARY, 250, Facts::distinctProducts));
        // Explorer.
        d.add(count("DEPARTMENTS_3", "Browser", "Buy from 3 different departments.", "Explorer", Tier.BRONZE, 3, Facts::departments));
        d.add(count("DEPARTMENTS_6", "Explorer", "Buy from 6 different departments.", "Explorer", Tier.SILVER, 6, Facts::departments));
        d.add(count("DEPARTMENTS_10", "Globetrotter", "Buy from 10 different departments.", "Explorer", Tier.GOLD, 10, Facts::departments));
        d.add(count("DEPARTMENTS_ALL", "Completionist", "Buy from every department.", "Explorer", Tier.LEGENDARY, 18, Facts::departments));
        d.add(count("BRANDS_5", "Brand curious", "Buy from 5 different brands.", "Explorer", Tier.BRONZE, 5, Facts::brands));
        d.add(count("BRANDS_15", "Brand hopper", "Buy from 15 different brands.", "Explorer", Tier.SILVER, 15, Facts::brands));
        d.add(count("BRANDS_40", "Brand ambassador", "Buy from 40 different brands.", "Explorer", Tier.GOLD, 40, Facts::brands));
        d.add(count("CONFIGURED_1", "Made to order", "Buy a product in a configuration you chose.", "Explorer", Tier.BRONZE, 1, Facts::configuredItems));
        d.add(count("CONFIGURED_10", "Spec sheet savant", "Buy 10 configured products.", "Explorer", Tier.SILVER, 10, Facts::configuredItems));
        // Specialists.
        d.add(units("GPU_FIRST", "Frame chaser", "Own a graphics card.", Tier.BRONZE, 1, "gpus"));
        d.add(units("GPU_COLLECTOR", "GPU collector", "Own 5 graphics cards.", Tier.SILVER, 5, "gpus"));
        d.add(units("GPU_FARM", "Render farm", "Own 25 graphics cards.", Tier.GOLD, 25, "gpus"));
        d.add(units("CPU_3", "Core counter", "Own 3 processors.", Tier.BRONZE, 3, "cpus"));
        d.add(units("HOMELAB_STARTER", "Homelab starter", "Buy your first server.", Tier.BRONZE, 1, "servers"));
        d.add(units("SERVER_RACK", "Rack it up", "Own 10 servers.", Tier.GOLD, 10, "servers"));
        d.add(units("AI_LAB", "AI lab", "Own an AI system.", Tier.GOLD, 1, "ai-systems"));
        d.add(units("NETWORK_5", "Packet pusher", "Own 5 networking products.", Tier.SILVER, 5, "networking"));
        d.add(units("NAS_OWNER", "Data hoarder", "Own a NAS.", Tier.BRONZE, 1, "nas"));
        d.add(units("KEY_HOLDER", "Key holder", "Own a hardware security key.", Tier.BRONZE, 1, "security-keys"));
        d.add(units("PHONE_FIRST", "Pocket computer", "Own a phone.", Tier.BRONZE, 1, "phones"));
        d.add(units("PHONE_5", "Phone collector", "Own 5 phones.", Tier.SILVER, 5, "phones"));
        d.add(units("LAPTOP_3", "Road warrior", "Own 3 laptops.", Tier.SILVER, 3, "laptops"));
        d.add(units("MULTI_MONITOR", "Battlestation", "Own 3 monitors.", Tier.SILVER, 3, "monitors"));
        d.add(units("KEYBOARD_3", "Keyboard enthusiast", "Own 3 keyboards.", Tier.BRONZE, 3, "keyboards"));
        d.add(units("AUDIOPHILE", "Audiophile", "Own 3 audio products.", Tier.BRONZE, 3, "audio"));
        d.add(units("HOME_THEATER", "Home theater", "Own a TV.", Tier.BRONZE, 1, "tvs"));
        d.add(units("CREATOR", "Creator kit", "Own 3 cameras or drones.", Tier.SILVER, 3, "cameras-drones"));
        d.add(units("PILOT", "Pilot", "Own a drone.", Tier.BRONZE, 1, "drones"));
        d.add(units("GAMER", "Player one", "Own 3 gaming products.", Tier.BRONZE, 3, "gaming"));
        d.add(units("SMART_HOME", "Smart home", "Own 5 smart-home devices.", Tier.SILVER, 5, "smart-home"));
        d.add(units("WEARABLE", "Quantified self", "Own a wearable.", Tier.BRONZE, 1, "wearables"));
        d.add(units("PRINT_SHOP", "Print shop", "Own 2 printers.", Tier.BRONZE, 2, "printers"));
        d.add(units("METAVERSE", "Metaverse", "Own a VR headset.", Tier.BRONZE, 1, "vr"));
        // Habits.
        d.add(count("WISHLIST_10", "Dreamer", "Save 10 products to your wishlists.", "Habits", Tier.BRONZE, 10, Facts::wishlistItems));
        d.add(count("ADDRESS_BOOK", "Home base", "Save a delivery address.", "Habits", Tier.BRONZE, 1, Facts::addresses));
        d.add(count("CHANGED_MIND", "Changed my mind", "Cancel or return an order.", "Habits", Tier.BRONZE, 1, Facts::cancelledOrReturned));
        return List.copyOf(d);
    }

    private static Def money(String code, String title, String desc, String group, Tier tier, String target,
            Function<Facts, BigDecimal> f) {
        return new Def(code, title, desc, group, tier, true, new BigDecimal(target), f);
    }

    private static Def count(String code, String title, String desc, String group, Tier tier, int target,
            Function<Facts, Integer> f) {
        return new Def(code, title, desc, group, tier, false, BigDecimal.valueOf(target), x -> BigDecimal.valueOf(f.apply(x)));
    }

    private static Def units(String code, String title, String desc, Tier tier, int target, String slug) {
        return count(code, title, desc, "Specialist", tier, target, x -> x.units(slug));
    }

    /** $950, $12.5K, $3.2M, $1B: compact, truncated so progress never looks further along than it is. */
    static String usd(BigDecimal v) {
        String[] units = {"", "K", "M", "B", "T"};
        BigDecimal n = v;
        int u = 0;
        while (n.compareTo(BigDecimal.valueOf(1000)) >= 0 && u < units.length - 1) {
            n = n.divide(BigDecimal.valueOf(1000));
            u++;
        }
        BigDecimal shown = n.setScale(u == 0 ? 0 : 1, RoundingMode.DOWN).stripTrailingZeros();
        return "$" + shown.toPlainString() + units[u];
    }
}
