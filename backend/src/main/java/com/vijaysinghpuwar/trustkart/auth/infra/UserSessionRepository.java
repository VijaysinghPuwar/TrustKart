package com.vijaysinghpuwar.trustkart.auth.infra;

import com.vijaysinghpuwar.trustkart.auth.domain.UserSession;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    /** Row lock so concurrent refreshes of one session are serialised and rotation can't race. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from UserSession s where s.id = :id")
    Optional<UserSession> findForUpdate(UUID id);

    Optional<UserSession> findByIdAndUserId(UUID id, Long userId);

    @Query("select s from UserSession s where s.userId = :userId and s.revokedAt is null and s.expiresAt > :now order by s.lastUsedAt desc")
    List<UserSession> findActive(Long userId, Instant now);
}
