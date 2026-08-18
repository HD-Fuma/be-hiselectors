package com.fuma.hiselectors.content.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnTransformer;

/** 콘텐츠 버전에 포함된 개별 미디어 (콘텐츠 > 콘텐츠 버전 > 콘텐츠 미디어) */
@Entity
@Table(
        name = "content_media",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_content_media_version_sequence",
                columnNames = {"content_version_id", "sequence_no"}))
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

    @Column(name = "media_url", columnDefinition = "TEXT")
    private String mediaUrl;

    @Column(name = "sns_media_id", length = 200)
    private String snsMediaId;

    @Column(name = "sequence_no", nullable = false)
    private Integer sequenceNo;

    // JSON: Instagram 본문, 프로필 설명, Youtube 본문, OCR/STT 추출 결과
    @Column(name = "body", columnDefinition = "JSON")
    @ColumnTransformer(read = "json_unquote(body)", write = "json_quote(?)")
    private String body;

    @Builder
    private ContentMedia(
            Long contentVersionId,
            MediaType mediaType,
            String mediaUrl,
            String snsMediaId,
            Integer sequenceNo,
            String body) {
        this.contentVersionId = contentVersionId;
        this.mediaType = mediaType;
        this.mediaUrl = mediaUrl;
        this.snsMediaId = snsMediaId;
        this.sequenceNo = sequenceNo;
        this.body = body;
    }

    public enum MediaType {
        TEXT,
        IMAGE,
        VIDEO
    }

}
