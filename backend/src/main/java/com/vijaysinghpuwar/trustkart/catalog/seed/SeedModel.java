package com.vijaysinghpuwar.trustkart.catalog.seed;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Shapes of the demo catalog JSON files under {@code resources/demo}. Unknown fields fail loudly. */
final class SeedModel {

    private SeedModel() {}

    record CategoryFile(List<CategorySeed> categories) {}

    /** {@code catalog/categories.json}: specs/children grafted onto existing categories, plus new top-level ones. */
    record CategoryExtensionFile(List<CategoryExtension> extensions, List<CategorySeed> categories) {
        List<CategoryExtension> extensionsOrEmpty() {
            return extensions == null ? List.of() : extensions;
        }

        List<CategorySeed> categoriesOrEmpty() {
            return categories == null ? List.of() : categories;
        }
    }

    record CategoryExtension(String extend, List<SpecSeed> specs, List<CategorySeed> children) {}

    /** {@code catalog/parked.json}: SKUs moved to catalog-data/pending because no official image exists yet. */
    record ParkedFile(List<String> skus) {}

    record CategorySeed(String slug, String name, String description, List<SpecSeed> specs, List<CategorySeed> children) {
        CategorySeed extendedWith(CategoryExtension e) {
            List<SpecSeed> s = new ArrayList<>(specsOrEmpty());
            s.addAll(e.specs() == null ? List.of() : e.specs());
            List<CategorySeed> c = new ArrayList<>(childrenOrEmpty());
            c.addAll(e.children() == null ? List.of() : e.children());
            return new CategorySeed(slug, name, description, s, c);
        }

        CategorySeed withChildren(List<CategorySeed> newChildren) {
            return new CategorySeed(slug, name, description, specs, newChildren);
        }

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
            Map<String, Object> specs,
            List<OptionGroupSeed> options) {}

    record OptionGroupSeed(String name, List<OptionValueSeed> values) {}

    record OptionValueSeed(String label, String price, @JsonProperty("default") Boolean isDefault, OptionImageSeed image) {}

    record OptionImageSeed(String small, String large, int width, int height, String alt) {}

    record ImageSeed(String large, String small, int width, int height, String alt, String match, String author,
            String license, String licenseUrl, String filePage) {}
}
