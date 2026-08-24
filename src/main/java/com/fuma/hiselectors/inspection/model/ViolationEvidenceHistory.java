package com.fuma.hiselectors.inspection.model;

import com.fuma.hiselectors.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "violation_evidence_history", uniqueConstraints = {
        @UniqueConstraint(
                name = "uq_violation_evidence_snapshot",
                columnNames = {
                        "violation_item_id",
                        "content_version_id",
                        "inspection_policy_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ViolationEvidenceHistory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "violation_evidence_history_id")
    private Long id;

    @Column(name = "violation_item_id", nullable = false)
    private Long violationItemId;

    @Column(name = "content_version_id", nullable = false)
    private Long contentVersionId;

    @Column(name = "inspection_policy_id", nullable = false)
    private Long inspectionPolicyId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence", nullable = false, columnDefinition = "json")
    private ViolationEvidence evidence;

    @Column(name = "detected_at", nullable = false)
    private LocalDateTime detectedAt;

    public static ViolationEvidenceHistory create(Long violationItemId, Long contentVersionId,
                                                  Long inspectionPolicyId,
                                                  ViolationEvidence evidence,
                                                  LocalDateTime detectedAt) {
        ViolationEvidenceHistory history = new ViolationEvidenceHistory();
        history.violationItemId = violationItemId;
        history.contentVersionId = contentVersionId;
        history.inspectionPolicyId = inspectionPolicyId;
        history.evidence = evidence;
        history.detectedAt = detectedAt;
        return history;
    }

    public void overwrite(ViolationEvidence evidence, LocalDateTime detectedAt) {
        this.evidence = evidence;
        this.detectedAt = detectedAt;
    }
}
