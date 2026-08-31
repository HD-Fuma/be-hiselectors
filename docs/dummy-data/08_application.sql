-- hi_selectors 로컬 테스트용 실제 공개 SNS 계정 기반 지원서 데이터
--
-- 기준일: 2026-08-31
-- 구성: 첨부 화면에 존재하는 계정만 적재
--       YouTube 9명 + Instagram 12명 = 총 21명
-- 선행 스크립트: 03_generation.sql, 05_users.sql
--
-- 사용자:
--   users.user_id는 명시하지 않고 AUTO_INCREMENT가 자동으로 배정한다.
--   hi_id는 SNS 핸들에서 @를 제거한 값이다.
--   users.hi_id가 varchar(20)이므로 life.practice.project는 life.practice.proj로 축약한다.
--   이름은 로컬 테스트용 가명이다.
--   비밀번호는 전부 0000이며 05_users.sql과 동일한 BCrypt 해시를 사용한다.
--
-- 지원서:
--   실행 시점에 기간상 진행 중이며 status=ACTIVE인 기수 1건에 신청한다.
--   21건 모두 미디어 수집 및 콘텐츠 분석 배치 대상이다.
--   적재 중에는 SCHEDULING_ENABLED=false로 두고,
--   적재 및 검증이 끝난 뒤 켜는 것을 권장한다.

USE `hi_selectors`;

SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;


DROP TEMPORARY TABLE IF EXISTS `tmp_application_accounts`;

CREATE TEMPORARY TABLE `tmp_application_accounts` (
                                                      `seed_no` INT NOT NULL,
                                                      `source_key` BIGINT NOT NULL COMMENT '원본 목록 식별용 값이며 users.user_id가 아님',
                                                      `sns_code` VARCHAR(20) NOT NULL,
                                                      `channel_name` VARCHAR(100) NOT NULL,
                                                      `account_id` VARCHAR(200) NOT NULL,
                                                      `hi_id` VARCHAR(20) NOT NULL,
                                                      `user_name` VARCHAR(50) NOT NULL,
                                                      `birth_date` DATE NOT NULL,
                                                      `gender` CHAR(2) NOT NULL,
                                                      `profile_url` VARCHAR(500) NOT NULL,

                                                      PRIMARY KEY (`seed_no`),
                                                      UNIQUE KEY `uq_tmp_application_source` (`source_key`),
                                                      UNIQUE KEY `uq_tmp_application_hi_id` (`hi_id`),
                                                      UNIQUE KEY `uq_tmp_application_sns_account` (`sns_code`, `account_id`)
)
    ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_0900_ai_ci;


-- =========================================================
-- 첨부 화면에 존재하는 계정 21명만 적재
-- =========================================================

INSERT INTO `tmp_application_accounts` (
    `seed_no`,
    `source_key`,
    `sns_code`,
    `channel_name`,
    `account_id`,
    `hi_id`,
    `user_name`,
    `birth_date`,
    `gender`,
    `profile_url`
)
VALUES

    -- =====================================================
    -- YouTube 9명
    -- =====================================================

    (1,
     100006,
     'YOUTUBE',
     '김지유 JIYU KIM',
     '@JIYU_KIM',
     'JIYU_KIM',
     '김지유',
     '1995-04-11',
     '여',
     'https://www.youtube.com/@JIYU_KIM'),

    (2,
     100007,
     'YOUTUBE',
     '제이미포유 Jaymeeforyou',
     '@jaymeeforyou',
     'jaymeeforyou',
     '이재미',
     '1993-10-03',
     '여',
     'https://www.youtube.com/@jaymeeforyou'),

    (3,
     100009,
     'YOUTUBE',
     '메이뮤 maymew',
     '@maymew',
     'maymew',
     '윤미유',
     '2000-07-08',
     '여',
     'https://www.youtube.com/@maymew'),

    (4,
     100010,
     'YOUTUBE',
     'RISABAE',
     '@RISABAE',
     'RISABAE',
     '이사배',
     '1991-09-13',
     '여',
     'https://www.youtube.com/@RISABAE'),

    (5,
     100016,
     'YOUTUBE',
     '안다 ANDA',
     '@ANDA',
     'ANDA',
     '김안다',
     '1995-05-25',
     '여',
     'https://www.youtube.com/@ANDA'),

    (6,
     100019,
     'YOUTUBE',
     '시도 sido',
     '@sido',
     'sido',
     '윤시도',
     '1996-12-24',
     '여',
     'https://www.youtube.com/@sido'),

    (7,
     100020,
     'YOUTUBE',
     'sookoh 수코',
     '@sookohaseyo',
     'sookohaseyo',
     '오수경',
     '1991-04-04',
     '여',
     'https://www.youtube.com/@sookohaseyo'),

    (8,
     100026,
     'YOUTUBE',
     '유리아YuRia',
     '@yuria',
     'yuria',
     '이유리',
     '1992-11-23',
     '여',
     'https://www.youtube.com/@yuria'),

    (9,
     100039,
     'YOUTUBE',
     '쁨이bbeume',
     '@bbeume',
     'bbeume',
     '이보미',
     '2001-01-15',
     '여',
     'https://www.youtube.com/@bbeume'),


    -- =====================================================
    -- Instagram 12명
    -- =====================================================

    (10,
     100042,
     'INSTAGRAM',
     '유머쥡',
     'humorzzip',
     'humorzzip',
     '박유진',
     '1996-06-21',
     '여',
     'https://www.instagram.com/humorzzip/'),

    (11,
     100043,
     'INSTAGRAM',
     '손지',
     'sonji7897',
     'sonji7897',
     '이손지',
     '1998-12-11',
     '여',
     'https://www.instagram.com/sonji7897/'),

    (12,
     100044,
     'INSTAGRAM',
     'movie0n_do',
     'movie0n_do',
     'movie0n_do',
     '최민도',
     '1991-02-05',
     '남',
     'https://www.instagram.com/movie0n_do/'),

    (13,
     100045,
     'INSTAGRAM',
     '보라앤드',
     'bora_and',
     'bora_and',
     '김보라',
     '1995-07-19',
     '여',
     'https://www.instagram.com/bora_and/'),

    (14,
     100046,
     'INSTAGRAM',
     '연희걷다',
     'life.practice.project',
     'life.practice.proj',
     '박연희',
     '1993-09-28',
     '여',
     'https://www.instagram.com/life.practice.project/'),

    (15,
     100049,
     'INSTAGRAM',
     '모어뎁트 | MOREDEPT',
     'moredept',
     'moredept',
     '이도윤',
     '1989-05-06',
     '남',
     'https://www.instagram.com/moredept/'),

    (16,
     100056,
     'INSTAGRAM',
     '웹툰프렌즈 WEBTOON FRIENDS',
     'naver_webtoon',
     'naver_webtoon',
     '이우진',
     '1993-12-25',
     '남',
     'https://www.instagram.com/naver_webtoon/'),

    (17,
     100059,
     'INSTAGRAM',
     '풋풋레터',
     'putput.letter',
     'putput.letter',
     '김지은',
     '1997-11-04',
     '여',
     'https://www.instagram.com/putput.letter/'),

    (18,
     100060,
     'INSTAGRAM',
     '삼각점 | traiangle_club',
     'traiangle_club',
     'traiangle_club',
     '이태린',
     '1994-03-22',
     '여',
     'https://www.instagram.com/traiangle_club/'),

    (19,
     100061,
     'INSTAGRAM',
     '프롬푸딩 | AI 매거진',
     'prom.pudding',
     'prom.pudding',
     '박지수',
     '1998-06-10',
     '여',
     'https://www.instagram.com/prom.pudding/'),

    (20,
     100062,
     'INSTAGRAM',
     '삐',
     'bbichive_',
     'bbichive_',
     '김보민',
     '2001-02-27',
     '여',
     'https://www.instagram.com/bbichive_/'),

    (21,
     100064,
     'INSTAGRAM',
     'Shellness',
     'shellness.kr',
     'shellness.kr',
     '김서현',
     '1990-07-07',
     '여',
     'https://www.instagram.com/shellness.kr/');


-- =========================================================
-- Seed 데이터 검증
-- 결과는 반드시 0이어야 한다.
-- =========================================================

SELECT COUNT(*) AS `invalid_seed_count`
FROM `tmp_application_accounts`
WHERE CHAR_LENGTH(`hi_id`) > 20

   OR CHAR_LENGTH(`user_name`) <> 3

   OR (
    `sns_code` = 'INSTAGRAM'
        AND `account_id` NOT REGEXP '^[A-Za-z0-9._]{1,30}$'
    )

   OR (
    `sns_code` = 'YOUTUBE'
        AND `account_id` NOT REGEXP '^@[^/[:space:]]{1,100}$'
    );


-- =========================================================
-- 더현대Hi 사용자 21명 추가
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
    `a`.`hi_id`,

    '$2a$10$yqyTDn5aDasCKPMZTGc3LuF6SaFqcZsYKG6G2D4yFhtjslZeJcpaW',

    `a`.`user_name`,
    `a`.`birth_date`,
    `a`.`gender`,

    CONCAT(
            LOWER(REPLACE(`a`.`hi_id`, '.', '_')),
            '@creator.example.com'
    ),

    CONCAT(
            '010-0000-',
            LPAD(`a`.`seed_no`, 4, '0')
    ),

    DATE_ADD(
            DATE_ADD(
                    '2024-01-01 08:00:00',
                    INTERVAL MOD(`a`.`seed_no` * 83, 880) DAY
            ),
            INTERVAL MOD(`a`.`seed_no` * 7919, 43200) SECOND
    ),

    DATE_ADD(
            DATE_ADD(
                    '2024-01-01 08:00:00',
                    INTERVAL MOD(`a`.`seed_no` * 83, 880) DAY
            ),
            INTERVAL MOD(`a`.`seed_no` * 7919, 43200) SECOND
    ),

    0,
    'N'

FROM `tmp_application_accounts` AS `a`

WHERE NOT EXISTS (
    SELECT 1
    FROM `users` AS `existing_user`
    WHERE `existing_user`.`hi_id` = `a`.`hi_id`
);


-- =========================================================
-- 현재 ACTIVE 기수 선택
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
-- 지원서 21건 생성
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
    `a`.`sns_code`,
    @active_generation_id,
    NULL,
    `a`.`account_id`,

    DATE_ADD(
            DATE_ADD(
                    '2026-08-01 08:30:00',
                    INTERVAL MOD(`a`.`seed_no` * 11, 30) DAY
            ),
            INTERVAL MOD(`a`.`seed_no` * 3571, 43200) SECOND
    ),

    b'0',

    NULL,
    NULL,
    NULL,
    NULL,
    NULL,

    DATE_ADD(
            DATE_ADD(
                    '2026-08-01 08:30:00',
                    INTERVAL MOD(`a`.`seed_no` * 11, 30) DAY
            ),
            INTERVAL MOD(`a`.`seed_no` * 3571, 43200) SECOND
    ),

    DATE_ADD(
            DATE_ADD(
                    '2026-08-01 08:30:00',
                    INTERVAL MOD(`a`.`seed_no` * 11, 30) DAY
            ),
            INTERVAL MOD(`a`.`seed_no` * 3571, 43200) SECOND
    ),

    'PENDING',

    'PENDING',
    0,
    NULL,
    NULL,

    'PENDING',
    0,
    NULL,
    NULL,

    `a`.`profile_url`,
    NULL

FROM `tmp_application_accounts` AS `a`

         JOIN `users` AS `u`
              ON `u`.`user_id` = (
                  SELECT MIN(`matched_user`.`user_id`)
                  FROM `users` AS `matched_user`
                  WHERE `matched_user`.`hi_id` = `a`.`hi_id`
              )

WHERE NOT EXISTS (
    SELECT 1
    FROM `application` AS `existing_application`
    WHERE `existing_application`.`user_id` = `u`.`user_id`
      AND `existing_application`.`generation_id` = @active_generation_id
)

ORDER BY `a`.`seed_no`;


-- =========================================================
-- 검증 1
-- YOUTUBE 9명, INSTAGRAM 12명이어야 한다.
-- =========================================================

SELECT
    `a`.`sns_code`,
    COUNT(DISTINCT `u`.`user_id`) AS `user_count`,
    COUNT(DISTINCT `app`.`application_id`) AS `application_count`

FROM `tmp_application_accounts` AS `a`

         LEFT JOIN `users` AS `u`
                   ON `u`.`user_id` = (
                       SELECT MIN(`matched_user`.`user_id`)
                       FROM `users` AS `matched_user`
                       WHERE `matched_user`.`hi_id` = `a`.`hi_id`
                   )

         LEFT JOIN `application` AS `app`
                   ON `app`.`user_id` = `u`.`user_id`
                       AND `app`.`generation_id` = @active_generation_id

GROUP BY `a`.`sns_code`

ORDER BY FIELD(
                 `a`.`sns_code`,
                 'YOUTUBE',
                 'INSTAGRAM'
         );


-- =========================================================
-- 검증 2
-- 21건 모두 최초 배치 대상이어야 한다.
-- =========================================================

SELECT
    COUNT(*) AS `initial_batch_target_count`

FROM `application` AS `app`

         JOIN `tmp_application_accounts` AS `a`
              ON `a`.`sns_code` = `app`.`sns_code`
                  AND `a`.`account_id` = `app`.`sns_account_id`

WHERE `app`.`generation_id` = @active_generation_id
  AND `app`.`status` = 'PENDING'
  AND `app`.`media_collection_status` = 'PENDING'
  AND `app`.`media_collection_retry_count` = 0
  AND `app`.`analysis_status` = 'PENDING'
  AND `app`.`analysis_retry_count` = 0;


-- =========================================================
-- 검증 3
-- 로그인 ID와 SNS 계정 매핑 확인
-- =========================================================

SELECT
    `a`.`seed_no`,
    `a`.`sns_code`,
    `a`.`channel_name`,
    `u`.`hi_id`,
    `u`.`name`,
    `app`.`sns_account_id`,
    `app`.`status`,
    `app`.`media_collection_status`,
    `app`.`analysis_status`

FROM `tmp_application_accounts` AS `a`

         JOIN `users` AS `u`
              ON `u`.`user_id` = (
                  SELECT MIN(`matched_user`.`user_id`)
                  FROM `users` AS `matched_user`
                  WHERE `matched_user`.`hi_id` = `a`.`hi_id`
              )

         JOIN `application` AS `app`
              ON `app`.`user_id` = `u`.`user_id`
                  AND `app`.`generation_id` = @active_generation_id

ORDER BY `a`.`seed_no`;


DROP TEMPORARY TABLE `tmp_application_accounts`;