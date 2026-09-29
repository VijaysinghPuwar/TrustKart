package com.vijaysinghpuwar.trustkart.leaderboard;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.vijaysinghpuwar.trustkart.common.money.MoneyWire;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Virtual-spending leaderboards, computed from order history.
 *
 * <p><b>What counts:</b> orders in status COMPLETED that belong to a signed-in account. Adding virtual funds never
 * counts; cancelled and returned orders leave status COMPLETED and so drop out of every total automatically (there are
 * no partial refunds). Guest purchases count once they move into an account at sign-in. Staff accounts (any role
 * besides CUSTOMER) and accounts an operator marked ineligible are excluded, so testing never produces public winners.
 * Unlimited-mode purchases count: this is a recreational virtual-spending ranking, not a measure of wealth.
 *
 * <p><b>Ranking:</b> {@code RANK()} over spend, so tied totals share a rank (1, 2, 2, 4). Display order among ties is
 * deterministic: whoever reached the total first (earlier latest order), then a stable internal id.
 *
 * <p><b>Caching:</b> the public boards are cached briefly and evicted after any purchase, cancellation, return or
 * leaderboard-profile change commits. There is no stored total that could drift, so nothing needs rebuilding.
 */
@Service
public class LeaderboardService {

    /** An anonymous row: hidden accounts are ranked but never identified. */
    public static final String ANONYMOUS = "Anonymous collector";

    private static final Duration CACHE_TTL = Duration.ofSeconds(30);

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Entry(int rank, String displayName, String avatarUrl, boolean anonymous, String virtualSpend,
            int orderCount, Instant memberSince, boolean currentUser) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Board(String type, String period, String periodLabel, int limit, int rankedCount, Instant generatedAt,
            Instant periodEndsAt, List<Entry> entries) {}

    /** A signed-in user's own standing; present even when outside the public top list. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Position(int rank, int rankedCount, String virtualSpend, int orderCount, boolean inTopList,
            String gapToTopList, String gapToTop10) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Standing(String period, String periodLabel, Position monthly, Position allTime, String displayName,
            boolean visible) {}

    /** One ranked row with the internal user id, which never leaves this service. */
    record Row(long userId, int rank, BigDecimal spend, int orders, Instant memberSince, String displayName,
            boolean visible, String avatarUrl) {}

    private record Cached(List<Row> rows, int rankedCount, Instant generatedAt, Instant expires) {}

    private final JdbcClient jdbc;
    private final Clock clock;
    private final LeaderboardProfiles profiles;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    public LeaderboardService(JdbcClient jdbc, Clock clock, LeaderboardProfiles profiles) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.profiles = profiles;
    }

    @Transactional(readOnly = true)
    public Board board(LeaderboardPeriod period, Long viewerUserId) {
        Cached c = cached(period);
        List<Entry> entries = c.rows().stream().map(r -> toEntry(r, viewerUserId, period)).toList();
        return new Board(period.type().name(), period.key(), period.label(), period.limit(), c.rankedCount(),
                c.generatedAt(), period.month() == null ? null : period.to(), entries);
    }

    @Transactional(readOnly = true)
    public Standing standing(long userId) {
        LeaderboardPeriod month = LeaderboardPeriod.currentMonth(clock);
        LeaderboardProfiles.Profile profile = profiles.find(userId).orElse(null);
        return new Standing(month.key(), month.label(), position(month, userId).orElse(null),
                position(LeaderboardPeriod.allTime(), userId).orElse(null),
                profile == null ? null : profile.displayName(), profile != null && profile.visible());
    }

    /** The user's rank in a period, or empty when they have no eligible spend there. */
    @Transactional(readOnly = true)
    public Optional<Position> position(LeaderboardPeriod period, long userId) {
        Cached c = cached(period);
        Optional<Row> mine = rankedRow(period, userId);
        return mine.map(r -> {
            boolean inTop = c.rows().stream().anyMatch(x -> x.userId() == userId);
            BigDecimal gapTop = !inTop && c.rows().size() >= period.limit() ? gap(c.rows().getLast().spend(), r.spend()) : null;
            BigDecimal gap10 = r.rank() > 10 && c.rows().size() >= 10 ? gap(c.rows().get(9).spend(), r.spend()) : null;
            return new Position(r.rank(), c.rankedCount(), MoneyWire.format(r.spend()), r.orders(), inTop,
                    gapTop == null ? null : MoneyWire.format(gapTop), gap10 == null ? null : MoneyWire.format(gap10));
        });
    }

    /** Ranked rows with internal ids, for the admin reconciliation only; never serialized to clients. */
    @Transactional(readOnly = true)
    List<Row> rows(LeaderboardPeriod period) {
        return cached(period).rows();
    }

    /** Evicts cached boards once a purchase, cancellation, return or profile change has committed. */
    @TransactionalEventListener(fallbackExecution = true)
    public void onChange(LeaderboardChanged event) {
        cache.clear();
    }

    // ---- Queries ------------------------------------------------------------------------------------------------

    /**
     * Eligible spend per account in [from, to), ranked. {@code RANK()} gives tied totals the same rank; ROW_NUMBER
     * with the tie-breakers fixes the display order.
     */
    private static final String RANKED = """
            WITH spend AS (
                SELECT s.user_id, sum(vp.total) AS spend, count(*) AS orders, max(vp.created_at) AS last_at
                FROM virtual_purchase vp
                JOIN shopper s ON s.id = vp.shopper_id
                WHERE vp.status = 'COMPLETED' AND s.user_id IS NOT NULL
                  AND vp.created_at >= :from AND vp.created_at < :to
                GROUP BY s.user_id
            ), eligible AS (
                SELECT sp.* FROM spend sp
                JOIN app_user u ON u.id = sp.user_id AND u.status = 'ACTIVE'
                LEFT JOIN leaderboard_profile lp ON lp.user_id = sp.user_id
                WHERE sp.spend > 0 AND coalesce(lp.eligible, TRUE)
                  AND NOT EXISTS (SELECT 1 FROM user_role ur JOIN role r ON r.id = ur.role_id
                                  WHERE ur.user_id = sp.user_id AND r.name <> 'CUSTOMER')
            ), ranked AS (
                SELECT e.*, RANK() OVER (ORDER BY e.spend DESC) AS rnk,
                       ROW_NUMBER() OVER (ORDER BY e.spend DESC, e.last_at ASC, e.user_id ASC) AS pos,
                       count(*) OVER () AS ranked_count
                FROM eligible e
            )
            SELECT r.user_id, r.rnk, r.spend, r.orders, r.pos, r.ranked_count, u.created_at AS member_since,
                   lp.display_name, coalesce(lp.visible, FALSE) AS visible,
                   CASE WHEN lp.visible AND lp.show_avatar THEN u.avatar_url END AS avatar_url
            FROM ranked r
            JOIN app_user u ON u.id = r.user_id
            LEFT JOIN leaderboard_profile lp ON lp.user_id = r.user_id
            """;

    private Cached cached(LeaderboardPeriod period) {
        Instant now = clock.instant();
        Cached c = cache.get(period.key());
        if (c != null && c.expires().isAfter(now)) {
            return c;
        }
        record Counted(Row row, int rankedCount) {}
        List<Counted> rows = jdbc.sql(RANKED + " WHERE r.pos <= :limit ORDER BY r.pos")
                .param("from", Timestamp.from(period.from())).param("to", Timestamp.from(period.to()))
                .param("limit", period.limit())
                .query((rs, i) -> new Counted(row(rs), rs.getInt("ranked_count")))
                .list();
        Cached fresh = new Cached(rows.stream().map(Counted::row).toList(),
                rows.isEmpty() ? 0 : rows.getFirst().rankedCount(), now, now.plus(CACHE_TTL));
        cache.put(period.key(), fresh);
        return fresh;
    }

    private Optional<Row> rankedRow(LeaderboardPeriod period, long userId) {
        return jdbc.sql(RANKED + " WHERE r.user_id = :user")
                .param("from", Timestamp.from(period.from())).param("to", Timestamp.from(period.to()))
                .param("user", userId)
                .query((rs, i) -> row(rs))
                .optional();
    }

    private static Row row(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Row(rs.getLong("user_id"), rs.getInt("rnk"), rs.getBigDecimal("spend"), rs.getInt("orders"),
                rs.getTimestamp("member_since").toInstant(), rs.getString("display_name"), rs.getBoolean("visible"),
                rs.getString("avatar_url"));
    }

    private static Entry toEntry(Row r, Long viewer, LeaderboardPeriod period) {
        boolean anonymous = !r.visible() || r.displayName() == null;
        return new Entry(r.rank(), anonymous ? ANONYMOUS : r.displayName(), anonymous ? null : r.avatarUrl(), anonymous,
                MoneyWire.format(r.spend()), r.orders(),
                period.type() == LeaderboardPeriod.Type.ALL_TIME && !anonymous ? r.memberSince() : null,
                viewer != null && viewer == r.userId());
    }

    private static BigDecimal gap(BigDecimal target, BigDecimal mine) {
        return target.subtract(mine).max(BigDecimal.ZERO).setScale(MoneyWire.SCALE);
    }
}
