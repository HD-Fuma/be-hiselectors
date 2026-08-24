package com.fuma.hiselectors.application.repository;

import com.fuma.hiselectors.application.model.ApplicationReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface ApplicationReportRepository extends JpaRepository<ApplicationReport, Long> {

    /** 지원자당 1건 upsert 전제. 가장 최근 저장분을 돌려준다. */
    Optional<ApplicationReport> findFirstByApplicationIdOrderByCreatedAtDesc(Long applicationId);

    /** 지원자당 1건(재평가 시 교체). 벌크 DELETE 한 방. */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM ApplicationReport r WHERE r.applicationId = :applicationId")
    void deleteByApplicationId(@Param("applicationId") Long applicationId);
}
