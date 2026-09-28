package com.vijaysinghpuwar.trustkart.security;

import com.vijaysinghpuwar.trustkart.common.error.ErrorCode;
import com.vijaysinghpuwar.trustkart.common.web.JsonErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/** Per-IP limits on unauthenticated, abuse-prone endpoints. Per-account and per-shopper limits live in services. */
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter limiter;
    private final JsonErrorWriter errors;

    public RateLimitFilter(RateLimiter limiter, JsonErrorWriter errors) {
        this.limiter = limiter;
        this.errors = errors;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        RateLimitPolicy policy = policyFor(request);
        if (policy != null) {
            RateLimiter.Decision decision = limiter.tryConsume(policy, request.getRemoteAddr());
            if (!decision.allowed()) {
                response.setHeader("Retry-After", Long.toString(decision.retryAfterSeconds()));
                errors.write(response, ErrorCode.RATE_LIMITED);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    static RateLimitPolicy policyFor(HttpServletRequest request) {
        String path = request.getRequestURI();
        if ("POST".equals(request.getMethod())) {
            return switch (path) {
                case "/api/v1/auth/login" -> RateLimitPolicy.LOGIN_PER_IP;
                case "/api/v1/auth/register" -> RateLimitPolicy.REGISTER_PER_IP;
                case "/api/v1/auth/refresh" -> RateLimitPolicy.REFRESH_PER_IP;
                default -> null;
            };
        }
        if ("GET".equals(request.getMethod()) && path.startsWith("/api/v1/search")) {
            return RateLimitPolicy.SEARCH_PER_IP;
        }
        return null;
    }
}
