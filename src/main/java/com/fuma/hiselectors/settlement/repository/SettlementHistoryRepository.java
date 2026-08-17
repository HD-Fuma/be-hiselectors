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

    Optional<SettlementHistory> findBySelectorsIdAndActivityMonth(
            Long selectorsId, LocalDateTime activityMonth);

    List<SettlementHistory> findAllByStatusAndActivityMonthLessThanEqualOrderByActivityMonthAsc(
            SettlementStatus status, LocalDateTime activityMonth);

    List<SettlementHistory> findAllByStatusIn(Collection<SettlementStatus> statuses);

    List<SettlementHistory> findAllByStatusInAndUpdatedAtLessThanEqual(
            Collection<SettlementStatus> statuses, LocalDateTime updatedAt);

    List<SettlementHistory> findAllBySelectorsIdAndStatus(
            Long selectorsId, SettlementStatus status);

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
            select h
            from SettlementHistory h
            where h.selectorsId = :selectorsId
              and h.activityMonth = :activityMonth
              and h.status in :statuses
            """)
    Optional<SettlementHistory> findBySelectorsIdAndActivityMonthAndStatusIn(
            @Param("selectorsId") Long selectorsId,
            @Param("activityMonth") LocalDateTime activityMonth,
            @Param("statuses") Collection<SettlementStatus> statuses);

    @Query("select min(h.activityMonth) from SettlementHistory h")
    LocalDateTime findEarliestActivityMonth();

    @Query("""
            select h
            from SettlementHistory h
            where h.activityMonth = :activityMonth
              and (:selectorsId is null or h.selectorsId = :selectorsId)
              and (:status is null or h.status = :status)
            """)
    Page<SettlementHistory> search(
            @Param("activityMonth") LocalDateTime activityMonth,
            @Param("selectorsId") Long selectorsId,
            @Param("status") SettlementStatus status,
            Pageable pageable);
}
