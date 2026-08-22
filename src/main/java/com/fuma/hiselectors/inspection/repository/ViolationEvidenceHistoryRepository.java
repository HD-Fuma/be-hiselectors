package com.fuma.hiselectors.inspection.repository;

import com.fuma.hiselectors.inspection.model.ViolationEvidenceHistory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ViolationEvidenceHistoryRepository
        extends JpaRepository<ViolationEvidenceHistory, Long> {

    Optional<ViolationEvidenceHistory>
    findByViolationItemIdAndContentVersionIdAndInspectionPolicyId(
            Long violationItemId, Long contentVersionId, Long inspectionPolicyId);

    List<ViolationEvidenceHistory> findAllByViolationItemIdOrderByDetectedAtAscIdAsc(
            Long violationItemId);

    @Query("""
            select history
            from ViolationEvidenceHistory history
            where history.contentVersionId = :contentVersionId
              and history.violationItemId in :violationItemIds
            order by history.id asc
            """)
    List<ViolationEvidenceHistory> findAllByContentVersionIdAndViolationItemIdIn(
            @Param("contentVersionId") Long contentVersionId,
            @Param("violationItemIds") List<Long> violationItemIds);
}
