package com.vijaysinghpuwar.trustkart.auth.infra;

import com.vijaysinghpuwar.trustkart.auth.domain.AppUser;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    /** email is CITEXT, so this match is case-insensitive in the database. */
    Optional<AppUser> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<AppUser> findByPublicId(UUID publicId);
}
