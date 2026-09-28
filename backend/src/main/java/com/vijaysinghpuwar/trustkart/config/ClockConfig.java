package com.vijaysinghpuwar.trustkart.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ClockConfig {

    /** Injected everywhere time matters so tests can pin it. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
