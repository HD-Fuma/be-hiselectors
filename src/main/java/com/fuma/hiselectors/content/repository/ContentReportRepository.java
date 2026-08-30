package com.fuma.hiselectors.content.repository;

import com.fuma.hiselectors.content.model.ContentReport;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentReportRepository extends JpaRepository<ContentReport, Long> {

    Optional<ContentReport> findFirstByContentVersionIdOrderByIdDesc(Long contentVersionId);

    Optional<ContentReport> findByContentVersionIdAndInspectionPolicyId(
            Long contentVersionId, Long inspectionPolicyId);
}
