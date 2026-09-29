package com.vijaysinghpuwar.trustkart.leaderboard;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * A ranking window. Months are UTC calendar months: everyone competes in the same window, a new month starts
 * at 00:00 UTC on the 1st simply because its key differs, and past months stay queryable from order history.
 */
public record LeaderboardPeriod(Type type, YearMonth month) {

    public enum Type { MONTHLY, ALL_TIME }

    public static final int MONTHLY_LIMIT = 50;
    public static final int ALL_TIME_LIMIT = 100;

    private static final Instant EPOCH = Instant.EPOCH;
    private static final Instant FAR_FUTURE = Instant.parse("9999-12-31T00:00:00Z");

    public static LeaderboardPeriod allTime() {
        return new LeaderboardPeriod(Type.ALL_TIME, null);
    }

    public static LeaderboardPeriod currentMonth(Clock clock) {
        return new LeaderboardPeriod(Type.MONTHLY, YearMonth.now(clock.withZone(ZoneOffset.UTC)));
    }

    public static LeaderboardPeriod month(YearMonth month) {
        return new LeaderboardPeriod(Type.MONTHLY, month);
    }

    public Instant from() {
        return month == null ? EPOCH : month.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    /** Exclusive end. */
    public Instant to() {
        return month == null ? FAR_FUTURE : month.plusMonths(1).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    public int limit() {
        return type == Type.MONTHLY ? MONTHLY_LIMIT : ALL_TIME_LIMIT;
    }

    /** "2026-09", or "all-time". */
    public String key() {
        return month == null ? "all-time" : month.format(DateTimeFormatter.ofPattern("uuuu-MM"));
    }

    /** "September 2026", or "All time". */
    public String label() {
        return month == null ? "All time"
                : month.getMonth().getDisplayName(TextStyle.FULL, Locale.US) + " " + month.getYear();
    }
}
