package com.vijaysinghpuwar.trustkart.security;

import java.time.Duration;

/** Named limits. Capacity tokens refill greedily over the period. */
public enum RateLimitPolicy {
    LOGIN_PER_IP(20, Duration.ofMinutes(5)),
    REGISTER_PER_IP(10, Duration.ofHours(1)),
    REFRESH_PER_IP(120, Duration.ofMinutes(5)),
    SEARCH_PER_IP(120, Duration.ofMinutes(1)),
    WALLET_CREDIT_PER_SHOPPER(30, Duration.ofMinutes(10)),
    PURCHASE_PER_SHOPPER(20, Duration.ofMinutes(10)),
    PASSWORD_CHANGE_PER_USER(10, Duration.ofHours(1));

    private final long capacity;
    private final Duration period;

    RateLimitPolicy(long capacity, Duration period) {
        this.capacity = capacity;
        this.period = period;
    }

    public long capacity() {
        return capacity;
    }

    public Duration period() {
        return period;
    }
}
