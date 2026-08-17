package com.fuma.hiselectors.content.model;

import com.fuma.hiselectors.common.BaseTimeEntity;
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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "content_version")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContentVersion extends BaseTimeEntity {

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

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ContentVersionStatus status;

    @Column(name = "inspected_at")
    private LocalDateTime inspectedAt;

    public static ContentVersion create(Long contentId, Long versionNo, String contentHash) {
        ContentVersion version = new ContentVersion();
        version.contentId = contentId;
        version.versionNo = versionNo;
        version.contentHash = contentHash;
        version.status = ContentVersionStatus.PENDING;
        return version;
    }

    public void startInspection() {
        if (status != ContentVersionStatus.PENDING && status != ContentVersionStatus.FAILED) {
            throw new BusinessException(ErrorCode.INVALID_CONTENT_INSPECTION_STATUS);
        }
        status = ContentVersionStatus.INSPECTING;
    }

    public void completeInspection(LocalDateTime completedAt) {
        if (status != ContentVersionStatus.INSPECTING) {
            throw new BusinessException(ErrorCode.INVALID_CONTENT_INSPECTION_STATUS);
        }
        status = ContentVersionStatus.COMPLETED;
        inspectedAt = completedAt;
    }
}
