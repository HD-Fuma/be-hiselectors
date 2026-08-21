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
import java.util.LinkedHashMap;
import java.util.Map;
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

    public static ContentMedia create(
            Long contentVersionId,
            MediaType mediaType,
            String mediaUrl,
            String snsMediaId,
            Integer sequenceNo,
            Map<String, Object> body) {
        ContentMedia media = new ContentMedia();
        media.contentVersionId = contentVersionId;
        media.mediaType = mediaType;
        media.mediaUrl = mediaUrl;
        media.snsMediaId = snsMediaId;
        media.sequenceNo = sequenceNo;
        media.replaceBody(body);
        return media;
    }

    public Map<String, Object> bodyOrEmpty() {
        return body == null ? Map.of() : body;
    }

    public void replaceBody(Map<String, Object> body) {
        this.body = body == null ? new LinkedHashMap<>() : new LinkedHashMap<>(body);
    }
}
