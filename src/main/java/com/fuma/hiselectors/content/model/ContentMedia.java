package com.fuma.hiselectors.content.model;

import com.fuma.hiselectors.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "content_media")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContentMedia extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "content_media_id")
    private Long id;

    @Column(name = "content_version_id", nullable = false)
    private Long contentVersionId;

    @Column(name = "media_url", length = 500)
    private String mediaUrl;

    @Column(name = "thumbnail_url", columnDefinition = "TEXT")
    private String thumbnailUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, length = 20)
    private MediaType mediaType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "body", columnDefinition = "json")
    private Map<String, Object> body = new LinkedHashMap<>();

    @Column(name = "sns_media_id", length = 200)
    private String snsMediaId;

    @Column(name = "sequence_no", nullable = false)
    private Integer sequenceNo;

    /** 현재 body의 STT/OCR를 실제로 생성한 검수 정책. */
    @Column(name = "extracted_with_policy_id")
    private Long extractedWithPolicyId;

    @Column(name = "extraction_input_hash", length = 64)
    private String extractionInputHash;

    @Column(name = "extracted_at")
    private LocalDateTime extractedAt;

    public static ContentMedia create(
            Long contentVersionId,
            MediaType mediaType,
            String mediaUrl,
            String snsMediaId,
            Integer sequenceNo,
            Map<String, Object> body) {
        return create(contentVersionId, mediaType, mediaUrl, null, snsMediaId, sequenceNo, body);
    }

    public static ContentMedia create(
            Long contentVersionId,
            MediaType mediaType,
            String mediaUrl,
            String thumbnailUrl,
            String snsMediaId,
            Integer sequenceNo,
            Map<String, Object> body) {
        ContentMedia media = new ContentMedia();
        media.contentVersionId = contentVersionId;
        media.mediaType = mediaType;
        media.mediaUrl = mediaUrl;
        media.thumbnailUrl = thumbnailUrl;
        media.snsMediaId = snsMediaId;
        media.sequenceNo = sequenceNo;
        media.replaceBody(body);
        return media;
    }

    public static ContentMedia create(Long contentVersionId, String mediaUrl,
                                      MediaType mediaType, Map<String, Object> body) {
        return create(contentVersionId, mediaUrl, mediaType, body, 0);
    }

    public static ContentMedia create(Long contentVersionId, String mediaUrl,
                                      MediaType mediaType, Map<String, Object> body,
                                      int sequenceNo) {
        return create(contentVersionId, mediaType, mediaUrl, null, sequenceNo, body);
    }

    public Map<String, Object> bodyOrEmpty() {
        return body == null ? Map.of() : body;
    }

    public void replaceBody(Map<String, Object> body) {
        this.body = body == null ? new LinkedHashMap<>() : new LinkedHashMap<>(body);
    }

    public void markExtracted(Long policyId, String inputHash, LocalDateTime extractedAt) {
        this.extractedWithPolicyId = policyId;
        this.extractionInputHash = inputHash;
        this.extractedAt = extractedAt;
    }

    public boolean refreshExternalUrls(String mediaUrl, String thumbnailUrl) {
        String nextMediaUrl = hasText(mediaUrl) ? mediaUrl : this.mediaUrl;
        String nextThumbnailUrl = hasText(thumbnailUrl) ? thumbnailUrl : this.thumbnailUrl;
        if (Objects.equals(this.mediaUrl, nextMediaUrl)
                && Objects.equals(this.thumbnailUrl, nextThumbnailUrl)) {
            return false;
        }
        this.mediaUrl = nextMediaUrl;
        this.thumbnailUrl = nextThumbnailUrl;
        return true;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
