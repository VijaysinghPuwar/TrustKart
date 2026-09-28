package com.vijaysinghpuwar.trustkart.security;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param jwtSecret        HMAC key for access tokens; at least 32 bytes, supplied via TRUSTKART_JWT_SECRET
 * @param secureCookies    Secure flag on auth cookies; only disabled for plain-http local development
 */
@Validated
@ConfigurationProperties(prefix = "trustkart.auth")
public record AuthProperties(
        @NotBlank @Size(min = 32) String jwtSecret,
        @NotNull Duration accessTokenTtl,
        @NotNull Duration refreshTokenTtl,
        boolean secureCookies,
        @Min(1) int maxFailedLogins,
        @NotNull Duration lockDuration) {}
