package com.vijaysinghpuwar.trustkart.catalog.application;

import com.vijaysinghpuwar.trustkart.catalog.application.CatalogViews.CategoryDto;
import com.vijaysinghpuwar.trustkart.catalog.application.CatalogViews.CompareDto;
import com.vijaysinghpuwar.trustkart.catalog.application.CatalogViews.CompareRowDto;
import com.vijaysinghpuwar.trustkart.catalog.application.CatalogViews.FacetOptionDto;
import com.vijaysinghpuwar.trustkart.catalog.application.CatalogViews.FacetsDto;
import com.vijaysinghpuwar.trustkart.catalog.application.CatalogViews.HomeDto;
import com.vijaysinghpuwar.trustkart.catalog.application.CatalogViews.ImageCreditDto;
import com.vijaysinghpuwar.trustkart.catalog.application.CatalogViews.OptionGroupDto;
import com.vijaysinghpuwar.trustkart.catalog.application.CatalogViews.OptionImageDto;
import com.vijaysinghpuwar.trustkart.catalog.application.CatalogViews.OptionValueDto;
import com.vijaysinghpuwar.trustkart.catalog.application.CatalogViews.PriceRangeDto;
import com.vijaysinghpuwar.trustkart.catalog.application.CatalogViews.ProductCardDto;
import com.vijaysinghpuwar.trustkart.catalog.application.CatalogViews.ProductDetailDto;
import com.vijaysinghpuwar.trustkart.catalog.application.CatalogViews.Ref;
import com.vijaysinghpuwar.trustkart.catalog.application.CatalogViews.ShelfDto;
import com.vijaysinghpuwar.trustkart.catalog.application.CatalogViews.SpecFacetDto;
import com.vijaysinghpuwar.trustkart.catalog.application.CatalogViews.SpecGroupDto;
import com.vijaysinghpuwar.trustkart.catalog.application.CatalogViews.SpecValueDto;
import com.vijaysinghpuwar.trustkart.catalog.domain.Product;
import com.vijaysinghpuwar.trustkart.catalog.domain.ProductOptions;
import com.vijaysinghpuwar.trustkart.catalog.domain.SpecDataType;
import com.vijaysinghpuwar.trustkart.catalog.domain.SpecDefinition;
import com.vijaysinghpuwar.trustkart.catalog.infra.CatalogFacetRepository;
import com.vijaysinghpuwar.trustkart.catalog.infra.CategoryRepository;
import com.vijaysinghpuwar.trustkart.catalog.infra.ProductRepository;
import com.vijaysinghpuwar.trustkart.catalog.infra.ProductSearchRepository;
import com.vijaysinghpuwar.trustkart.catalog.infra.SpecDefinitionRepository;
import com.vijaysinghpuwar.trustkart.common.error.ApiError;
import com.vijaysinghpuwar.trustkart.common.error.ApiException;
import com.vijaysinghpuwar.trustkart.common.error.ErrorCode;
import com.vijaysinghpuwar.trustkart.common.error.NotFoundException;
import com.vijaysinghpuwar.trustkart.common.error.ValidationException;
import com.vijaysinghpuwar.trustkart.common.money.MoneyWire;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
            new String[] {"latest-iphones", "Latest iPhones", "iPhone 18 Pro, iPhone Air and the full lineup"},
            new String[] {"galaxy-flagships", "Galaxy flagships", "Galaxy S26 Ultra, Z Fold8 and Z Flip8"},
            new String[] {"premium-laptops", "Premium laptops", "MacBook, XPS, Zenbook, Galaxy Book and more"},
            new String[] {"dream-gpus", "Dream GPUs", "From gaming cards to AI accelerators"},
            new String[] {"ai-lab", "Build an AI lab", "Workstations and GPU servers"},
            new String[] {"homelab-starter", "Homelab starter", "Servers, switches, NAS and UPS"},
            new String[] {"oled-displays", "4K & OLED displays", "OLED TVs and high-refresh monitors"},
            new String[] {"ultimate-gaming-setup", "Ultimate gaming setup", "Consoles, handhelds and gear"},
            new String[] {"developer-setup", "Developer setup", "Laptops, displays and keyboards"},
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

    /** Cards for any number of ids (internal callers such as wishlists; the public lookup endpoint is capped). */
    public List<ProductCardDto> lookupAll(Collection<Long> ids) {
        return search.findSummaries(new LinkedHashSet<>(ids)).stream().map(CatalogMapper::card).toList();
    }

    /** Current price and stock for products, keyed by id. Used by cart and checkout to reprice server-side. */
    public Map<Long, ProductSummary> summariesById(Collection<Long> ids) {
        return search.findSummaries(new LinkedHashSet<>(ids)).stream()
                .collect(Collectors.toMap(ProductSummary::id, s -> s));
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

        List<OptionGroupDto> options = product.getOptions().stream()
                .map(g -> new OptionGroupDto(g.name(), g.values().stream()
                        .map(v -> new OptionValueDto(v.label(), v.price() == null ? null : MoneyWire.format(v.price()), v.isDefault(),
                                v.image() == null ? null : new OptionImageDto(v.image().small(), v.image().large(),
                                        v.image().width(), v.image().height(), v.image().alt())))
                        .toList()))
                .toList();
        return new ProductDetailDto(CatalogMapper.card(summary), product.getDescription(), product.getWarrantyMonths(),
                breadcrumbs, images, specGroups, facets.collectionTags(product.getId()), related, options);
    }

    /**
     * The server's answer to "what does this configuration cost?". {@code selection} is canonical: every option group,
     * in display order, mapped to the chosen label (defaults filled in). {@code label} reads like "512 GB · Silver" and
     * is null for products without options, whose unit price is simply the product price.
     */
    public record ResolvedOptions(Map<String, String> selection, String label, BigDecimal unitPrice) {}

    /** Resolves a shopper's option choice. Unknown groups or values are a 400 (VALIDATION_ERROR), never a guess. */
    public ResolvedOptions resolveOptions(long productId, Map<String, String> selection) {
        Product product = products.findById(productId).orElseThrow(() -> new NotFoundException("Product"));
        return resolve(product, selection == null ? Map.of() : selection);
    }

    /** Same as {@link #resolveOptions} but empty when the stored choice no longer exists (e.g. an old cart line). */
    public Optional<ResolvedOptions> resolveOptionsIfValid(long productId, Map<String, String> selection) {
        try {
            return Optional.of(resolveOptions(productId, selection));
        } catch (ValidationException e) {
            return Optional.empty();
        }
    }

    private static ResolvedOptions resolve(Product product, Map<String, String> selection) {
        List<ProductOptions.Group> groups = product.getOptions();
        List<ApiError.FieldError> errors = new ArrayList<>();
        for (String name : selection.keySet()) {
            if (groups.stream().noneMatch(g -> g.name().equals(name))) {
                errors.add(new ApiError.FieldError("options." + name, "is not an option of this product"));
            }
        }
        Map<String, String> canonical = new LinkedHashMap<>();
        BigDecimal price = product.getPrice();
        for (ProductOptions.Group g : groups) {
            String wanted = selection.get(g.name());
            ProductOptions.Value chosen = wanted == null ? g.defaultValue() : g.find(wanted);
            if (chosen == null) {
                errors.add(new ApiError.FieldError("options." + g.name(), "must be one of the listed choices"));
                continue;
            }
            canonical.put(g.name(), chosen.label());
            if (chosen.price() != null) {
                price = chosen.price();
            }
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        String label = canonical.isEmpty() ? null : String.join(" · ", canonical.values());
        return new ResolvedOptions(Collections.unmodifiableMap(canonical), label, price.setScale(MoneyWire.SCALE));
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

    private static final Set<String> HERO_MATCHES = Set.of("EXACT", "PRODUCT_LINE");
    private static final int HERO_SLIDES = 5;

    public HomeDto home() {
        List<ProductSummary> dealSummaries = search.search(ProductQuery.browse(24).withOnSale(ProductSort.DISCOUNT)).items();
        List<ProductCardDto> deals = dealSummaries.stream().limit(12).map(CatalogMapper::card).toList();
        ProductQuery featuredQuery = new ProductQuery(List.of(), false, List.of(), List.of(), null, null, false, false,
                null, List.of(), ProductSort.FEATURED, 0, 10);
        List<ProductSummary> featuredSummaries = search.search(featuredQuery).items().stream()
                .filter(ProductSummary::featured).toList();
        List<ProductCardDto> featured = featuredSummaries.stream().map(CatalogMapper::card).toList();

        List<ShelfDto> tiles = HOME_TILES.stream()
                .map(t -> new ShelfDto(t[0], t[1], t[2], "/collections/" + t[0], collection(t[0], 4)))
                .filter(t -> !t.items().isEmpty())
                .toList();
        List<ProductCardDto> slides = heroSlides(dealSummaries, featuredSummaries);
        return new HomeDto(slides.isEmpty() ? null : slides.getFirst(), slides, tiles, deals, featured, categoryTree());
    }

    /**
     * The home banner: up to {@value #HERO_SLIDES} products, one per category, best discount first, rotated daily so
     * the lead slide changes. The banner is the most prominent imagery on the site, so only manufacturer studio
     * photos of the exact model or its product line qualify; community photos and illustrations never headline it.
     */
    private List<ProductCardDto> heroSlides(List<ProductSummary> deals, List<ProductSummary> featured) {
        List<ProductSummary> candidates = java.util.stream.Stream.concat(deals.stream(), featured.stream())
                .filter(p -> p.image() != null && p.image().studio() && HERO_MATCHES.contains(p.image().match().name())
                        && CatalogMapper.maxQuantity(p.stockStatus(), p.sellableQuantity()) > 0)
                .toList();
        List<ProductCardDto> slides = new java.util.ArrayList<>();
        Set<String> categories = new java.util.HashSet<>();
        Set<Long> ids = new java.util.HashSet<>();
        for (ProductSummary c : candidates) {
            if (slides.size() < HERO_SLIDES && ids.add(c.id()) && categories.add(c.categorySlug())) {
                slides.add(CatalogMapper.card(c));
            }
        }
        if (slides.isEmpty()) {
            return slides;
        }
        java.util.Collections.rotate(slides, -(LocalDate.now(clock).getDayOfYear() % slides.size()));
        return slides;
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
