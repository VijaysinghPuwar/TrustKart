package com.vijaysinghpuwar.trustkart.shopper;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ShopperRepository extends JpaRepository<Shopper, Long> {

    Optional<Shopper> findByUserId(Long userId);

    Optional<Shopper> findByGuestTokenHash(String guestTokenHash);

    @Modifying
    @Query("delete from Shopper s where s.userId is null and s.lastSeenAt < :cutoff")
    int deleteIdleGuests(Instant cutoff);
}
