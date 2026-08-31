-- hi_selectors 로컬 성능 테스트용 기수 데이터
--
-- 기준일: 2026-08-31
-- 구성: 총 10개 기수, 진행 중 1개, 종료 9개
-- 모집 기간과 활동 기간은 동일하며, 각 기수는 연속된 3개월 동안 활동한다.
-- 시작 시각은 해당 분기 첫 달 1일 00:00:00,
-- 종료 시각은 마지막 달 말일 23:59:59로 고정한다.
--
-- 현재 진행 중인 10기는 2026-09-30까지 활동한다.
-- 종료 기수의 selector_excellence_selected_at은 활동 종료 7일 후
-- 우수 활동자 선정 배치가 실행된 시각(00:20)으로 계산했다.
--
-- 이 스크립트는 generation 하위 테이블이 비어 있고
-- 더미 데이터 적재 중 스케줄러가 중지된 상태에서 실행한다.

USE `hi_selectors`;

DELETE FROM `generation`;
ALTER TABLE `generation` AUTO_INCREMENT = 1;

INSERT INTO `generation` (
    `generation_id`,
    `generation_name`,
    `start_date`,
    `end_date`,
    `activity_start_date`,
    `activity_end_date`,
    `selector_excellence_selected_at`,
    `status`
)
VALUES
    (1,
     '셀렉터스 1기',
     '2024-04-01 00:00:00', '2024-06-30 23:59:59',
     '2024-04-01 00:00:00', '2024-06-30 23:59:59',
     '2024-07-07 00:20:00',
     'INACTIVE'),

    (2,
     '셀렉터스 2기',
     '2024-07-01 00:00:00', '2024-09-30 23:59:59',
     '2024-07-01 00:00:00', '2024-09-30 23:59:59',
     '2024-10-07 00:20:00',
     'INACTIVE'),

    (3,
     '셀렉터스 3기',
     '2024-10-01 00:00:00', '2024-12-31 23:59:59',
     '2024-10-01 00:00:00', '2024-12-31 23:59:59',
     '2025-01-07 00:20:00',
     'INACTIVE'),

    (4,
     '셀렉터스 4기',
     '2025-01-01 00:00:00', '2025-03-31 23:59:59',
     '2025-01-01 00:00:00', '2025-03-31 23:59:59',
     '2025-04-07 00:20:00',
     'INACTIVE'),

    (5,
     '셀렉터스 5기',
     '2025-04-01 00:00:00', '2025-06-30 23:59:59',
     '2025-04-01 00:00:00', '2025-06-30 23:59:59',
     '2025-07-07 00:20:00',
     'INACTIVE'),

    (6,
     '셀렉터스 6기',
     '2025-07-01 00:00:00', '2025-09-30 23:59:59',
     '2025-07-01 00:00:00', '2025-09-30 23:59:59',
     '2025-10-07 00:20:00',
     'INACTIVE'),

    (7,
     '셀렉터스 7기',
     '2025-10-01 00:00:00', '2025-12-31 23:59:59',
     '2025-10-01 00:00:00', '2025-12-31 23:59:59',
     '2026-01-07 00:20:00',
     'INACTIVE'),

    (8,
     '셀렉터스 8기',
     '2026-01-01 00:00:00', '2026-03-31 23:59:59',
     '2026-01-01 00:00:00', '2026-03-31 23:59:59',
     '2026-04-07 00:20:00',
     'INACTIVE'),

    (9,
     '셀렉터스 9기',
     '2026-04-01 00:00:00', '2026-06-30 23:59:59',
     '2026-04-01 00:00:00', '2026-06-30 23:59:59',
     '2026-07-07 00:20:00',
     'INACTIVE'),

    (10,
     '셀렉터스 10기',
     '2026-07-01 00:00:00', '2026-09-30 23:59:59',
     '2026-07-01 00:00:00', '2026-09-30 23:59:59',
     NULL,
     'ACTIVE');

ALTER TABLE `generation` AUTO_INCREMENT = 11;

-- 상태 분포 검증: ACTIVE 1건, INACTIVE 9건이어야 한다.
SELECT
    `status`,
    COUNT(*) AS `generation_count`
FROM `generation`
GROUP BY `status`
ORDER BY FIELD(`status`, 'ACTIVE', 'INACTIVE');

-- 기간 및 파생 상태 검증.
SELECT
    `generation_id`,
    `generation_name`,
    `start_date`,
    `end_date`,
    `activity_start_date`,
    `activity_end_date`,
    TIMESTAMPDIFF(
            MONTH,
            DATE(`activity_start_date`),
            DATE_ADD(DATE(`activity_end_date`), INTERVAL 1 DAY)
    ) AS `activity_months`,
    CASE
        WHEN TIMESTAMP('2026-08-31 23:59:59') < `activity_start_date` THEN 'SCHEDULED'
        WHEN TIMESTAMP('2026-08-31 00:00:00') > `activity_end_date` THEN 'ENDED'
        ELSE 'IN_PROGRESS'
        END AS `period_status`,
    `selector_excellence_selected_at`,
    `status`
FROM `generation`
ORDER BY `generation_id`;

-- 모집 기간과 활동 기간 불일치, 월 경계 오류 및 기간 겹침이 모두 0건이어야 한다.
SELECT
    SUM(
            `start_date` <> `activity_start_date`
                OR `end_date` <> `activity_end_date`
    ) AS `recruitment_activity_mismatch_count`,

    SUM(
            DAY(`activity_start_date`) <> 1
                OR TIME(`activity_start_date`) <> '00:00:00'
                OR DATE(`activity_end_date`) <> LAST_DAY(`activity_end_date`)
                OR TIME(`activity_end_date`) <> '23:59:59'
    ) AS `month_boundary_error_count`,

    SUM(
            TIMESTAMPDIFF(
                    MONTH,
                    DATE(`activity_start_date`),
                    DATE_ADD(DATE(`activity_end_date`), INTERVAL 1 DAY)
            ) <> 3
    ) AS `activity_duration_error_count`
FROM `generation`;

SELECT
    COUNT(*) AS `overlap_count`
FROM `generation` AS earlier
         JOIN `generation` AS later
              ON earlier.`generation_id` < later.`generation_id`
                  AND earlier.`activity_start_date` <= later.`activity_end_date`
                  AND earlier.`activity_end_date` >= later.`activity_start_date`;