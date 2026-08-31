-- hi_selectors 로컬 성능 테스트용 크리에이터 제안 이력 데이터
--
-- 기준일: 2026-08-31 (Asia/Seoul)
-- 선행 데이터:
--   01_admin.sql 또는 활성 관리자 데이터
--   서비스에서 수집한 creator_pool 데이터
--
-- 코드 기준 의미:
--   proposal_history 1행은 관리자 제안 메일이 성공적으로 발송된 이력 1건이다.
--   메일 발송 실패 시 트랜잭션이 롤백되므로 실패 상태나 본문은 저장하지 않는다.
--
-- 생성 규칙:
--   1. is_deleted=0이고 공개 이메일이 있는 크리에이터만 대상이다.
--   2. 대상 크리에이터의 약 35%에게 제안 이력을 만든다.
--   3. 대부분 1회, 일부는 충분한 기간을 두고 2~3회 후속 제안한 것으로 만든다.
--   4. 제안 시각은 오전 업무시간에 가장 많고, 오후·저녁·이른 아침·늦은 밤도
--      일부 포함한다. 주말 제안의 약 75%는 다음 월요일로 이동한다.
--   5. 기존 풀 데이터는 등록 다음 날 이후에만 이력을 생성한다.
--      기준일 당일 수집한 행은 현재 풀 스냅샷을 가져온 것으로 보고 과거 이력을
--      백필한다. 따라서 이 경우에만 proposal_history가 creator_pool.created_at보다
--      이를 수 있으며, 아래 검증 쿼리에서 별도 정보로 확인할 수 있다.
--   6. proposal_history_id는 created_at 오름차순으로 1부터 부여한다.
--
-- creator_pool 건수에 따라 생성되는 이력 수가 달라지는 동적 스크립트다.
-- 활성 관리자 또는 조건에 맞는 크리에이터가 없으면 0건이 생성된다.

USE `hi_selectors`;
SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;

SET @previous_session_time_zone = @@session.time_zone;
SET time_zone = '+09:00';

SET @proposal_window_start = DATE('2025-09-01');
SET @proposal_reference_date = DATE('2026-08-31');
SET @proposal_reference_end = TIMESTAMP('2026-08-31 23:59:59');

DELETE FROM `proposal_history`;
ALTER TABLE `proposal_history` AUTO_INCREMENT = 1;

INSERT INTO `proposal_history` (
    `creator_id`,
    `admin_id`,
    `created_at`
)
WITH
`admin_pool` AS (
    SELECT
        `admin_id`,
        ROW_NUMBER() OVER (ORDER BY `admin_id`) AS `admin_rank`,
        COUNT(*) OVER () AS `admin_count`
    FROM `admin`
    WHERE `is_deleted` = 0
),
`eligible_creator_base` AS (
    SELECT
        `creator_pool_id`,
        GREATEST(
            CASE
                WHEN `created_at` IS NULL
                     OR `created_at` >= @proposal_reference_date
                    THEN @proposal_window_start
                ELSE DATE_ADD(DATE(`created_at`), INTERVAL 1 DAY)
            END,
            @proposal_window_start
        ) AS `available_start_date`
    FROM `creator_pool`
    WHERE `is_deleted` = 0
      AND `email` IS NOT NULL
      AND TRIM(`email`) <> ''
),
`ranked_creators` AS (
    SELECT
        `creator_pool_id`,
        `available_start_date`,
        ROW_NUMBER() OVER (
            ORDER BY
                CRC32(CONCAT('proposal-select:', `creator_pool_id`)),
                `creator_pool_id`
        ) AS `selection_rank`,
        COUNT(*) OVER () AS `eligible_creator_count`
    FROM `eligible_creator_base`
),
`selected_creator_base` AS (
    SELECT
        `creator_pool_id`,
        `available_start_date`,
        DATEDIFF(
            @proposal_reference_date,
            `available_start_date`
        ) AS `available_day_count`
    FROM `ranked_creators`
    WHERE `selection_rank` <= GREATEST(
        1,
        CEIL(`eligible_creator_count` * 0.35)
    )
),
`selected_creators` AS (
    SELECT
        `creator_pool_id`,
        `available_start_date`,
        `available_day_count`,
        CASE
            WHEN `available_day_count` >= 180
                 AND MOD(
                     CRC32(CONCAT('proposal-repeat:', `creator_pool_id`)),
                     100
                 ) < 5
                THEN 3
            WHEN `available_day_count` >= 60
                 AND MOD(
                     CRC32(CONCAT('proposal-repeat:', `creator_pool_id`)),
                     100
                 ) < 25
                THEN 2
            ELSE 1
        END AS `proposal_count`
    FROM `selected_creator_base`
),
`proposal_numbers` AS (
    SELECT 1 AS `proposal_no`
    UNION ALL SELECT 2
    UNION ALL SELECT 3
),
`proposal_percentages` AS (
    SELECT
        `c`.`creator_pool_id`,
        `c`.`available_start_date`,
        `c`.`available_day_count`,
        `c`.`proposal_count`,
        `n`.`proposal_no`,
        CASE
            WHEN `c`.`proposal_count` = 1
                THEN 5 + MOD(
                    CRC32(CONCAT(
                        'proposal-date:',
                        `c`.`creator_pool_id`,
                        ':',
                        `n`.`proposal_no`
                    )),
                    91
                )
            WHEN `c`.`proposal_count` = 2
                 AND `n`.`proposal_no` = 1
                THEN 10 + MOD(
                    CRC32(CONCAT(
                        'proposal-date:',
                        `c`.`creator_pool_id`,
                        ':',
                        `n`.`proposal_no`
                    )),
                    41
                )
            WHEN `c`.`proposal_count` = 2
                THEN 60 + MOD(
                    CRC32(CONCAT(
                        'proposal-date:',
                        `c`.`creator_pool_id`,
                        ':',
                        `n`.`proposal_no`
                    )),
                    36
                )
            WHEN `n`.`proposal_no` = 1
                THEN 10 + MOD(
                    CRC32(CONCAT(
                        'proposal-date:',
                        `c`.`creator_pool_id`,
                        ':',
                        `n`.`proposal_no`
                    )),
                    16
                )
            WHEN `n`.`proposal_no` = 2
                THEN 40 + MOD(
                    CRC32(CONCAT(
                        'proposal-date:',
                        `c`.`creator_pool_id`,
                        ':',
                        `n`.`proposal_no`
                    )),
                    21
                )
            ELSE 75 + MOD(
                CRC32(CONCAT(
                    'proposal-date:',
                    `c`.`creator_pool_id`,
                    ':',
                    `n`.`proposal_no`
                )),
                21
            )
        END AS `date_percent`
    FROM `selected_creators` AS `c`
    CROSS JOIN `proposal_numbers` AS `n`
    WHERE `n`.`proposal_no` <= `c`.`proposal_count`
),
`raw_proposal_dates` AS (
    SELECT
        `creator_pool_id`,
        `proposal_no`,
        DATE_ADD(
            `available_start_date`,
            INTERVAL FLOOR(
                `available_day_count` * `date_percent` / 100
            ) DAY
        ) AS `proposal_date`
    FROM `proposal_percentages`
),
`adjusted_proposal_dates` AS (
    SELECT
        `creator_pool_id`,
        `proposal_no`,
        CASE
            WHEN MOD(
                CRC32(CONCAT(
                    'proposal-weekday:',
                    `creator_pool_id`,
                    ':',
                    `proposal_no`
                )),
                100
            ) < 75
                 AND DAYOFWEEK(`proposal_date`) = 7
                THEN DATE_ADD(`proposal_date`, INTERVAL 2 DAY)
            WHEN MOD(
                CRC32(CONCAT(
                    'proposal-weekday:',
                    `creator_pool_id`,
                    ':',
                    `proposal_no`
                )),
                100
            ) < 75
                 AND DAYOFWEEK(`proposal_date`) = 1
                THEN DATE_ADD(`proposal_date`, INTERVAL 1 DAY)
            ELSE `proposal_date`
        END AS `proposal_date`
    FROM `raw_proposal_dates`
),
`timed_proposals` AS (
    SELECT
        `creator_pool_id`,
        `proposal_no`,
        `proposal_date`,
        MOD(
            CRC32(CONCAT(
                'proposal-time-bucket:',
                `creator_pool_id`,
                ':',
                `proposal_no`
            )),
            100
        ) AS `time_bucket`,
        CRC32(CONCAT(
            'proposal-time-second:',
            `creator_pool_id`,
            ':',
            `proposal_no`
        )) AS `time_seed`
    FROM `adjusted_proposal_dates`
),
`proposal_rows` AS (
    SELECT
        `creator_pool_id`,
        `proposal_no`,
        DATE_ADD(
            `proposal_date`,
            INTERVAL CASE
                WHEN `time_bucket` < 3
                    THEN 7 * 3600 + MOD(`time_seed`, 2 * 3600)
                WHEN `time_bucket` < 51
                    THEN 9 * 3600 + MOD(`time_seed`, 3 * 3600)
                WHEN `time_bucket` < 88
                    THEN 13 * 3600 + MOD(`time_seed`, 5 * 3600)
                WHEN `time_bucket` < 98
                    THEN 18 * 3600 + MOD(`time_seed`, 3 * 3600)
                ELSE 21 * 3600 + MOD(`time_seed`, 90 * 60)
            END SECOND
        ) AS `created_at`
    FROM `timed_proposals`
)
SELECT
    `p`.`creator_pool_id`,
    `a`.`admin_id`,
    `p`.`created_at`
FROM `proposal_rows` AS `p`
INNER JOIN `admin_pool` AS `a`
    ON `a`.`admin_rank` = MOD(
        CRC32(CONCAT(
            'proposal-admin:',
            `p`.`creator_pool_id`,
            ':',
            `p`.`proposal_no`
        )),
        `a`.`admin_count`
    ) + 1
ORDER BY
    `p`.`created_at`,
    `p`.`creator_pool_id`,
    `p`.`proposal_no`;

-- AUTO_INCREMENT는 실제 동적 생성 건수의 다음 값으로 자동 설정된다.

-- 검증 1: 전체 이력과 제안 대상 크리에이터 비율
SELECT
    (
        SELECT COUNT(*)
        FROM `creator_pool`
        WHERE `is_deleted` = 0
          AND `email` IS NOT NULL
          AND TRIM(`email`) <> ''
    ) AS `eligible_creator_count`,
    COUNT(DISTINCT `creator_id`) AS `proposed_creator_count`,
    COUNT(*) AS `proposal_history_count`,
    ROUND(
        COUNT(DISTINCT `creator_id`) * 100.0
        / NULLIF((
            SELECT COUNT(*)
            FROM `creator_pool`
            WHERE `is_deleted` = 0
              AND `email` IS NOT NULL
              AND TRIM(`email`) <> ''
        ), 0),
        2
    ) AS `proposed_creator_rate_percent`
FROM `proposal_history`;

-- 검증 2: 크리에이터별 제안 횟수 분포
SELECT
    `proposal_count`,
    COUNT(*) AS `creator_count`
FROM (
    SELECT
        `creator_id`,
        COUNT(*) AS `proposal_count`
    FROM `proposal_history`
    GROUP BY `creator_id`
) AS `creator_proposal_counts`
GROUP BY `proposal_count`
ORDER BY `proposal_count`;

-- 검증 3: 시간대 분포
SELECT
    CASE
        WHEN HOUR(`created_at`) BETWEEN 7 AND 8 THEN 'EARLY_MORNING_07_08'
        WHEN HOUR(`created_at`) BETWEEN 9 AND 11 THEN 'MORNING_09_11'
        WHEN HOUR(`created_at`) BETWEEN 13 AND 17 THEN 'AFTERNOON_13_17'
        WHEN HOUR(`created_at`) BETWEEN 18 AND 20 THEN 'EVENING_18_20'
        ELSE 'LATE_NIGHT_21_22'
    END AS `time_band`,
    COUNT(*) AS `proposal_count`
FROM `proposal_history`
GROUP BY `time_band`
ORDER BY MIN(HOUR(`created_at`));

-- 검증 4: 요일 분포와 관리자별 제안 건수
SELECT
    DAYOFWEEK(`created_at`) AS `day_of_week_no`,
    DAYNAME(`created_at`) AS `day_of_week`,
    COUNT(*) AS `proposal_count`
FROM `proposal_history`
GROUP BY DAYOFWEEK(`created_at`), DAYNAME(`created_at`)
ORDER BY `day_of_week_no`;

SELECT
    `a`.`admin_id`,
    `a`.`name`,
    COUNT(`p`.`proposal_history_id`) AS `proposal_count`
FROM `admin` AS `a`
LEFT JOIN `proposal_history` AS `p`
    ON `p`.`admin_id` = `a`.`admin_id`
WHERE `a`.`is_deleted` = 0
GROUP BY `a`.`admin_id`, `a`.`name`
ORDER BY `a`.`admin_id`;

-- 검증 5: 비활성/이메일 없는 크리에이터, 비활성 관리자 또는
--          기준일 이후의 잘못된 이력이 없어야 한다.
SELECT COUNT(*) AS `invalid_proposal_history_count`
FROM `proposal_history` AS `p`
INNER JOIN `creator_pool` AS `c`
    ON `c`.`creator_pool_id` = `p`.`creator_id`
INNER JOIN `admin` AS `a`
    ON `a`.`admin_id` = `p`.`admin_id`
WHERE `c`.`is_deleted` <> 0
   OR `c`.`email` IS NULL
   OR TRIM(`c`.`email`) = ''
   OR `a`.`is_deleted` <> 0
   OR `p`.`created_at` > @proposal_reference_end;

-- 기준일 당일 수집한 풀을 과거 이력으로 백필한 건수다.
-- 새로 수집한 creator_pool을 사용하면 0보다 클 수 있으며 오류가 아니다.
SELECT COUNT(*) AS `backfilled_before_pool_registration_count`
FROM `proposal_history` AS `p`
INNER JOIN `creator_pool` AS `c`
    ON `c`.`creator_pool_id` = `p`.`creator_id`
WHERE `c`.`created_at` IS NOT NULL
  AND `p`.`created_at` < `c`.`created_at`;

SET time_zone = @previous_session_time_zone;
