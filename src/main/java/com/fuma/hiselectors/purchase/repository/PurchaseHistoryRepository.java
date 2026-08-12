package com.fuma.hiselectors.purchase.repository;

import com.fuma.hiselectors.purchase.model.PurchaseHistory;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurchaseHistoryRepository extends JpaRepository<PurchaseHistory, Long> {

    Optional<PurchaseHistory> findByOrderNoAndProductId(String orderNo, Long productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p
            from PurchaseHistory p
            where p.orderNo = :orderNo and p.productId = :productId
            """)
    Optional<PurchaseHistory> findByOrderNoAndProductIdForUpdate(
            @Param("orderNo") String orderNo, @Param("productId") Long productId);
}
