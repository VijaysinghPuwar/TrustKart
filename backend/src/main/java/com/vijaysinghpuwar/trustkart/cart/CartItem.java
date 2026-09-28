package com.vijaysinghpuwar.trustkart.cart;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** A product in a shopper's cart. Prices are never stored here; they are read fresh on every view and at checkout. */
@Entity
@Table(name = "cart_item")
public class CartItem {

    @Id
    private UUID id;

    @Column(name = "shopper_id", nullable = false, updatable = false)
    private Long shopperId;

    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;

    @Column(nullable = false)
    private int quantity;

    /** Canonical option selection (every group, defaults filled in); empty for products without options. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, String> options = new TreeMap<>();

    @Column(name = "saved_for_later", nullable = false)
    private boolean savedForLater;

    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CartItem() {}

    public CartItem(long shopperId, long productId, Map<String, String> options, int quantity, Instant now) {
        this.id = UUID.randomUUID();
        this.shopperId = shopperId;
        this.productId = productId;
        this.options = new TreeMap<>(options);
        this.quantity = quantity;
        this.addedAt = now;
        this.updatedAt = now;
    }

    public void setQuantity(int quantity, Instant now) {
        this.quantity = quantity;
        this.updatedAt = now;
    }

    public void setSavedForLater(boolean saved, Instant now) {
        this.savedForLater = saved;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public Long getShopperId() {
        return shopperId;
    }

    public Long getProductId() {
        return productId;
    }

    public Map<String, String> getOptions() {
        return options == null ? Map.of() : options;
    }

    /** Same product and same configuration. */
    public boolean sameSelection(long productId, Map<String, String> selection) {
        return this.productId == productId && getOptions().equals(selection);
    }

    public int getQuantity() {
        return quantity;
    }

    public boolean isSavedForLater() {
        return savedForLater;
    }

    public Instant getAddedAt() {
        return addedAt;
    }
}
