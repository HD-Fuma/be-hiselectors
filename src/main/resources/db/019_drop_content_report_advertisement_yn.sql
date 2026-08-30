-- content_report.advertisement_yn은 엔티티·API에서 쓰지 않는다.
-- 광고 여부는 AD_DISCLOSURE_INVALID 룰 위반으로만 남긴다.
SET @drop_content_report_advertisement_yn = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'content_report'
          AND column_name = 'advertisement_yn'
    ),
    'ALTER TABLE content_report DROP COLUMN advertisement_yn',
    'SELECT 1'
);

PREPARE drop_content_report_advertisement_yn
    FROM @drop_content_report_advertisement_yn;
EXECUTE drop_content_report_advertisement_yn;
DEALLOCATE PREPARE drop_content_report_advertisement_yn;

SELECT column_name, column_type, is_nullable
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'content_report'
ORDER BY ordinal_position;
