-- 셀렉터스별로 삭제되지 않은 최신 수집 계정 하나만 보존한다.
DELETE account
FROM selectors_sns_account account
JOIN (
    SELECT selectors_sns_account_id
    FROM (
        SELECT selectors_sns_account_id,
               ROW_NUMBER() OVER (
                   PARTITION BY selectors_id
                   ORDER BY is_deleted ASC, last_collected_at DESC, selectors_sns_account_id DESC
               ) AS rn
        FROM selectors_sns_account
    ) ranked
    WHERE rn > 1
) duplicate ON duplicate.selectors_sns_account_id = account.selectors_sns_account_id;

ALTER TABLE selectors_sns_account
    ADD CONSTRAINT uq_selectors_sns_account_selectors_id UNIQUE (selectors_id);
