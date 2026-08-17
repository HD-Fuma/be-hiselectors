package com.fuma.hiselectors.content.client.dto;

/** 콘텐츠에 포함된 각각의 미디어 (이미지, 영상) */
public record RawContentMedia(
        // SNS가 개별 미디어에 부여한 ID
        String snsMediaId,

        // TEXT, IMAGE, VIDEO
        MediaType mediaType,

        // 이미지나 영상 파일 CDN 주소, 외부 API가 제공하지 않으면 null
        String mediaUrl
) {

    public enum MediaType {
        TEXT,
        IMAGE,
        VIDEO
    }
}
