package com.fuma.hiselectors.application.repository;

import com.fuma.hiselectors.application.model.ApplicationReport;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationReportRepository extends JpaRepository<ApplicationReport, Long> {

    /** 지원자당 1건 upsert 전제. 가장 최근 저장분을 돌려준다. */
    Optional<ApplicationReport> findFirstByApplicationIdOrderByCreatedAtDesc(Long applicationId);

    /** 재취합 시 기존 리포트를 지우고 다시 저장하기 위한 삭제. */
    void deleteByApplicationId(Long applicationId);
}
