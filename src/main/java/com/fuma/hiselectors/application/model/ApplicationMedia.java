package com.fuma.hiselectors.application.model;

import com.fuma.hiselectors.common.BaseTimeEntity;
import com.fuma.hiselectors.content.model.ContentType;
import com.fuma.hiselectors.content.model.MediaType;
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
                columnNames = {"application_id", "sns_content_id", "sns_media_id"}))
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

    /** 같은 게시물의 모든 이미지·영상 행이 공유하는 SNS 게시물 ID. */
    @Column(name = "sns_content_id", nullable = false, length = 200)
    private String snsContentId;

    /** 게시물 안의 개별 이미지·영상 ID. */
    @Column(name = "sns_media_id", nullable = false, length = 200)
    private String snsMediaId;

    /** YouTube watch URL 또는 Instagram permalink. */
    @Column(name = "content_url", columnDefinition = "TEXT")
    private String contentUrl;

    /** Instagram 이미지·영상 CDN URL. YouTube는 제공하지 않아 null. */
    @Column(name = "media_url", columnDefinition = "TEXT")
    private String mediaUrl;

    /** 플랫폼이 제공한 썸네일 URL. 제공하지 않으면 null. */
    @Column(name = "thumbnail_url", columnDefinition = "TEXT")
    private String thumbnailUrl;

    /** Instagram 게시물 유형. REELS 또는 POST. */
    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", length = 20)
    private ContentType contentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", length = 20)
    private MediaType mediaType;

    @Column(columnDefinition = "TEXT")
    private String caption;

    @Column(columnDefinition = "TEXT")
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** 게시물 최신순 순서. 같은 게시물의 모든 미디어가 같은 값을 가진다. */
    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    /** 한 게시물 안의 미디어 순서. */
    @Column(name = "media_sequence_no", nullable = false)
    private int mediaSequenceNo;

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
    private ApplicationMedia(Long applicationId, SnsPlatform snsCode,
                             String snsContentId, String snsMediaId,
                             String contentUrl, String mediaUrl, String thumbnailUrl,
                             ContentType contentType, MediaType mediaType,
                             String caption, String title, String description,
                             int sequenceNo, int mediaSequenceNo,
                             LocalDateTime publishedAt,
                             Long viewCount, Long likeCount, Long commentCount,
                             LocalDateTime collectedAt) {
        this.applicationId = applicationId;
        this.snsCode = snsCode;
        this.snsContentId = snsContentId;
        this.snsMediaId = snsMediaId;
        this.contentUrl = contentUrl;
        this.mediaUrl = mediaUrl;
        this.thumbnailUrl = thumbnailUrl;
        this.contentType = contentType;
        this.mediaType = mediaType;
        this.caption = caption;
        this.title = title;
        this.description = description;
        this.sequenceNo = sequenceNo;
        this.mediaSequenceNo = mediaSequenceNo;
        this.publishedAt = publishedAt;
        this.viewCount = viewCount;
        this.likeCount = likeCount;
        this.commentCount = commentCount;
        this.collectedAt = collectedAt;
    }
}
