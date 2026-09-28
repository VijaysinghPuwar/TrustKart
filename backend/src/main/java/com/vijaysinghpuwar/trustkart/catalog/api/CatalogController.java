package com.vijaysinghpuwar.trustkart.catalog.api;

import com.vijaysinghpuwar.trustkart.catalog.application.CatalogMapper;
import com.vijaysinghpuwar.trustkart.catalog.application.CatalogQueryBuilder;
import com.vijaysinghpuwar.trustkart.catalog.application.CatalogService;
import com.vijaysinghpuwar.trustkart.catalog.application.CatalogViews.CategoryDto;
import com.vijaysinghpuwar.trustkart.catalog.application.CatalogViews.CompareDto;
import com.vijaysinghpuwar.trustkart.catalog.application.CatalogViews.FacetsDto;
import com.vijaysinghpuwar.trustkart.catalog.application.CatalogViews.HomeDto;
import com.vijaysinghpuwar.trustkart.catalog.application.CatalogViews.PageDto;
import com.vijaysinghpuwar.trustkart.catalog.application.CatalogViews.ProductCardDto;
import com.vijaysinghpuwar.trustkart.catalog.application.CatalogViews.ProductDetailDto;
import com.vijaysinghpuwar.trustkart.catalog.application.ListingParams;
import com.vijaysinghpuwar.trustkart.catalog.application.PageResult;
import com.vijaysinghpuwar.trustkart.catalog.application.ProductSort;
import com.vijaysinghpuwar.trustkart.search.application.QueryInterpreter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/catalog")
@Tag(name = "Catalog", description = "Public product catalog")
class CatalogController {

    private static final String SLUG = "[a-z0-9]+(-[a-z0-9]+)*";
    /** Catalog responses are public and not personalised; a short shared cache keeps them fast. */
    private static final CacheControl PUBLIC_SHORT = CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic();

    private final CatalogService catalog;
    private final CatalogQueryBuilder queries;

    CatalogController(CatalogService catalog, CatalogQueryBuilder queries) {
        this.catalog = catalog;
        this.queries = queries;
    }

    @GetMapping("/home")
    @Operation(summary = "Everything the home page needs in one request")
    ResponseEntity<HomeDto> home() {
        return ResponseEntity.ok().cacheControl(PUBLIC_SHORT).body(catalog.home());
    }

    @GetMapping("/categories")
    ResponseEntity<List<CategoryDto>> categories() {
        return ResponseEntity.ok().cacheControl(PUBLIC_SHORT).body(catalog.categoryTree());
    }

    @GetMapping("/categories/{slug}/facets")
    @Operation(summary = "Filter facets (brands, price range, category-specific specs) for a category")
    ResponseEntity<FacetsDto> facets(@PathVariable @Pattern(regexp = SLUG) @Size(max = 80) String slug) {
        return ResponseEntity.ok().cacheControl(PUBLIC_SHORT).body(catalog.facets(slug));
    }

    @GetMapping("/products")
    @Operation(summary = "Browse and filter products. Spec filters: spec.<key>=value, spec.<key>.min, spec.<key>.max")
    ResponseEntity<PageDto<ProductCardDto>> products(@RequestParam MultiValueMap<String, String> params) {
        ListingParams p = ListingParams.from(params);
        List<String> terms = p.q() == null ? List.of() : QueryInterpreter.interpret(p.q()).terms();
        ProductSort fallback = terms.isEmpty() ? ProductSort.FEATURED : ProductSort.RELEVANCE;
        PageResult<ProductCardDto> page = catalog.search(
                queries.build(p, terms, p.category(), p.minPrice(), p.maxPrice(), fallback)).map(CatalogMapper::card);
        return ResponseEntity.ok().cacheControl(PUBLIC_SHORT)
                .body(new PageDto<>(page.items(), page.page(), page.size(), page.totalItems(), page.totalPages()));
    }

    @GetMapping("/products/lookup")
    @Operation(summary = "Product cards by id, in the given order (recently viewed, wishlists)")
    List<ProductCardDto> lookup(@RequestParam @Size(max = CatalogService.MAX_LOOKUP) List<@Min(1) Long> ids) {
        return catalog.lookup(ids);
    }

    @GetMapping("/products/{slug}")
    ResponseEntity<ProductDetailDto> product(@PathVariable @Pattern(regexp = SLUG) @Size(max = 120) String slug) {
        return ResponseEntity.ok().cacheControl(PUBLIC_SHORT).body(catalog.detail(slug));
    }

    @GetMapping("/compare")
    @Operation(summary = "Side-by-side comparison of 2 to 4 products")
    CompareDto compare(@RequestParam @Size(min = 2, max = CatalogService.MAX_COMPARE) List<@Pattern(regexp = SLUG) String> slugs) {
        return catalog.compare(slugs);
    }

    @GetMapping("/collections/{tag}")
    List<ProductCardDto> collection(@PathVariable @Pattern(regexp = SLUG) @Size(max = 60) String tag,
            @RequestParam(defaultValue = "12") @Min(1) @Max(48) int limit) {
        return catalog.collection(tag, limit);
    }
}
