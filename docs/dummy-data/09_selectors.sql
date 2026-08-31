-- hi_selectors 로컬 테스트용 승인 완료 셀렉터스 데이터
--
-- 기준일: 2026-08-31
-- 구성: Instagram 6명
--   신규 3명: 10기부터 활동
--   재참여 2명: 9기 -> 10기
--   장기 활동 1명: 8기 -> 9기 -> 10기
-- 선행 스크립트: 03_generation.sql, 05_users.sql

USE `hi_selectors`;
SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;

DROP TEMPORARY TABLE IF EXISTS `tmp_approved_selectors_first_membership`;
DROP TEMPORARY TABLE IF EXISTS `tmp_approved_selectors_memberships`;
DROP TEMPORARY TABLE IF EXISTS `tmp_approved_selectors_accounts`;

CREATE TEMPORARY TABLE `tmp_approved_selectors_accounts` (
                                                             `seed_no` INT NOT NULL,
                                                             `account_id` VARCHAR(100) NOT NULL,
                                                             `hi_id` VARCHAR(20) NOT NULL,
                                                             `user_name` VARCHAR(50) NOT NULL,
                                                             `birth_date` DATE NOT NULL,
                                                             `gender` CHAR(2) NOT NULL,
                                                             `follower_count` BIGINT NOT NULL,
                                                             `content_count` BIGINT NOT NULL,
                                                             `last_content_at` DATETIME NOT NULL,
                                                             `profile_url` VARCHAR(500) NOT NULL,
                                                             `application_created_at` DATETIME NOT NULL,
                                                             `approved_at` DATETIME NOT NULL,

                                                             PRIMARY KEY (`seed_no`),
                                                             UNIQUE KEY `uq_tmp_approved_hi_id` (`hi_id`),
                                                             UNIQUE KEY `uq_tmp_approved_account` (`account_id`)
)
    ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_0900_ai_ci;


INSERT INTO `tmp_approved_selectors_accounts` (
    `seed_no`,
    `account_id`,
    `hi_id`,
    `user_name`,
    `birth_date`,
    `gender`,
    `follower_count`,
    `content_count`,
    `last_content_at`,
    `profile_url`,
    `application_created_at`,
    `approved_at`
)
VALUES
    (1,
     '_ejchoi',
     '_ejchoi',
     '최은지',
     '1995-03-18',
     '여',
     28400,
     417,
     '2026-08-27 19:42:00',
     'https://www.instagram.com/_ejchoi/',
     '2026-08-24 10:18:00',
     '2026-08-29 14:20:00'),

    (2,
     '_eunvitamin_',
     '_eunvitamin_',
     '김은비',
     '1997-11-06',
     '여',
     19700,
     362,
     '2026-08-29 12:16:00',
     'https://www.instagram.com/_eunvitamin_/',
     '2026-08-25 15:37:00',
     '2026-08-29 15:05:00'),

    (3,
     'by.ellenlee',
     'by.ellenlee',
     '이은서',
     '1993-06-22',
     '여',
     46300,
     589,
     '2026-08-28 21:08:00',
     'https://www.instagram.com/by.ellenlee/',
     '2026-08-25 20:44:00',
     '2026-08-29 16:40:00'),

    (4,
     'hyosun_kim_',
     'hyosun_kim_',
     '김효선',
     '1994-09-13',
     '여',
     32800,
     476,
     '2026-08-30 18:25:00',
     'https://www.instagram.com/hyosun_kim_/',
     '2026-08-26 09:52:00',
     '2026-08-30 11:15:00'),

    (5,
     'jennifer_wanna.b',
     'jennifer_wanna.b',
     '박제니',
     '1991-02-27',
     '여',
     74100,
     814,
     '2026-08-29 20:33:00',
     'https://www.instagram.com/jennifer_wanna.b/',
     '2026-08-26 14:21:00',
     '2026-08-30 13:50:00'),

    (6,
     'jieunisong',
     'jieunisong',
     '송지은',
     '1996-07-09',
     '여',
     25100,
     395,
     '2026-08-30 22:04:00',
     'https://www.instagram.com/jieunisong/',
     '2026-08-27 11:06:00',
     '2026-08-30 16:25:00');


-- =========================================================
-- 셀렉터스 기수 참여 이력
-- =========================================================

CREATE TEMPORARY TABLE `tmp_approved_selectors_memberships` (
                                                                `seed_no` INT NOT NULL,
                                                                `generation_id` BIGINT NOT NULL,
                                                                `joined_at` DATETIME NOT NULL,
                                                                `total_sales` BIGINT NOT NULL,
                                                                `confirmed_purchase_count` BIGINT NOT NULL,
                                                                `paid_commission_amount` BIGINT NOT NULL,

                                                                PRIMARY KEY (`seed_no`, `generation_id`)
)
    ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_0900_ai_ci;


INSERT INTO `tmp_approved_selectors_memberships` (
    `seed_no`,
    `generation_id`,
    `joined_at`,
    `total_sales`,
    `confirmed_purchase_count`,
    `paid_commission_amount`
)
VALUES
    -- 10기 신규 활동자 3명
    (1, 10, '2026-08-29 14:20:00',       0,  0,      0),
    (5, 10, '2026-08-30 13:50:00',       0,  0,      0),
    (6, 10, '2026-08-30 16:25:00',       0,  0,      0),

    -- 9기 -> 10기
    (2,  9, '2026-04-02 15:05:00', 1860000, 24,  93000),
    (2, 10, '2026-07-01 00:25:00',       0,  0,      0),

    (3,  9, '2026-04-03 16:40:00', 4530000, 51, 226500),
    (3, 10, '2026-07-01 00:31:00',       0,  0,      0),

    -- 8기 -> 9기 -> 10기
    (4,  8, '2026-01-02 11:15:00', 2740000, 34, 137000),
    (4,  9, '2026-04-01 00:28:00', 5180000, 63, 259000),
    (4, 10, '2026-07-01 00:34:00',       0,  0,      0);


-- =========================================================
-- 핵심 수정 부분
--
-- 각 셀렉터스의 최초 활동 시각을 별도 TEMPORARY TABLE로 계산한다.
-- 이후 같은 SQL에서 memberships를 여러 번 열 필요가 없어진다.
-- =========================================================

CREATE TEMPORARY TABLE `tmp_approved_selectors_first_membership` (
                                                                     `seed_no` INT NOT NULL,
                                                                     `first_joined_at` DATETIME NOT NULL,
                                                                     PRIMARY KEY (`seed_no`)
)
    ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_0900_ai_ci;


INSERT INTO `tmp_approved_selectors_first_membership` (
    `seed_no`,
    `first_joined_at`
)
SELECT
    `seed_no`,
    MIN(`joined_at`)
FROM `tmp_approved_selectors_memberships`
GROUP BY `seed_no`;


-- =========================================================
-- Seed 검증
-- =========================================================

SELECT
    SUM(CHAR_LENGTH(`hi_id`) > 20)
        AS `invalid_hi_id_length_count`,

    SUM(CHAR_LENGTH(`user_name`) <> 3)
        AS `invalid_name_length_count`,

    SUM(`account_id` NOT REGEXP '^[A-Za-z0-9._]{1,30}$')
        AS `invalid_instagram_id_count`,

    SUM(`approved_at` < `application_created_at`)
        AS `invalid_approval_time_count`

FROM `tmp_approved_selectors_accounts`;


START TRANSACTION;


-- =========================================================
-- 1. 더현대Hi 사용자 생성
--
-- 수정:
-- tmp_approved_selectors_memberships를 SELECT에서 두 번 읽지 않는다.
-- first_membership을 한 번 JOIN해서 first_joined_at을 재사용한다.
-- =========================================================

INSERT INTO `users` (
    `hi_id`,
    `hi_password`,
    `name`,
    `birth_date`,
    `gender`,
    `email`,
    `phone`,
    `created_at`,
    `updated_at`,
    `is_deleted`,
    `alimtalk`
)
SELECT
    `seed`.`hi_id`,

    '$2a$10$yqyTDn5aDasCKPMZTGc3LuF6SaFqcZsYKG6G2D4yFhtjslZeJcpaW',

    `seed`.`user_name`,
    `seed`.`birth_date`,
    `seed`.`gender`,

    CONCAT(
            REPLACE(`seed`.`hi_id`, '.', '_'),
            '@selectors.example.com'
    ),

    CONCAT(
            '010-0099-',
            LPAD(`seed`.`seed_no`, 4, '0')
    ),

    LEAST(
            DATE_SUB(
                    DATE_SUB(
                            `seed`.`application_created_at`,
                            INTERVAL 120 DAY
                    ),
                    INTERVAL (`seed`.`seed_no` * 17) DAY
            ),
            DATE_SUB(
                    `first_membership`.`first_joined_at`,
                    INTERVAL 90 DAY
            )
    ),

    LEAST(
            DATE_SUB(
                    DATE_SUB(
                            `seed`.`application_created_at`,
                            INTERVAL 120 DAY
                    ),
                    INTERVAL (`seed`.`seed_no` * 17) DAY
            ),
            DATE_SUB(
                    `first_membership`.`first_joined_at`,
                    INTERVAL 90 DAY
            )
    ),

    0,
    'N'

FROM `tmp_approved_selectors_accounts` AS `seed`

         JOIN `tmp_approved_selectors_first_membership` AS `first_membership`
              ON `first_membership`.`seed_no` = `seed`.`seed_no`

WHERE NOT EXISTS (
    SELECT 1
    FROM `users` AS `existing_user`
    WHERE `existing_user`.`hi_id` = `seed`.`hi_id`
);


-- =========================================================
-- 활성 기수
-- =========================================================

SET @active_generation_id := (
    SELECT `generation_id`
    FROM `generation`
    WHERE `status` = 'ACTIVE'
      AND CURRENT_TIMESTAMP BETWEEN `start_date` AND `end_date`
    ORDER BY `start_date` ASC
    LIMIT 1
);


-- =========================================================
-- 2. 승인 완료 지원서 생성
-- =========================================================

INSERT INTO `application` (
    `user_id`,
    `sns_code`,
    `generation_id`,
    `admin_id`,
    `sns_account_id`,
    `policy_agreed_at`,
    `alarm_yn`,
    `follower_count`,
    `content_count`,
    `last_content_at`,
    `engagement_rate`,
    `inspected_at`,
    `created_at`,
    `updated_at`,
    `status`,
    `media_collection_status`,
    `media_collection_retry_count`,
    `media_collected_at`,
    `media_collection_error`,
    `analysis_status`,
    `analysis_retry_count`,
    `analyzed_at`,
    `analysis_error`,
    `profile_url`,
    `profile_image_url`
)
SELECT
    `u`.`user_id`,
    'INSTAGRAM',
    @active_generation_id,
    NULL,
    `seed`.`account_id`,
    `seed`.`application_created_at`,
    b'0',
    `seed`.`follower_count`,
    `seed`.`content_count`,
    `seed`.`last_content_at`,
    NULL,
    NULL,
    `seed`.`application_created_at`,
    `seed`.`approved_at`,
    'APPROVED',

    'PENDING',
    0,
    NULL,
    NULL,

    'PENDING',
    0,
    NULL,
    NULL,

    `seed`.`profile_url`,
    NULL

FROM `tmp_approved_selectors_accounts` AS `seed`

         JOIN `users` AS `u`
              ON `u`.`user_id` = (
                  SELECT MIN(`matched_user`.`user_id`)
                  FROM `users` AS `matched_user`
                  WHERE `matched_user`.`hi_id` = `seed`.`hi_id`
              )

WHERE NOT EXISTS (
    SELECT 1
    FROM `application` AS `existing_application`
    WHERE `existing_application`.`user_id` = `u`.`user_id`
      AND `existing_application`.`generation_id` = @active_generation_id
);


-- 기존 지원서가 있으면 승인 상태로 맞춘다.

UPDATE `application` AS `app`

    JOIN `users` AS `u`
    ON `u`.`user_id` = `app`.`user_id`

    JOIN `tmp_approved_selectors_accounts` AS `seed`
    ON `seed`.`hi_id` = `u`.`hi_id`

SET
    `app`.`status` = 'APPROVED',

    `app`.`updated_at` =
        GREATEST(
                COALESCE(
                        `app`.`updated_at`,
                        `seed`.`approved_at`
                ),
                `seed`.`approved_at`
        )

WHERE `app`.`generation_id` = @active_generation_id;


-- =========================================================
-- 3. 셀렉터스 생성
--
-- 최초 가입 시각은 first_membership에서 가져온다.
-- =========================================================

INSERT INTO `selectors` (
    `application_id`,
    `user_id`,
    `selectors_role_id`,
    `selectors_code`,
    `selectors_nickname`,
    `category`,
    `created_at`,
    `updated_at`,
    `is_deleted`
)
SELECT
    `app`.`application_id`,
    `app`.`user_id`,
    'ACTIVE',

    CONCAT(
            'RC',
            LPAD(
                    `app`.`application_id` * 2003 - 806,
                    9,
                    '0'
            ),
            'T'
    ),

    `seed`.`user_name`,
    NULL,
    `first_membership`.`first_joined_at`,
    `seed`.`approved_at`,
    0

FROM `tmp_approved_selectors_accounts` AS `seed`

         JOIN `tmp_approved_selectors_first_membership` AS `first_membership`
              ON `first_membership`.`seed_no` = `seed`.`seed_no`

         JOIN `users` AS `u`
              ON `u`.`user_id` = (
                  SELECT MIN(`matched_user`.`user_id`)
                  FROM `users` AS `matched_user`
                  WHERE `matched_user`.`hi_id` = `seed`.`hi_id`
              )

         JOIN `application` AS `app`
              ON `app`.`user_id` = `u`.`user_id`
                  AND `app`.`generation_id` = @active_generation_id

WHERE NOT EXISTS (
    SELECT 1
    FROM `selectors` AS `existing_selectors`
    WHERE `existing_selectors`.`user_id` = `u`.`user_id`
);


-- 기존 셀렉터스가 있다면 활성화

UPDATE `selectors` AS `s`

    JOIN `users` AS `u`
    ON `u`.`user_id` = `s`.`user_id`

    JOIN `tmp_approved_selectors_accounts` AS `seed`
    ON `seed`.`hi_id` = `u`.`hi_id`

    JOIN `tmp_approved_selectors_first_membership` AS `first_membership`
    ON `first_membership`.`seed_no` = `seed`.`seed_no`

    JOIN `application` AS `app`
    ON `app`.`user_id` = `u`.`user_id`
        AND `app`.`generation_id` = @active_generation_id

SET
    `s`.`application_id` = `app`.`application_id`,
    `s`.`selectors_role_id` = 'ACTIVE',
    `s`.`is_deleted` = 0,

    `s`.`created_at` =
        LEAST(
                COALESCE(
                        `s`.`created_at`,
                        `seed`.`approved_at`
                ),
                `first_membership`.`first_joined_at`
        ),

    `s`.`updated_at` = `seed`.`approved_at`;


-- =========================================================
-- 4. 기수 참여 이력 생성
-- =========================================================

INSERT INTO `selectors_generation` (
    `selectors_id`,
    `generation_id`,
    `created_at`,
    `total_sales`,
    `confirmed_purchase_count`,
    `paid_commission_amount`
)
SELECT
    `s`.`selectors_id`,
    `membership`.`generation_id`,
    `membership`.`joined_at`,
    `membership`.`total_sales`,
    `membership`.`confirmed_purchase_count`,
    `membership`.`paid_commission_amount`

FROM `tmp_approved_selectors_memberships` AS `membership`

         JOIN `tmp_approved_selectors_accounts` AS `seed`
              ON `seed`.`seed_no` = `membership`.`seed_no`

         JOIN `users` AS `u`
              ON `u`.`user_id` = (
                  SELECT MIN(`matched_user`.`user_id`)
                  FROM `users` AS `matched_user`
                  WHERE `matched_user`.`hi_id` = `seed`.`hi_id`
              )

         JOIN `selectors` AS `s`
              ON `s`.`user_id` = `u`.`user_id`

WHERE NOT EXISTS (
    SELECT 1
    FROM `selectors_generation` AS `existing_generation`
    WHERE `existing_generation`.`selectors_id` = `s`.`selectors_id`
      AND `existing_generation`.`generation_id`
        = `membership`.`generation_id`
);


-- 재실행 시 현재 이력이 아직 집계되지 않았다면 가입시각 보정

UPDATE `selectors_generation` AS `sg`

    JOIN `selectors` AS `s`
    ON `s`.`selectors_id` = `sg`.`selectors_id`

    JOIN `users` AS `u`
    ON `u`.`user_id` = `s`.`user_id`

    JOIN `tmp_approved_selectors_accounts` AS `seed`
    ON `seed`.`hi_id` = `u`.`hi_id`

    JOIN `tmp_approved_selectors_memberships` AS `membership`
    ON `membership`.`seed_no` = `seed`.`seed_no`
        AND `membership`.`generation_id` = `sg`.`generation_id`

SET
    `sg`.`created_at` = `membership`.`joined_at`

WHERE `sg`.`total_sales` = 0
  AND `sg`.`confirmed_purchase_count` = 0
  AND `sg`.`paid_commission_amount` = 0;


-- =========================================================
-- 5. Instagram 대표 SNS 계정 생성
-- =========================================================

INSERT INTO `selectors_sns_account` (
    `created_at`,
    `updated_at`,
    `account_id`,
    `profile_url`,
    `is_deleted`,
    `follower_count`,
    `last_collected_at`,
    `profile_image_url`,
    `selectors_id`,
    `sns_code`
)
SELECT
    `seed`.`approved_at`,
    `seed`.`approved_at`,
    `seed`.`account_id`,
    `seed`.`profile_url`,
    b'0',
    `seed`.`follower_count`,
    NULL,
    NULL,
    `s`.`selectors_id`,
    'INSTAGRAM'

FROM `tmp_approved_selectors_accounts` AS `seed`

         JOIN `users` AS `u`
              ON `u`.`user_id` = (
                  SELECT MIN(`matched_user`.`user_id`)
                  FROM `users` AS `matched_user`
                  WHERE `matched_user`.`hi_id` = `seed`.`hi_id`
              )

         JOIN `selectors` AS `s`
              ON `s`.`user_id` = `u`.`user_id`

WHERE NOT EXISTS (
    SELECT 1
    FROM `selectors_sns_account` AS `existing_account`
    WHERE `existing_account`.`selectors_id` = `s`.`selectors_id`
);


UPDATE `selectors_sns_account` AS `ssa`

    JOIN `selectors` AS `s`
    ON `s`.`selectors_id` = `ssa`.`selectors_id`

    JOIN `users` AS `u`
    ON `u`.`user_id` = `s`.`user_id`

    JOIN `tmp_approved_selectors_accounts` AS `seed`
    ON `seed`.`hi_id` = `u`.`hi_id`

SET
    `ssa`.`sns_code` = 'INSTAGRAM',
    `ssa`.`account_id` = `seed`.`account_id`,
    `ssa`.`profile_url` = `seed`.`profile_url`,
    `ssa`.`follower_count` = `seed`.`follower_count`,
    `ssa`.`is_deleted` = b'0',
    `ssa`.`updated_at` = `seed`.`approved_at`;


COMMIT;


-- =========================================================
-- 검증 1
-- =========================================================

SELECT
    `seed`.`account_id`,
    `u`.`user_id`,
    `app`.`application_id`,
    `app`.`status` AS `application_status`,
    `app`.`media_collection_status`,
    `app`.`analysis_status`,
    `s`.`selectors_id`,
    `s`.`selectors_role_id`,
    `s`.`category`,
    `sg`.`generation_id`,
    `ssa`.`sns_code`,
    `ssa`.`follower_count`

FROM `tmp_approved_selectors_accounts` AS `seed`

         JOIN `users` AS `u`
              ON `u`.`user_id` = (
                  SELECT MIN(`matched_user`.`user_id`)
                  FROM `users` AS `matched_user`
                  WHERE `matched_user`.`hi_id` = `seed`.`hi_id`
              )

         JOIN `application` AS `app`
              ON `app`.`user_id` = `u`.`user_id`
                  AND `app`.`generation_id` = @active_generation_id

         JOIN `selectors` AS `s`
              ON `s`.`user_id` = `u`.`user_id`

         JOIN `selectors_generation` AS `sg`
              ON `sg`.`selectors_id` = `s`.`selectors_id`
                  AND `sg`.`generation_id` = @active_generation_id

         JOIN `selectors_sns_account` AS `ssa`
              ON `ssa`.`selectors_id` = `s`.`selectors_id`

ORDER BY `seed`.`seed_no`;


-- =========================================================
-- 검증 2
-- =========================================================

SELECT
    SUM(`app`.`status` <> 'APPROVED')
        AS `not_approved_count`,

    SUM(
            `s`.`selectors_role_id` <> 'ACTIVE'
                OR `s`.`is_deleted` <> 0
    ) AS `inactive_selectors_count`,

    SUM(`sg`.`selectors_generation_id` IS NULL)
        AS `missing_generation_count`,

    SUM(`ssa`.`selectors_sns_account_id` IS NULL)
        AS `missing_sns_account_count`,

    SUM(`report`.`application_report_id` IS NOT NULL)
        AS `unexpected_report_count`,

    SUM(`s`.`category` IS NULL)
        AS `pending_category_count`

FROM `tmp_approved_selectors_accounts` AS `seed`

         JOIN `users` AS `u`
              ON `u`.`user_id` = (
                  SELECT MIN(`matched_user`.`user_id`)
                  FROM `users` AS `matched_user`
                  WHERE `matched_user`.`hi_id` = `seed`.`hi_id`
              )

         JOIN `application` AS `app`
              ON `app`.`user_id` = `u`.`user_id`
                  AND `app`.`generation_id` = @active_generation_id

         JOIN `selectors` AS `s`
              ON `s`.`user_id` = `u`.`user_id`

         LEFT JOIN `selectors_generation` AS `sg`
                   ON `sg`.`selectors_id` = `s`.`selectors_id`
                       AND `sg`.`generation_id` = @active_generation_id

         LEFT JOIN `selectors_sns_account` AS `ssa`
                   ON `ssa`.`selectors_id` = `s`.`selectors_id`

         LEFT JOIN `application_report` AS `report`
                   ON `report`.`application_id` = `app`.`application_id`;


-- =========================================================
-- 검증 3
-- 총 10개의 기수 참여 행이 생성되어야 한다.
-- =========================================================

SELECT
    `seed`.`account_id`,
    `g`.`generation_name`,
    `sg`.`created_at` AS `joined_at`,
    `sg`.`total_sales`,
    `sg`.`confirmed_purchase_count`,
    `sg`.`paid_commission_amount`

FROM `tmp_approved_selectors_memberships` AS `membership`

         JOIN `tmp_approved_selectors_accounts` AS `seed`
              ON `seed`.`seed_no` = `membership`.`seed_no`

         JOIN `users` AS `u`
              ON `u`.`user_id` = (
                  SELECT MIN(`matched_user`.`user_id`)
                  FROM `users` AS `matched_user`
                  WHERE `matched_user`.`hi_id` = `seed`.`hi_id`
              )

         JOIN `selectors` AS `s`
              ON `s`.`user_id` = `u`.`user_id`

         JOIN `selectors_generation` AS `sg`
              ON `sg`.`selectors_id` = `s`.`selectors_id`
                  AND `sg`.`generation_id` = `membership`.`generation_id`

         JOIN `generation` AS `g`
              ON `g`.`generation_id` = `sg`.`generation_id`

ORDER BY
    `seed`.`seed_no`,
    `g`.`generation_id`;


SELECT
    COUNT(*) AS `expected_membership_count`,

    SUM(`sg`.`selectors_generation_id` IS NULL)
             AS `missing_membership_count`,

    SUM(
            `membership`.`joined_at`
                NOT BETWEEN `g`.`start_date` AND `g`.`end_date`
    ) AS `out_of_period_membership_count`

FROM `tmp_approved_selectors_memberships` AS `membership`

         JOIN `tmp_approved_selectors_accounts` AS `seed`
              ON `seed`.`seed_no` = `membership`.`seed_no`

         JOIN `users` AS `u`
              ON `u`.`user_id` = (
                  SELECT MIN(`matched_user`.`user_id`)
                  FROM `users` AS `matched_user`
                  WHERE `matched_user`.`hi_id` = `seed`.`hi_id`
              )

         JOIN `selectors` AS `s`
              ON `s`.`user_id` = `u`.`user_id`

         LEFT JOIN `selectors_generation` AS `sg`
                   ON `sg`.`selectors_id` = `s`.`selectors_id`
                       AND `sg`.`generation_id` = `membership`.`generation_id`

         JOIN `generation` AS `g`
              ON `g`.`generation_id` = `membership`.`generation_id`;


DROP TEMPORARY TABLE IF EXISTS `tmp_approved_selectors_first_membership`;
DROP TEMPORARY TABLE IF EXISTS `tmp_approved_selectors_memberships`;
DROP TEMPORARY TABLE IF EXISTS `tmp_approved_selectors_accounts`;