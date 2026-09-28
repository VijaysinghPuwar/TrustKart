package com.vijaysinghpuwar.trustkart.catalog.application;

import com.vijaysinghpuwar.trustkart.catalog.domain.SpecDataType;
import com.vijaysinghpuwar.trustkart.catalog.domain.SpecDefinition;
import com.vijaysinghpuwar.trustkart.common.error.ApiException;
import com.vijaysinghpuwar.trustkart.common.error.ErrorCode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns {@code spec.<key>=v}, {@code spec.<key>.min=n} and {@code spec.<key>.max=n} query parameters into
 * validated {@link SpecFilter}s. Unknown keys, wrong types and oversized requests are rejected with 400,
 * which also guarantees only whitelisted keys ever reach SQL.
 */
public final class SpecFilterParser {

    public static final String PREFIX = "spec.";
    static final int MAX_FILTERS = 12;
    static final int MAX_VALUES = 20;
    private static final int MAX_TEXT_LENGTH = 100;

    private SpecFilterParser() {}

    public static List<SpecFilter> parse(Map<String, List<String>> params, Map<String, SpecDefinition> definitions) {
        Map<String, Builder> builders = new LinkedHashMap<>();
        params.forEach((name, values) -> {
            if (!name.startsWith(PREFIX)) {
                return;
            }
            String rest = name.substring(PREFIX.length());
            String bound = null;
            if (rest.endsWith(".min") || rest.endsWith(".max")) {
                bound = rest.substring(rest.length() - 3);
                rest = rest.substring(0, rest.length() - 4);
            }
            SpecDefinition def = definitions.get(rest);
            if (def == null || !def.isFilterable()) {
                throw invalid(name, "is not a filter for this category");
            }
            if (values.size() > MAX_VALUES) {
                throw invalid(name, "has too many values");
            }
            Builder b = builders.computeIfAbsent(rest, k -> new Builder(def));
            if (bound != null) {
                b.bound(name, bound, values);
            } else {
                values.forEach(v -> b.value(name, v));
            }
        });
        if (builders.size() > MAX_FILTERS) {
            throw invalid("spec", "too many filters");
        }
        return builders.values().stream().map(Builder::build).toList();
    }

    private static ApiException invalid(String field, String message) {
        return new ApiException(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultMessage(),
                Map.of("field", field, "reason", message));
    }

    private static final class Builder {
        private final SpecDefinition def;
        private final List<String> text = new ArrayList<>();
        private final List<BigDecimal> numbers = new ArrayList<>();
        private Boolean bool;
        private BigDecimal min;
        private BigDecimal max;

        Builder(SpecDefinition def) {
            this.def = def;
        }

        void value(String field, String raw) {
            switch (def.getDataType()) {
                case TEXT -> {
                    if (raw.isBlank() || raw.length() > MAX_TEXT_LENGTH) {
                        throw invalid(field, "has an invalid value");
                    }
                    text.add(raw);
                }
                case NUMBER -> numbers.add(number(field, raw));
                case BOOLEAN -> {
                    if (!raw.equals("true") && !raw.equals("false")) {
                        throw invalid(field, "must be true or false");
                    }
                    bool = Boolean.valueOf(raw);
                }
            }
        }

        void bound(String field, String which, List<String> values) {
            if (def.getDataType() != SpecDataType.NUMBER || values.size() != 1) {
                throw invalid(field, "range is only allowed once on numeric specs");
            }
            BigDecimal n = number(field, values.getFirst());
            if (which.equals("min")) {
                min = n;
            } else {
                max = n;
            }
        }

        private static BigDecimal number(String field, String raw) {
            try {
                BigDecimal n = new BigDecimal(raw);
                if (n.abs().compareTo(BigDecimal.valueOf(1_000_000_000L)) > 0 || n.scale() > 4) {
                    throw invalid(field, "is out of range");
                }
                return n;
            } catch (NumberFormatException e) {
                throw invalid(field, "must be a number");
            }
        }

        SpecFilter build() {
            return new SpecFilter(def.getKey(), def.getDataType(), text, numbers, bool, min, max);
        }
    }
}
