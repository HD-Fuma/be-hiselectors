package com.fuma.hiselectors.report.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "content_report")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContentReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "content_report_id")
    private Long id;

    @Column(name = "content_version_id", nullable = false)
    private Long contentVersionId;

    @Column(name = "advertisement_yn")
    private Boolean advertisementYn;

    /** 분석 전체를 담은 JSON. 카테고리는 더현대 공식 코드만 들어간다. */
    @Column(columnDefinition = "json")
    private String summary;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Builder
    private ContentReport(Long contentVersionId, Boolean advertisementYn, String summary) {
        this.contentVersionId = contentVersionId;
        this.advertisementYn = advertisementYn;
        this.summary = summary;
    }
}
