package com.vijaysinghpuwar.trustkart.security;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Makes logout, "sign out this device", password changes and refresh-token reuse take effect immediately, before
 * short-lived access tokens expire. Every access token names its session, and the session table is the authority:
 * the token is accepted only while that row exists, is not revoked and has not expired. That is one primary-key
 * lookup per authenticated request.
 *
 * <p>This used to be a Redis denylist that treated a missing key as "not revoked", so a key lost to eviction, a
 * restart or a failed write quietly re-enabled a signed-out token. Nothing here depends on cache state any more.
 * If the database can't answer, the exception propagates and the request fails rather than being let through.
 */
@Component
public class SessionRevocation {

    private final JdbcClient jdbc;

    public SessionRevocation(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** True for revoked, expired and unknown sessions. */
    public boolean isRevoked(UUID sessionId) {
        return jdbc.sql("SELECT revoked_at IS NOT NULL OR expires_at < now() FROM user_session WHERE id = :id")
                .param("id", sessionId).query(Boolean.class).optional().orElse(true);
    }
}
