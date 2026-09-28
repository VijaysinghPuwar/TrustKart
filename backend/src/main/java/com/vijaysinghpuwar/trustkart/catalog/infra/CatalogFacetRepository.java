package com.vijaysinghpuwar.trustkart.catalog.infra;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Aggregates used to build filter facets and category counts. */
@Repository
public class CatalogFacetRepository {

    public record ValueCount(String value, long count) {}

    public record PriceRange(BigDecimal min, BigDecimal max) {}

    private final JdbcClient jdbc;

    public CatalogFacetRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Visible (non-draft) product count per category, not including descendants. */
    public Map<Long, Long> countsByCategory() {
        return jdbc.sql("SELECT category_id, count(*) AS n FROM product WHERE status <> 'DRAFT' GROUP BY category_id")
                .query((rs, i) -> Map.entry(rs.getLong("category_id"), rs.getLong("n")))
                .list().stream()
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public List<ValueCount> brandCounts(Collection<Long> categoryIds) {
        return jdbc.sql("""
                        SELECT b.slug AS value, count(*) AS n FROM product p JOIN brand b ON b.id = p.brand_id
                        WHERE p.status <> 'DRAFT' AND p.category_id IN (:ids)
                        GROUP BY b.slug ORDER BY n DESC, b.slug""")
                .param("ids", categoryIds)
                .query((rs, i) -> new ValueCount(rs.getString("value"), rs.getLong("n")))
                .list();
    }

    public Map<String, String> brandNames(Collection<String> slugs) {
        if (slugs.isEmpty()) {
            return Map.of();
        }
        return jdbc.sql("SELECT slug, name FROM brand WHERE slug IN (:slugs)")
                .param("slugs", slugs)
                .query((rs, i) -> Map.entry(rs.getString("slug"), rs.getString("name")))
                .list().stream()
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public PriceRange priceRange(Collection<Long> categoryIds) {
        return jdbc.sql("SELECT min(price) AS lo, max(price) AS hi FROM product WHERE status <> 'DRAFT' AND category_id IN (:ids)")
                .param("ids", categoryIds)
                .query((rs, i) -> new PriceRange(rs.getBigDecimal("lo"), rs.getBigDecimal("hi")))
                .single();
    }

    /** Distinct values of one spec key (as text) with counts. The key is pre-validated and bound as a parameter. */
    public List<ValueCount> specValueCounts(Collection<Long> categoryIds, String key) {
        return jdbc.sql("""
                        SELECT p.specs ->> :key AS value, count(*) AS n FROM product p
                        WHERE p.status <> 'DRAFT' AND p.category_id IN (:ids) AND jsonb_exists(p.specs, :key)
                        GROUP BY 1 ORDER BY n DESC, 1""")
                .param("key", key)
                .param("ids", categoryIds)
                .query((rs, i) -> new ValueCount(rs.getString("value"), rs.getLong("n")))
                .list();
    }

    public List<String> collectionTags(long productId) {
        return jdbc.sql("SELECT tag FROM product_collection WHERE product_id = :id ORDER BY tag")
                .param("id", productId)
                .query(String.class)
                .list();
    }

    public void addToCollection(long productId, String tag, int sortOrder) {
        jdbc.sql("""
                        INSERT INTO product_collection (product_id, tag, sort_order) VALUES (:id, :tag, :sort)
                        ON CONFLICT DO NOTHING""")
                .param("id", productId)
                .param("tag", tag)
                .param("sort", sortOrder)
                .update();
    }
}
