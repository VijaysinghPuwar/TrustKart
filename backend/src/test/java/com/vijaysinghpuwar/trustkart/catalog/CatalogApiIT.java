package com.vijaysinghpuwar.trustkart.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.vijaysinghpuwar.trustkart.catalog.infra.CategoryRepository;
import com.vijaysinghpuwar.trustkart.catalog.infra.ProductRepository;
import com.vijaysinghpuwar.trustkart.catalog.seed.DemoCatalogSeeder;
import com.vijaysinghpuwar.trustkart.search.application.QueryInterpreter;
import com.vijaysinghpuwar.trustkart.search.application.SuggestService;
import com.vijaysinghpuwar.trustkart.support.IntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class CatalogApiIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    DemoCatalogSeeder seeder;

    @Autowired
    ProductRepository products;

    @Autowired
    CategoryRepository categories;

    @BeforeEach
    void seed() {
        seeder.seed();
    }

    @Test
    void seedingIsRepeatableAndNeverDuplicates() {
        long before = products.count();
        DemoCatalogSeeder.Result again = seeder.seed();

        assertThat(again.productsCreated()).isZero();
        assertThat(again.categoriesCreated()).isZero();
        assertThat(again.productsSkipped()).isEqualTo((int) before);
        assertThat(products.count()).isEqualTo(before).isGreaterThanOrEqualTo(100);
    }

    @Test
    void everyInterpreterCategoryExists() {
        QueryInterpreter.knownCategorySlugs()
                .forEach(slug -> assertThat(categories.findBySlug(slug)).as(slug).isPresent());
    }

    @Test
    void homeReturnsHeroTilesDealsAndCategories() throws Exception {
        mvc.perform(get("/api/v1/catalog/home"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=60, public"))
                .andExpect(jsonPath("$.hero.slug").isNotEmpty())
                .andExpect(jsonPath("$.tiles", hasSize(6)))
                .andExpect(jsonPath("$.tiles[*].items", everyItem(hasSize(4))))
                .andExpect(jsonPath("$.deals[*].compareAtPrice", everyItem(instanceOf(String.class))))
                .andExpect(jsonPath("$.categories", hasSize(11)));
    }

    @Test
    void moneyIsSerialisedAsDecimalStrings() throws Exception {
        mvc.perform(get("/api/v1/catalog/products/nvidia-rtx-5090-fe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.product.price").value("1999.99"))
                .andExpect(jsonPath("$.product.stockStatus").value("LOW_STOCK"))
                .andExpect(jsonPath("$.product.stockLeft").value(2))
                .andExpect(jsonPath("$.product.maxQuantity").value(2))
                .andExpect(jsonPath("$.breadcrumbs[*].slug", contains("components", "gpus")))
                .andExpect(jsonPath("$.specGroups[0].specs[*].label", hasItem("GPU")))
                .andExpect(jsonPath("$.images[0].credit").isNotEmpty());
    }

    @Test
    void categoryFilterIncludesDescendants() throws Exception {
        mvc.perform(get("/api/v1/catalog/products").param("category", "laptops").param("size", "60"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(8))
                .andExpect(jsonPath("$.items[*].category.slug", hasItem("gaming-laptops")))
                .andExpect(jsonPath("$.items[*].category.slug", hasItem("developer-laptops")));
    }

    @Test
    void numericSpecRangeFiltersUseJsonb() throws Exception {
        String body = mvc.perform(get("/api/v1/catalog/products").param("category", "gpus").param("spec.vramGb.min", "32"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> slugs = JsonPath.read(body, "$.items[*].slug");
        assertThat(slugs).containsExactlyInAnyOrder("nvidia-rtx-5090-fe", "nvidia-rtx-pro-6000-blackwell", "nvidia-h200-nvl");
    }

    @Test
    void textAndBooleanSpecFilters() throws Exception {
        mvc.perform(get("/api/v1/catalog/products").param("category", "cpus").param("spec.socket", "AM5"))
                .andExpect(jsonPath("$.totalItems").value(2));
        mvc.perform(get("/api/v1/catalog/products").param("category", "keyboards").param("spec.quiet", "true"))
                .andExpect(jsonPath("$.totalItems").value(3));
    }

    @Test
    void unknownSpecKeyIsRejectedNotPassedToSql() throws Exception {
        mvc.perform(get("/api/v1/catalog/products").param("category", "gpus").param("spec.price'--", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(get("/api/v1/catalog/products").param("category", "gpus").param("spec.socket", "AM5"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void specFiltersRequireACategory() throws Exception {
        mvc.perform(get("/api/v1/catalog/products").param("spec.vramGb.min", "16"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidListingParametersReturn400() throws Exception {
        mvc.perform(get("/api/v1/catalog/products").param("size", "500")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/catalog/products").param("minPrice", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/catalog/products").param("minPrice", "10").param("maxPrice", "5"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/catalog/products").param("sort", "price; drop table product"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/catalog/products").param("category", "../etc")).andExpect(status().isBadRequest());
    }

    @Test
    void sortsByPriceWithStablePaging() throws Exception {
        String body = mvc.perform(get("/api/v1/catalog/products").param("category", "servers").param("sort", "price_asc"))
                .andReturn().getResponse().getContentAsString();
        List<String> prices = JsonPath.read(body, "$.items[*].price");
        assertThat(prices).extracting(BigDecimal::new).isSortedAccordingTo(BigDecimal::compareTo);
    }

    @Test
    void paginationReportsTotals() throws Exception {
        mvc.perform(get("/api/v1/catalog/products").param("size", "10").param("page", "1"))
                .andExpect(jsonPath("$.items", hasSize(10)))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalItems", greaterThan(100)))
                .andExpect(jsonPath("$.totalPages", greaterThan(10)));
    }

    @Test
    void naturalLanguageSearchIsInterpreted() throws Exception {
        mvc.perform(get("/api/v1/search").param("q", "quiet mechanical keyboard for programming under $100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("exact"))
                .andExpect(jsonPath("$.smartAvailable").value(false))
                .andExpect(jsonPath("$.interpretation[?(@.kind == 'CATEGORY')].value").value("Keyboards"))
                .andExpect(jsonPath("$.interpretation[?(@.kind == 'BUDGET')].value").value("Under $100"))
                .andExpect(jsonPath("$.results.items[0].slug").value("keychron-v3-max"));
    }

    @Test
    void removingAnInterpretedChipWidensTheSearch() throws Exception {
        mvc.perform(get("/api/v1/search").param("q", "keyboard under $100").param("ignore", "budget"))
                .andExpect(jsonPath("$.interpretation[?(@.kind == 'BUDGET')]").isEmpty())
                .andExpect(jsonPath("$.results.totalItems").value(4));
    }

    @Test
    void everyExampleQueryReturnsResults() throws Exception {
        for (String example : SuggestService.EXAMPLES) {
            String body = mvc.perform(get("/api/v1/search").param("q", example))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            Integer total = JsonPath.read(body, "$.results.totalItems");
            assertThat(total).as(example).isPositive();
        }
    }

    @Test
    void injectionAttemptsInSearchAreHarmless() throws Exception {
        mvc.perform(get("/api/v1/search").param("q", "' OR 1=1; DROP TABLE product; -- :* & | !()"))
                .andExpect(status().isOk());
        assertThat(products.count()).isGreaterThanOrEqualTo(100);
    }

    @Test
    void suggestionsIncludeInterpretationAndProducts() throws Exception {
        mvc.perform(get("/api/v1/search/suggest").param("q", "yubikey"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products[0].name", startsWith("Yubico")));
    }

    @Test
    void compareAlignsSpecsAndFlagsDifferences() throws Exception {
        mvc.perform(get("/api/v1/catalog/compare").param("slugs", "amd-ryzen-9-9950x", "intel-core-ultra-9-285k"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products", hasSize(2)))
                .andExpect(jsonPath("$.rows[?(@.key == 'socket')].values[*]", contains("AM5", "LGA1851")))
                .andExpect(jsonPath("$.rows[?(@.key == 'socket')].differs", contains(true)));
    }

    @Test
    void compareEnforcesTwoToFourProducts() throws Exception {
        mvc.perform(get("/api/v1/catalog/compare").param("slugs", "amd-ryzen-9-9950x"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/catalog/compare").param("slugs", "a", "b", "c", "d", "e"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownProductIs404() throws Exception {
        mvc.perform(get("/api/v1/catalog/products/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void facetsExposeCategorySpecificFilters() throws Exception {
        mvc.perform(get("/api/v1/catalog/categories/gpus/facets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.specs[*].key", hasItem("vramGb")))
                .andExpect(jsonPath("$.brands[*].value", hasItem("nvidia")))
                .andExpect(jsonPath("$.price.min").value("599.99"));
    }
}
