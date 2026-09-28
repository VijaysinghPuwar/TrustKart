package com.vijaysinghpuwar.trustkart.config;

import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration(proxyBeanMethods = false)
class PasswordConfig {

    /**
     * Argon2id with OWASP's recommended minimums (19 MiB memory, 2 iterations, 1 lane). Hashes are prefixed
     * with {argon2}, so parameters or algorithms can change later and old hashes upgrade on next login.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        Argon2PasswordEncoder argon2 = new Argon2PasswordEncoder(16, 32, 1, 19 * 1024, 2);
        return new DelegatingPasswordEncoder("argon2", Map.of("argon2", argon2, "bcrypt", new BCryptPasswordEncoder(12)));
    }
}
