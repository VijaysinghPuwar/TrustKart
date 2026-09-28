package com.vijaysinghpuwar.trustkart.security;

import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** The signed-in user as established by the access token for this request. */
public record AuthenticatedUser(long userId, UUID publicId, UUID sessionId, String displayName) {

    public static Optional<AuthenticatedUser> current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken token && token.isAuthenticated()) {
            Jwt jwt = token.getToken();
            return Optional.of(new AuthenticatedUser(
                    ((Number) jwt.getClaim(JwtClaims.USER_ID)).longValue(),
                    UUID.fromString(jwt.getSubject()),
                    UUID.fromString(jwt.getClaimAsString(JwtClaims.SESSION_ID)),
                    jwt.getClaimAsString(JwtClaims.NAME)));
        }
        return Optional.empty();
    }
}
