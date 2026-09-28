package com.vijaysinghpuwar.trustkart.config;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import java.time.Duration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisConnectionDetails;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class RedisConfig {

    /** A dedicated Lettuce client for Bucket4j (it needs a String/byte[] codec), built from Boot's connection details. */
    @Bean(destroyMethod = "shutdown")
    RedisClient rateLimitRedisClient(DataRedisConnectionDetails details) {
        DataRedisConnectionDetails.Standalone standalone = details.getStandalone();
        RedisURI.Builder uri = RedisURI.builder().withHost(standalone.getHost()).withPort(standalone.getPort())
                .withTimeout(Duration.ofSeconds(2));
        if (details.getPassword() != null && !details.getPassword().isEmpty()) {
            uri.withPassword(details.getPassword().toCharArray());
        }
        return RedisClient.create(uri.build());
    }

    @Bean(destroyMethod = "close")
    StatefulRedisConnection<String, byte[]> rateLimitConnection(RedisClient client) {
        return client.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
    }

    @Bean
    ProxyManager<String> rateLimitBuckets(StatefulRedisConnection<String, byte[]> connection) {
        return Bucket4jLettuce.casBasedBuilder(connection)
                .expirationAfterWrite(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(10)))
                .build();
    }
}
