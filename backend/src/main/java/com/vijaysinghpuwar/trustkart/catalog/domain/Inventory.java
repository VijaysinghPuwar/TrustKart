package com.vijaysinghpuwar.trustkart.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "inventory")
public class Inventory {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(nullable = false)
    private int available;

    @Column(nullable = false)
    private int reserved;

    @Column(name = "low_stock_threshold", nullable = false)
    private int lowStockThreshold;

    @Column(name = "backorder_allowed", nullable = false)
    private boolean backorderAllowed;

    /** Demo-only: the level the restock job tops this item back up to. 0 keeps a product intentionally sold out. */
    @Column(name = "restock_target", nullable = false)
    private int restockTarget;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    private long version;

    protected Inventory() {}

    public Inventory(Product product, int available, int lowStockThreshold, boolean backorderAllowed, int restockTarget) {
        if (available < 0 || lowStockThreshold < 0 || restockTarget < 0) {
            throw new IllegalArgumentException("Inventory quantities cannot be negative");
        }
        this.product = product;
        this.available = available;
        this.lowStockThreshold = lowStockThreshold;
        this.backorderAllowed = backorderAllowed;
        this.restockTarget = restockTarget;
    }

    public StockStatus stockStatus(ProductStatus productStatus) {
        return StockStatus.derive(productStatus, available, reserved, lowStockThreshold, backorderAllowed);
    }

    public int getAvailable() {
        return available;
    }

    public int getReserved() {
        return reserved;
    }

    public int getLowStockThreshold() {
        return lowStockThreshold;
    }

    public boolean isBackorderAllowed() {
        return backorderAllowed;
    }
}
