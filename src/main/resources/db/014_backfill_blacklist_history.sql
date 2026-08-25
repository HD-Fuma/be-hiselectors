-- 패널티가 3회 이상 누적됐지만 블랙리스트 이력이 없는 기존 셀렉터스를 보정한다.
INSERT INTO blacklist_history (
    selectors_id, reason, status, created_at, updated_at
)
SELECT accumulated.selectors_id,
       '패널티 누적 3회로 인한 자동 블랙리스트 전환',
       'ACTIVE',
       CURRENT_TIMESTAMP(6),
       CURRENT_TIMESTAMP(6)
FROM (
    SELECT selectors_id
    FROM penalty_history
    GROUP BY selectors_id
    HAVING COUNT(*) >= 3
) accumulated
WHERE NOT EXISTS (
    SELECT 1
    FROM blacklist_history history
    WHERE history.selectors_id = accumulated.selectors_id
      AND history.status = 'ACTIVE'
);

UPDATE selectors s
JOIN (
    SELECT selectors_id
    FROM penalty_history
    GROUP BY selectors_id
    HAVING COUNT(*) >= 3
) accumulated ON accumulated.selectors_id = s.selectors_id
SET s.selectors_role_id = 'BLACKLIST'
WHERE s.selectors_role_id <> 'BLACKLIST';
