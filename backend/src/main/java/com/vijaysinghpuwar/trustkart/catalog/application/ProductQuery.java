package com.vijaysinghpuwar.trustkart.catalog.application;

import java.math.BigDecimal;
import java.util.List;

/**
 * Everything the product listing can filter on. {@code terms} are already sanitised to [a-z0-9] tokens.
 * When {@code matchAnyTerm} is false every term must match; true relaxes to any term.
 */
public record ProductQuery(
        List<String> terms,
        boolean matchAnyTerm,
        List<Long> categoryIds,
        List<String> brandSlugs,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        boolean inStockOnly,
        boolean onSaleOnly,
        String collection,
        List<SpecFilter> specFilters,
        ProductSort sort,
        int page,
        int size) {

    public static final int MAX_PAGE_SIZE = 60;

    public ProductQuery {
        terms = List.copyOf(terms);
        categoryIds = List.copyOf(categoryIds);
        brandSlugs = List.copyOf(brandSlugs);
        specFilters = List.copyOf(specFilters);
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("page/size out of range");
        }
    }

    public boolean hasText() {
        return !terms.isEmpty();
    }

    public ProductQuery withMatchAnyTerm() {
        return new ProductQuery(terms, true, categoryIds, brandSlugs, minPrice, maxPrice, inStockOnly, onSaleOnly,
                collection, specFilters, sort, page, size);
    }

    public static ProductQuery browse(int size) {
        return new ProductQuery(List.of(), false, List.of(), List.of(), null, null, false, false, null, List.of(),
                ProductSort.FEATURED, 0, size);
    }

    public ProductQuery withCollection(String tag) {
        return new ProductQuery(terms, matchAnyTerm, categoryIds, brandSlugs, minPrice, maxPrice, inStockOnly,
                onSaleOnly, tag, specFilters, sort, page, size);
    }

    public ProductQuery withOnSale(ProductSort newSort) {
        return new ProductQuery(terms, matchAnyTerm, categoryIds, brandSlugs, minPrice, maxPrice, inStockOnly, true,
                collection, specFilters, newSort, page, size);
    }

    public ProductQuery withCategories(List<Long> ids) {
        return new ProductQuery(terms, matchAnyTerm, ids, brandSlugs, minPrice, maxPrice, inStockOnly, onSaleOnly,
                collection, specFilters, sort, page, size);
    }
}
