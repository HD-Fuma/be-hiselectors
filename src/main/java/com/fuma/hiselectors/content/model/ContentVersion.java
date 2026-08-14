package com.fuma.hiselectors.content.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    // 현재 버전의 TEXT 유형 값을 순서대로 모아 생성한 SHA-256 해시
    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(length = 20)
    private String status;

    @Column(name = "inspected_at")
    private LocalDateTime inspectedAt;

    @Builder
    private ContentVersion(Long contentId, Long adminId, Long versionNo, String contentHash,
                           LocalDateTime createdAt, String status, LocalDateTime inspectedAt) {
        this.contentId = contentId;
        this.adminId = adminId;
        this.versionNo = versionNo;
        this.contentHash = contentHash;
        this.createdAt = createdAt;
        this.status = status;
        this.inspectedAt = inspectedAt;
    }
}
