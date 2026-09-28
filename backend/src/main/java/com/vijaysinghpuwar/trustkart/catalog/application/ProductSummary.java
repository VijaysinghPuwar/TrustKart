package com.vijaysinghpuwar.trustkart.catalog.application;

import com.vijaysinghpuwar.trustkart.catalog.domain.ImageMatch;
import com.vijaysinghpuwar.trustkart.catalog.domain.StockStatus;
import java.math.BigDecimal;

/** Read model for product cards and listings, produced directly by SQL (one query, no N+1). */
public record ProductSummary(
        long id,
        String slug,
        String sku,
        String name,
        String brandName,
        String brandSlug,
        String categorySlug,
        String categoryName,
        String summary,
        BigDecimal price,
        BigDecimal compareAtPrice,
        StockStatus stockStatus,
        int sellableQuantity,
        boolean featured,
        Image image) {

    public record Image(String small, String large, int width, int height, String alt, ImageMatch match) {}
}
