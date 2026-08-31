-- hi_selectors 로컬 테스트용 셀렉터스 정산 계좌 더미데이터
--
-- 현재 selectors에 존재하는 셀렉터스마다 settlement_account 1건을 생성한다.
--
-- account_holder : users.name
-- bank_name      : 하나은행 / 우리은행 / 신한은행 / KB국민은행 순환
-- settlement_type: INDIVIDUAL
--
-- account_number / business_number는
-- 기존 테스트 데이터의 암호화 값을 그대로 재사용한다.

USE `hi_selectors`;

INSERT INTO `settlement_account` (
    `created_at`,
    `updated_at`,
    `account_holder`,
    `account_number`,
    `bank_name`,
    `business_number`,
    `is_deleted`,
    `selectors_id`,
    `settlement_type`
)
SELECT
    -- 생성일
    COALESCE(`s`.`created_at`, '2026-08-31 12:00:00'),

    -- 수정일
    COALESCE(`s`.`created_at`, '2026-08-31 12:00:00'),

    -- 예금주 = 더현대Hi 사용자명
    `u`.`name`,

    -- 계좌번호 암호화 값
    CASE MOD(`s`.`selectors_id`, 2)
        WHEN 0 THEN
            'enc:v1:7A/AN6cWtwvEl0EshF3CW4Q8j5+p7lLPXMLy5kHccpBCdNTM'
        ELSE
            'enc:v1:UNr8ePcspTtlUGjFn55WmE9YWeVXuKDdtxZWVhkIH6dGtMXgmg=='
        END,

    -- 은행명
    CASE MOD(`s`.`selectors_id`, 4)
        WHEN 0 THEN '하나은행'
        WHEN 1 THEN '우리은행'
        WHEN 2 THEN '신한은행'
        WHEN 3 THEN 'KB국민은행'
        END,

    -- 사업자번호 암호화 값
    CASE MOD(`s`.`selectors_id`, 2)
        WHEN 0 THEN
            'enc:v1:mqTJ0xXiXo1voKyRyFMFV2WNEQhMRWOpzDOmtYbQYt/1HjuvKfpnnfb1'
        ELSE
            'enc:v1:jT46snx4hdswneRGjPED3GNS+G9DMy7mXtz1X4DZkubGI6yusC/Ypn3R'
        END,

    b'0',

    `s`.`selectors_id`,

    'INDIVIDUAL'

FROM `selectors` AS `s`

         INNER JOIN `users` AS `u`
                    ON `u`.`user_id` = `s`.`user_id`

-- 이미 정산 계좌가 있는 셀렉터스는 추가하지 않는다.
WHERE NOT EXISTS (
    SELECT 1
    FROM `settlement_account` AS `existing`
    WHERE `existing`.`selectors_id` = `s`.`selectors_id`
)

ORDER BY `s`.`selectors_id`;


SELECT
    `s`.`selectors_id`,
    `u`.`name`,
    `sa`.`settlement_account_id`,
    `sa`.`account_holder`,
    `sa`.`bank_name`,
    `sa`.`settlement_type`
FROM `selectors` AS `s`
         JOIN `users` AS `u`
              ON `u`.`user_id` = `s`.`user_id`
         LEFT JOIN `settlement_account` AS `sa`
                   ON `sa`.`selectors_id` = `s`.`selectors_id`
ORDER BY `s`.`selectors_id`;