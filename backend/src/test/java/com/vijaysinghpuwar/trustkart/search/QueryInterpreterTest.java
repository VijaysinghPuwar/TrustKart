package com.vijaysinghpuwar.trustkart.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.vijaysinghpuwar.trustkart.search.application.QueryInterpretation;
import com.vijaysinghpuwar.trustkart.search.application.QueryInterpreter;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class QueryInterpreterTest {

    @Test
    void readsCategoryBudgetAndKeywordsFromNaturalLanguage() {
        QueryInterpretation r = QueryInterpreter.interpret("quiet mechanical keyboard for programming under $100");

        assertThat(r.categorySlug()).isEqualTo("keyboards");
        assertThat(r.maxPrice()).isEqualByComparingTo("100");
        assertThat(r.minPrice()).isNull();
        assertThat(r.terms()).containsExactly("quiet", "mechanical", "programming");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "laptop under $3,500            | 3500",
        "monitor below 400              | 400",
        "ssd up to $250.50              | 250.50",
        "server under 20k               | 20000",
        "mouse $80 or less              | 80",
        "keyboard max $150              | 150",
    })
    void readsMaximumBudgets(String query, String expectedMax) {
        assertThat(QueryInterpreter.interpret(query).maxPrice()).isEqualByComparingTo(expectedMax);
    }

    @Test
    void readsPriceRangesAndSwapsReversedBounds() {
        QueryInterpretation r = QueryInterpreter.interpret("gpu between $1,000 and $500");
        assertThat(r.minPrice()).isEqualByComparingTo("500");
        assertThat(r.maxPrice()).isEqualByComparingTo("1000");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "GPU with at least 24 GB for local AI",
        "monitor over 144 Hz",
        "psu above 1000 W",
        "cpu with at least 16 cores",
        "switch with more than 8 ports",
    })
    void numbersWithUnitsAreNotPrices(String query) {
        QueryInterpretation r = QueryInterpreter.interpret(query);
        assertThat(r.minPrice()).isNull();
        assertThat(r.maxPrice()).isNull();
    }

    @Test
    void prefersTheLongestCategoryPhrase() {
        assertThat(QueryInterpreter.interpret("gaming laptop with oled").categorySlug()).isEqualTo("gaming-laptops");
        assertThat(QueryInterpreter.interpret("nvidia graphics card").categorySlug()).isEqualTo("gpus");
    }

    @Test
    void termsAreSanitisedToAlphanumericTokens() {
        QueryInterpretation r = QueryInterpreter.interpret("rtx' OR 1=1; DROP TABLE product;-- :* & | !");
        assertThat(r.terms()).allMatch(t -> t.matches("[a-z0-9]+"));
        assertThat(r.terms()).contains("rtx", "drop", "table", "product");
    }

    @Test
    void capsQueryLengthAndTermCount() {
        String longQuery = "word ".repeat(100) + "a b c d e f g h i j k l m n o p q r s t u v w x y z";
        QueryInterpretation r = QueryInterpreter.interpret(longQuery);
        assertThat(r.original()).hasSizeLessThanOrEqualTo(QueryInterpreter.MAX_QUERY_LENGTH);
        assertThat(r.terms()).hasSizeLessThanOrEqualTo(8);
    }

    @Test
    void blankQueryIsEmpty() {
        assertThat(QueryInterpreter.interpret("   ").isEmpty()).isTrue();
        assertThat(QueryInterpreter.interpret(null).isEmpty()).isTrue();
    }

    @Test
    void amountsAreExactDecimals() {
        assertThat(QueryInterpreter.interpret("ssd under $99.99").maxPrice()).isEqualTo(new BigDecimal("99.99"));
    }
}
