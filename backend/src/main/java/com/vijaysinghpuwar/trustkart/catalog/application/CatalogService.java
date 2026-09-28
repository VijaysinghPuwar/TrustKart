package com.vijaysinghpuwar.trustkart.catalog.application;

import com.vijaysinghpuwar.trustkart.catalog.application.CatalogViews.CategoryDto;
import com.vijaysinghpuwar.trustkart.catalog.application.CatalogViews.CompareDto;
import com.vijaysinghpuwar.trustkart.catalog.application.CatalogViews.CompareRowDto;
import com.vijaysinghpuwar.trustkart.catalog.application.CatalogViews.FacetOptionDto;
import com.vijaysinghpuwar.trustkart.catalog.application.CatalogViews.FacetsDto;
import com.vijaysinghpuwar.trustkart.catalog.application.CatalogViews.HomeDto;
import com.vijaysinghpuwar.trustkart.catalog.application.CatalogViews.ImageCreditDto;
import com.vijaysinghpuwar.trustkart.catalog.application.CatalogViews.PriceRangeDto;
import com.vijaysinghpuwar.trustkart.catalog.application.CatalogViews.ProductCardDto;
import com.vijaysinghpuwar.trustkart.catalog.application.CatalogViews.ProductDetailDto;
import com.vijaysinghpuwar.trustkart.catalog.application.CatalogViews.Ref;
import com.vijaysinghpuwar.trustkart.catalog.application.CatalogViews.ShelfDto;
import com.vijaysinghpuwar.trustkart.catalog.application.CatalogViews.SpecFacetDto;
import com.vijaysinghpuwar.trustkart.catalog.application.CatalogViews.SpecGroupDto;
import com.vijaysinghpuwar.trustkart.catalog.application.CatalogViews.SpecValueDto;
import com.vijaysinghpuwar.trustkart.catalog.domain.Product;
import com.vijaysinghpuwar.trustkart.catalog.domain.SpecDataType;
import com.vijaysinghpuwar.trustkart.catalog.domain.SpecDefinition;
import com.vijaysinghpuwar.trustkart.catalog.infra.CatalogFacetRepository;
import com.vijaysinghpuwar.trustkart.catalog.infra.CategoryRepository;
import com.vijaysinghpuwar.trustkart.catalog.infra.ProductRepository;
import com.vijaysinghpuwar.trustkart.catalog.infra.ProductSearchRepository;
import com.vijaysinghpuwar.trustkart.catalog.infra.SpecDefinitionRepository;
import com.vijaysinghpuwar.trustkart.common.error.ApiException;
import com.vijaysinghpuwar.trustkart.common.error.ErrorCode;
import com.vijaysinghpuwar.trustkart.common.error.NotFoundException;
import com.vijaysinghpuwar.trustkart.common.money.MoneyWire;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CatalogService {

    public static final int MAX_COMPARE = 4;
    public static final int MAX_LOOKUP = 24;

    /** Curated home-page tiles: collection tag, title, subtitle. Editorial groupings, not personalisation. */
    private static final List<String[]> HOME_TILES = List.of(
            new String[] {"dream-gpus", "Dream GPUs", "From gaming cards to AI accelerators"},
            new String[] {"developer-setup", "Developer setup", "Laptops, displays and keyboards"},
            new String[] {"homelab-starter", "Homelab starter", "Servers, switches, NAS and UPS"},
            new String[] {"ai-lab", "Build an AI lab", "Workstations and GPU servers"},
            new String[] {"enterprise-lab", "Enterprise & servers", "Rack gear for the server room"},
            new String[] {"security-lab", "Cybersecurity lab", "Firewalls, keys and cameras"});

    private final CategoryRepository categories;
    private final SpecDefinitionRepository specDefinitions;
    private final ProductRepository products;
    private final ProductSearchRepository search;
    private final CatalogFacetRepository facets;
    private final Clock clock;

    public CatalogService(CategoryRepository categories, SpecDefinitionRepository specDefinitions,
            ProductRepository products, ProductSearchRepository search, CatalogFacetRepository facets, Clock clock) {
        this.categories = categories;
        this.specDefinitions = specDefinitions;
        this.products = products;
        this.search = search;
        this.facets = facets;
        this.clock = clock;
    }

    public CategoryTree tree() {
        return new CategoryTree(categories.findAll());
    }

    public List<CategoryDto> categoryTree() {
        CategoryTree tree = tree();
        Map<Long, Long> direct = facets.countsByCategory();
        return tree.roots().stream().map(r -> toCategoryDto(tree, r, direct)).toList();
    }

    public CategoryTree.Node requireCategory(CategoryTree tree, String slug) {
        return tree.bySlug(slug).orElseThrow(() -> new NotFoundException("Category"));
    }

    /** Definitions inherited down the category path; a child's definition wins over an ancestor's. */
    public Map<String, SpecDefinition> effectiveDefinitions(CategoryTree tree, long categoryId) {
        List<Long> path = tree.pathTo(categoryId).stream().map(CategoryTree.Node::id).toList();
        Map<Long, Integer> depth = new java.util.HashMap<>();
        for (int i = 0; i < path.size(); i++) {
            depth.put(path.get(i), i);
        }
        Map<String, SpecDefinition> out = new LinkedHashMap<>();
        specDefinitions.findByCategoryIdInOrderBySortOrderAsc(path).stream()
                .sorted(Comparator.comparingInt((SpecDefinition d) -> depth.get(d.getCategory().getId()))
                        .thenComparingInt(SpecDefinition::getSortOrder))
                .forEach(d -> out.put(d.getKey(), d));
        return out;
    }

    public PageResult<ProductSummary> search(ProductQuery query) {
        return search.search(query);
    }

    public List<ProductCardDto> lookup(Collection<Long> ids) {
        if (ids.size() > MAX_LOOKUP) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Too many product ids.");
        }
        return search.findSummaries(new LinkedHashSet<>(ids)).stream().map(CatalogMapper::card).toList();
    }

    public List<ProductCardDto> collection(String tag, int limit) {
        return search.search(ProductQuery.browse(limit).withCollection(tag)).items().stream()
                .map(CatalogMapper::card).toList();
    }

    public ProductDetailDto detail(String slug) {
        Product product = products.findWithDetailsBySlug(slug)
                .filter(p -> p.getStatus() != com.vijaysinghpuwar.trustkart.catalog.domain.ProductStatus.DRAFT)
                .orElseThrow(() -> new NotFoundException("Product"));
        CategoryTree tree = tree();
        ProductSummary summary = search.findSummaries(List.of(product.getId())).getFirst();

        List<Ref> breadcrumbs = tree.pathTo(product.getCategory().getId()).stream()
                .map(n -> new Ref(n.slug(), n.name())).toList();
        List<ImageCreditDto> images = product.getImages().stream()
                .map(i -> new ImageCreditDto(i.getUrlLarge(), i.getUrlSmall(), i.getWidth(), i.getHeight(), i.getAlt(),
                        i.getMatchType().name(), i.getCredit(), i.getSourceUrl()))
                .toList();

        Map<String, SpecDefinition> defs = effectiveDefinitions(tree, product.getCategory().getId());
        Map<String, List<SpecValueDto>> grouped = new LinkedHashMap<>();
        defs.values().forEach(d -> {
            Object value = product.getSpecs().get(d.getKey());
            if (value != null) {
                grouped.computeIfAbsent(d.getGroupLabel(), g -> new ArrayList<>())
                        .add(new SpecValueDto(d.getKey(), d.getLabel(), CatalogMapper.formatSpec(d, value)));
            }
        });
        List<SpecGroupDto> specGroups = grouped.entrySet().stream()
                .map(e -> new SpecGroupDto(e.getKey(), e.getValue())).toList();

        ProductQuery relatedQuery = ProductQuery.browse(9).withCategories(List.of(product.getCategory().getId()));
        List<ProductCardDto> related = search.search(relatedQuery).items().stream()
                .filter(s -> s.id() != product.getId())
                .sorted(Comparator.comparing(s -> s.price().subtract(product.getPrice()).abs()))
                .limit(8)
                .map(CatalogMapper::card)
                .toList();

        return new ProductDetailDto(CatalogMapper.card(summary), product.getDescription(), product.getWarrantyMonths(),
                breadcrumbs, images, specGroups, facets.collectionTags(product.getId()), related);
    }

    public FacetsDto facets(String categorySlug) {
        CategoryTree tree = tree();
        CategoryTree.Node node = requireCategory(tree, categorySlug);
        List<Long> ids = tree.selfAndDescendantIds(node.id());

        var brandCounts = facets.brandCounts(ids);
        Map<String, String> brandNames = facets.brandNames(brandCounts.stream().map(CatalogFacetRepository.ValueCount::value).toList());
        List<FacetOptionDto> brands = brandCounts.stream()
                .map(b -> new FacetOptionDto(b.value(), brandNames.getOrDefault(b.value(), b.value()), b.count()))
                .toList();

        CatalogFacetRepository.PriceRange range = facets.priceRange(ids);
        PriceRangeDto price = new PriceRangeDto(MoneyWire.format(range.min()), MoneyWire.format(range.max()));

        List<SpecFacetDto> specs = effectiveDefinitions(tree, node.id()).values().stream()
                .filter(SpecDefinition::isFilterable)
                .map(d -> specFacet(d, ids))
                .filter(f -> f.options().size() > 1)
                .toList();
        return new FacetsDto(new Ref(node.slug(), node.name()), brands, price, specs);
    }

    public CompareDto compare(List<String> slugs) {
        List<String> distinct = slugs.stream().distinct().toList();
        if (distinct.size() < 2 || distinct.size() > MAX_COMPARE) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Choose between 2 and 4 products to compare.");
        }
        Map<String, Product> bySlug = products.findWithDetailsBySlugIn(distinct).stream()
                .collect(Collectors.toMap(Product::getSlug, p -> p));
        if (bySlug.size() != distinct.size()) {
            throw new NotFoundException("Product");
        }
        List<Product> ordered = distinct.stream().map(bySlug::get).toList();
        Map<Long, ProductSummary> summaries = search.findSummaries(ordered.stream().map(Product::getId).toList()).stream()
                .collect(Collectors.toMap(ProductSummary::id, s -> s));
        List<ProductCardDto> cards = ordered.stream().map(p -> CatalogMapper.card(summaries.get(p.getId()))).toList();

        CategoryTree tree = tree();
        List<CompareRowDto> rows = new ArrayList<>();
        rows.add(row("price", "Price", "Overview", cards.stream().map(c -> "$" + c.price()).toList()));
        rows.add(row("availability", "Availability", "Overview", cards.stream().map(c -> label(c.stockStatus())).toList()));
        rows.add(row("brand", "Brand", "Overview", ordered.stream().map(p -> p.getBrand().getName()).toList()));
        rows.add(row("category", "Category", "Overview", ordered.stream().map(p -> p.getCategory().getName()).toList()));
        rows.add(row("warranty", "Warranty", "Overview",
                ordered.stream().map(p -> p.getWarrantyMonths() == 0 ? "None" : p.getWarrantyMonths() + " months").toList()));

        // Union of comparable specs across all products, keeping first-seen order; missing values show as null.
        Map<String, SpecDefinition> union = new LinkedHashMap<>();
        Map<Long, Map<String, SpecDefinition>> perProduct = new java.util.HashMap<>();
        for (Product p : ordered) {
            Map<String, SpecDefinition> defs = effectiveDefinitions(tree, p.getCategory().getId());
            perProduct.put(p.getId(), defs);
            defs.values().stream().filter(SpecDefinition::isComparable).forEach(d -> union.putIfAbsent(d.getKey(), d));
        }
        for (SpecDefinition d : union.values()) {
            List<String> values = ordered.stream().map(p -> {
                SpecDefinition own = perProduct.get(p.getId()).get(d.getKey());
                return own == null ? null : CatalogMapper.formatSpec(own, p.getSpecs().get(d.getKey()));
            }).toList();
            if (values.stream().anyMatch(Objects::nonNull)) {
                rows.add(row(d.getKey(), d.getLabel(), d.getGroupLabel(), values));
            }
        }
        return new CompareDto(cards, rows);
    }

    public HomeDto home() {
        List<ProductCardDto> deals = search.search(ProductQuery.browse(12).withOnSale(ProductSort.DISCOUNT)).items()
                .stream().map(CatalogMapper::card).toList();
        ProductQuery featuredQuery = new ProductQuery(List.of(), false, List.of(), List.of(), null, null, false, false,
                null, List.of(), ProductSort.FEATURED, 0, 10);
        List<ProductCardDto> featured = search.search(featuredQuery).items().stream()
                .filter(ProductSummary::featured).map(CatalogMapper::card).toList();

        List<ShelfDto> tiles = HOME_TILES.stream()
                .map(t -> new ShelfDto(t[0], t[1], t[2], "/collections/" + t[0], collection(t[0], 4)))
                .filter(t -> !t.items().isEmpty())
                .toList();
        return new HomeDto(heroOfTheDay(deals, featured), tiles, deals, featured, categoryTree());
    }

    /** Rotates daily through discounted featured products (falls back to any deal) so the pick is deterministic per day. */
    private ProductCardDto heroOfTheDay(List<ProductCardDto> deals, List<ProductCardDto> featured) {
        Set<Long> featuredIds = featured.stream().map(ProductCardDto::id).collect(Collectors.toSet());
        List<ProductCardDto> pool = deals.stream().filter(d -> featuredIds.contains(d.id()) && d.maxQuantity() > 0).toList();
        if (pool.isEmpty()) {
            pool = deals.isEmpty() ? featured : deals;
        }
        if (pool.isEmpty()) {
            return null;
        }
        int day = LocalDate.now(clock).getDayOfYear();
        return pool.get(day % pool.size());
    }

    private SpecFacetDto specFacet(SpecDefinition d, List<Long> categoryIds) {
        List<FacetOptionDto> options = facets.specValueCounts(categoryIds, d.getKey()).stream()
                .map(v -> new FacetOptionDto(v.value(), optionLabel(d, v.value()), v.count()))
                .sorted(optionOrder(d))
                .toList();
        return new SpecFacetDto(d.getKey(), d.getLabel(), d.getDataType().name(), d.getUnit(), options);
    }

    private static Comparator<FacetOptionDto> optionOrder(SpecDefinition d) {
        if (d.getDataType() == SpecDataType.NUMBER) {
            return Comparator.comparing(o -> new BigDecimal(o.value()));
        }
        if (d.getDataType() == SpecDataType.BOOLEAN) {
            return Comparator.comparing(FacetOptionDto::value).reversed();
        }
        return Comparator.comparingLong(FacetOptionDto::count).reversed().thenComparing(FacetOptionDto::value);
    }

    private static String optionLabel(SpecDefinition d, String raw) {
        return switch (d.getDataType()) {
            case BOOLEAN -> "true".equals(raw) ? "Yes" : "No";
            case NUMBER -> CatalogMapper.formatSpec(d, new BigDecimal(raw));
            case TEXT -> raw;
        };
    }

    private static CompareRowDto row(String key, String label, String group, List<String> values) {
        boolean differs = values.stream().distinct().count() > 1;
        return new CompareRowDto(key, label, group, values, differs);
    }

    private static String label(String stockStatus) {
        return switch (stockStatus) {
            case "IN_STOCK" -> "In stock";
            case "LOW_STOCK" -> "Low stock";
            case "BACKORDER" -> "Backorder";
            case "DISCONTINUED" -> "Discontinued";
            default -> "Out of stock";
        };
    }

    private static CategoryDto toCategoryDto(CategoryTree tree, CategoryTree.Node node, Map<Long, Long> direct) {
        long total = tree.selfAndDescendantIds(node.id()).stream().mapToLong(id -> direct.getOrDefault(id, 0L)).sum();
        List<CategoryDto> children = tree.childrenOf(node.id()).stream().map(c -> toCategoryDto(tree, c, direct)).toList();
        return new CategoryDto(node.slug(), node.name(), node.description(), total, children);
    }
}
