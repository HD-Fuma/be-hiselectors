-- 콘텐츠 미디어 추출 결과는 content_media.body에 저장하고,
-- 리포트 분석 및 실행 메타데이터는 버전화된 JSON 계약으로 확장한다.
ALTER TABLE content_report
    ADD COLUMN report_schema_version varchar(20) DEFAULT NULL
        COMMENT 'content_report.analysis JSON 스키마 버전' AFTER inspection_policy_id,
    ADD COLUMN analysis json DEFAULT NULL
        COMMENT '콘텐츠 상세 분석 결과' AFTER report_schema_version,
    ADD COLUMN execution_metadata json DEFAULT NULL
        COMMENT '모델, 요청 ID, 소요 시간 등 실행 메타데이터' AFTER analysis;

-- 기존 행을 유지한 채 후속 단계에서 리포트와 위반 근거 스냅샷을 연결한다.
ALTER TABLE violation_evidence_history
    ADD COLUMN content_report_id bigint DEFAULT NULL
        COMMENT '근거 스냅샷을 생성한 콘텐츠 리포트 ID' AFTER inspection_policy_id,
    ADD KEY idx_violation_evidence_history_report (content_report_id),
    ADD CONSTRAINT fk_violation_evidence_history_report
        FOREIGN KEY (content_report_id)
        REFERENCES content_report (content_report_id);
