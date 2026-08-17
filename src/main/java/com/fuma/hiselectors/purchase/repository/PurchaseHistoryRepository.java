package com.fuma.hiselectors.purchase.repository;

import com.fuma.hiselectors.purchase.model.PurchaseHistory;
import com.fuma.hiselectors.purchase.model.PurchaseStatus;
import com.fuma.hiselectors.settlement.dto.SettlementPurchaseHistoryResponse;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PurchaseHistoryRepository extends JpaRepository<PurchaseHistory, Long> {

    @Query("""
            select new com.fuma.hiselectors.settlement.dto.SettlementPurchaseHistoryResponse(
                    p.id,
                    s.id,
                    s.selectorsCode,
                    s.selectorsNickname,
                    u.id,
                    u.hiId,
                    p.orderNo,
                    product.productCode,
                    p.quantity,
                    p.paidAmount,
                    p.purchasedAt,
                    p.confirmedAt,
                    p.status)
            from PurchaseHistory p
            join Selectors s on s.id = p.selectorsId
            join User u on u.id = p.userId
            join Product product on product.id = p.productId
            where p.selectorsId is not null
              and (:selectorsId is null or p.selectorsId = :selectorsId)
              and (:startInclusive is null or p.purchasedAt >= :startInclusive)
              and (:endExclusive is null or p.purchasedAt < :endExclusive)
            """)
    Page<SettlementPurchaseHistoryResponse> searchForSettlementAdmin(
            @Param("selectorsId") Long selectorsId,
            @Param("startInclusive") LocalDateTime startInclusive,
            @Param("endExclusive") LocalDateTime endExclusive,
            Pageable pageable);

    Optional<PurchaseHistory> findByOrderNoAndProductId(String orderNo, Long productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p
            from PurchaseHistory p
            where p.orderNo = :orderNo and p.productId = :productId
            """)
    Optional<PurchaseHistory> findByOrderNoAndProductIdForUpdate(
            @Param("orderNo") String orderNo, @Param("productId") Long productId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update PurchaseHistory p
            set p.status = :confirmedStatus, p.confirmedAt = :confirmedAt
            where p.status = :purchasedStatus
              and p.purchasedAt < :cutoffExclusive
            """)
    int confirmExpiredPurchases(
            @Param("purchasedStatus") PurchaseStatus purchasedStatus,
            @Param("confirmedStatus") PurchaseStatus confirmedStatus,
            @Param("cutoffExclusive") LocalDateTime cutoffExclusive,
            @Param("confirmedAt") LocalDateTime confirmedAt);

    @Query("""
            select coalesce(sum(p.paidAmount), 0) as totalSales,
                   count(p) as confirmedPurchaseCount
            from PurchaseHistory p
            where p.selectorsId = :selectorsId
              and p.status = :status
              and p.purchasedAt >= :startInclusive
              and p.purchasedAt < :endExclusive
            """)
    PurchaseSettlementSummary summarizeConfirmedPurchasesForActivityMonth(
            @Param("selectorsId") Long selectorsId,
            @Param("status") PurchaseStatus status,
            @Param("startInclusive") LocalDateTime startInclusive,
            @Param("endExclusive") LocalDateTime endExclusive);

    @Query("""
            select count(distinct p.orderNo)
            from PurchaseHistory p
            where p.selectorsId = :selectorsId
              and p.status in :statuses
            """)
    long countDistinctOrdersBySelectorsIdAndStatusIn(
            @Param("selectorsId") Long selectorsId,
            @Param("statuses") Collection<PurchaseStatus> statuses);

    @Query("""
            select count(distinct p.orderNo)
            from PurchaseHistory p
            where p.selectorsId = :selectorsId
              and p.status in :statuses
              and p.purchasedAt >= :startInclusive
              and p.purchasedAt < :endExclusive
            """)
    long countDistinctOrdersBySelectorsIdAndStatusInAndPurchasedAtBetween(
            @Param("selectorsId") Long selectorsId,
            @Param("statuses") Collection<PurchaseStatus> statuses,
            @Param("startInclusive") LocalDateTime startInclusive,
            @Param("endExclusive") LocalDateTime endExclusive);

    @Query("""
            select min(p.purchasedAt)
            from PurchaseHistory p
            where p.status = :status
            """)
    LocalDateTime findEarliestPurchasedAtByStatus(@Param("status") PurchaseStatus status);
}
