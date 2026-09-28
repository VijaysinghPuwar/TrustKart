package com.vijaysinghpuwar.trustkart.cart;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.vijaysinghpuwar.trustkart.catalog.application.CatalogViews.ProductCardDto;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @param subtotal  server-computed sum over active, purchasable lines (decimal string)
 * @param itemCount total units in active lines
 */
public record CartView(List<Line> items, List<Line> savedForLater, String subtotal, int itemCount) {

    /**
     * {@code options}: the chosen configuration (empty for products without options); {@code optionsLabel}: it as
     * shown to shoppers, e.g. "512 GB · Silver"; {@code optionImage}: that configuration's own photo (e.g. the chosen
     * colour), when it has one. {@code unitPrice} is the configuration's price.
     * {@code issue}: null, OUT_OF_STOCK, DISCONTINUED, QUANTITY_EXCEEDS_STOCK or OPTION_UNAVAILABLE.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Line(UUID id, ProductCardDto product, Map<String, String> options, String optionsLabel, String optionImage,
            int quantity,
            String unitPrice, String lineTotal, String issue) {}

    public static CartView empty() {
        return new CartView(List.of(), List.of(), "0.00", 0);
    }
}
