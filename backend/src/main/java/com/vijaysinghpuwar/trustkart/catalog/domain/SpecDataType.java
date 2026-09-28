package com.vijaysinghpuwar.trustkart.catalog.domain;

public enum SpecDataType {
    TEXT,
    NUMBER,
    BOOLEAN;

    /** True when a JSON value parsed from seed/admin input has the shape this type requires. */
    public boolean accepts(Object value) {
        return switch (this) {
            case TEXT -> value instanceof String s && !s.isBlank();
            case NUMBER -> value instanceof Number;
            case BOOLEAN -> value instanceof Boolean;
        };
    }
}
