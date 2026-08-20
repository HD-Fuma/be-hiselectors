package com.fuma.hiselectors.productgroup.repository;

import com.fuma.hiselectors.productgroup.model.ProductGroupItem;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductGroupItemRepository extends JpaRepository<ProductGroupItem, Long> {
    @EntityGraph(attributePaths = "product")
    List<ProductGroupItem> findAllByGroupIdInAndDeletedFalseOrderByGroupIdAscDisplayOrderAsc(
            List<Long> groupIds);

    @EntityGraph(attributePaths = "product")
    List<ProductGroupItem> findAllByGroupIdAndDeletedFalseOrderByDisplayOrderAsc(Long groupId);

    @EntityGraph(attributePaths = "product")
    List<ProductGroupItem> findAllByGroupIdOrderByDisplayOrderAsc(Long groupId);

    @Query("""
            select (count(item) > 0) from ProductGroupItem item
            where item.group.selectorsId = :selectorsId
              and item.group.deleted = false
              and item.deleted = false
              and item.product.id = :productId
            """)
    boolean existsActiveProductForSelectors(@Param("selectorsId") Long selectorsId,
                                            @Param("productId") Long productId);
}
