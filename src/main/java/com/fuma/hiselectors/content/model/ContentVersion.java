package com.fuma.hiselectors.content.model;

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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 콘텐츠 버전별 스냅샷 */
@Entity
@Table(name = "content_version", uniqueConstraints = {
        @UniqueConstraint(
                name = "uq_content_version_content_no",
                columnNames = {"content_id", "version_no"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContentVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "content_version_id")
    private Long id;

    @Column(name = "content_id", nullable = false)
    private Long contentId;

    @Column(name = "admin_id")
    private Long adminId;

    @Column(name = "version_no", nullable = false)
    private Long versionNo;

    // 현재 버전의 TEXT와 미디어 순서·유형·SNS ID를 반영한 SHA-256 해시
    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "creation_reason", nullable = false, length = 30)
    private ContentVersionCreationReason creationReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ContentVersionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "inspection_decision", length = 20)
    private ContentInspectionDecision inspectionDecision;

    @Column(name = "inspected_at")
    private LocalDateTime inspectedAt;

    @Builder
    private ContentVersion(Long contentId, Long adminId, Long versionNo, String contentHash,
                           ContentVersionCreationReason creationReason,
                           LocalDateTime createdAt, ContentVersionStatus status,
                           LocalDateTime inspectedAt) {
        this.contentId = contentId;
        this.adminId = adminId;
        this.versionNo = versionNo;
        this.contentHash = contentHash;
        this.creationReason = creationReason;
        this.createdAt = createdAt;
        this.status = status;
        this.inspectedAt = inspectedAt;
    }

    public static ContentVersion create(Long contentId, Long versionNo, String contentHash) {
        return create(contentId, versionNo, contentHash, inferredReason(versionNo),
                LocalDateTime.now());
    }

    public static ContentVersion create(Long contentId, Long versionNo, String contentHash,
                                        LocalDateTime createdAt) {
        return create(contentId, versionNo, contentHash, inferredReason(versionNo), createdAt);
    }

    public static ContentVersion create(Long contentId, Long versionNo, String contentHash,
                                        ContentVersionCreationReason creationReason,
                                        LocalDateTime createdAt) {
        return ContentVersion.builder()
                .contentId(contentId)
                .versionNo(versionNo)
                .contentHash(contentHash)
                .creationReason(creationReason)
                .createdAt(createdAt)
                .status(ContentVersionStatus.PENDING)
                .build();
    }

    private static ContentVersionCreationReason inferredReason(Long versionNo) {
        return versionNo != null && versionNo == 1L
                ? ContentVersionCreationReason.INITIAL
                : ContentVersionCreationReason.SOURCE_CHANGE;
    }

    public void startInspection() {
        if (inspectionDecision != null) {
            throw new BusinessException(ErrorCode.CONTENT_INSPECTION_ALREADY_CONFIRMED);
        }
        if (status == ContentVersionStatus.INSPECTING) {
            throw new BusinessException(ErrorCode.INVALID_CONTENT_INSPECTION_STATUS);
        }
        if (status == null
                || status == ContentVersionStatus.PENDING
                || status == ContentVersionStatus.COMPLETED
                || status == ContentVersionStatus.FAILED) {
            status = ContentVersionStatus.INSPECTING;
            return;
        }
        throw new BusinessException(ErrorCode.INVALID_CONTENT_INSPECTION_STATUS);
    }

    public void completeInspection(LocalDateTime inspectedAt) {
        if (status != ContentVersionStatus.INSPECTING) {
            throw new BusinessException(ErrorCode.INVALID_CONTENT_INSPECTION_STATUS);
        }
        status = ContentVersionStatus.COMPLETED;
        this.inspectedAt = inspectedAt;
    }

    public void failInspection() {
        if (status != ContentVersionStatus.INSPECTING) {
            throw new BusinessException(ErrorCode.INVALID_CONTENT_INSPECTION_STATUS);
        }
        status = ContentVersionStatus.FAILED;
    }

    public void confirmInspection(ContentInspectionDecision decision) {
        if (status != ContentVersionStatus.COMPLETED || inspectionDecision != null) {
            throw new BusinessException(ErrorCode.CONTENT_INSPECTION_ALREADY_CONFIRMED);
        }
        inspectionDecision = decision;
    }

    public void resetInspectionDecision() {
        if (status != ContentVersionStatus.COMPLETED || inspectionDecision == null) {
            throw new BusinessException(ErrorCode.INVALID_CONTENT_INSPECTION_STATUS);
        }
        inspectionDecision = null;
    }
}
