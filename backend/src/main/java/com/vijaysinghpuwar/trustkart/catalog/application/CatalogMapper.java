package com.vijaysinghpuwar.trustkart.catalog.application;

import com.vijaysinghpuwar.trustkart.catalog.application.CatalogViews.ImageDto;
import com.vijaysinghpuwar.trustkart.catalog.application.CatalogViews.ProductCardDto;
import com.vijaysinghpuwar.trustkart.catalog.application.CatalogViews.Ref;
import com.vijaysinghpuwar.trustkart.catalog.domain.SpecDataType;
import com.vijaysinghpuwar.trustkart.catalog.domain.SpecDefinition;
import com.vijaysinghpuwar.trustkart.catalog.domain.StockStatus;
import com.vijaysinghpuwar.trustkart.common.money.MoneyWire;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** Pure mapping and formatting helpers for catalog views. */
public final class CatalogMapper {

    /** Per-line cap: realistic for a shop, and bounds how much stock one request can claim. */
    public static final int MAX_QUANTITY_PER_LINE = 10;

    private CatalogMapper() {}

    public static ProductCardDto card(ProductSummary s) {
        ImageDto image = s.image() == null ? null : new ImageDto(s.image().small(), s.image().large(),
                s.image().width(), s.image().height(), s.image().alt(), s.image().match().name());
        return new ProductCardDto(s.id(), s.slug(), s.sku(), s.name(), new Ref(s.brandSlug(), s.brandName()),
                new Ref(s.categorySlug(), s.categoryName()), s.summary(), MoneyWire.format(s.price()),
                MoneyWire.format(s.compareAtPrice()), percentOff(s.price(), s.compareAtPrice()), s.stockStatus().name(),
                s.stockStatus() == StockStatus.LOW_STOCK ? s.sellableQuantity() : null,
                maxQuantity(s.stockStatus(), s.sellableQuantity()), s.featured(), image);
    }

    public static int maxQuantity(StockStatus status, int sellable) {
        if (!status.purchasable()) {
            return 0;
        }
        return status == StockStatus.BACKORDER ? MAX_QUANTITY_PER_LINE : Math.min(MAX_QUANTITY_PER_LINE, sellable);
    }

    /** Whole percent saved, rounded down so a discount is never overstated. */
    public static int percentOff(BigDecimal price, BigDecimal compareAt) {
        if (compareAt == null || compareAt.compareTo(price) <= 0) {
            return 0;
        }
        return compareAt.subtract(price).multiply(BigDecimal.valueOf(100))
                .divide(compareAt, 0, RoundingMode.DOWN).intValueExact();
    }

    /** Human-readable spec value: "16 GB", "5.7 GHz", "Yes". */
    public static String formatSpec(SpecDefinition def, Object value) {
        if (value == null) {
            return null;
        }
        if (def.getDataType() == SpecDataType.BOOLEAN) {
            return Boolean.TRUE.equals(value) ? "Yes" : "No";
        }
        if (def.getDataType() == SpecDataType.NUMBER && value instanceof Number n) {
            String number = new BigDecimal(n.toString()).stripTrailingZeros().toPlainString();
            return def.getUnit() == null ? number : number + " " + def.getUnit();
        }
        return value.toString();
    }
}
