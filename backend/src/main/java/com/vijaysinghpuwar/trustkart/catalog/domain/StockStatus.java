package com.vijaysinghpuwar.trustkart.catalog.domain;

public enum StockStatus {
    IN_STOCK,
    LOW_STOCK,
    OUT_OF_STOCK,
    BACKORDER,
    DISCONTINUED;

    public boolean purchasable() {
        return this == IN_STOCK || this == LOW_STOCK || this == BACKORDER;
    }

    /**
     * The single rule for stock status, shared by entity code and SQL-projected rows so they can never disagree.
     */
    public static StockStatus derive(ProductStatus productStatus, int available, int reserved, int lowStockThreshold,
            boolean backorderAllowed) {
        if (productStatus == ProductStatus.DISCONTINUED) {
            return DISCONTINUED;
        }
        int sellable = available - reserved;
        if (sellable <= 0) {
            return backorderAllowed ? BACKORDER : OUT_OF_STOCK;
        }
        return sellable <= lowStockThreshold ? LOW_STOCK : IN_STOCK;
    }
}
