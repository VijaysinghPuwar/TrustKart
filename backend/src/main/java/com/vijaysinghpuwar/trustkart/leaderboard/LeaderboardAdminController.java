package com.vijaysinghpuwar.trustkart.leaderboard;

import com.vijaysinghpuwar.trustkart.common.money.MoneyWire;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Operator view of the leaderboards. Read-only by design: ranks derive from order history, so there is no way to
 * edit one. "Reconcile" re-sums each ranked account's completed orders independently and reports any mismatch.
 */
@RestController
@RequestMapping("/api/v1/admin/leaderboards")
@PreAuthorize("hasAuthority('PERM_analytics:read')")
@Tag(name = "Leaderboards (admin)")
class LeaderboardAdminController {

    record Status(String month, int monthlyRanked, int allTimeRanked, long cancelledOrReturnedOrders,
            String cancelledOrReturnedValue, long guestOnlyOrders, long staffAccountsExcluded, long accountsMarkedIneligible,
            long publicProfiles) {}

    record Mismatch(int rank, String boardSpend, String orderSum) {}

    record Reconciliation(String period, int checked, List<Mismatch> mismatches) {}

    private final LeaderboardService leaderboards;
    private final JdbcClient jdbc;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    LeaderboardAdminController(LeaderboardService leaderboards, JdbcClient jdbc, Clock clock, ApplicationEventPublisher events) {
        this.leaderboards = leaderboards;
        this.jdbc = jdbc;
        this.clock = clock;
        this.events = events;
    }

    @GetMapping
    @Transactional(readOnly = true)
    @Operation(summary = "Ranking counts and what is excluded from them")
    Status status() {
        LeaderboardPeriod month = LeaderboardPeriod.currentMonth(clock);
        record Closed(long count, BigDecimal value) {}
        Closed closed = jdbc.sql("""
                        SELECT count(*), coalesce(sum(total), 0) FROM virtual_purchase WHERE status IN ('CANCELLED', 'REFUNDED')""")
                .query((rs, i) -> new Closed(rs.getLong(1), rs.getBigDecimal(2))).single();
        return new Status(month.key(),
                leaderboards.board(month, null).rankedCount(),
                leaderboards.board(LeaderboardPeriod.allTime(), null).rankedCount(),
                closed.count(), MoneyWire.format(closed.value()),
                count("""
                        SELECT count(*) FROM virtual_purchase vp JOIN shopper s ON s.id = vp.shopper_id
                        WHERE s.user_id IS NULL AND vp.status = 'COMPLETED'"""),
                count("""
                        SELECT count(DISTINCT ur.user_id) FROM user_role ur JOIN role r ON r.id = ur.role_id
                        WHERE r.name <> 'CUSTOMER'"""),
                count("SELECT count(*) FROM leaderboard_profile WHERE NOT eligible"),
                count("SELECT count(*) FROM leaderboard_profile WHERE visible"));
    }

    @GetMapping("/reconcile")
    @Transactional(readOnly = true)
    @Operation(summary = "Re-sum completed orders for every all-time ranked account and report mismatches")
    Reconciliation reconcile() {
        List<LeaderboardService.Row> rows = leaderboards.rows(LeaderboardPeriod.allTime());
        List<Mismatch> mismatches = rows.stream().map(r -> {
            BigDecimal sum = jdbc.sql("""
                            SELECT coalesce(sum(vp.total), 0) FROM virtual_purchase vp JOIN shopper s ON s.id = vp.shopper_id
                            WHERE s.user_id = :u AND vp.status = 'COMPLETED'""")
                    .param("u", r.userId())
                    .query(BigDecimal.class).single();
            return sum.compareTo(r.spend()) == 0 ? null
                    : new Mismatch(r.rank(), MoneyWire.format(r.spend()), MoneyWire.format(sum));
        }).filter(java.util.Objects::nonNull).toList();
        return new Reconciliation("all-time", rows.size(), mismatches);
    }

    @PostMapping("/refresh")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Drop cached boards so the next request recomputes them from order history")
    void refresh() {
        events.publishEvent(new LeaderboardChanged());
    }

    private long count(String sql) {
        return jdbc.sql(sql).query(Long.class).single();
    }
}
