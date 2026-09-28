package com.vijaysinghpuwar.trustkart.cart;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.vijaysinghpuwar.trustkart.catalog.application.CatalogViews.ProductCardDto;
import java.util.List;
import java.util.UUID;

/**
 * @param subtotal  server-computed sum over active, purchasable lines (decimal string)
 * @param itemCount total units in active lines
 */
public record CartView(List<Line> items, List<Line> savedForLater, String subtotal, int itemCount) {

    /** {@code issue}: null, OUT_OF_STOCK, DISCONTINUED or QUANTITY_EXCEEDS_STOCK. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Line(UUID id, ProductCardDto product, int quantity, String unitPrice, String lineTotal, String issue) {}

    public static CartView empty() {
        return new CartView(List.of(), List.of(), "0.00", 0);
    }
}
