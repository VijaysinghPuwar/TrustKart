package com.vijaysinghpuwar.trustkart.security;

import com.vijaysinghpuwar.trustkart.common.error.ApiException;
import com.vijaysinghpuwar.trustkart.common.error.ErrorCode;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Distributed token buckets in Redis, shared by every API instance. If Redis is unavailable the limiter
 * fails open (logged): brute-force protection for login still holds because account lockout lives in
 * PostgreSQL, and shopping keeps working.
 */
@Component
public class RateLimiter {

    public record Decision(boolean allowed, long retryAfterSeconds) {}

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    private final ProxyManager<String> buckets;

    public RateLimiter(ProxyManager<String> buckets) {
        this.buckets = buckets;
    }

    public Decision tryConsume(RateLimitPolicy policy, String key) {
        BucketConfiguration config = BucketConfiguration.builder()
                .addLimit(Bandwidth.builder().capacity(policy.capacity()).refillGreedy(policy.capacity(), policy.period()).build())
                .build();
        try {
            ConsumptionProbe probe = buckets.getProxy("tk:rl:" + policy.name() + ":" + key, () -> config)
                    .tryConsumeAndReturnRemaining(1);
            long retryAfter = probe.isConsumed() ? 0 : Math.max(1, Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds());
            return new Decision(probe.isConsumed(), retryAfter);
        } catch (RuntimeException e) {
            log.warn("Rate limiter unavailable, allowing request (policy {}): {}", policy, e.getMessage());
            return new Decision(true, 0);
        }
    }

    /** Service-level guard: throws 429 with a Retry-After hint when the bucket is empty. */
    public void check(RateLimitPolicy policy, String key) {
        Decision d = tryConsume(policy, key);
        if (!d.allowed()) {
            throw new ApiException(ErrorCode.RATE_LIMITED, ErrorCode.RATE_LIMITED.defaultMessage(),
                    Map.of("retryAfterSeconds", d.retryAfterSeconds()));
        }
    }
}
