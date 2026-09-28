package com.vijaysinghpuwar.trustkart.catalog.application;

import java.util.List;
import java.util.function.Function;

public record PageResult<T>(List<T> items, int page, int size, long totalItems) {

    public PageResult {
        items = List.copyOf(items);
    }

    public int totalPages() {
        return size == 0 ? 0 : (int) Math.ceil((double) totalItems / size);
    }

    public <R> PageResult<R> map(Function<T, R> mapper) {
        return new PageResult<>(items.stream().map(mapper).toList(), page, size, totalItems);
    }
}
