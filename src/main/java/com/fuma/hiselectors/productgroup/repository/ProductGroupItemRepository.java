package com.fuma.hiselectors.productgroup.repository;

import com.fuma.hiselectors.productgroup.model.ProductGroupItem;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductGroupItemRepository extends JpaRepository<ProductGroupItem, Long> {
    @EntityGraph(attributePaths = "product")
    List<ProductGroupItem> findAllByGroupIdInAndDeletedFalseOrderByGroupIdAscDisplayOrderAsc(
            List<Long> groupIds);

    @EntityGraph(attributePaths = "product")
    List<ProductGroupItem> findAllByGroupIdAndDeletedFalseOrderByDisplayOrderAsc(Long groupId);

    @EntityGraph(attributePaths = "product")
    List<ProductGroupItem> findAllByGroupIdOrderByDisplayOrderAsc(Long groupId);
}
