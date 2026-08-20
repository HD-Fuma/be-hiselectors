-- OAuth 인증 시점의 전체 공개 콘텐츠 수와 90일 수집 콘텐츠 형식을 보존한다.
ALTER TABLE application
    ADD COLUMN content_count BIGINT NULL
        COMMENT '플랫폼이 제공한 전체 공개 콘텐츠 수'
        AFTER follower_count;

ALTER TABLE application_media
    ADD COLUMN content_type VARCHAR(20) NULL
        COMMENT 'SHORT_FORM / LONG_FORM / SHORTS / FEED'
        AFTER media_url;
