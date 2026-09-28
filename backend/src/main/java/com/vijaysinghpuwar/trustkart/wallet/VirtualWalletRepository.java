package com.vijaysinghpuwar.trustkart.wallet;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface VirtualWalletRepository extends JpaRepository<VirtualWallet, Long> {

    Optional<VirtualWallet> findByShopperId(Long shopperId);

    /**
     * SELECT ... FOR UPDATE. Every balance change takes this row lock first, so concurrent purchases and
     * credits on one wallet run one after another and can never both spend the same funds.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from VirtualWallet w where w.shopperId = :shopperId")
    Optional<VirtualWallet> findByShopperIdForUpdate(Long shopperId);
}
