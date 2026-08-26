package com.fuma.hiselectors.inspection.model;

import com.fuma.hiselectors.common.BaseTimeEntity;
import com.fuma.hiselectors.content.model.ContentVersion;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "violation_item", uniqueConstraints = {
        @UniqueConstraint(
                name = "uq_violation_item_content_type",
                columnNames = {"content_id", "violation_type_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ViolationItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "violation_item_id")
    private Long id;

    @Column(name = "content_id", nullable = false)
    private Long contentId;

    @Column(name = "content_version_id", nullable = false)
    private Long contentVersionId;

    @Column(name = "last_detected_content_version_id", nullable = false)
    private Long lastDetectedContentVersionId;

    @Column(name = "resolved_content_version_id")
    private Long resolvedContentVersionId;

    @Column(name = "content_media_id")
    private Long contentMediaId;

    @Column(name = "violation_type_id", nullable = false)
    private Long violationTypeId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence", nullable = false, columnDefinition = "json")
    private ViolationEvidence evidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ViolationStatus status;

    public static ViolationItem pending(ContentVersion version, Long violationTypeId,
                                        ViolationEvidence evidence) {
        ViolationItem item = new ViolationItem();
        item.contentId = version.getContentId();
        item.contentVersionId = version.getId();
        item.lastDetectedContentVersionId = version.getId();
        item.violationTypeId = violationTypeId;
        item.evidence = evidence;
        item.contentMediaId = representativeMediaId(evidence.locations());
        item.status = ViolationStatus.PENDING;
        return item;
    }

    public void detectAgain(ContentVersion version, ViolationEvidence evidence) {
        requireOpen();
        applyDetection(version, evidence);
    }

    public void reopen(ContentVersion version, ViolationEvidence evidence) {
        if (isOpen()) {
            throw new BusinessException(ErrorCode.INVALID_VIOLATION_STATUS_TRANSITION);
        }
        status = ViolationStatus.PENDING;
        resolvedContentVersionId = null;
        applyDetection(version, evidence);
    }

    public void resolve(ContentVersion version) {
        requireOpen();
        status = ViolationStatus.RESOLVED;
        resolvedContentVersionId = version.getId();
    }

    public void confirm() {
        if (status != ViolationStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_VIOLATION_STATUS_TRANSITION);
        }
        status = ViolationStatus.VIOLATION_CONFIRMED;
    }

    public void requestEdit() {
        if (status != ViolationStatus.VIOLATION_CONFIRMED) {
            throw new BusinessException(ErrorCode.INVALID_VIOLATION_STATUS_TRANSITION);
        }
        status = ViolationStatus.EDIT_REQUESTED;
    }

    public void dismiss() {
        if (status != ViolationStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_VIOLATION_STATUS_TRANSITION);
        }
        status = ViolationStatus.DISMISSED;
    }

    public boolean resetInspectionDecision() {
        if (status != ViolationStatus.VIOLATION_CONFIRMED
                && status != ViolationStatus.DISMISSED
                && status != ViolationStatus.EDIT_REQUESTED) {
            return false;
        }
        status = ViolationStatus.PENDING;
        return true;
    }

    public boolean isOpen() {
        return status == ViolationStatus.PENDING
                || status == ViolationStatus.VIOLATION_CONFIRMED
                || status == ViolationStatus.EDIT_REQUESTED;
    }

    private void applyDetection(ContentVersion version, ViolationEvidence evidence) {
        lastDetectedContentVersionId = version.getId();
        this.evidence = evidence;
        contentMediaId = representativeMediaId(evidence.locations());
    }

    private void requireOpen() {
        if (!isOpen()) {
            throw new BusinessException(ErrorCode.INVALID_VIOLATION_STATUS_TRANSITION);
        }
    }

    private static Long representativeMediaId(List<EvidenceLocation> locations) {
        return locations.stream()
                .map(EvidenceLocation::contentMediaId)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }
}
