package com.vijaysinghpuwar.trustkart.catalog.seed;

import com.vijaysinghpuwar.trustkart.catalog.domain.Brand;
import com.vijaysinghpuwar.trustkart.catalog.domain.Category;
import com.vijaysinghpuwar.trustkart.catalog.domain.ImageMatch;
import com.vijaysinghpuwar.trustkart.catalog.domain.Inventory;
import com.vijaysinghpuwar.trustkart.catalog.domain.Product;
import com.vijaysinghpuwar.trustkart.catalog.domain.ProductImage;
import com.vijaysinghpuwar.trustkart.catalog.domain.ProductStatus;
import com.vijaysinghpuwar.trustkart.catalog.domain.SpecDataType;
import com.vijaysinghpuwar.trustkart.catalog.domain.SpecDefinition;
import com.vijaysinghpuwar.trustkart.catalog.infra.BrandRepository;
import com.vijaysinghpuwar.trustkart.catalog.infra.CatalogFacetRepository;
import com.vijaysinghpuwar.trustkart.catalog.infra.CategoryRepository;
import com.vijaysinghpuwar.trustkart.catalog.infra.ProductRepository;
import com.vijaysinghpuwar.trustkart.catalog.infra.SpecDefinitionRepository;
import com.vijaysinghpuwar.trustkart.catalog.seed.SeedModel.CategorySeed;
import com.vijaysinghpuwar.trustkart.catalog.seed.SeedModel.ImageSeed;
import com.vijaysinghpuwar.trustkart.catalog.seed.SeedModel.ProductSeed;
import com.vijaysinghpuwar.trustkart.catalog.seed.SeedModel.SpecSeed;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Loads the demo catalog. Repeatable: running it any number of times leaves exactly one copy of every
 * category, spec definition, brand and product, and never overwrites a product that already exists (so
 * admin edits survive restarts). It is only invoked when {@code trustkart.demo.seed-catalog=true}.
 */
@Component
public class DemoCatalogSeeder {

    public record Result(int categoriesCreated, int productsCreated, int productsSkipped) {}

    private static final Logger log = LoggerFactory.getLogger(DemoCatalogSeeder.class);
    private static final int BACKORDER_CAP = 0;

    private final ObjectMapper mapper;
    private final CategoryRepository categories;
    private final SpecDefinitionRepository specDefinitions;
    private final BrandRepository brands;
    private final ProductRepository products;
    private final CatalogFacetRepository facets;

    public DemoCatalogSeeder(ObjectMapper mapper, CategoryRepository categories, SpecDefinitionRepository specDefinitions,
            BrandRepository brands, ProductRepository products, CatalogFacetRepository facets) {
        this.mapper = mapper;
        this.categories = categories;
        this.specDefinitions = specDefinitions;
        this.brands = brands;
        this.products = products;
        this.facets = facets;
    }

    @Transactional
    public Result seed() {
        List<CategorySeed> categorySeeds = read("demo/categories.json", SeedModel.CategoryFile.class).categories();
        List<ProductSeed> productSeeds = read("demo/products.json", SeedModel.ProductFile.class).products();
        Map<String, ImageSeed> images = read("demo/images.json", new TypeReference<Map<String, ImageSeed>>() {});

        Map<String, Category> bySlug = new HashMap<>();
        Map<String, List<SpecSeed>> effectiveSpecs = new HashMap<>();
        int[] created = {0};
        for (int i = 0; i < categorySeeds.size(); i++) {
            upsertCategory(categorySeeds.get(i), null, i, List.of(), bySlug, effectiveSpecs, created);
        }

        int productsCreated = 0;
        int skipped = 0;
        for (ProductSeed seed : productSeeds) {
            if (products.existsBySku(seed.sku())) {
                skipped++;
                continue;
            }
            createProduct(seed, bySlug, effectiveSpecs, images);
            productsCreated++;
        }
        log.info("Demo catalog seeded: {} categories created, {} products created, {} already present",
                created[0], productsCreated, skipped);
        return new Result(created[0], productsCreated, skipped);
    }

    private void upsertCategory(CategorySeed seed, Category parent, int order, List<SpecSeed> inherited,
            Map<String, Category> bySlug, Map<String, List<SpecSeed>> effectiveSpecs, int[] created) {
        Category category = categories.findBySlug(seed.slug()).orElseGet(() -> {
            created[0]++;
            return categories.save(new Category(seed.slug(), seed.name(), parent, seed.description(), order));
        });
        bySlug.put(seed.slug(), category);

        List<SpecSeed> specs = seed.specsOrEmpty();
        for (int i = 0; i < specs.size(); i++) {
            SpecSeed s = specs.get(i);
            if (!specDefinitions.existsByCategoryIdAndKey(category.getId(), s.key())) {
                specDefinitions.save(new SpecDefinition(category, s.key(), s.label(), SpecDataType.valueOf(s.type()),
                        s.unit(), Objects.requireNonNullElse(s.group(), "Specifications"),
                        Boolean.TRUE.equals(s.filterable()), !Boolean.FALSE.equals(s.comparable()), i));
            }
        }
        List<SpecSeed> effective = new ArrayList<>(inherited);
        effective.addAll(specs);
        effectiveSpecs.put(seed.slug(), effective);

        List<CategorySeed> children = seed.childrenOrEmpty();
        for (int i = 0; i < children.size(); i++) {
            upsertCategory(children.get(i), category, i, effective, bySlug, effectiveSpecs, created);
        }
    }

    private void createProduct(ProductSeed seed, Map<String, Category> categoriesBySlug,
            Map<String, List<SpecSeed>> effectiveSpecs, Map<String, ImageSeed> images) {
        Category category = require(categoriesBySlug.get(seed.category()), "Unknown category " + seed.category());
        validateSpecs(seed, effectiveSpecs.get(seed.category()));
        Brand brand = brands.findByName(seed.brand()).orElseGet(() -> brands.save(new Brand(slugify(seed.brand()), seed.brand())));

        ProductStatus status = Boolean.TRUE.equals(seed.discontinued()) ? ProductStatus.DISCONTINUED : ProductStatus.ACTIVE;
        Product product = new Product(seed.sku(), seed.slug(), seed.name(), brand, category, seed.summary(),
                seed.description(), money(seed.price()), seed.compareAt() == null ? null : money(seed.compareAt()),
                Objects.requireNonNullElse(seed.warranty(), 12), seed.specs(), Objects.requireNonNullElse(seed.keywords(), ""),
                Boolean.TRUE.equals(seed.featured()), status);
        product.setSearchText(searchText(seed, category));

        int available = seed.stock().get(0);
        int threshold = seed.stock().get(1);
        product.attachInventory(new Inventory(product, available, threshold, Boolean.TRUE.equals(seed.backorder()),
                status == ProductStatus.DISCONTINUED ? BACKORDER_CAP : available));

        ImageSeed image = images.get(seed.slug());
        if (image != null) {
            product.addImage(new ProductImage(image.large(), image.small(), image.width(), image.height(), image.alt(),
                    ImageMatch.valueOf(image.match()), image.author() + ", " + image.license(), image.filePage(), 0));
        } else {
            log.warn("No image for demo product {}", seed.slug());
        }
        Product saved = products.save(product);

        List<String> tags = seed.collections() == null ? List.of() : seed.collections();
        for (int i = 0; i < tags.size(); i++) {
            facets.addToCollection(saved.getId(), tags.get(i), i);
        }
    }

    private static void validateSpecs(ProductSeed seed, List<SpecSeed> definitions) {
        Map<String, SpecSeed> byKey = definitions.stream()
                .collect(Collectors.toMap(SpecSeed::key, s -> s, (a, b) -> b));
        seed.specs().forEach((key, value) -> {
            SpecSeed def = byKey.get(key);
            if (def == null) {
                throw new IllegalStateException(seed.sku() + ": spec '" + key + "' is not defined for " + seed.category());
            }
            if (!SpecDataType.valueOf(def.type()).accepts(value)) {
                throw new IllegalStateException(seed.sku() + ": spec '" + key + "' must be " + def.type());
            }
        });
    }

    private static String searchText(ProductSeed seed, Category category) {
        List<String> parts = new ArrayList<>(List.of(seed.brand()));
        for (Category c = category; c != null; c = c.getParent()) {
            parts.add(c.getName());
        }
        seed.specs().values().stream().filter(String.class::isInstance).map(String.class::cast).forEach(parts::add);
        return String.join(" ", parts);
    }

    private static BigDecimal money(String raw) {
        BigDecimal value = new BigDecimal(raw);
        if (value.scale() > 2 || value.signum() <= 0) {
            throw new IllegalStateException("Invalid demo price " + raw);
        }
        return value.setScale(2);
    }

    static String slugify(String name) {
        String ascii = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    private static <T> T require(T value, String message) {
        if (value == null) {
            throw new IllegalStateException(message);
        }
        return value;
    }

    private <T> T read(String path, Class<T> type) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return mapper.readValue(in, type);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + path, e);
        }
    }

    private <T> T read(String path, TypeReference<T> type) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return mapper.readValue(in, type);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + path, e);
        }
    }
}
