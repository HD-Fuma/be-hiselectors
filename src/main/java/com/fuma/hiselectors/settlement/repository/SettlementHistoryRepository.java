package com.fuma.hiselectors.settlement.repository;

import com.fuma.hiselectors.settlement.model.SettlementHistory;
import com.fuma.hiselectors.settlement.model.SettlementStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SettlementHistoryRepository extends JpaRepository<SettlementHistory, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from SettlementHistory h where h.id = :settlementId")
    Optional<SettlementHistory> findByIdForUpdate(@Param("settlementId") Long settlementId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from SettlementHistory h where h.id in :settlementIds order by h.activityYearMonth, h.id")
    List<SettlementHistory> findAllByIdInForUpdate(
            @Param("settlementIds") Collection<Long> settlementIds);

    Optional<SettlementHistory> findBySelectorsIdAndActivityYearMonth(
            Long selectorsId, Integer activityYearMonth);

    List<SettlementHistory> findAllByStatusAndActivityYearMonthLessThanEqualOrderByActivityYearMonthAsc(
            SettlementStatus status, Integer activityYearMonth);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select h from SettlementHistory h
            where h.selectorsId = :selectorsId
              and h.status = :status
            order by h.activityYearMonth, h.id
            """)
    List<SettlementHistory> findAllBySelectorsIdAndStatusForUpdate(
            @Param("selectorsId") Long selectorsId,
            @Param("status") SettlementStatus status);

    @Query("""
            select h from SettlementHistory h
            where h.status = :status
              and ((h.scheduledPaymentYearMonth is null
                    and h.activityYearMonth <= :latestActivityYearMonth)
                   or h.scheduledPaymentYearMonth <= :paymentYearMonth)
            order by h.activityYearMonth, h.id
            """)
    List<SettlementHistory> findAllPayablePending(
            @Param("status") SettlementStatus status,
            @Param("latestActivityYearMonth") Integer latestActivityYearMonth,
            @Param("paymentYearMonth") Integer paymentYearMonth);

    @Query("""
            select h from SettlementHistory h
            where h.status = :status
              and ((h.scheduledPaymentYearMonth is null
                    and h.activityYearMonth = :activityYearMonth)
                   or h.scheduledPaymentYearMonth = :paymentYearMonth)
              and h.settlementAmount > 0
            order by h.activityYearMonth, h.id
            """)
    List<SettlementHistory> findAllUpcomingPending(
            @Param("status") SettlementStatus status,
            @Param("activityYearMonth") Integer activityYearMonth,
            @Param("paymentYearMonth") Integer paymentYearMonth);

    List<SettlementHistory> findAllByStatusIn(Collection<SettlementStatus> statuses);

    List<SettlementHistory> findAllByStatusInAndUpdatedAtLessThanEqual(
            Collection<SettlementStatus> statuses, LocalDateTime updatedAt);

    List<SettlementHistory> findAllBySelectorsIdAndStatus(
            Long selectorsId, SettlementStatus status);

    List<SettlementHistory> findAllBySelectorsIdAndStatusIn(
            Long selectorsId, Collection<SettlementStatus> statuses);

    Page<SettlementHistory> findAllBySelectorsIdOrderByActivityMonthDesc(
            Long selectorsId, Pageable pageable);

    List<SettlementHistory> findAllBySelectorsIdAndActivityMonthGreaterThanEqualAndActivityMonthLessThanOrderByActivityMonthDesc(
            Long selectorsId, LocalDateTime startMonth, LocalDateTime endMonth);

    @Query("""
            select distinct year(h.activityMonth)
            from SettlementHistory h
            where h.selectorsId = :selectorsId
            order by year(h.activityMonth) desc
            """)
    List<Integer> findAvailableYearsBySelectorsId(@Param("selectorsId") Long selectorsId);

    @Query("""
            select coalesce(sum(h.settlementAmount), 0)
            from SettlementHistory h
            where h.selectorsId = :selectorsId
              and h.status = :status
            """)
    long sumCommissionBySelectorsIdAndStatus(
            @Param("selectorsId") Long selectorsId,
            @Param("status") SettlementStatus status);

    @Query("""
            select coalesce(sum(h.totalSales), 0)
            from SettlementHistory h
            where h.selectorsId = :selectorsId
            """)
    long sumSalesBySelectorsId(@Param("selectorsId") Long selectorsId);

    @Query("""
            select h
            from SettlementHistory h
            where h.selectorsId = :selectorsId
              and h.activityYearMonth = :activityYearMonth
              and h.status in :statuses
            """)
    Optional<SettlementHistory> findBySelectorsIdAndActivityYearMonthAndStatusIn(
            @Param("selectorsId") Long selectorsId,
            @Param("activityYearMonth") Integer activityYearMonth,
            @Param("statuses") Collection<SettlementStatus> statuses);

    @Query("select min(h.activityMonth) from SettlementHistory h")
    LocalDateTime findEarliestActivityMonth();

    @Query("""
            select h
            from SettlementHistory h
            where h.activityYearMonth = :activityYearMonth
              and (:selectorsId is null or h.selectorsId = :selectorsId)
              and (:status is null or h.status = :status)
              and (:nonZeroSettlementAmount = false or h.settlementAmount <> 0)
              and exists (
                  select s.id
                  from Selectors s
                  where s.id = h.selectorsId
              )
            """)
    Page<SettlementHistory> search(
            @Param("activityYearMonth") Integer activityYearMonth,
            @Param("selectorsId") Long selectorsId,
            @Param("status") SettlementStatus status,
            @Param("nonZeroSettlementAmount") boolean nonZeroSettlementAmount,
            Pageable pageable);

    @Query("""
            select h.activityYearMonth as activityYearMonth,
                   h.status as status,
                   count(h) as settlementCount,
                   coalesce(sum(h.confirmedPurchaseCount), 0) as confirmedPurchaseCount,
                   coalesce(sum(h.totalSales), 0) as confirmedSalesAmount,
                   coalesce(sum(h.settlementAmount), 0) as settlementAmount
            from SettlementHistory h
            where h.activityYearMonth between :fromActivityYearMonth and :toActivityYearMonth
              and (:selectorsId is null or h.selectorsId = :selectorsId)
              and (:status is null or h.status = :status)
              and exists (
                  select s.id
                  from Selectors s
                  where s.id = h.selectorsId
              )
            group by h.activityYearMonth, h.status
            order by h.activityYearMonth, h.status
            """)
    List<SettlementAggregate> summarizeByMonthAndStatus(
            @Param("fromActivityYearMonth") Integer fromActivityYearMonth,
            @Param("toActivityYearMonth") Integer toActivityYearMonth,
            @Param("selectorsId") Long selectorsId,
            @Param("status") SettlementStatus status);

    interface SettlementAggregate {

        int getActivityYearMonth();

        SettlementStatus getStatus();

        long getSettlementCount();

        long getConfirmedPurchaseCount();

        long getConfirmedSalesAmount();

        long getSettlementAmount();
    }
}
