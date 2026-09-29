package com.vijaysinghpuwar.trustkart.notification;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.vijaysinghpuwar.trustkart.common.error.NotFoundException;
import com.vijaysinghpuwar.trustkart.leaderboard.LeaderboardPeriod;
import com.vijaysinghpuwar.trustkart.leaderboard.LeaderboardService;
import com.vijaysinghpuwar.trustkart.purchase.OrderTracking;
import com.vijaysinghpuwar.trustkart.purchase.TrackingStage;
import com.vijaysinghpuwar.trustkart.shopper.ShopperMergedEvent;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * In-app notifications derived from order milestones. Nothing schedules them: whenever a shopper asks, the
 * milestones their orders have reached by now are inserted with ON CONFLICT DO NOTHING on a deterministic
 * dedupe key, so generation is idempotent and a notification is stamped with when the event happened, not
 * when it was noticed.
 */
@Service
public class NotificationService {

    /** Orders older than this no longer produce notifications (they were delivered or closed long ago). */
    private static final Duration LOOKBACK = Duration.ofDays(45);
    private static final int KEEP = 200;

    public enum Type {
        ORDER_PLACED(true), ORDER_SHIPPED(true), OUT_FOR_DELIVERY(false), DELIVERED(false), ORDER_CANCELLED(true),
        ORDER_REFUNDED(true);

        /** true: governed by the "order updates" preference; false: by "delivery updates". */
        final boolean orderUpdate;

        Type(boolean orderUpdate) {
            this.orderUpdate = orderUpdate;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record NotificationView(UUID id, String type, String title, String body, String link, String imageUrl,
            Instant createdAt, boolean read) {}

    public record NotificationPage(List<NotificationView> items, long unreadCount, int page, int size, long totalItems) {}

    /** {@code leaderboardUpdates}: rank milestones and monthly results. */
    public record Preferences(boolean orderUpdates, boolean deliveryUpdates, boolean leaderboardUpdates) {}

    private record Order(UUID id, String orderNumber, String status, Instant createdAt, Instant closedAt, int itemCount,
            String firstItem, String imageUrl) {}

    private final JdbcClient jdbc;
    private final Clock clock;
    private final LeaderboardService leaderboards;

    public NotificationService(JdbcClient jdbc, Clock clock, LeaderboardService leaderboards) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.leaderboards = leaderboards;
    }

    @Transactional
    public NotificationPage list(long shopperId, int page, int size) {
        sync(shopperId);
        List<NotificationView> items = jdbc.sql("""
                        SELECT public_id, type, title, body, link, image_url, created_at, read_at FROM notification
                        WHERE shopper_id = :s ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset""")
                .param("s", shopperId).param("limit", size).param("offset", page * size)
                .query((rs, n) -> new NotificationView(rs.getObject("public_id", UUID.class), rs.getString("type"),
                        rs.getString("title"), rs.getString("body"), rs.getString("link"), rs.getString("image_url"),
                        rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("read_at") != null))
                .list();
        long total = jdbc.sql("SELECT count(*) FROM notification WHERE shopper_id = :s").param("s", shopperId)
                .query(Long.class).single();
        return new NotificationPage(items, unread(shopperId), page, size, total);
    }

    @Transactional
    public long unreadCount(long shopperId) {
        sync(shopperId);
        return unread(shopperId);
    }

    @Transactional
    public void markRead(long shopperId, UUID id) {
        int updated = jdbc.sql("""
                        UPDATE notification SET read_at = coalesce(read_at, :now) WHERE shopper_id = :s AND public_id = :id""")
                .param("now", Timestamp.from(clock.instant())).param("s", shopperId).param("id", id).update();
        if (updated == 0) {
            throw new NotFoundException("Notification");
        }
    }

    @Transactional
    public void markAllRead(long shopperId) {
        jdbc.sql("UPDATE notification SET read_at = :now WHERE shopper_id = :s AND read_at IS NULL")
                .param("now", Timestamp.from(clock.instant())).param("s", shopperId).update();
    }

    @Transactional(readOnly = true)
    public Preferences preferences(long shopperId) {
        return jdbc.sql("""
                        SELECT order_updates, delivery_updates, leaderboard_updates FROM notification_preference
                        WHERE shopper_id = :s""")
                .param("s", shopperId)
                .query((rs, n) -> new Preferences(rs.getBoolean(1), rs.getBoolean(2), rs.getBoolean(3)))
                .optional().orElse(new Preferences(true, true, true));
    }

    @Transactional
    public Preferences updatePreferences(long shopperId, Preferences p) {
        jdbc.sql("""
                        INSERT INTO notification_preference (shopper_id, order_updates, delivery_updates, leaderboard_updates, updated_at)
                        VALUES (:s, :o, :d, :l, :now)
                        ON CONFLICT (shopper_id) DO UPDATE
                        SET order_updates = :o, delivery_updates = :d, leaderboard_updates = :l, updated_at = :now""")
                .param("s", shopperId).param("o", p.orderUpdates()).param("d", p.deliveryUpdates())
                .param("l", p.leaderboardUpdates())
                .param("now", Timestamp.from(clock.instant())).update();
        return p;
    }

    /** Inserts every milestone reached by now that isn't recorded yet. Safe to call on every request. */
    void sync(long shopperId) {
        Instant now = clock.instant();
        Preferences prefs = preferences(shopperId);
        List<Order> orders = jdbc.sql("""
                        SELECT vp.public_id, vp.order_number, vp.status, vp.created_at, vp.refunded_at, vp.item_count,
                               first.product_name, first.image_url
                        FROM virtual_purchase vp
                        CROSS JOIN LATERAL (SELECT product_name, image_url FROM virtual_purchase_item
                                            WHERE purchase_id = vp.id ORDER BY id LIMIT 1) first
                        WHERE vp.shopper_id = :s AND vp.created_at > :since""")
                .param("s", shopperId).param("since", Timestamp.from(now.minus(LOOKBACK)))
                .query((rs, n) -> new Order(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getTimestamp(4).toInstant(), rs.getTimestamp(5) == null ? null : rs.getTimestamp(5).toInstant(),
                        rs.getInt(6), rs.getString(7), rs.getString(8)))
                .list();
        // Work out every milestone due by now, then insert only those not recorded yet: a poll where nothing changed
        // costs one indexed lookup instead of one INSERT per milestone.
        Map<String, Runnable> due = new LinkedHashMap<>();
        for (Order o : orders) {
            for (Milestone m : milestones(o, now)) {
                if (m.type().orderUpdate ? !prefs.orderUpdates() : !prefs.deliveryUpdates()) {
                    continue;
                }
                String key = "order:" + o.id() + ":" + m.type().name();
                due.put(key, () -> insert(shopperId, m.type().name(), key, m.title(), m.body(),
                        "/account/purchases/" + o.id(), o.imageUrl(), m.at()));
            }
        }
        int inserted = 0;
        if (!due.isEmpty()) {
            Set<String> recorded = recordedKeys(shopperId, due.keySet());
            for (Map.Entry<String, Runnable> e : due.entrySet()) {
                if (!recorded.contains(e.getKey())) {
                    e.getValue().run();
                    inserted++;
                }
            }
        }
        if (prefs.leaderboardUpdates()) {
            inserted += syncLeaderboard(shopperId, now);
        }
        if (inserted > 0) {
            // Keep the newest KEEP rows per shopper so the table can't grow without bound.
            jdbc.sql("""
                            DELETE FROM notification WHERE shopper_id = :s AND id NOT IN
                            (SELECT id FROM notification WHERE shopper_id = :s ORDER BY created_at DESC, id DESC LIMIT :keep)""")
                    .param("s", shopperId).param("keep", KEEP).update();
        }
    }

    private Set<String> recordedKeys(long shopperId, Collection<String> keys) {
        return new HashSet<>(jdbc.sql("SELECT dedupe_key FROM notification WHERE shopper_id = :s AND dedupe_key IN (:keys)")
                .param("s", shopperId).param("keys", keys).query(String.class).list());
    }

    // ---- Leaderboard milestones -----------------------------------------------------------------------------------

    /** Tiers, best first: a notification is sent only on reaching a better tier than any already notified. */
    private static final int[] MONTHLY_TIERS = {1, 3, 10, 50};
    private static final int[] ALL_TIME_TIERS = {10, 25, 100};

    /**
     * Rank milestones and last month's result. Milestone tiers all fall inside the cached top lists, so no ranking
     * query runs for them; last month's result is looked up only until it has been recorded. Returns rows inserted.
     */
    private int syncLeaderboard(long shopperId, Instant now) {
        Long userId = jdbc.sql("SELECT user_id FROM shopper WHERE id = :s").param("s", shopperId)
                .query(Long.class).optional().orElse(null);
        if (userId == null) {
            return 0;
        }
        // Every leaderboard notification already recorded for this shopper, in one query.
        Set<String> recorded = new HashSet<>(jdbc.sql("""
                        SELECT dedupe_key FROM notification WHERE shopper_id = :s AND dedupe_key LIKE 'lb:%'""")
                .param("s", shopperId).query(String.class).list());
        int inserted = 0;
        LeaderboardPeriod month = LeaderboardPeriod.currentMonth(clock);
        OptionalInt monthly = leaderboards.topListRank(month, userId);
        if (monthly.isPresent()) {
            int rank = monthly.getAsInt();
            inserted += milestone(shopperId, recorded, "lb:" + month.key(), MONTHLY_TIERS, rank, now,
                    tier -> tier == 1 ? "You're #1 this month" : "You're in this month's Top " + tier,
                    tier -> "Your virtual spending ranks #" + rank + " for " + month.label() + ".");
        }
        OptionalInt allTime = leaderboards.topListRank(LeaderboardPeriod.allTime(), userId);
        if (allTime.isPresent()) {
            int rank = allTime.getAsInt();
            inserted += milestone(shopperId, recorded, "lb:all-time", ALL_TIME_TIERS, rank, now,
                    tier -> "You entered the all-time Top " + tier,
                    tier -> "Your lifetime virtual spending ranks #" + rank + ".");
        }
        // Last month's final result, once the month is over. The ranking query runs only for a shopper who actually
        // completed an order last month and hasn't been told yet.
        LeaderboardPeriod previous = LeaderboardPeriod.month(month.month().minusMonths(1));
        String resultKey = "lb:" + previous.key() + ":result";
        if (!recorded.contains(resultKey) && spentIn(userId, previous)) {
            Optional<LeaderboardService.Position> result = leaderboards.position(previous, userId);
            if (result.isPresent()) {
                LeaderboardService.Position p = result.get();
                insert(shopperId, "LEADERBOARD_RESULT", resultKey, "Your " + previous.label() + " result",
                        "Final rank #" + p.rank() + " of " + p.rankedCount() + " with " + usd(p.virtualSpend())
                                + " in virtual spending across " + p.orderCount()
                                + (p.orderCount() == 1 ? " order." : " orders."),
                        "/rankings", null, previous.to());
                inserted++;
            }
        }
        return inserted;
    }

    private boolean spentIn(long userId, LeaderboardPeriod period) {
        return Boolean.TRUE.equals(jdbc.sql("""
                        SELECT EXISTS (SELECT 1 FROM virtual_purchase vp JOIN shopper s ON s.id = vp.shopper_id
                                       WHERE s.user_id = :u AND vp.status = 'COMPLETED'
                                         AND vp.created_at >= :from AND vp.created_at < :to)""")
                .param("u", userId).param("from", Timestamp.from(period.from()))
                .param("to", Timestamp.from(period.to())).query(Boolean.class).single());
    }

    private int milestone(long shopperId, Set<String> recorded, String period, int[] tiers, int rank, Instant now,
            java.util.function.IntFunction<String> title, java.util.function.IntFunction<String> body) {
        Integer reached = null;
        for (int tier : tiers) {
            if (rank <= tier) {
                reached = tier; // tiers ascend by size, so the first match is the best tier reached
                break;
            }
        }
        if (reached == null) {
            return 0;
        }
        String prefix = period + ":top";
        int bestNotified = recorded.stream().filter(k -> k.startsWith(prefix))
                .mapToInt(k -> Integer.parseInt(k.substring(prefix.length()))).min().orElse(Integer.MAX_VALUE);
        if (bestNotified <= reached) {
            return 0;
        }
        insert(shopperId, "LEADERBOARD_MILESTONE", prefix + reached, title.apply(reached), body.apply(reached),
                "/rankings", null, now);
        return 1;
    }

    private void insert(long shopperId, String type, String key, String title, String body, String link, String image,
            Instant at) {
        jdbc.sql("""
                        INSERT INTO notification (public_id, shopper_id, type, dedupe_key, title, body, link, image_url, created_at)
                        VALUES (:id, :s, :type, :key, :title, :body, :link, :img, :at)
                        ON CONFLICT (shopper_id, dedupe_key) DO NOTHING""")
                .param("id", UUID.randomUUID()).param("s", shopperId).param("type", type).param("key", key)
                .param("title", title).param("body", body).param("link", link).param("img", image)
                .param("at", Timestamp.from(at)).update();
    }

    private static String usd(String amount) {
        return java.text.NumberFormat.getCurrencyInstance(java.util.Locale.US).format(new java.math.BigDecimal(amount));
    }

    private record Milestone(Type type, String title, String body, Instant at) {}

    private static List<Milestone> milestones(Order o, Instant now) {
        String what = o.itemCount() > 1 ? o.firstItem() + " and " + (o.itemCount() - 1) + " more" : o.firstItem();
        // A cancelled order's journey stopped at cancellation.
        Instant until = "CANCELLED".equals(o.status()) && o.closedAt() != null ? o.closedAt() : now;
        TrackingStage reached = OrderTracking.stageAt(o.createdAt(), until);
        List<Milestone> out = new ArrayList<>();
        out.add(new Milestone(Type.ORDER_PLACED, "Order confirmed", "Order " + o.orderNumber() + ": " + what + ".",
                o.createdAt()));
        if (reached.ordinal() >= TrackingStage.SHIPPED.ordinal()) {
            out.add(new Milestone(Type.ORDER_SHIPPED, "Your order has shipped", what + " is on its way.",
                    OrderTracking.reachedAt(o.createdAt(), TrackingStage.SHIPPED)));
        }
        if (reached.ordinal() >= TrackingStage.OUT_FOR_DELIVERY.ordinal()) {
            out.add(new Milestone(Type.OUT_FOR_DELIVERY, "Out for delivery", what + " arrives today.",
                    OrderTracking.reachedAt(o.createdAt(), TrackingStage.OUT_FOR_DELIVERY)));
        }
        if (reached == TrackingStage.DELIVERED) {
            out.add(new Milestone(Type.DELIVERED, "Delivered", what + " was delivered.",
                    OrderTracking.reachedAt(o.createdAt(), TrackingStage.DELIVERED)));
        }
        if (o.closedAt() != null && "CANCELLED".equals(o.status())) {
            out.add(new Milestone(Type.ORDER_CANCELLED, "Order cancelled",
                    "Order " + o.orderNumber() + " was cancelled and refunded.", o.closedAt()));
        }
        if (o.closedAt() != null && "REFUNDED".equals(o.status())) {
            out.add(new Milestone(Type.ORDER_REFUNDED, "Return complete",
                    "Your refund for order " + o.orderNumber() + " was issued.", o.closedAt()));
        }
        return out;
    }

    private long unread(long shopperId) {
        return jdbc.sql("SELECT count(*) FROM notification WHERE shopper_id = :s AND read_at IS NULL")
                .param("s", shopperId).query(Long.class).single();
    }

    /** Guest notifications follow the shopper into their account; duplicates of what the account already has are dropped. */
    @EventListener
    @Transactional
    public void onShopperMerged(ShopperMergedEvent event) {
        jdbc.sql("""
                        DELETE FROM notification f WHERE f.shopper_id = :from AND EXISTS
                        (SELECT 1 FROM notification t WHERE t.shopper_id = :to AND t.dedupe_key = f.dedupe_key)""")
                .param("from", event.fromShopperId()).param("to", event.toShopperId()).update();
        jdbc.sql("UPDATE notification SET shopper_id = :to WHERE shopper_id = :from")
                .param("from", event.fromShopperId()).param("to", event.toShopperId()).update();
        jdbc.sql("""
                        UPDATE notification_preference SET shopper_id = :to WHERE shopper_id = :from
                        AND NOT EXISTS (SELECT 1 FROM notification_preference WHERE shopper_id = :to)""")
                .param("from", event.fromShopperId()).param("to", event.toShopperId()).update();
    }
}
