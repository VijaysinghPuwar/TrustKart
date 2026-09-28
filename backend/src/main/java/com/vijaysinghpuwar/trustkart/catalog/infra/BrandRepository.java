package com.vijaysinghpuwar.trustkart.catalog.infra;

import com.vijaysinghpuwar.trustkart.catalog.domain.Brand;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BrandRepository extends JpaRepository<Brand, Long> {

    Optional<Brand> findByName(String name);

    Optional<Brand> findBySlug(String slug);
}
