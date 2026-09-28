package com.vijaysinghpuwar.trustkart.shopper;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Owner of carts, wallets, purchases and collections: either a guest (cookie) or a signed-in user. */
@Entity
@Table(name = "shopper")
public class Shopper {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    private UUID publicId;

    @Column(name = "guest_token_hash", unique = true)
    private String guestTokenHash;

    @Column(name = "user_id", unique = true)
    private Long userId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    protected Shopper() {}

    private Shopper(String guestTokenHash, Long userId, Instant now) {
        this.publicId = UUID.randomUUID();
        this.guestTokenHash = guestTokenHash;
        this.userId = userId;
        this.createdAt = now;
        this.lastSeenAt = now;
    }

    public static Shopper guest(String guestTokenHash, Instant now) {
        return new Shopper(guestTokenHash, null, now);
    }

    public static Shopper forUser(long userId, Instant now) {
        return new Shopper(null, userId, now);
    }

    /** A guest signs in or registers and has no shopper yet: their guest shopper becomes the account's. */
    public void attachToUser(long userId) {
        this.userId = userId;
        this.guestTokenHash = null;
    }

    public void seen(Instant now) {
        this.lastSeenAt = now;
    }

    public boolean isGuest() {
        return userId == null;
    }

    public Long getId() {
        return id;
    }

    public UUID getPublicId() {
        return publicId;
    }

    public Long getUserId() {
        return userId;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }
}
