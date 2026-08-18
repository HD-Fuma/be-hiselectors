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

/** 수집 시점의 콘텐츠 성과 스냅샷 */
@Entity
@Table(name = "content_engagement", uniqueConstraints = {
        @UniqueConstraint(
                name = "uq_content_engagement_content_created",
                columnNames = {"content_id", "created_at"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContentEngagement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "content_engagement_id")
    private Long id;

    @Column(name = "content_id", nullable = false)
    private Long contentId;

    @Column(name = "view_count")
    private Long viewCount;

    @Column(name = "like_count")
    private Long likeCount;

    @Column(name = "comment_count")
    private Long commentCount;

    @Column(name = "share_count")
    private Long shareCount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    private ContentEngagement(Long contentId, Long viewCount, Long likeCount,
                              Long commentCount, Long shareCount, LocalDateTime createdAt) {
        this.contentId = contentId;
        this.viewCount = viewCount;
        this.likeCount = likeCount;
        this.commentCount = commentCount;
        this.shareCount = shareCount;
        this.createdAt = createdAt;
    }
}
