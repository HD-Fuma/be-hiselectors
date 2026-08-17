package com.fuma.hiselectors.content.client.dto;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.model.ContentType;
import java.time.LocalDateTime;
import java.util.List;

/**
 * SNS 계정의 모든 콘텐츠 데이터
 *
 * 셀렉터스 콘텐츠 여부 판별 전 데이터입니다. 해당 데이터는 DB에 저장하지 않습니다.
 */
public record RawContent(
        // INSTAGRAM, YOUTUBE ...
        SnsPlatform snsCode,

        // SNS가 부여한 고유 ID
        String snsContentId,

        // 콘텐츠 원문을 확인할 수 있는 주소
        String contentUrl,

        // FEED, SHORT_FORM, LONG_FORM ...
        ContentType contentType,

        // Instagram 본문, YouTube 제목·설명 등 TEXT 목록
        List<String> texts,

        // SNS 게시 시각
        LocalDateTime createdAt,

        // 콘텐츠에 포함된 이미지와 영상 목록
        List<RawContentMedia> media
) {
    public RawContent {
        texts = texts == null ? List.of() : texts.stream()
                .filter(text -> text != null && !text.isBlank())
                .toList();
        media = media == null ? List.of() : List.copyOf(media);
    }

    public RawContent(
            SnsPlatform snsCode,
            String snsContentId,
            String contentUrl,
            ContentType contentType,
            String caption,
            LocalDateTime createdAt,
            List<RawContentMedia> media) {
        this(snsCode, snsContentId, contentUrl, contentType,
                caption == null ? List.of() : List.of(caption),
                createdAt, media);
    }

    /** 셀렉터스 콘텐츠 판별용 전체 TEXT 결합 */
    public String caption() {
        return String.join("\n", texts);
    }
}
