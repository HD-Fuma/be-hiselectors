package com.fuma.hiselectors.penalty.repository;

import com.fuma.hiselectors.penalty.model.PenaltyHistory;
import com.fuma.hiselectors.penalty.model.PenaltyStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface PenaltyHistoryRepository extends JpaRepository<PenaltyHistory, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PenaltyHistory> findFirstBySelectorsIdAndStatusOrderByIdDesc(
            Long selectorsId, PenaltyStatus status);
}
