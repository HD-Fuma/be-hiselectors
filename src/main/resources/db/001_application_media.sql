-- 지원자 심사용 미디어 URL.
--
-- 지원자는 아직 셀렉터스가 아니라 content 테이블(selectors_id 참조)에 넣을 수 없다.
-- 콘텐츠 본문·버전은 보관하지 않고, 분석에 필요한 URL 과 수집 시점 지표만 남긴다.
-- Gemini 가 영상 URL 을 직접 받으므로 미디어 파일 자체는 저장하지 않는다.

CREATE TABLE application_media
(
    application_media_id BIGINT       NOT NULL AUTO_INCREMENT,
    application_id       BIGINT       NOT NULL,
    sns_code             VARCHAR(20)  NOT NULL COMMENT 'YOUTUBE / INSTAGRAM',
    sns_content_id       VARCHAR(200) NOT NULL COMMENT 'SNS 원본 콘텐츠 ID. 중복 수집 방지용',
    media_url            TEXT         NOT NULL COMMENT 'YouTube watch URL 또는 Instagram permalink',
    sequence_no          INT          NOT NULL COMMENT '수집 순서. 최신순 0부터',
    published_at         DATETIME(6)  NULL COMMENT '게시 시각',
    view_count           BIGINT       NULL,
    like_count           BIGINT       NULL,
    comment_count        BIGINT       NULL,
    collected_at         DATETIME(6)  NOT NULL COMMENT '수집 시각',
    created_at           DATETIME(6)  NULL,
    updated_at           DATETIME(6)  NULL,
    PRIMARY KEY (application_media_id),
    CONSTRAINT uq_application_media UNIQUE (application_id, sns_content_id),
    KEY idx_application_media_application_id (application_id),
    CONSTRAINT FK_Application_TO_ApplicationMedia_1
        FOREIGN KEY (application_id) REFERENCES application (application_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT '지원자 심사용 수집 미디어';
