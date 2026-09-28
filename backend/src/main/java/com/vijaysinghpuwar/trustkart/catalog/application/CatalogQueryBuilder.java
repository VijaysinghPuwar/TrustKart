package com.vijaysinghpuwar.trustkart.catalog.application;

import com.vijaysinghpuwar.trustkart.catalog.domain.SpecDefinition;
import com.vijaysinghpuwar.trustkart.common.error.ApiException;
import com.vijaysinghpuwar.trustkart.common.error.ErrorCode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Resolves category slugs and spec filters and assembles a {@link ProductQuery}. */
@Component
public class CatalogQueryBuilder {

    private final CatalogService catalog;

    public CatalogQueryBuilder(CatalogService catalog) {
        this.catalog = catalog;
    }

    public ProductQuery build(ListingParams p, List<String> terms, String categorySlug, BigDecimal minPrice,
            BigDecimal maxPrice, ProductSort defaultSort) {
        CategoryTree tree = catalog.tree();
        List<Long> categoryIds = List.of();
        List<SpecFilter> specFilters = List.of();
        if (categorySlug != null) {
            CategoryTree.Node node = catalog.requireCategory(tree, categorySlug);
            categoryIds = tree.selfAndDescendantIds(node.id());
            Map<String, SpecDefinition> defs = catalog.effectiveDefinitions(tree, node.id());
            specFilters = SpecFilterParser.parse(p.specParams(), defs);
        } else if (!p.specParams().isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Specification filters need a category.");
        }
        ProductSort sort = p.sort() != null ? p.sort() : defaultSort;
        return new ProductQuery(terms, false, categoryIds, p.brands(), minPrice, maxPrice, p.inStock(), p.onSale(),
                p.collection(), specFilters, sort, p.page(), p.size());
    }
}
