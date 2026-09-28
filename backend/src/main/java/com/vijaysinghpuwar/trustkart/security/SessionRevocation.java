package com.vijaysinghpuwar.trustkart.security;

import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Makes logout and "sign out this device" take effect immediately, before short-lived access tokens expire.
 * Revoked session IDs go into a Redis denylist for the access-token lifetime. If Redis is unreachable the
 * check falls back to the session table, so an outage never turns revoked tokens back on.
 */
@Component
public class SessionRevocation {

    private static final Logger log = LoggerFactory.getLogger(SessionRevocation.class);
    private static final String PREFIX = "tk:revoked-sid:";

    private final StringRedisTemplate redis;
    private final JdbcClient jdbc;
    private final Duration ttl;

    public SessionRevocation(StringRedisTemplate redis, JdbcClient jdbc, AuthProperties props) {
        this.redis = redis;
        this.jdbc = jdbc;
        this.ttl = props.accessTokenTtl().plusMinutes(1);
    }

    public void markRevoked(UUID sessionId) {
        try {
            redis.opsForValue().set(PREFIX + sessionId, "1", ttl);
        } catch (RuntimeException e) {
            log.warn("Could not write session denylist entry; database check will still reject it: {}", e.getMessage());
        }
    }

    public boolean isRevoked(UUID sessionId) {
        try {
            return Boolean.TRUE.equals(redis.hasKey(PREFIX + sessionId));
        } catch (RuntimeException e) {
            log.warn("Session denylist unavailable, checking database: {}", e.getMessage());
            return jdbc.sql("SELECT revoked_at IS NOT NULL OR expires_at < now() FROM user_session WHERE id = :id")
                    .param("id", sessionId).query(Boolean.class).optional().orElse(true);
        }
    }
}
