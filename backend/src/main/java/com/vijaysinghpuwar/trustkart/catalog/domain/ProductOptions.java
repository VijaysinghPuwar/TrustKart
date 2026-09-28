package com.vijaysinghpuwar.trustkart.catalog.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A product's selectable purchase options, e.g. Storage (256 GB / 512 GB / 1 TB) and Color. Stored as JSONB on
 * {@code product.options}. Rules, enforced by {@link #validate}: every group has at least one value and exactly one
 * default; labels are unique within a group; at most one group is priced, and in that group every value has a price
 * (the absolute unit price for that choice) and the default's price equals the product's base price.
 */
public final class ProductOptions {

    /** Photo of this choice (e.g. the product in this colour); served paths under /images/catalog. */
    public record Image(String small, String large, int width, int height, String alt) {}

    public record Value(String label, BigDecimal price, boolean isDefault, Image image) {

        public Value(String label, BigDecimal price, boolean isDefault) {
            this(label, price, isDefault, null);
        }
    }

    public record Group(String name, List<Value> values) {

        public boolean priced() {
            return values.stream().anyMatch(v -> v.price() != null);
        }

        public Value defaultValue() {
            return values.stream().filter(Value::isDefault).findFirst().orElse(values.getFirst());
        }

        public Value find(String label) {
            return values.stream().filter(v -> v.label().equals(label)).findFirst().orElse(null);
        }
    }

    private ProductOptions() {}

    public static List<Group> fromJson(List<Map<String, Object>> json) {
        List<Group> groups = new ArrayList<>();
        if (json == null) {
            return groups;
        }
        for (Map<String, Object> g : json) {
            List<Value> values = new ArrayList<>();
            Object raw = g.get("values");
            if (raw instanceof List<?> list) {
                for (Object o : list) {
                    if (o instanceof Map<?, ?> v) {
                        Object price = v.get("price");
                        values.add(new Value(String.valueOf(v.get("label")),
                                price == null ? null : new BigDecimal(price.toString()), Boolean.TRUE.equals(v.get("default")),
                                image(v.get("image"))));
                    }
                }
            }
            groups.add(new Group(String.valueOf(g.get("name")), List.copyOf(values)));
        }
        return List.copyOf(groups);
    }

    private static Image image(Object raw) {
        if (!(raw instanceof Map<?, ?> m) || m.get("small") == null || m.get("large") == null) {
            return null;
        }
        return new Image(String.valueOf(m.get("small")), String.valueOf(m.get("large")),
                ((Number) m.get("width")).intValue(), ((Number) m.get("height")).intValue(), String.valueOf(m.get("alt")));
    }

    public static List<Map<String, Object>> toJson(List<Group> groups) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Group g : groups) {
            List<Map<String, Object>> values = new ArrayList<>();
            for (Value v : g.values()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("label", v.label());
                if (v.price() != null) {
                    m.put("price", v.price().setScale(2).toPlainString());
                }
                if (v.isDefault()) {
                    m.put("default", true);
                }
                if (v.image() != null) {
                    Map<String, Object> img = new LinkedHashMap<>();
                    img.put("small", v.image().small());
                    img.put("large", v.image().large());
                    img.put("width", v.image().width());
                    img.put("height", v.image().height());
                    img.put("alt", v.image().alt());
                    m.put("image", img);
                }
                values.add(m);
            }
            Map<String, Object> group = new LinkedHashMap<>();
            group.put("name", g.name());
            group.put("values", values);
            out.add(group);
        }
        return out;
    }

    /** Throws {@link IllegalArgumentException} describing the first broken rule. */
    public static void validate(List<Group> groups, BigDecimal basePrice) {
        Set<String> names = new HashSet<>();
        int pricedGroups = 0;
        for (Group g : groups) {
            if (g.name() == null || g.name().isBlank() || !names.add(g.name())) {
                throw new IllegalArgumentException("option group names must be unique and non-blank");
            }
            if (g.values().isEmpty()) {
                throw new IllegalArgumentException("option group " + g.name() + " has no values");
            }
            Set<String> labels = new HashSet<>();
            for (Value v : g.values()) {
                if (v.label() == null || v.label().isBlank() || !labels.add(v.label())) {
                    throw new IllegalArgumentException("labels in " + g.name() + " must be unique and non-blank");
                }
                if (v.price() != null && v.price().signum() <= 0) {
                    throw new IllegalArgumentException("option prices must be positive");
                }
                if (v.image() != null && (!v.image().small().startsWith("/images/") || !v.image().large().startsWith("/images/")
                        || v.image().width() <= 0 || v.image().height() <= 0 || v.image().alt().isBlank())) {
                    throw new IllegalArgumentException("option images must be local /images/ paths with size and alt text");
                }
            }
            if (g.values().stream().filter(Value::isDefault).count() != 1) {
                throw new IllegalArgumentException("option group " + g.name() + " needs exactly one default");
            }
            if (g.priced()) {
                pricedGroups++;
                if (g.values().stream().anyMatch(v -> v.price() == null)) {
                    throw new IllegalArgumentException("every value in priced group " + g.name() + " needs a price");
                }
                if (g.defaultValue().price().compareTo(basePrice) != 0) {
                    throw new IllegalArgumentException("default of " + g.name() + " must equal the base price " + basePrice);
                }
            }
        }
        if (pricedGroups > 1) {
            throw new IllegalArgumentException("at most one option group may carry prices");
        }
    }
}
