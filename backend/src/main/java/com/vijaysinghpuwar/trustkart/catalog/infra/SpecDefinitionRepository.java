package com.vijaysinghpuwar.trustkart.catalog.infra;

import com.vijaysinghpuwar.trustkart.catalog.domain.SpecDefinition;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpecDefinitionRepository extends JpaRepository<SpecDefinition, Long> {

    List<SpecDefinition> findByCategoryIdInOrderBySortOrderAsc(Collection<Long> categoryIds);

    boolean existsByCategoryIdAndKey(Long categoryId, String key);
}
