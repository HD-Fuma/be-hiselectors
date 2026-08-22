package com.fuma.hiselectors.analytics.repository;

import com.fuma.hiselectors.analytics.model.ClickLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClickLogRepository extends JpaRepository<ClickLog, Long> {

    boolean existsBySelectorsId(Long selectorsId);
}
