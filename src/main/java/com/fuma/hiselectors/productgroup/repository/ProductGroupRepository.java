package com.fuma.hiselectors.productgroup.repository;

import com.fuma.hiselectors.productgroup.model.ProductGroup;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductGroupRepository extends JpaRepository<ProductGroup, Long> {
    List<ProductGroup> findAllBySelectorsIdAndDeletedFalseOrderByGroupNoAsc(Long selectorsId);
    Optional<ProductGroup> findByIdAndSelectorsIdAndDeletedFalse(Long id, Long selectorsId);
    Optional<ProductGroup> findFirstBySelectorsIdOrderByGroupNoDesc(Long selectorsId);
}
