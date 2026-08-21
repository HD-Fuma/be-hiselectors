-- 콘텐츠 검수 정책/리포트/위반 이력
-- 아직 운영 DB에 반영되지 않은 최초 마이그레이션 기준이다.
-- JPA ddl-auto=validate 이므로 애플리케이션 배포 전에 적용한다.

CREATE TABLE IF NOT EXISTS inspection_policy (
    inspection_policy_id BIGINT NOT NULL AUTO_INCREMENT,
    platform VARCHAR(20) NOT NULL,
    version VARCHAR(40) NOT NULL,

    rule_config JSON NOT NULL,
    rule_config_hash VARCHAR(64) NOT NULL,

    ai_model_name VARCHAR(100) NOT NULL,
    ai_prompt_version VARCHAR(40) NOT NULL,
    ai_prompt TEXT NOT NULL,
    ai_config_hash VARCHAR(64) NOT NULL,

    stt_model_name VARCHAR(100) NULL,
    ocr_model_name VARCHAR(100) NULL,
    extraction_prompt_version VARCHAR(40) NULL,
    extraction_prompt TEXT NULL,
    extraction_config_hash VARCHAR(64) NOT NULL,

    config_hash VARCHAR(64) NOT NULL,
    is_active TINYINT(1) NOT NULL DEFAULT 0,
    activated_at DATETIME NULL,
    created_at DATETIME NULL,
    updated_at DATETIME NULL,

    PRIMARY KEY (inspection_policy_id),
    UNIQUE KEY uq_inspection_policy_platform_version (platform, version),
    UNIQUE KEY uq_inspection_policy_config_hash (config_hash),
    KEY ix_inspection_policy_platform_active (platform, is_active)
);

-- body에는 콘텐츠 내용(STT/OCR/요약)만 두고 추출 출처는 컬럼으로 분리한다.
ALTER TABLE content_media
    ADD COLUMN extracted_with_policy_id BIGINT NULL,
    ADD COLUMN extraction_input_hash VARCHAR(64) NULL,
    ADD COLUMN extracted_at DATETIME NULL,
    ADD CONSTRAINT fk_content_media_extracted_policy
        FOREIGN KEY (extracted_with_policy_id)
        REFERENCES inspection_policy (inspection_policy_id);

-- 기존 content_version_id UNIQUE를 제거해 재검수마다 리포트 행을 추가할 수 있게 한다.
SET @content_report_unique_index = (
    SELECT INDEX_NAME
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'content_report'
      AND column_name = 'content_version_id'
      AND non_unique = 0
      AND index_name <> 'PRIMARY'
    LIMIT 1
);
SET @drop_content_report_unique = IF(
    @content_report_unique_index IS NULL,
    'SELECT 1',
    CONCAT('ALTER TABLE content_report DROP INDEX `',
           @content_report_unique_index, '`')
);
PREPARE drop_content_report_unique_stmt FROM @drop_content_report_unique;
EXECUTE drop_content_report_unique_stmt;
DEALLOCATE PREPARE drop_content_report_unique_stmt;

ALTER TABLE content_report
    ADD COLUMN inspection_policy_id BIGINT NULL,
    ADD KEY ix_content_report_version_latest (content_version_id, content_report_id),
    ADD CONSTRAINT fk_content_report_policy
        FOREIGN KEY (inspection_policy_id)
        REFERENCES inspection_policy (inspection_policy_id);

-- 콘텐츠 위반은 콘텐츠·위반 유형당 한 행을 유지한다.
ALTER TABLE violation_item
    ADD COLUMN content_id BIGINT NULL AFTER violation_item_id;

UPDATE violation_item vi
INNER JOIN content_version cv ON cv.content_version_id = vi.content_version_id
SET vi.content_id = cv.content_id
WHERE vi.content_id IS NULL;

DELETE vi
FROM violation_item vi
INNER JOIN (
    SELECT violation_item_id
    FROM (
        SELECT
            violation_item_id,
            ROW_NUMBER() OVER (
                PARTITION BY content_id, violation_type_id
                ORDER BY
                    CASE
                        WHEN status IN ('PENDING', 'VIOLATION_CONFIRMED', 'EDIT_REQUESTED')
                            THEN 0
                        ELSE 1
                    END,
                    updated_at DESC,
                    violation_item_id DESC
            ) AS row_num
        FROM violation_item
    ) ranked
    WHERE ranked.row_num > 1
) dup ON dup.violation_item_id = vi.violation_item_id;

ALTER TABLE violation_item
    MODIFY COLUMN content_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_violation_item_content
        FOREIGN KEY (content_id) REFERENCES content (content_id),
    ADD UNIQUE KEY uq_violation_item_content_type (content_id, violation_type_id);

CREATE TABLE IF NOT EXISTS violation_evidence_history (
    violation_evidence_history_id BIGINT NOT NULL AUTO_INCREMENT,
    violation_item_id BIGINT NOT NULL,
    content_version_id BIGINT NOT NULL,
    inspection_policy_id BIGINT NOT NULL,
    evidence JSON NOT NULL,
    detected_at DATETIME NOT NULL,
    created_at DATETIME NULL,
    updated_at DATETIME NULL,

    PRIMARY KEY (violation_evidence_history_id),
    UNIQUE KEY uq_violation_evidence_snapshot (
        violation_item_id,
        content_version_id,
        inspection_policy_id
    ),
    CONSTRAINT fk_violation_evidence_history_item
        FOREIGN KEY (violation_item_id)
        REFERENCES violation_item (violation_item_id),
    CONSTRAINT fk_violation_evidence_history_policy
        FOREIGN KEY (inspection_policy_id)
        REFERENCES inspection_policy (inspection_policy_id)
);
