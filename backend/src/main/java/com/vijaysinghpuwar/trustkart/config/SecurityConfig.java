package com.vijaysinghpuwar.trustkart.config;

import com.vijaysinghpuwar.trustkart.common.error.ErrorCode;
import com.vijaysinghpuwar.trustkart.common.web.JsonErrorWriter;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
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
    SecurityFilterChain apiChain(HttpSecurity http, JsonErrorWriter errorWriter) throws Exception {
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
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> errorWriter.write(res, ErrorCode.UNAUTHENTICATED))
                        .accessDeniedHandler((req, res, ex) -> errorWriter.write(res, ErrorCode.FORBIDDEN)))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/catalog/**", "/api/v1/search/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated());
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
