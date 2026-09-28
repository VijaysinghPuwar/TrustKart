package com.vijaysinghpuwar.trustkart.purchase;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VirtualPurchaseRepository extends JpaRepository<VirtualPurchase, Long> {

    @EntityGraph(attributePaths = "items")
    Optional<VirtualPurchase> findByShopperIdAndIdempotencyKey(Long shopperId, String idempotencyKey);

    /** Owner-scoped: another shopper's purchase id yields empty, which the API reports as 404. */
    @EntityGraph(attributePaths = "items")
    Optional<VirtualPurchase> findByPublicIdAndShopperId(UUID publicId, Long shopperId);

    Page<VirtualPurchase> findByShopperIdOrderByCreatedAtDesc(Long shopperId, Pageable pageable);
}
