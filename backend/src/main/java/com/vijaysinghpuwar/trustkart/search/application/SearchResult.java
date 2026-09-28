package com.vijaysinghpuwar.trustkart.search.application;

import com.vijaysinghpuwar.trustkart.catalog.application.CatalogViews.PageDto;
import com.vijaysinghpuwar.trustkart.catalog.application.CatalogViews.ProductCardDto;
import java.util.List;

/**
 * @param mode           the mode actually used ("exact" until semantic search is enabled)
 * @param smartAvailable whether Smart (semantic) Search is configured in this environment
 * @param relaxed        true when no product matched every word, so results match some of the words
 */
public record SearchResult(
        String query,
        String mode,
        boolean smartAvailable,
        boolean relaxed,
        List<Chip> interpretation,
        PageDto<ProductCardDto> results) {

    /** One piece of the interpretation. {@code ignoreKey} is sent back as ignore=... to remove it. */
    public record Chip(String kind, String label, String value, String ignoreKey) {}
}
