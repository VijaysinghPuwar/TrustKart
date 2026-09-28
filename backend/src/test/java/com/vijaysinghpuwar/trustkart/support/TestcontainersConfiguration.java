package com.vijaysinghpuwar.trustkart.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Real PostgreSQL (with pgvector, same image as docker-compose) and real Redis for integration tests.
 * H2 is deliberately not used: JSONB, row locks and full-text search behave differently.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:8-alpine")).withExposedPorts(6379);

    static {
        POSTGRES.start();
        REDIS.start();
    }

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return POSTGRES;
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redisContainer() {
        return REDIS;
    }
}
