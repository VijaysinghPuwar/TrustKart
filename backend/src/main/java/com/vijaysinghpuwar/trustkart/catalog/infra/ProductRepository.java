package com.vijaysinghpuwar.trustkart.catalog.infra;

import com.vijaysinghpuwar.trustkart.catalog.domain.Product;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsBySku(String sku);

    @EntityGraph(attributePaths = {"brand", "category", "images", "inventory"})
    Optional<Product> findWithDetailsBySlug(String slug);

    @EntityGraph(attributePaths = {"brand", "category", "images", "inventory"})
    List<Product> findWithDetailsBySlugIn(Collection<String> slugs);

    @EntityGraph(attributePaths = {"brand", "category", "inventory"})
    List<Product> findWithDetailsByIdIn(Collection<Long> ids);
}
