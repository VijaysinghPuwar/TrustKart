package com.vijaysinghpuwar.trustkart.search.application;

import java.math.BigDecimal;
import java.util.List;

/**
 * How a free-text query was understood. Shown to shoppers as removable chips, so it must be an honest,
 * deterministic reading of what they typed.
 */
public record QueryInterpretation(
        String original,
        List<String> terms,
        String categorySlug,
        String categoryPhrase,
        BigDecimal minPrice,
        BigDecimal maxPrice) {

    public QueryInterpretation {
        terms = List.copyOf(terms);
    }

    public boolean isEmpty() {
        return terms.isEmpty() && categorySlug == null && minPrice == null && maxPrice == null;
    }
}
