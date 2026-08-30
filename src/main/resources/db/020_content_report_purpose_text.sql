-- Gemini overview.purpose가 varchar(100)을 넘는 경우가 있어 저장이 실패한다.
-- summary/flow/overall_assessment와 같이 text로 맞춘다.
SET @widen_content_report_purpose = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'content_report'
          AND column_name = 'purpose'
          AND data_type <> 'text'
    ),
    'ALTER TABLE content_report MODIFY COLUMN purpose text',
    'SELECT 1'
);

PREPARE widen_content_report_purpose FROM @widen_content_report_purpose;
EXECUTE widen_content_report_purpose;
DEALLOCATE PREPARE widen_content_report_purpose;
