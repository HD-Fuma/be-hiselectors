package com.fuma.hiselectors.inspection.repository;

import com.fuma.hiselectors.inspection.model.ViolationEvidenceHistory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ViolationEvidenceHistoryRepository
        extends JpaRepository<ViolationEvidenceHistory, Long> {

    Optional<ViolationEvidenceHistory>
    findByViolationItemIdAndContentVersionIdAndInspectionPolicyId(
            Long violationItemId, Long contentVersionId, Long inspectionPolicyId);

    List<ViolationEvidenceHistory> findAllByViolationItemIdOrderByDetectedAtAscIdAsc(
            Long violationItemId);
}
