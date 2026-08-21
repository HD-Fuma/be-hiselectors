-- 지원자 콘텐츠 분석 결과(영속). 장애 재개용 멱등 캐시.
-- ddl-auto=validate 이므로 이 DDL 을 앱 부팅 전에 실행해야 한다(없으면 기동 실패).
-- content_key UNIQUE 로 멱등(같은 콘텐츠 재분석 방지). 평가 후 applicant_id 단위 삭제.
CREATE TABLE application_content_analysis (
    content_analysis_id BIGINT       NOT NULL AUTO_INCREMENT,
    applicant_id        BIGINT       NOT NULL,
    content_key         VARCHAR(200) NOT NULL,
    source              VARCHAR(20),
    stt                 LONGTEXT,
    ocr                 LONGTEXT,
    category            VARCHAR(30),
    keywords            LONGTEXT,
    hate_suspected      BIT(1)       NOT NULL DEFAULT 0,
    created_at          DATETIME(6),
    updated_at          DATETIME(6),
    PRIMARY KEY (content_analysis_id),
    CONSTRAINT uq_aca_content_key UNIQUE (content_key),
    INDEX idx_aca_applicant (applicant_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
