package com.vijaysinghpuwar.trustkart.search.application;

import com.vijaysinghpuwar.trustkart.catalog.application.CatalogMapper;
import com.vijaysinghpuwar.trustkart.catalog.application.CatalogService;
import com.vijaysinghpuwar.trustkart.catalog.application.CatalogViews.Ref;
import com.vijaysinghpuwar.trustkart.catalog.application.CategoryTree;
import com.vijaysinghpuwar.trustkart.catalog.application.ProductQuery;
import com.vijaysinghpuwar.trustkart.catalog.application.ProductSort;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SuggestService {

    /** Example queries shown in the search box. Each one returns results against the demo catalog (tested). */
    public static final List<String> EXAMPLES = List.of(
            "Quiet mechanical keyboard for programming under $100",
            "GPU with at least 24 GB for local AI",
            "Homelab switch with 10G SFP+",
            "Rack server with ECC memory",
            "4K monitor with USB-C for a MacBook",
            "Security key for passkeys",
            "Gaming laptop under $3,500",
            "NAS for home backups",
            "UPS for a server rack",
            "Laser printer for a small office",
            "Webcam with privacy shutter",
            "AI server with H200");

    private final CatalogService catalog;

    public SuggestService(CatalogService catalog) {
        this.catalog = catalog;
    }

    public Suggestions suggest(String raw) {
        QueryInterpretation reading = QueryInterpreter.interpret(raw);
        String q = reading.original().toLowerCase(Locale.ROOT);
        if (q.isBlank()) {
            return new Suggestions(EXAMPLES.subList(0, 4), List.of(), List.of(), List.of());
        }
        List<String> words = List.of(q.split("[^a-z0-9$]+"));
        List<String> queries = EXAMPLES.stream()
                .filter(e -> words.stream().filter(w -> w.length() > 1).allMatch(w -> e.toLowerCase(Locale.ROOT).contains(w)))
                .limit(4)
                .toList();

        CategoryTree tree = catalog.tree();
        List<Ref> categories = new ArrayList<>();
        if (reading.categorySlug() != null) {
            tree.bySlug(reading.categorySlug()).ifPresent(n -> categories.add(new Ref(n.slug(), n.name())));
        }

        List<Long> categoryIds = reading.categorySlug() == null ? List.of()
                : tree.bySlug(reading.categorySlug()).map(n -> tree.selfAndDescendantIds(n.id())).orElse(List.of());
        List<com.vijaysinghpuwar.trustkart.catalog.application.CatalogViews.ProductCardDto> products = List.of();
        if (!reading.terms().isEmpty() || !categoryIds.isEmpty()) {
            ProductQuery query = new ProductQuery(reading.terms(), false, categoryIds, List.of(), reading.minPrice(),
                    reading.maxPrice(), false, false, null, List.of(), ProductSort.RELEVANCE, 0, 4);
            var page = catalog.search(query);
            if (page.totalItems() == 0 && reading.terms().size() > 1) {
                page = catalog.search(query.withMatchAnyTerm());
            }
            products = page.items().stream().map(CatalogMapper::card).toList();
        }

        List<Suggestions.Chip> chips = new ArrayList<>();
        categories.forEach(c -> chips.add(new Suggestions.Chip("Category", c.name())));
        NumberFormat money = NumberFormat.getCurrencyInstance(Locale.US);
        money.setMaximumFractionDigits(0);
        if (reading.maxPrice() != null && reading.minPrice() != null) {
            chips.add(new Suggestions.Chip("Budget", money.format(reading.minPrice()) + " to " + money.format(reading.maxPrice())));
        } else if (reading.maxPrice() != null) {
            chips.add(new Suggestions.Chip("Budget", "Under " + money.format(reading.maxPrice())));
        } else if (reading.minPrice() != null) {
            chips.add(new Suggestions.Chip("Budget", "Over " + money.format(reading.minPrice())));
        }
        reading.terms().forEach(t -> chips.add(new Suggestions.Chip("Keyword", t)));
        return new Suggestions(queries, categories, products, chips);
    }
}
