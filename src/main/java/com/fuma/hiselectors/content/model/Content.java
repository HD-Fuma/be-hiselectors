package com.fuma.hiselectors.content.model;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "content")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Content extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "content_id")
    private Long id;

    @Column(name = "selectors_id", nullable = false)
    private Long selectorsId;

    @Enumerated(EnumType.STRING)
    @Column(name = "sns_code", nullable = false, length = 20)
    private SnsPlatform snsCode;

    @Column(name = "sns_content_id", nullable = false, length = 200)
    private String snsContentId;

    @Column(name = "content_url", nullable = false, length = 500)
    private String contentUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false, length = 20)
    private ContentType contentType;

    @Column(name = "last_version_no", nullable = false)
    private Long lastVersionNo;

    @Builder
    private Content(Long selectorsId, SnsPlatform snsCode, String snsContentId,
                    String contentUrl, ContentType contentType, Long lastVersionNo) {
        this.selectorsId = selectorsId;
        this.snsCode = snsCode;
        this.snsContentId = snsContentId;
        this.contentUrl = contentUrl;
        this.contentType = contentType;
        this.lastVersionNo = lastVersionNo == null ? 1L : lastVersionNo;
    }
}
