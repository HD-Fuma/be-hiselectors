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

    Optional<SettlementHistory> findBySelectorsIdAndSettlementMonth(
            Long selectorsId, LocalDateTime settlementMonth);

    List<SettlementHistory> findAllBySettlementMonthAndStatus(
            LocalDateTime settlementMonth, SettlementStatus status);

    Page<SettlementHistory> findAllBySelectorsIdOrderBySettlementMonthDesc(
            Long selectorsId, Pageable pageable);

    List<SettlementHistory> findAllBySelectorsIdAndSettlementMonthGreaterThanEqualAndSettlementMonthLessThanOrderBySettlementMonthDesc(
            Long selectorsId, LocalDateTime startMonth, LocalDateTime endMonth);

    @Query("""
            select distinct year(h.settlementMonth)
            from SettlementHistory h
            where h.selectorsId = :selectorsId
            order by year(h.settlementMonth) desc
            """)
    List<Integer> findAvailableYearsBySelectorsId(@Param("selectorsId") Long selectorsId);

    @Query("""
            select coalesce(sum(h.commission), 0)
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
              and h.settlementMonth = :settlementMonth
              and h.status in :statuses
            """)
    Optional<SettlementHistory> findBySelectorsIdAndSettlementMonthAndStatusIn(
            @Param("selectorsId") Long selectorsId,
            @Param("settlementMonth") LocalDateTime settlementMonth,
            @Param("statuses") Collection<SettlementStatus> statuses);

    @Query("select min(h.settlementMonth) from SettlementHistory h")
    LocalDateTime findEarliestSettlementMonth();

    @Query("""
            select h
            from SettlementHistory h
            where h.settlementMonth = :settlementMonth
              and (:selectorsId is null or h.selectorsId = :selectorsId)
              and (:status is null or h.status = :status)
            """)
    Page<SettlementHistory> search(
            @Param("settlementMonth") LocalDateTime settlementMonth,
            @Param("selectorsId") Long selectorsId,
            @Param("status") SettlementStatus status,
            Pageable pageable);
}
