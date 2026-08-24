package com.fuma.hiselectors.content.client.dto;

import java.util.List;

/** 콘텐츠에 포함된 각각의 미디어 (이미지, 영상) */
public record RawContentMedia(
        // SNS가 개별 미디어에 부여한 ID
        String snsMediaId,

        // TEXT, IMAGE, VIDEO
        MediaType mediaType,

        // 이미지나 영상 파일 CDN 주소, 외부 API가 제공하지 않으면 null
        String mediaUrl,

        // 외부 API가 제공한 썸네일 주소 목록
        List<String> thumbnailUrls
) {

    public RawContentMedia {
        thumbnailUrls = thumbnailUrls == null ? List.of() : thumbnailUrls.stream()
                .filter(url -> url != null && !url.isBlank())
                .distinct()
                .toList();
    }

    public RawContentMedia(String snsMediaId, MediaType mediaType, String mediaUrl) {
        this(snsMediaId, mediaType, mediaUrl, List.of());
    }

    public enum MediaType {
        TEXT,
        IMAGE,
        VIDEO
    }
}
