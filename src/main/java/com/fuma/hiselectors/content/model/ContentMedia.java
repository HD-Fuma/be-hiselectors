package com.fuma.hiselectors.content.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 콘텐츠 버전에 포함된 개별 미디어 (콘텐츠 > 콘텐츠 버전 > 콘텐츠 미디어) */
@Entity
@Table(name = "content_media")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContentMedia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "content_media_id")
    private Long id;

    @Column(name = "content_version_id", nullable = false)
    private Long contentVersionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, length = 19)
    private MediaType mediaType;

    @Column(name = "media_url", length = 500)
    private String mediaUrl;

    // TEXT: Instagram 본문, 프로필 설명, Youtube 본문, OCR/STT 추출 결과
    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String body;

    @Builder
    private ContentMedia(Long contentVersionId, MediaType mediaType,
                         String mediaUrl, String body) {
        this.contentVersionId = contentVersionId;
        this.mediaType = mediaType;
        this.mediaUrl = mediaUrl;
        this.body = body;
    }

    public enum MediaType {
        TEXT,
        IMAGE,
        VIDEO
    }

}
