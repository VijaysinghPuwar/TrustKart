package com.vijaysinghpuwar.trustkart.catalog.application;

import com.vijaysinghpuwar.trustkart.catalog.domain.SpecDataType;
import java.math.BigDecimal;
import java.util.List;

/**
 * A validated filter on one JSONB spec key. The key has already been checked against spec_definition,
 * so it can be bound as a query parameter without ever being concatenated into SQL.
 */
public record SpecFilter(
        String key,
        SpecDataType type,
        List<String> textValues,
        List<BigDecimal> numberValues,
        Boolean booleanValue,
        BigDecimal min,
        BigDecimal max) {

    public SpecFilter {
        textValues = List.copyOf(textValues);
        numberValues = List.copyOf(numberValues);
    }
}
