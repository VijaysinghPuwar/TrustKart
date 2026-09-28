package com.vijaysinghpuwar.trustkart.search.application;

import com.vijaysinghpuwar.trustkart.catalog.application.CatalogMapper;
import com.vijaysinghpuwar.trustkart.catalog.application.CatalogQueryBuilder;
import com.vijaysinghpuwar.trustkart.catalog.application.CatalogService;
import com.vijaysinghpuwar.trustkart.catalog.application.CatalogViews.PageDto;
import com.vijaysinghpuwar.trustkart.catalog.application.CatalogViews.ProductCardDto;
import com.vijaysinghpuwar.trustkart.catalog.application.CategoryTree;
import com.vijaysinghpuwar.trustkart.catalog.application.ListingParams;
import com.vijaysinghpuwar.trustkart.catalog.application.PageResult;
import com.vijaysinghpuwar.trustkart.catalog.application.ProductQuery;
import com.vijaysinghpuwar.trustkart.catalog.application.ProductSort;
import com.vijaysinghpuwar.trustkart.catalog.application.ProductSummary;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SearchService {

    private final CatalogService catalog;
    private final CatalogQueryBuilder queries;

    public SearchService(CatalogService catalog, CatalogQueryBuilder queries) {
        this.catalog = catalog;
        this.queries = queries;
    }

    public SearchResult search(ListingParams params) {
        QueryInterpretation reading = QueryInterpreter.interpret(params.q());
        List<String> ignore = List.copyOf(params.ignore());

        List<String> terms = reading.terms().stream().filter(t -> !ignore.contains("term:" + t)).toList();
        String category = params.category() != null ? params.category()
                : ignore.contains("category") ? null : reading.categorySlug();
        boolean budgetIgnored = ignore.contains("budget");
        BigDecimal min = params.minPrice() != null ? params.minPrice() : budgetIgnored ? null : reading.minPrice();
        BigDecimal max = params.maxPrice() != null ? params.maxPrice() : budgetIgnored ? null : reading.maxPrice();

        ProductSort defaultSort = terms.isEmpty() ? ProductSort.FEATURED : ProductSort.RELEVANCE;
        ProductQuery query = queries.build(params, terms, category, min, max, defaultSort);
        PageResult<ProductSummary> page = catalog.search(query);
        boolean relaxed = false;
        if (page.totalItems() == 0 && terms.size() > 1) {
            page = catalog.search(query.withMatchAnyTerm());
            relaxed = page.totalItems() > 0;
        }

        List<SearchResult.Chip> chips = chips(reading, params, category, min, max, terms);
        PageResult<ProductCardDto> cards = page.map(CatalogMapper::card);
        PageDto<ProductCardDto> dto = new PageDto<>(cards.items(), cards.page(), cards.size(), cards.totalItems(),
                cards.totalPages());
        return new SearchResult(reading.original(), "exact", false, relaxed, chips, dto);
    }

    private List<SearchResult.Chip> chips(QueryInterpretation reading, ListingParams params, String category,
            BigDecimal min, BigDecimal max, List<String> terms) {
        List<SearchResult.Chip> chips = new ArrayList<>();
        // Only chips that came from reading the text are removable; explicit filters are shown by the filter panel.
        if (params.category() == null && category != null) {
            CategoryTree tree = catalog.tree();
            String name = tree.bySlug(category).map(CategoryTree.Node::name).orElse(category);
            chips.add(new SearchResult.Chip("CATEGORY", "Category", name, "category"));
        }
        boolean budgetFromText = params.minPrice() == null && params.maxPrice() == null && (min != null || max != null);
        if (budgetFromText) {
            chips.add(new SearchResult.Chip("BUDGET", "Budget", budgetLabel(min, max), "budget"));
        }
        terms.forEach(t -> chips.add(new SearchResult.Chip("TERM", "Keyword", t, "term:" + t)));
        return chips;
    }

    private static String budgetLabel(BigDecimal min, BigDecimal max) {
        if (min != null && max != null) {
            return "$" + whole(min) + " to $" + whole(max);
        }
        return max != null ? "Under $" + whole(max) : "Over $" + whole(min);
    }

    private static String whole(BigDecimal amount) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.US);
        format.setMaximumFractionDigits(2);
        return format.format(amount);
    }
}
