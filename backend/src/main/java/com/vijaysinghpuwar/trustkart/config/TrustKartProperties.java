package com.vijaysinghpuwar.trustkart.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "trustkart")
public record TrustKartProperties(Cors cors, Demo demo) {

    public record Cors(List<String> allowedOrigins) {}

    public record Demo(boolean seedCatalog) {}
}
