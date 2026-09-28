package com.vijaysinghpuwar.trustkart.catalog.seed;

import java.util.List;
import java.util.Map;

/** Shapes of the demo catalog JSON files under {@code resources/demo}. Unknown fields fail loudly. */
final class SeedModel {

    private SeedModel() {}

    record CategoryFile(List<CategorySeed> categories) {}

    record CategorySeed(String slug, String name, String description, List<SpecSeed> specs, List<CategorySeed> children) {
        List<SpecSeed> specsOrEmpty() {
            return specs == null ? List.of() : specs;
        }

        List<CategorySeed> childrenOrEmpty() {
            return children == null ? List.of() : children;
        }
    }

    record SpecSeed(String key, String label, String type, String unit, String group, Boolean filterable, Boolean comparable) {}

    record ProductFile(List<ProductSeed> products) {}

    record ProductSeed(
            String sku,
            String slug,
            String name,
            String brand,
            String category,
            String price,
            String compareAt,
            Integer warranty,
            List<Integer> stock,
            Boolean featured,
            Boolean backorder,
            Boolean discontinued,
            List<String> collections,
            String summary,
            String description,
            String keywords,
            Map<String, Object> specs) {}

    record ImageSeed(String large, String small, int width, int height, String alt, String match, String author,
            String license, String licenseUrl, String filePage) {}
}
