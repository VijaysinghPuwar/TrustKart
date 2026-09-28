package com.vijaysinghpuwar.trustkart.catalog.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** Read views returned by the catalog API. Built inside the service transaction; money is always a decimal string. */
public final class CatalogViews {

    private CatalogViews() {}

    public record Ref(String slug, String name) {}

    public record ImageDto(String small, String large, int width, int height, String alt, String match) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ProductCardDto(
            long id,
            String slug,
            String sku,
            String name,
            Ref brand,
            Ref category,
            String summary,
            String price,
            String compareAtPrice,
            int percentOff,
            String stockStatus,
            Integer stockLeft,
            int maxQuantity,
            boolean featured,
            ImageDto image) {}

    public record PageDto<T>(List<T> items, int page, int size, long totalItems, int totalPages) {}

    public record CategoryDto(String slug, String name, String description, long productCount, List<CategoryDto> children) {}

    public record SpecValueDto(String key, String label, String value) {}

    public record SpecGroupDto(String group, List<SpecValueDto> specs) {}

    public record ImageCreditDto(String large, String small, int width, int height, String alt, String match,
            String credit, String sourceUrl) {}

    public record ProductDetailDto(
            ProductCardDto product,
            String description,
            int warrantyMonths,
            List<Ref> breadcrumbs,
            List<ImageCreditDto> images,
            List<SpecGroupDto> specGroups,
            List<String> collections,
            List<ProductCardDto> related) {}

    public record FacetOptionDto(String value, String label, long count) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SpecFacetDto(String key, String label, String type, String unit, List<FacetOptionDto> options) {}

    public record PriceRangeDto(String min, String max) {}

    public record FacetsDto(Ref category, List<FacetOptionDto> brands, PriceRangeDto price, List<SpecFacetDto> specs) {}

    public record CompareRowDto(String key, String label, String group, List<String> values, boolean differs) {}

    public record CompareDto(List<ProductCardDto> products, List<CompareRowDto> rows) {}

    public record ShelfDto(String key, String title, String subtitle, String link, List<ProductCardDto> items) {}

    public record HomeDto(ProductCardDto hero, List<ShelfDto> tiles, List<ProductCardDto> deals,
            List<ProductCardDto> featured, List<CategoryDto> categories) {}
}
