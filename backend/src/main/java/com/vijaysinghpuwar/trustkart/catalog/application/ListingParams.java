package com.vijaysinghpuwar.trustkart.catalog.application;

import com.vijaysinghpuwar.trustkart.common.error.ApiException;
import com.vijaysinghpuwar.trustkart.common.error.ErrorCode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Validated listing/search request parameters, parsed from the raw query string. */
public record ListingParams(
        String q,
        String category,
        List<String> brands,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        boolean inStock,
        boolean onSale,
        String collection,
        ProductSort sort,
        int page,
        int size,
        Map<String, List<String>> specParams,
        Set<String> ignore) {

    private static final Pattern SLUG = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");
    private static final BigDecimal MAX_PRICE = new BigDecimal("100000000");
    private static final int MAX_BRANDS = 30;

    public static ListingParams from(Map<String, List<String>> raw) {
        String q = first(raw, "q");
        if (q != null && q.length() > 200) {
            throw invalid("q", "must be at most 200 characters");
        }
        String category = slug(raw, "category");
        List<String> brands = raw.getOrDefault("brand", List.of());
        if (brands.size() > MAX_BRANDS || brands.stream().anyMatch(b -> !SLUG.matcher(b).matches())) {
            throw invalid("brand", "is invalid");
        }
        BigDecimal min = price(raw, "minPrice");
        BigDecimal max = price(raw, "maxPrice");
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw invalid("minPrice", "must not exceed maxPrice");
        }
        String sortRaw = first(raw, "sort");
        ProductSort sort = sortRaw == null ? null
                : ProductSort.parse(sortRaw).orElseThrow(() -> invalid("sort", "is not a supported sort"));
        int page = integer(raw, "page", 0, 0, 10_000);
        int size = integer(raw, "size", 24, 1, ProductQuery.MAX_PAGE_SIZE);
        Map<String, List<String>> specs = raw.entrySet().stream()
                .filter(e -> e.getKey().startsWith(SpecFilterParser.PREFIX))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        Set<String> ignore = Set.copyOf(raw.getOrDefault("ignore", List.of()));
        return new ListingParams(q, category, List.copyOf(brands), min, max, bool(raw, "inStock"), bool(raw, "onSale"),
                slug(raw, "collection"), sort, page, size, specs, ignore);
    }

    private static String first(Map<String, List<String>> raw, String key) {
        List<String> values = raw.get(key);
        if (values == null || values.isEmpty() || values.getFirst().isBlank()) {
            return null;
        }
        return values.getFirst().strip();
    }

    private static String slug(Map<String, List<String>> raw, String key) {
        String value = first(raw, key);
        if (value != null && (value.length() > 80 || !SLUG.matcher(value).matches())) {
            throw invalid(key, "is invalid");
        }
        return value;
    }

    private static BigDecimal price(Map<String, List<String>> raw, String key) {
        String value = first(raw, key);
        if (value == null) {
            return null;
        }
        try {
            BigDecimal n = new BigDecimal(value);
            if (n.signum() < 0 || n.compareTo(MAX_PRICE) > 0 || n.scale() > 2) {
                throw invalid(key, "is out of range");
            }
            return n;
        } catch (NumberFormatException e) {
            throw invalid(key, "must be a number");
        }
    }

    private static int integer(Map<String, List<String>> raw, String key, int fallback, int lo, int hi) {
        String value = first(raw, key);
        if (value == null) {
            return fallback;
        }
        try {
            int n = Integer.parseInt(value);
            if (n < lo || n > hi) {
                throw invalid(key, "is out of range");
            }
            return n;
        } catch (NumberFormatException e) {
            throw invalid(key, "must be a whole number");
        }
    }

    private static boolean bool(Map<String, List<String>> raw, String key) {
        return "true".equals(first(raw, key));
    }

    private static ApiException invalid(String field, String reason) {
        return new ApiException(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultMessage(),
                Map.of("field", field, "reason", reason));
    }
}
