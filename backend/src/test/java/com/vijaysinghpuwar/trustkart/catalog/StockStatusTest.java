package com.vijaysinghpuwar.trustkart.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.vijaysinghpuwar.trustkart.catalog.application.CatalogMapper;
import com.vijaysinghpuwar.trustkart.catalog.domain.ProductStatus;
import com.vijaysinghpuwar.trustkart.catalog.domain.StockStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class StockStatusTest {

    @Test
    void derivesStatusFromSellableQuantity() {
        assertThat(StockStatus.derive(ProductStatus.ACTIVE, 50, 0, 5, false)).isEqualTo(StockStatus.IN_STOCK);
        assertThat(StockStatus.derive(ProductStatus.ACTIVE, 5, 0, 5, false)).isEqualTo(StockStatus.LOW_STOCK);
        assertThat(StockStatus.derive(ProductStatus.ACTIVE, 8, 4, 5, false)).isEqualTo(StockStatus.LOW_STOCK);
        assertThat(StockStatus.derive(ProductStatus.ACTIVE, 0, 0, 5, false)).isEqualTo(StockStatus.OUT_OF_STOCK);
        assertThat(StockStatus.derive(ProductStatus.ACTIVE, 3, 3, 5, false)).isEqualTo(StockStatus.OUT_OF_STOCK);
        assertThat(StockStatus.derive(ProductStatus.ACTIVE, 0, 0, 5, true)).isEqualTo(StockStatus.BACKORDER);
    }

    @Test
    void discontinuedWinsOverStock() {
        assertThat(StockStatus.derive(ProductStatus.DISCONTINUED, 100, 0, 5, true)).isEqualTo(StockStatus.DISCONTINUED);
        assertThat(StockStatus.DISCONTINUED.purchasable()).isFalse();
    }

    @Test
    void maxQuantityIsCappedAndZeroWhenUnavailable() {
        assertThat(CatalogMapper.maxQuantity(StockStatus.IN_STOCK, 400)).isEqualTo(CatalogMapper.MAX_QUANTITY_PER_LINE);
        assertThat(CatalogMapper.maxQuantity(StockStatus.LOW_STOCK, 3)).isEqualTo(3);
        assertThat(CatalogMapper.maxQuantity(StockStatus.OUT_OF_STOCK, 0)).isZero();
        assertThat(CatalogMapper.maxQuantity(StockStatus.BACKORDER, 0)).isEqualTo(CatalogMapper.MAX_QUANTITY_PER_LINE);
    }

    @Test
    void percentOffRoundsDownAndIgnoresNonDiscounts() {
        assertThat(CatalogMapper.percentOff(new BigDecimal("1299.99"), new BigDecimal("1599.99"))).isEqualTo(18);
        assertThat(CatalogMapper.percentOff(new BigDecimal("100.00"), null)).isZero();
        assertThat(CatalogMapper.percentOff(new BigDecimal("100.00"), new BigDecimal("100.00"))).isZero();
    }
}
