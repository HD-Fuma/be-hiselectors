-- 같은 콘텐츠 버전과 검수 정책의 재실행은 기존 리포트를 갱신한다.
-- 기존 중복 데이터가 있다면 가장 최근 리포트로 근거 이력을 연결한 뒤 정리한다.
UPDATE violation_evidence_history history
JOIN content_report old_report
  ON old_report.content_report_id = history.content_report_id
JOIN (
    SELECT content_version_id,
           inspection_policy_id,
           MAX(content_report_id) AS latest_report_id
    FROM content_report
    WHERE inspection_policy_id IS NOT NULL
    GROUP BY content_version_id, inspection_policy_id
) latest
  ON latest.content_version_id = old_report.content_version_id
 AND latest.inspection_policy_id = old_report.inspection_policy_id
SET history.content_report_id = latest.latest_report_id
WHERE history.content_report_id <> latest.latest_report_id;

DELETE old_report
FROM content_report old_report
JOIN content_report newer_report
  ON newer_report.content_version_id = old_report.content_version_id
 AND newer_report.inspection_policy_id = old_report.inspection_policy_id
 AND newer_report.content_report_id > old_report.content_report_id
WHERE old_report.inspection_policy_id IS NOT NULL;

ALTER TABLE content_report
    ADD CONSTRAINT uq_content_report_version_policy
        UNIQUE (content_version_id, inspection_policy_id);
