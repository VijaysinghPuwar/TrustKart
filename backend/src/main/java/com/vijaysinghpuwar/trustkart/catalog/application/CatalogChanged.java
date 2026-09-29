package com.vijaysinghpuwar.trustkart.catalog.application;

/** Published after the catalog itself changes (seeding), so cached catalog views are rebuilt. */
public record CatalogChanged() {}
