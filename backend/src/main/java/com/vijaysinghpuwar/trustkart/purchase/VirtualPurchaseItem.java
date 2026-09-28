package com.vijaysinghpuwar.trustkart.purchase;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** Snapshot of a line as purchased; later catalog edits don't rewrite history. */
@Entity
@Table(name = "virtual_purchase_item")
public class VirtualPurchaseItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_id")
    private VirtualPurchase purchase;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_slug", nullable = false)
    private String productSlug;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "category_name", nullable = false)
    private String categoryName;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "line_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal lineTotal;

    @Column(name = "stock_committed", nullable = false)
    private int stockCommitted;

    protected VirtualPurchaseItem() {}

    VirtualPurchaseItem(long productId, String productSlug, String productName, String categoryName, String imageUrl,
            BigDecimal unitPrice, int quantity, int stockCommitted) {
        this.productId = productId;
        this.productSlug = productSlug;
        this.productName = productName;
        this.categoryName = categoryName;
        this.imageUrl = imageUrl;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
        this.stockCommitted = stockCommitted;
    }

    void attachTo(VirtualPurchase purchase) {
        this.purchase = purchase;
    }

    public Long getProductId() {
        return productId;
    }

    public String getProductSlug() {
        return productSlug;
    }

    public String getProductName() {
        return productName;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }

    public int getStockCommitted() {
        return stockCommitted;
    }
}
