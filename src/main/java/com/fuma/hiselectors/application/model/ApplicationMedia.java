package com.fuma.hiselectors.application.model;

import com.fuma.hiselectors.common.BaseTimeEntity;
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

@Entity
@Table(
        name = "application_media",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_application_media",
                columnNames = {"application_id", "sns_content_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApplicationMedia extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "application_media_id")
    private Long id;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "sns_code", nullable = false, length = 20)
    private SnsPlatform snsCode;

    @Column(name = "sns_content_id", nullable = false, length = 200)
    private String snsContentId;

    /** YouTube watch URL 또는 Instagram permalink. */
    @Column(name = "media_url", nullable = false, columnDefinition = "TEXT")
    private String mediaUrl;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "view_count")
    private Long viewCount;

    @Column(name = "like_count")
    private Long likeCount;

    @Column(name = "comment_count")
    private Long commentCount;

    @Column(name = "collected_at", nullable = false, updatable = false)
    private LocalDateTime collectedAt;

    @Builder
    private ApplicationMedia(Long applicationId, SnsPlatform snsCode, String snsContentId,
                             String mediaUrl, int sequenceNo, LocalDateTime publishedAt,
                             Long viewCount, Long likeCount, Long commentCount) {
        this.applicationId = applicationId;
        this.snsCode = snsCode;
        this.snsContentId = snsContentId;
        this.mediaUrl = mediaUrl;
        this.sequenceNo = sequenceNo;
        this.publishedAt = publishedAt;
        this.viewCount = viewCount;
        this.likeCount = likeCount;
        this.commentCount = commentCount;
        this.collectedAt = LocalDateTime.now();
    }

}
