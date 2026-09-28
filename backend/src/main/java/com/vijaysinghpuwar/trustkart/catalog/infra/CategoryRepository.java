package com.vijaysinghpuwar.trustkart.catalog.infra;

import com.vijaysinghpuwar.trustkart.catalog.domain.Category;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findBySlug(String slug);
}
