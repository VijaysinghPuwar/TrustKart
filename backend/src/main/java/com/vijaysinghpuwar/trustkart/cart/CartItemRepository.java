package com.vijaysinghpuwar.trustkart.cart;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CartItemRepository extends JpaRepository<CartItem, UUID> {

    List<CartItem> findByShopperIdOrderByAddedAtAsc(Long shopperId);

    /** Ownership is part of the lookup, so another shopper's item id is indistinguishable from a missing one. */
    Optional<CartItem> findByIdAndShopperId(UUID id, Long shopperId);

    List<CartItem> findByShopperIdAndProductId(Long shopperId, Long productId);

    long countByShopperId(Long shopperId);

    /**
     * Locks the shopper's row until the transaction ends, so changes that read a line before writing it (adding
     * increments the quantity) run one at a time per shopper instead of overwriting each other.
     */
    @Query(value = "SELECT id FROM shopper WHERE id = :shopperId FOR UPDATE", nativeQuery = true)
    Long lockShopper(Long shopperId);
}
