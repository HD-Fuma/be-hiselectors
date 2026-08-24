-- 콘텐츠 검수 근거·버전 이력 개선
-- MySQL 8 기준. JPA ddl-auto=validate 이므로 애플리케이션 배포 전에 적용한다.

ALTER TABLE content_version
    ADD COLUMN creation_reason VARCHAR(30) NULL AFTER content_hash;

UPDATE content_version
SET creation_reason = CASE
    WHEN version_no = 1 THEN 'INITIAL'
    ELSE 'SOURCE_CHANGE'
END
WHERE creation_reason IS NULL;

ALTER TABLE content_version
    MODIFY COLUMN creation_reason VARCHAR(30) NOT NULL;

-- RULE 전용 유형은 RULE, 그 외 AI 검수 유형은 AI로 기존 evidence를 보정한다.
UPDATE violation_item vi
INNER JOIN violation_type vt
        ON vt.violation_type_id = vi.violation_type_id
SET vi.evidence = JSON_SET(
        vi.evidence,
        '$.source',
        CASE
            WHEN vt.code IN ('AD_DISCLOSURE_INVALID', 'AFFILIATE_LINK_INVALID')
                THEN 'RULE'
            ELSE 'AI'
        END)
WHERE JSON_EXTRACT(vi.evidence, '$.source') IS NULL;

UPDATE violation_evidence_history veh
INNER JOIN violation_item vi
        ON vi.violation_item_id = veh.violation_item_id
INNER JOIN violation_type vt
        ON vt.violation_type_id = vi.violation_type_id
SET veh.evidence = JSON_SET(
        veh.evidence,
        '$.source',
        CASE
            WHEN vt.code IN ('AD_DISCLOSURE_INVALID', 'AFFILIATE_LINK_INVALID')
                THEN 'RULE'
            ELSE 'AI'
        END)
WHERE JSON_EXTRACT(veh.evidence, '$.source') IS NULL;
