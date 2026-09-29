package com.vijaysinghpuwar.trustkart.search.api;

import com.vijaysinghpuwar.trustkart.catalog.application.ListingParams;
import com.vijaysinghpuwar.trustkart.search.application.SearchResult;
import com.vijaysinghpuwar.trustkart.search.application.SearchService;
import com.vijaysinghpuwar.trustkart.search.application.SuggestService;
import com.vijaysinghpuwar.trustkart.search.application.Suggestions;
import com.vijaysinghpuwar.trustkart.common.web.PublicCache;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/search")
@Tag(name = "Search")
class SearchController {

    private final SearchService search;
    private final SuggestService suggest;

    SearchController(SearchService search, SuggestService suggest) {
        this.search = search;
        this.suggest = suggest;
    }

    @GetMapping
    @Operation(summary = "Search with query interpretation (category, budget, keywords) plus all listing filters")
    ResponseEntity<SearchResult> search(@RequestParam MultiValueMap<String, String> params) {
        return ResponseEntity.ok().cacheControl(PublicCache.CATALOG).body(search.search(ListingParams.from(params)));
    }

    @GetMapping("/suggest")
    @Operation(summary = "Type-ahead suggestions: example queries, categories and top product matches")
    ResponseEntity<Suggestions> suggest(@RequestParam(defaultValue = "") @Size(max = 200) String q) {
        return ResponseEntity.ok().cacheControl(PublicCache.CATALOG).body(suggest.suggest(q));
    }
}
