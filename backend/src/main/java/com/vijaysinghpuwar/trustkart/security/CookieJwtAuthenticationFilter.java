package com.vijaysinghpuwar.trustkart.security;

import com.vijaysinghpuwar.trustkart.common.error.ErrorCode;
import com.vijaysinghpuwar.trustkart.common.web.JsonErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates requests from the HttpOnly access-token cookie.
 *
 * <p>Why not Spring's {@code oauth2ResourceServer()}: it exempts every request that carries a bearer token
 * from CSRF protection. That is safe for tokens in the Authorization header (browsers never attach those on
 * their own) but a CSRF hole for tokens in cookies, which browsers attach automatically. An integration test
 * caught it. This filter reuses the same decoder (signature, issuer, audience, expiry, revocation) and
 * converter while leaving CSRF enforcement untouched.
 */
public class CookieJwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtDecoder decoder;
    private final Converter<Jwt, ? extends AbstractAuthenticationToken> converter;
    private final JsonErrorWriter errors;
    private final CookieBearerTokenResolver resolver = new CookieBearerTokenResolver();

    public CookieJwtAuthenticationFilter(JwtDecoder decoder, Converter<Jwt, ? extends AbstractAuthenticationToken> converter,
            JsonErrorWriter errors) {
        this.decoder = decoder;
        this.converter = converter;
        this.errors = errors;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = resolver.resolve(request);
        if (token == null) {
            chain.doFilter(request, response);
            return;
        }
        AbstractAuthenticationToken authentication;
        try {
            authentication = converter.convert(decoder.decode(token));
        } catch (JwtException e) {
            // A present-but-invalid token (expired, revoked, tampered) is a 401 even on public routes, so the
            // SPA knows to refresh; it never silently degrades to anonymous.
            SecurityContextHolder.clearContext();
            errors.write(response, ErrorCode.UNAUTHENTICATED);
            return;
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
