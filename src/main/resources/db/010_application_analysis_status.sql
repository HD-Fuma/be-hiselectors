-- 지원자 콘텐츠 분석(STT/OCR→리포트) 상태 추적. 미디어 수집(media_collection_status) 다음 단계.
-- ddl-auto=validate 이므로 부팅 전 실행. 기존 행은 DEFAULT 'PENDING'/0 으로 채워진다.
ALTER TABLE application
    ADD COLUMN analysis_status      VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    ADD COLUMN analysis_retry_count INT          NOT NULL DEFAULT 0,
    ADD COLUMN analyzed_at          DATETIME(6)  NULL,
    ADD COLUMN analysis_error       VARCHAR(500) NULL;
