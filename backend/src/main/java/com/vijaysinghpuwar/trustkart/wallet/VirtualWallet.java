package com.vijaysinghpuwar.trustkart.wallet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * A shopper's virtual balance. It has no monetary value; it exists so the purchase flow can demonstrate
 * correct money handling (BigDecimal, locking, ledgering, idempotency). The balance only changes through
 * {@link WalletService}, which writes a matching ledger row for every movement.
 */
@Entity
@Table(name = "virtual_wallet")
public class VirtualWallet {

    public static final BigDecimal MAX_BALANCE = new BigDecimal("1000000000000.00");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shopper_id", nullable = false, unique = true, updatable = false)
    private Long shopperId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WalletMode mode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected VirtualWallet() {}

    public VirtualWallet(long shopperId, Instant now) {
        this.shopperId = shopperId;
        this.balance = BigDecimal.ZERO.setScale(2);
        this.mode = WalletMode.BUDGET;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Moves the balance by a signed amount. Guards the invariants the database also enforces. */
    BigDecimal move(BigDecimal delta, Instant now) {
        BigDecimal next = balance.add(delta);
        if (next.signum() < 0) {
            throw new IllegalStateException("Virtual balance cannot go negative");
        }
        if (next.compareTo(MAX_BALANCE) > 0) {
            throw new IllegalStateException("Virtual balance above maximum");
        }
        balance = next;
        updatedAt = now;
        return next;
    }

    void setMode(WalletMode mode, Instant now) {
        this.mode = mode;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getShopperId() {
        return shopperId;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public WalletMode getMode() {
        return mode;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
