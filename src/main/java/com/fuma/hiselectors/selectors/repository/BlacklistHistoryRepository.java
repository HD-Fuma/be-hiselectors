package com.fuma.hiselectors.selectors.repository;

import com.fuma.hiselectors.selectors.model.BlacklistHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlacklistHistoryRepository extends JpaRepository<BlacklistHistory, Long> {

    boolean existsBySelectorsIdAndStatus(Long selectorsId, String status);
}
