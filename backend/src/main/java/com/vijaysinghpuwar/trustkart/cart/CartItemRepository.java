package com.vijaysinghpuwar.trustkart.cart;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartItemRepository extends JpaRepository<CartItem, UUID> {

    List<CartItem> findByShopperIdOrderByAddedAtAsc(Long shopperId);

    /** Ownership is part of the lookup, so another shopper's item id is indistinguishable from a missing one. */
    Optional<CartItem> findByIdAndShopperId(UUID id, Long shopperId);

    List<CartItem> findByShopperIdAndProductId(Long shopperId, Long productId);

    long countByShopperId(Long shopperId);
}
