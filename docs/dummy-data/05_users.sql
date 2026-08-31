-- hi_selectors 로컬 성능 테스트용 사용자 데이터
--
-- 기준일: 2026-08-31
-- 구성: 총 100,000명
-- 로그인 ID: hiuser1 ~ hiuser100000
-- 비밀번호: 전 사용자 0000 (BCrypt 해시 공통 사용)
--
-- 인구 분포:
--   여성 52,000명 / 남성 48,000명
--   여성 20대 13,000명, 여성 30대 13,000명
--   그 외 성별·연령대는 각 7,000~10,000명으로 분산
--
-- 개인정보 안전:
--   이름은 실제 한국인 이름 조합처럼 생성하지만 특정 실존 인물을 나타내지 않는다.
--   이메일은 RFC 2606 예약 도메인인 example.com의 하위 도메인을 사용한다.
--   전화번호는 실제 발송 사고를 피하기 위해 로컬 테스트 전용 010-0001~0010 형식을 사용한다.
--
-- user_id는 명시하지 않고 users.AUTO_INCREMENT가 자동으로 배정한다.
-- 기존 users 행이 있어도 PK 충돌 없이 현재 AUTO_INCREMENT 다음 값부터 생성된다.

USE `hi_selectors`;
SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;

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
WITH
`digits` AS (
    SELECT 0 AS `n`
    UNION ALL SELECT 1
    UNION ALL SELECT 2
    UNION ALL SELECT 3
    UNION ALL SELECT 4
    UNION ALL SELECT 5
    UNION ALL SELECT 6
    UNION ALL SELECT 7
    UNION ALL SELECT 8
    UNION ALL SELECT 9
),
`sequence_numbers` AS (
    SELECT
        `d4`.`n` * 10000
        + `d3`.`n` * 1000
        + `d2`.`n` * 100
        + `d1`.`n` * 10
        + `d0`.`n`
        + 1 AS `seq`
    FROM `digits` AS `d0`
    CROSS JOIN `digits` AS `d1`
    CROSS JOIN `digits` AS `d2`
    CROSS JOIN `digits` AS `d3`
    CROSS JOIN `digits` AS `d4`
),
`distribution_slots` AS (
    SELECT
        `seq`,
        MOD(`seq` * 37 + 17, 100000) + 1 AS `demographic_slot`,
        MOD(`seq` * 73 + 29, 100000) + 1 AS `signup_slot`
    FROM `sequence_numbers`
),
`profiles` AS (
    SELECT
        `seq`,
        `signup_slot`,
        CASE
            WHEN `demographic_slot` <= 52000 THEN '여'
            ELSE '남'
        END AS `gender`,
        CASE
            WHEN `demographic_slot` <= 13000 THEN 20
            WHEN `demographic_slot` <= 26000 THEN 30
            WHEN `demographic_slot` <= 36000 THEN 40
            WHEN `demographic_slot` <= 45000 THEN 50
            WHEN `demographic_slot` <= 52000 THEN 60
            WHEN `demographic_slot` <= 62000 THEN 20
            WHEN `demographic_slot` <= 72000 THEN 30
            WHEN `demographic_slot` <= 82000 THEN 40
            WHEN `demographic_slot` <= 91000 THEN 50
            ELSE 60
        END AS `age_group`
    FROM `distribution_slots`
),
`bounded_profiles` AS (
    SELECT
        `seq`,
        `signup_slot`,
        `gender`,
        `age_group`,
        CASE `age_group`
            WHEN 20 THEN DATE('1996-09-01')
            WHEN 30 THEN DATE('1986-09-01')
            WHEN 40 THEN DATE('1976-09-01')
            WHEN 50 THEN DATE('1966-09-01')
            ELSE DATE('1956-09-01')
        END AS `birth_start`,
        CASE `age_group`
            WHEN 20 THEN DATE('2006-08-31')
            WHEN 30 THEN DATE('1996-08-31')
            WHEN 40 THEN DATE('1986-08-31')
            WHEN 50 THEN DATE('1976-08-31')
            ELSE DATE('1966-08-31')
        END AS `birth_end`,
        CASE
            WHEN `signup_slot` <= 5000 THEN DATE('2022-01-01')
            WHEN `signup_slot` <= 17000 THEN DATE('2023-01-01')
            WHEN `signup_slot` <= 40000 THEN DATE('2024-01-01')
            WHEN `signup_slot` <= 72000 THEN DATE('2025-01-01')
            ELSE DATE('2026-01-01')
        END AS `signup_start`,
        CASE
            WHEN `signup_slot` <= 5000 THEN DATE('2022-12-31')
            WHEN `signup_slot` <= 17000 THEN DATE('2023-12-31')
            WHEN `signup_slot` <= 40000 THEN DATE('2024-12-31')
            WHEN `signup_slot` <= 72000 THEN DATE('2025-12-31')
            ELSE DATE('2026-08-31')
        END AS `signup_end`
    FROM `profiles`
),
`dated_profiles` AS (
    SELECT
        `seq`,
        `gender`,
        `age_group`,
        DATE_ADD(
            `birth_start`,
            INTERVAL MOD(
                `seq` * 7919 + 104729,
                DATEDIFF(`birth_end`, `birth_start`) + 1
            ) DAY
        ) AS `birth_date`,
        DATE_ADD(
            DATE_ADD(
                `signup_start`,
                INTERVAL MOD(
                    `seq` * 3571 + 8191,
                    DATEDIFF(`signup_end`, `signup_start`) + 1
                ) DAY
            ),
            INTERVAL MOD(`seq` * 1069 + 97, 86400) SECOND
        ) AS `created_at`
    FROM `bounded_profiles`
),
`prepared_users` AS (
    SELECT
        `seq`,
        `gender`,
        `age_group`,
        `birth_date`,
        `created_at`,
        CASE
            WHEN MOD(`seq` * 19, 50) = 0 THEN 1
            ELSE 0
        END AS `is_deleted`
    FROM `dated_profiles`
)
SELECT
    CONCAT('hiuser', `seq`) AS `hi_id`,
    '$2a$10$yqyTDn5aDasCKPMZTGc3LuF6SaFqcZsYKG6G2D4yFhtjslZeJcpaW'
        AS `hi_password`,
    CONCAT(
        ELT(
            MOD(`seq` * 17, 30) + 1,
            '김', '이', '박', '최', '정', '강', '조', '윤', '장', '임',
            '한', '오', '서', '신', '권', '황', '안', '송', '전', '홍',
            '유', '고', '문', '양', '손', '배', '백', '허', '남', '심'
        ),
        CASE
            WHEN `gender` = '여' AND `age_group` = 20 THEN ELT(
                MOD(`seq` * 29, 16) + 1,
                '서연', '서윤', '지우', '하윤', '지유', '민서', '수아', '채원',
                '예은', '윤서', '은서', '소윤', '나연', '다은', '유나', '예린'
            )
            WHEN `gender` = '여' AND `age_group` = 30 THEN ELT(
                MOD(`seq` * 29, 16) + 1,
                '민지', '지은', '수진', '유진', '은지', '지혜', '혜진', '소영',
                '아영', '보람', '현정', '나영', '윤정', '미정', '정민', '선영'
            )
            WHEN `gender` = '여' AND `age_group` = 40 THEN ELT(
                MOD(`seq` * 29, 16) + 1,
                '지영', '은정', '현주', '미영', '정은', '수연', '경아', '선미',
                '혜영', '유미', '영미', '희정', '주희', '성희', '진희', '민정'
            )
            WHEN `gender` = '여' AND `age_group` = 50 THEN ELT(
                MOD(`seq` * 29, 16) + 1,
                '미숙', '경희', '영숙', '정희', '은숙', '명희', '순영', '현숙',
                '영희', '미경', '정숙', '선희', '혜숙', '경숙', '영자', '금희'
            )
            WHEN `gender` = '여' THEN ELT(
                MOD(`seq` * 29, 16) + 1,
                '영희', '정숙', '순자', '영자', '경자', '미자', '명자', '옥자',
                '춘자', '숙자', '순희', '인숙', '복순', '말순', '혜자', '경순'
            )
            WHEN `gender` = '남' AND `age_group` = 20 THEN ELT(
                MOD(`seq` * 29, 16) + 1,
                '민준', '서준', '도윤', '예준', '시우', '하준', '지호', '주원',
                '준우', '건우', '우진', '현우', '지훈', '승민', '재윤', '태윤'
            )
            WHEN `gender` = '남' AND `age_group` = 30 THEN ELT(
                MOD(`seq` * 29, 16) + 1,
                '민수', '준호', '성민', '동현', '재현', '현우', '지훈', '태현',
                '영준', '승현', '진우', '정훈', '상현', '민석', '재훈', '성현'
            )
            WHEN `gender` = '남' AND `age_group` = 40 THEN ELT(
                MOD(`seq` * 29, 16) + 1,
                '성호', '정우', '준영', '동욱', '상훈', '재영', '태훈', '민호',
                '현석', '진호', '승호', '영진', '대현', '성진', '종현', '기훈'
            )
            WHEN `gender` = '남' AND `age_group` = 50 THEN ELT(
                MOD(`seq` * 29, 16) + 1,
                '영수', '성수', '정호', '동수', '상철', '재호', '병철', '승환',
                '경수', '종수', '태식', '명수', '용호', '진수', '광호', '철수'
            )
            ELSE ELT(
                MOD(`seq` * 29, 16) + 1,
                '영수', '철수', '성호', '정식', '병수', '상수', '종철', '영철',
                '동수', '재식', '광수', '명호', '태수', '용식', '진호', '경수'
            )
        END
    ) AS `name`,
    `birth_date`,
    `gender`,
    CONCAT(
        'hiuser',
        `seq`,
        '@',
        ELT(
            MOD(`seq` * 13, 5) + 1,
            'naver.example.com',
            'gmail.example.com',
            'daum.example.com',
            'kakao.example.com',
            'outlook.example.com'
        )
    ) AS `email`,
    CONCAT(
        '010-',
        LPAD(FLOOR((`seq` - 1) / 10000) + 1, 4, '0'),
        '-',
        LPAD(MOD(`seq` - 1, 10000), 4, '0')
    ) AS `phone`,
    `created_at`,
    CASE
        WHEN MOD(`seq` * 31, 100) < 35 THEN `created_at`
        ELSE DATE_ADD(
            `created_at`,
            INTERVAL MOD(
                `seq` * 2053 + 97,
                TIMESTAMPDIFF(
                    SECOND,
                    `created_at`,
                    TIMESTAMP('2026-08-31 23:59:59')
                ) + 1
            ) SECOND
        )
    END AS `updated_at`,
    `is_deleted`,
    CASE
        WHEN `is_deleted` = 0
             AND MOD(`seq` * 23 + 7, 100) < 64
            THEN 'Y'
        ELSE 'N'
    END AS `alimtalk`
FROM `prepared_users`
WHERE NOT EXISTS (
    SELECT 1
    FROM `users` AS `existing_user`
    WHERE `existing_user`.`hi_id` = CONCAT('hiuser', `prepared_users`.`seq`)
)
ORDER BY `seq`;

-- 검증 1: 이번 스크립트가 만든 hiuser 계정 수, 자동 PK 범위 및 공통 비밀번호 해시
SELECT
    COUNT(*) AS `user_count`,
    MIN(`user_id`) AS `min_user_id`,
    MAX(`user_id`) AS `max_user_id`,
    SUM(`hi_id` NOT REGEXP '^hiuser[0-9]+$') AS `invalid_hi_id_count`,
    SUM(
        `hi_password`
        <> '$2a$10$yqyTDn5aDasCKPMZTGc3LuF6SaFqcZsYKG6G2D4yFhtjslZeJcpaW'
    ) AS `invalid_password_hash_count`
FROM `users`
WHERE `hi_id` REGEXP '^hiuser[0-9]+$';

-- 검증 2: 기준일 현재 성별·연령대 분포
-- 기대값:
--   여 20대 13,000 / 여 30대 13,000 / 여 40대 10,000
--   여 50대  9,000 / 여 60대  7,000
--   남 20대 10,000 / 남 30대 10,000 / 남 40대 10,000
--   남 50대  9,000 / 남 60대  9,000
SELECT
    `gender`,
    CONCAT(
        FLOOR(TIMESTAMPDIFF(YEAR, `birth_date`, DATE('2026-08-31')) / 10) * 10,
        '대'
    ) AS `age_group`,
    COUNT(*) AS `user_count`
FROM `users`
WHERE `hi_id` REGEXP '^hiuser[0-9]+$'
GROUP BY
    `gender`,
    CONCAT(
        FLOOR(TIMESTAMPDIFF(YEAR, `birth_date`, DATE('2026-08-31')) / 10) * 10,
        '대'
    )
ORDER BY
    FIELD(`gender`, '여', '남'),
    `age_group`;

-- 검증 3: 로그인 ID, 이메일, 전화번호는 각각 중복이 없어야 한다.
SELECT
    (SELECT COUNT(*) - COUNT(DISTINCT `hi_id`)
       FROM `users` WHERE `hi_id` REGEXP '^hiuser[0-9]+$')
        AS `duplicate_hi_id_count`,
    (SELECT COUNT(*) - COUNT(DISTINCT `email`)
       FROM `users` WHERE `hi_id` REGEXP '^hiuser[0-9]+$')
        AS `duplicate_email_count`,
    (SELECT COUNT(*) - COUNT(DISTINCT `phone`)
       FROM `users` WHERE `hi_id` REGEXP '^hiuser[0-9]+$')
        AS `duplicate_phone_count`;

-- 검증 4: 가입 연도, 탈퇴 여부, 알림톡 동의 분포
SELECT
    YEAR(`created_at`) AS `signup_year`,
    COUNT(*) AS `user_count`,
    SUM(`is_deleted` = 1) AS `deleted_count`,
    SUM(`alimtalk` = 'Y') AS `alimtalk_agreed_count`
FROM `users`
WHERE `hi_id` REGEXP '^hiuser[0-9]+$'
GROUP BY YEAR(`created_at`)
ORDER BY `signup_year`;
