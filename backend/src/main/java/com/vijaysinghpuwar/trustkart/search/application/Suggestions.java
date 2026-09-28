package com.vijaysinghpuwar.trustkart.search.application;

import com.vijaysinghpuwar.trustkart.catalog.application.CatalogViews.ProductCardDto;
import com.vijaysinghpuwar.trustkart.catalog.application.CatalogViews.Ref;
import java.util.List;

public record Suggestions(List<String> queries, List<Ref> categories, List<ProductCardDto> products, List<Chip> interpretation) {

    public record Chip(String label, String value) {}
}
