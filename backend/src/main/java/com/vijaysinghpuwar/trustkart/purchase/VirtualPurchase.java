package com.vijaysinghpuwar.trustkart.purchase;

import com.vijaysinghpuwar.trustkart.wallet.WalletMode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "virtual_purchase")
public class VirtualPurchase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    private UUID publicId;

    @Column(name = "order_number", nullable = false, unique = true, updatable = false)
    private String orderNumber;

    @Column(name = "shopper_id", nullable = false)
    private Long shopperId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PurchaseStatus status;

    @Column(name = "item_count", nullable = false)
    private int itemCount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal shipping;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    @Column(name = "wallet_mode", nullable = false)
    private WalletMode walletMode;

    @Column(name = "balance_before", precision = 19, scale = 2)
    private BigDecimal balanceBefore;

    @Column(name = "balance_after", precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_preset", nullable = false)
    private DeliveryPreset deliveryPreset;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "simulation_address", columnDefinition = "jsonb")
    private SimulationAddress simulationAddress;

    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, updatable = false)
    private String requestHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    @OneToMany(mappedBy = "purchase", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<VirtualPurchaseItem> items = new ArrayList<>();

    protected VirtualPurchase() {}

    VirtualPurchase(String orderNumber, long shopperId, List<VirtualPurchaseItem> items, WalletMode walletMode,
            DeliveryPreset preset, SimulationAddress address, String idempotencyKey, String requestHash, Instant now) {
        this.publicId = UUID.randomUUID();
        this.orderNumber = orderNumber;
        this.shopperId = shopperId;
        this.status = PurchaseStatus.COMPLETED;
        this.walletMode = walletMode;
        this.deliveryPreset = preset;
        this.simulationAddress = address;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.createdAt = now;
        this.shipping = BigDecimal.ZERO.setScale(2);
        this.subtotal = BigDecimal.ZERO.setScale(2);
        items.forEach(i -> {
            i.attachTo(this);
            this.items.add(i);
            this.subtotal = this.subtotal.add(i.getLineTotal());
            this.itemCount += i.getQuantity();
        });
        this.total = subtotal.add(shipping);
    }

    void recordBalances(BigDecimal before, BigDecimal after) {
        this.balanceBefore = before;
        this.balanceAfter = after;
    }

    void markRefunded(Instant now) {
        this.status = PurchaseStatus.REFUNDED;
        this.refundedAt = now;
    }

    public Long getId() {
        return id;
    }

    public UUID getPublicId() {
        return publicId;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public Long getShopperId() {
        return shopperId;
    }

    public PurchaseStatus getStatus() {
        return status;
    }

    public int getItemCount() {
        return itemCount;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getShipping() {
        return shipping;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public WalletMode getWalletMode() {
        return walletMode;
    }

    public BigDecimal getBalanceBefore() {
        return balanceBefore;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public DeliveryPreset getDeliveryPreset() {
        return deliveryPreset;
    }

    public SimulationAddress getSimulationAddress() {
        return simulationAddress;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getRefundedAt() {
        return refundedAt;
    }

    public List<VirtualPurchaseItem> getItems() {
        return List.copyOf(items);
    }
}
