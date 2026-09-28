package com.vijaysinghpuwar.trustkart.catalog.application;

import java.util.Locale;
import java.util.Optional;

public enum ProductSort {
    RELEVANCE,
    FEATURED,
    PRICE_ASC,
    PRICE_DESC,
    DISCOUNT,
    NEWEST,
    NAME;

    public static Optional<ProductSort> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(raw.trim().toUpperCase(Locale.ROOT).replace('-', '_')));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
