package com.vijaysinghpuwar.trustkart.catalog.infra;

import com.vijaysinghpuwar.trustkart.catalog.application.PageResult;
import com.vijaysinghpuwar.trustkart.catalog.application.ProductQuery;
import com.vijaysinghpuwar.trustkart.catalog.application.ProductSort;
import com.vijaysinghpuwar.trustkart.catalog.application.ProductSummary;
import com.vijaysinghpuwar.trustkart.catalog.application.SpecFilter;
import com.vijaysinghpuwar.trustkart.catalog.domain.ImageMatch;
import com.vijaysinghpuwar.trustkart.catalog.domain.ProductStatus;
import com.vijaysinghpuwar.trustkart.catalog.domain.StockStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Product listing and search in a single SQL statement: filters, full-text ranking and the total count (a window
 * function) pick one page of products first, and only that page is joined to its primary image. Joining images
 * before the LIMIT would look one up for every matching product (2,201 lookups for a 24-card page).
 *
 * <p>Nothing user-supplied is ever concatenated into the SQL. Search terms are reduced to [a-z0-9] tokens
 * before being turned into a tsquery, and JSONB spec keys arrive pre-validated and are bound as parameters.
 */
@Repository
public class ProductSearchRepository {

    private static final String CARD_COLUMNS = """
            p.id, p.slug, p.sku, p.name, p.summary, p.price, p.compare_at_price, p.featured, p.status, p.created_at,
            b.name AS brand_name, b.slug AS brand_slug, c.slug AS category_slug, c.name AS category_name,
            i.available, i.reserved, i.low_stock_threshold, i.backorder_allowed
            """;

    private static final String FROM = """
            FROM product p
            JOIN brand b ON b.id = p.brand_id
            JOIN category c ON c.id = p.category_id
            JOIN inventory i ON i.product_id = p.id
            """;

    private static final String PRIMARY_IMAGE = """
            LEFT JOIN LATERAL (
                SELECT pi.url_small, pi.url_large, pi.width, pi.height, pi.alt, pi.match_type,
                       position('Manufacturer product image' IN pi.credit) > 0 AS studio
                FROM product_image pi WHERE pi.product_id = p.id ORDER BY pi.sort_order LIMIT 1
            ) img ON TRUE
            """;

    private static final String IMAGE_COLUMNS = "img.url_small, img.url_large, img.width, img.height, img.alt, img.match_type, img.studio";

    private final JdbcClient jdbc;

    public ProductSearchRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public PageResult<ProductSummary> search(ProductQuery q) {
        List<String> where = new ArrayList<>(List.of("p.status <> 'DRAFT'"));
        var params = new LinkedHashMap<String, Object>();

        String rank = "0";
        if (q.hasText()) {
            params.put("tsq", toTsQuery(q.terms(), q.matchAnyTerm()));
            where.add("p.search_vector @@ to_tsquery('english', :tsq)");
            rank = "ts_rank_cd(p.search_vector, to_tsquery('english', :tsq))";
        }
        if (!q.categoryIds().isEmpty()) {
            where.add("p.category_id IN (:categoryIds)");
            params.put("categoryIds", q.categoryIds());
        }
        if (!q.brandSlugs().isEmpty()) {
            where.add("b.slug IN (:brandSlugs)");
            params.put("brandSlugs", q.brandSlugs());
        }
        if (q.minPrice() != null) {
            where.add("p.price >= :minPrice");
            params.put("minPrice", q.minPrice());
        }
        if (q.maxPrice() != null) {
            where.add("p.price <= :maxPrice");
            params.put("maxPrice", q.maxPrice());
        }
        if (q.inStockOnly()) {
            where.add("p.status = 'ACTIVE' AND (i.available - i.reserved > 0 OR i.backorder_allowed)");
        }
        if (q.onSaleOnly()) {
            where.add("p.compare_at_price IS NOT NULL");
        }
        if (q.collection() != null) {
            where.add("EXISTS (SELECT 1 FROM product_collection pc WHERE pc.product_id = p.id AND pc.tag = :collection)");
            params.put("collection", q.collection());
        }
        for (int n = 0; n < q.specFilters().size(); n++) {
            where.add(specClause(q.specFilters().get(n), n, params));
        }

        String filter = " WHERE " + String.join(" AND ", where);
        // The page CTE has the same column names as the tables it reads, so orderBy() works on both sides.
        String sql = "WITH page AS (SELECT " + CARD_COLUMNS + ", " + rank + " AS relevance, count(*) OVER () AS total "
                + FROM + filter + " ORDER BY " + orderBy(q) + " LIMIT :limit OFFSET :offset) "
                + "SELECT p.*, " + IMAGE_COLUMNS + " FROM page p " + PRIMARY_IMAGE + " ORDER BY " + orderBy(q);
        params.put("limit", q.size());
        params.put("offset", (long) q.page() * q.size());

        long[] total = {0};
        List<ProductSummary> items = jdbc.sql(sql).params(params).query((rs, i) -> {
            total[0] = rs.getLong("total");
            return mapRow(rs);
        }).list();
        if (items.isEmpty() && q.page() > 0) {
            // Past the last page the window count has no row to ride on; the real total still matters to callers
            // (search widens its query when it believes nothing matched).
            total[0] = jdbc.sql("SELECT count(*) " + FROM + filter).params(params).query(Long.class).single();
        }
        return new PageResult<>(items, q.page(), q.size(), total[0]);
    }

    /** Product cards by id, in the order given (used for recently viewed and curated rows). */
    public List<ProductSummary> findSummaries(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        String sql = "SELECT " + CARD_COLUMNS + ", " + IMAGE_COLUMNS + " " + FROM + PRIMARY_IMAGE
                + " WHERE p.id IN (:ids) AND p.status <> 'DRAFT'";
        var byId = jdbc.sql(sql).param("ids", ids).query((rs, i) -> mapRow(rs)).list().stream()
                .collect(Collectors.toMap(ProductSummary::id, s -> s));
        return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    /**
     * Builds a prefix tsquery from sanitised tokens: "rtx 509" becomes "rtx:* &amp; 509:*".
     * Tokens are [a-z0-9] only (enforced by the query interpreter), so the string cannot contain operators.
     */
    static String toTsQuery(List<String> terms, boolean any) {
        return terms.stream()
                .map(t -> t.replaceAll("[^a-z0-9]", ""))
                .filter(t -> !t.isEmpty())
                .map(t -> t + ":*")
                .collect(Collectors.joining(any ? " | " : " & "));
    }

    private static String specClause(SpecFilter f, int n, Map<String, Object> params) {
        String key = "specKey" + n;
        params.put(key, f.key());
        List<String> parts = new ArrayList<>();
        switch (f.type()) {
            case TEXT -> {
                if (!f.textValues().isEmpty()) {
                    params.put("specText" + n, f.textValues());
                    parts.add("p.specs ->> :" + key + " IN (:specText" + n + ")");
                }
            }
            case NUMBER -> {
                // jsonb_typeof guards the cast, so a malformed value can never raise a SQL error.
                String num = "(CASE WHEN jsonb_typeof(p.specs -> :" + key + ") = 'number' THEN (p.specs ->> :" + key
                        + ")::numeric END)";
                if (!f.numberValues().isEmpty()) {
                    params.put("specNum" + n, f.numberValues());
                    parts.add(num + " IN (:specNum" + n + ")");
                }
                if (f.min() != null) {
                    params.put("specMin" + n, f.min());
                    parts.add(num + " >= :specMin" + n);
                }
                if (f.max() != null) {
                    params.put("specMax" + n, f.max());
                    parts.add(num + " <= :specMax" + n);
                }
            }
            case BOOLEAN -> {
                params.put("specBool" + n, f.booleanValue());
                parts.add("(CASE WHEN jsonb_typeof(p.specs -> :" + key + ") = 'boolean' THEN (p.specs ->> :" + key
                        + ")::boolean END) = :specBool" + n);
            }
        }
        // jsonb_exists() rather than the ? operator, which JDBC would read as a positional placeholder.
        return parts.isEmpty() ? "jsonb_exists(p.specs, :" + key + ")" : "(" + String.join(" AND ", parts) + ")";
    }

    private static String orderBy(ProductQuery q) {
        ProductSort sort = q.sort() == ProductSort.RELEVANCE && !q.hasText() ? ProductSort.FEATURED : q.sort();
        // Discontinued items sink to the bottom whatever the sort; p.id makes paging deterministic.
        String tail = ", p.id";
        String head = "(p.status = 'DISCONTINUED'), ";
        return head + switch (sort) {
            case RELEVANCE -> "relevance DESC, p.featured DESC";
            case FEATURED -> "p.featured DESC, p.price DESC";
            case PRICE_ASC -> "p.price ASC";
            case PRICE_DESC -> "p.price DESC";
            case DISCOUNT -> "(1 - p.price / NULLIF(p.compare_at_price, 0)) DESC NULLS LAST";
            case NEWEST -> "p.created_at DESC";
            case NAME -> "p.name ASC";
        } + tail;
    }

    private static ProductSummary mapRow(ResultSet rs) throws SQLException {
        ProductStatus status = ProductStatus.valueOf(rs.getString("status"));
        int available = rs.getInt("available");
        int reserved = rs.getInt("reserved");
        StockStatus stock = StockStatus.derive(status, available, reserved, rs.getInt("low_stock_threshold"),
                rs.getBoolean("backorder_allowed"));
        String small = rs.getString("url_small");
        ProductSummary.Image image = small == null ? null : new ProductSummary.Image(small, rs.getString("url_large"),
                rs.getInt("width"), rs.getInt("height"), rs.getString("alt"), ImageMatch.valueOf(rs.getString("match_type")),
                rs.getBoolean("studio"));
        return new ProductSummary(
                rs.getLong("id"), rs.getString("slug"), rs.getString("sku"), rs.getString("name"),
                rs.getString("brand_name"), rs.getString("brand_slug"), rs.getString("category_slug"),
                rs.getString("category_name"), rs.getString("summary"), rs.getBigDecimal("price"),
                rs.getBigDecimal("compare_at_price"), stock, Math.max(0, available - reserved),
                rs.getBoolean("featured"), image);
    }
}
