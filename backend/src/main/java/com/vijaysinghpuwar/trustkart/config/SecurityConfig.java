package com.vijaysinghpuwar.trustkart.config;

import com.vijaysinghpuwar.trustkart.common.error.ErrorCode;
import com.vijaysinghpuwar.trustkart.common.web.JsonErrorWriter;
import com.vijaysinghpuwar.trustkart.security.CookieJwtAuthenticationFilter;
import com.vijaysinghpuwar.trustkart.security.RateLimitFilter;
import com.vijaysinghpuwar.trustkart.security.RateLimiter;
import com.vijaysinghpuwar.trustkart.security.AuthProperties;
import com.vijaysinghpuwar.trustkart.security.oauth.OAuthHandlers;
import com.vijaysinghpuwar.trustkart.security.oauth.RedisAuthorizationRequestRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig {

    /** The API only ever returns JSON, so its CSP can forbid everything. */
    private static final String API_CSP = "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'";

    /** Swagger UI (dev only) needs its own scripts and styles. */
    private static final String DOCS_CSP =
            "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; frame-ancestors 'none'";

    @Bean
    @Order(1)
    SecurityFilterChain docsChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**")
                .authorizeHttpRequests(a -> a.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable()) // read-only GETs; no state-changing endpoints under these paths
                .headers(h -> h.contentSecurityPolicy(csp -> csp.policyDirectives(DOCS_CSP)));
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain apiChain(HttpSecurity http, JsonErrorWriter errorWriter, RateLimiter rateLimiter,
            JwtDecoder jwtDecoder, JwtAuthenticationConverter jwtConverter,
            ObjectProvider<ClientRegistrationRepository> googleRegistrations, OAuthHandlers oauthHandlers,
            StringRedisTemplate redis, AuthProperties authProperties) throws Exception {
        if (googleRegistrations.getIfAvailable() != null) {
            // Sign in with Google (OIDC authorization code flow). Spring validates state, nonce and the ID token;
            // OAuthHandlers then issues TrustKart's own cookie session.
            http.oauth2Login(o -> o
                    .loginPage("/signin")
                    .authorizationEndpoint(a -> a.baseUri("/api/v1/auth/oauth2/authorization")
                            .authorizationRequestRepository(new RedisAuthorizationRequestRepository(redis, authProperties)))
                    .redirectionEndpoint(r -> r.baseUri("/api/v1/auth/oauth2/callback/*"))
                    .successHandler(oauthHandlers)
                    .failureHandler(oauthHandlers));
        }
        http.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(cors -> {})
                // Auth travels in cookies, so CSRF protection stays on: the SPA echoes the XSRF-TOKEN cookie
                // back in the X-XSRF-TOKEN header on every state-changing request.
                .csrf(csrf -> csrf.spa())
                .formLogin(f -> f.disable())
                .httpBasic(b -> b.disable())
                .logout(l -> l.disable())
                .requestCache(c -> c.disable())
                .headers(h -> h
                        .contentSecurityPolicy(csp -> csp.policyDirectives(API_CSP))
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .frameOptions(f -> f.deny())
                        .permissionsPolicyHeader(p -> p.policy("camera=(), microphone=(), geolocation=(), payment=()")))
                .addFilterBefore(new RateLimitFilter(rateLimiter, errorWriter), AnonymousAuthenticationFilter.class)
                .addFilterBefore(new CookieJwtAuthenticationFilter(jwtDecoder, jwtConverter, errorWriter),
                        AnonymousAuthenticationFilter.class)
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> errorWriter.write(res, ErrorCode.UNAUTHENTICATED))
                        .accessDeniedHandler((req, res, ex) -> errorWriter.write(res, ErrorCode.FORBIDDEN)))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/catalog/**", "/api/v1/search/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf", "/api/v1/auth/providers", "/api/v1/me").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/oauth2/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login",
                                "/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll()
                        // Guests shop too: these resolve the owner from the session or the guest cookie.
                        .requestMatchers("/api/v1/cart/**", "/api/v1/wishlist/**", "/api/v1/wallet/**",
                                "/api/v1/checkout/**", "/api/v1/purchases/**", "/api/v1/collection/**", "/api/v1/addresses/**",
                                "/api/v1/notifications/**").permitAll()
                        .requestMatchers("/api/v1/me/**").authenticated()
                        .requestMatchers("/api/v1/admin/**").authenticated()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().denyAll());
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(TrustKartProperties props) {
        CorsConfiguration config = new CorsConfiguration();
        // Explicit origins only. A wildcard with credentials is refused by browsers and by design here.
        config.setAllowedOrigins(props.cors().allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
        config.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN", "Idempotency-Key", "X-Request-ID"));
        config.setExposedHeaders(List.of("X-Request-ID"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
