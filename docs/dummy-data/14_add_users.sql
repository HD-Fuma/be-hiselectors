-- hi_selectors 로컬 성능 테스트용 유튜브/인스타그램 추가 셀렉터스 데이터
--
-- 기준일: 2026-08-31
-- 구성: 총 37명 (YouTube 25명, Instagram 12명)
-- 활동 기수: 현재 진행 중인 기수(ACTIVE, 10기)
--
-- 선행 스크립트: 03_generation.sql, 05_users.sql
-- 비밀번호: 전 사용자 0000 (BCrypt 해시 공통 사용)

USE `hi_selectors`;
SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;

DROP TEMPORARY TABLE IF EXISTS `tmp_new_selectors_accounts`;

CREATE TEMPORARY TABLE `tmp_new_selectors_accounts` (
    `seed_no` INT NOT NULL,
    `sns_code` VARCHAR(20) NOT NULL,
    `account_id` VARCHAR(100) NOT NULL,
    `hi_id` VARCHAR(20) NOT NULL,
    `user_name` VARCHAR(50) NOT NULL,
    `channel_name` VARCHAR(100) NOT NULL,
    `profile_url` VARCHAR(500) NOT NULL,
    `follower_count` BIGINT NOT NULL,
    `content_count` BIGINT NOT NULL,
    `last_content_at` DATETIME NOT NULL,
    `application_created_at` DATETIME NOT NULL,
    `approved_at` DATETIME NOT NULL,
    `birth_date` DATE NOT NULL,
    `gender` CHAR(2) NOT NULL,
    PRIMARY KEY (`seed_no`),
    UNIQUE KEY `uq_tmp_new_hi_id` (`hi_id`),
    UNIQUE KEY `uq_tmp_new_account` (`account_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- =========================================================
-- 신규 셀렉터스 37명 시드 데이터 삽입
-- =========================================================
INSERT INTO `tmp_new_selectors_accounts` (
    `seed_no`, `sns_code`, `account_id`, `hi_id`, `user_name`, `channel_name`,
    `profile_url`, `follower_count`, `content_count`, `last_content_at`,
    `application_created_at`, `approved_at`, `birth_date`, `gender`
)
VALUES
    -- YouTube (25명)
    (1, 'YOUTUBE', '@mama', 'mama', '김마마', '마마(MAMA)', 'https://www.youtube.com/@mama', 152000, 180, '2026-08-28 10:00:00', '2026-08-20 10:00:00', '2026-08-25 10:00:00', '1992-05-15', '여'),
    (2, 'YOUTUBE', '@Naegung_foodstory', 'Naegung_foodstory', '이내궁', '내궁 푸드스토리', 'https://www.youtube.com/@Naegung_foodstory', 84000, 230, '2026-08-27 10:00:00', '2026-08-20 11:00:00', '2026-08-25 11:00:00', '1994-08-20', '여'),
    (3, 'YOUTUBE', '@ggyonghouse', 'ggyonghouse', '박꾱', '꾱하우스', 'https://www.youtube.com/@ggyonghouse', 12300, 95, '2026-08-26 10:00:00', '2026-08-20 12:00:00', '2026-08-25 12:00:00', '1995-11-11', '여'),
    (4, 'YOUTUBE', '@greendotori', 'greendotori', '최도토리', '그린도토리', 'https://www.youtube.com/@greendotori', 32000, 150, '2026-08-25 10:00:00', '2026-08-20 13:00:00', '2026-08-25 13:00:00', '1991-03-24', '남'),
    (5, 'YOUTUBE', '@HOSU00', 'HOSU00', '정호수', '호수 HOSU', 'https://www.youtube.com/@HOSU00', 45000, 142, '2026-08-28 10:00:00', '2026-08-20 14:00:00', '2026-08-25 14:00:00', '1998-07-07', '여'),
    (6, 'YOUTUBE', '@madebymomoka', 'madebymomoka', '강모모카', 'made by momoka', 'https://www.youtube.com/@madebymomoka', 21000, 80, '2026-08-29 10:00:00', '2026-08-20 15:00:00', '2026-08-25 15:00:00', '1999-12-15', '여'),
    (7, 'YOUTUBE', '@namcook', 'namcook', '조남국', '남쿡 namcook', 'https://www.youtube.com/@namcook', 56000, 310, '2026-08-30 10:00:00', '2026-08-20 16:00:00', '2026-08-25 16:00:00', '1988-02-28', '남'),
    (8, 'YOUTUBE', '@minkyung8711', 'minkyung8711', '윤민경', '민경로그', 'https://www.youtube.com/@minkyung8711', 11000, 60, '2026-08-27 10:00:00', '2026-08-20 17:00:00', '2026-08-25 17:00:00', '1987-11-10', '여'),
    (9, 'YOUTUBE', '@majifoodtv', 'majifoodtv', '장마지', '마지푸드TV', 'https://www.youtube.com/@majifoodtv', 78000, 420, '2026-08-28 10:00:00', '2026-08-21 10:00:00', '2026-08-26 10:00:00', '1993-06-18', '여'),
    (10, 'YOUTUBE', '@hayoung_e', 'hayoung_e', '임하영', '하영이', 'https://www.youtube.com/@hayoung_e', 14500, 50, '2026-08-29 10:00:00', '2026-08-21 11:00:00', '2026-08-26 11:00:00', '1996-09-05', '여'),
    (11, 'YOUTUBE', '@hongsi_v', 'hongsi_v', '한홍시', '홍시 V', 'https://www.youtube.com/@hongsi_v', 92000, 210, '2026-08-30 10:00:00', '2026-08-21 12:00:00', '2026-08-26 12:00:00', '1994-01-22', '여'),
    (12, 'YOUTUBE', '@coffictures', 'coffictures', '오커피', '커픽쳐스', 'https://www.youtube.com/@coffictures', 31000, 110, '2026-08-28 10:00:00', '2026-08-21 13:00:00', '2026-08-26 13:00:00', '1990-10-30', '남'),
    (13, 'YOUTUBE', '@da_song.e', 'da_song.e', '서다송', '다송이', 'https://www.youtube.com/@da_song.e', 26000, 95, '2026-08-27 10:00:00', '2026-08-21 14:00:00', '2026-08-26 14:00:00', '1997-04-12', '여'),
    (14, 'YOUTUBE', '@daygowoon7239', 'daygowoon7239', '신고운', '고운날', 'https://www.youtube.com/@daygowoon7239', 18000, 85, '2026-08-29 10:00:00', '2026-08-21 15:00:00', '2026-08-26 15:00:00', '1995-12-01', '여'),
    (15, 'YOUTUBE', '@ddmini', 'ddmini', '권디디', '디디미니', 'https://www.youtube.com/@ddmini', 215000, 480, '2026-08-30 10:00:00', '2026-08-21 16:00:00', '2026-08-26 16:00:00', '1992-08-15', '여'),
    (16, 'YOUTUBE', '@yym81', 'yym81', '황윤민', '윤민81', 'https://www.youtube.com/@yym81', 10500, 40, '2026-08-26 10:00:00', '2026-08-21 17:00:00', '2026-08-26 17:00:00', '1981-05-20', '남'),
    (17, 'YOUTUBE', '@traveldada1', 'traveldada1', '안다다', '트래블다다', 'https://www.youtube.com/@traveldada1', 65000, 150, '2026-08-29 10:00:00', '2026-08-22 10:00:00', '2026-08-27 10:00:00', '1991-07-08', '여'),
    (18, 'YOUTUBE', '@yniverselog', 'yniverselog', '송유니', '유니버스로그', 'https://www.youtube.com/@yniverselog', 42000, 115, '2026-08-30 10:00:00', '2026-08-22 11:00:00', '2026-08-27 11:00:00', '1996-03-14', '여'),
    (19, 'YOUTUBE', '@Amugaegae', 'Amugaegae', '전아무', '아무개개', 'https://www.youtube.com/@Amugaegae', 14000, 70, '2026-08-28 10:00:00', '2026-08-22 12:00:00', '2026-08-27 12:00:00', '1998-11-25', '남'),
    (20, 'YOUTUBE', '@빵띰', 'bbangddim', '홍빵띰', '빵띰', 'https://www.youtube.com/@%EB%B9%B5%EB%94%98', 53000, 190, '2026-08-29 10:00:00', '2026-08-22 13:00:00', '2026-08-27 13:00:00', '1994-09-09', '여'),
    (21, 'YOUTUBE', '@stylist_unnie', 'stylist_unnie', '유스타', '스타일리스트언니', 'https://www.youtube.com/@stylist_unnie', 125000, 340, '2026-08-30 10:00:00', '2026-08-22 14:00:00', '2026-08-27 14:00:00', '1989-10-18', '여'),
    (22, 'YOUTUBE', '@limbbeumlim', 'limbbeumlim', '고림쁨', '림쁨림', 'https://www.youtube.com/@limbbeumlim', 24000, 85, '2026-08-28 10:00:00', '2026-08-22 15:00:00', '2026-08-27 15:00:00', '1995-02-14', '여'),
    (23, 'YOUTUBE', '@102_knits', '102_knits', '문니트', '102 니츠', 'https://www.youtube.com/@102_knits', 18500, 65, '2026-08-27 10:00:00', '2026-08-22 16:00:00', '2026-08-27 16:00:00', '1992-04-05', '여'),
    (24, 'YOUTUBE', '@1mindiet', '1mindiet', '양다이', '1분다이어트', 'https://www.youtube.com/@1mindiet', 310000, 800, '2026-08-30 10:00:00', '2026-08-22 17:00:00', '2026-08-27 17:00:00', '1993-01-11', '남'),
    (25, 'YOUTUBE', '@BoraClaire', 'BoraClaire', '손보라', '보라클레어', 'https://www.youtube.com/@BoraClaire', 87000, 260, '2026-08-29 10:00:00', '2026-08-23 10:00:00', '2026-08-28 10:00:00', '1990-08-22', '여'),

    -- Instagram (12명)
    (26, 'INSTAGRAM', 'gmcoo.k', 'gmcoo.k', '배지엠', 'gmcoo.k', 'https://www.instagram.com/gmcoo.k/', 32000, 450, '2026-08-29 10:00:00', '2026-08-23 11:00:00', '2026-08-28 11:00:00', '1996-05-12', '여'),
    (27, 'INSTAGRAM', '_.__.ouo', '_.__.ouo', '백오유', '_.__.ouo', 'https://www.instagram.com/_.__.ouo/', 15000, 210, '2026-08-28 10:00:00', '2026-08-23 12:00:00', '2026-08-28 12:00:00', '1998-12-05', '여'),
    (28, 'INSTAGRAM', 'muk.seori', 'muk.seori', '허서리', 'muk.seori', 'https://www.instagram.com/muk.seori/', 58000, 730, '2026-08-30 10:00:00', '2026-08-23 13:00:00', '2026-08-28 13:00:00', '1994-11-20', '여'),
    (29, 'INSTAGRAM', 'guuu002_', 'guuu002_', '남구구', 'guuu002_', 'https://www.instagram.com/guuu002_/', 27000, 320, '2026-08-27 10:00:00', '2026-08-23 14:00:00', '2026-08-28 14:00:00', '1997-07-07', '남'),
    (30, 'INSTAGRAM', 'gijaengni', 'gijaengni', '심기쟁', 'gijaengni', 'https://www.instagram.com/gijaengni/', 41000, 500, '2026-08-29 10:00:00', '2026-08-23 15:00:00', '2026-08-28 15:00:00', '1995-03-15', '여'),
    (31, 'INSTAGRAM', 'bokchibokki', 'bokchibokki', '김복치', 'bokchibokki', 'https://www.instagram.com/bokchibokki/', 12000, 150, '2026-08-28 10:00:00', '2026-08-23 16:00:00', '2026-08-28 16:00:00', '1999-01-29', '여'),
    (32, 'INSTAGRAM', 'leopolt.studio', 'leopolt.studio', '이레오', 'leopolt.studio', 'https://www.instagram.com/leopolt.studio/', 89000, 1020, '2026-08-30 10:00:00', '2026-08-24 10:00:00', '2026-08-29 10:00:00', '1992-06-11', '남'),
    (33, 'INSTAGRAM', 'wavyyeyo', 'wavyyeyo', '박웨비', 'wavyyeyo', 'https://www.instagram.com/wavyyeyo/', 34000, 420, '2026-08-29 10:00:00', '2026-08-24 11:00:00', '2026-08-29 11:00:00', '1996-09-22', '여'),
    (34, 'INSTAGRAM', 'ceo_duck__', 'ceo_duck__', '최오리', 'ceo_duck__', 'https://www.instagram.com/ceo_duck__/', 105000, 1150, '2026-08-30 10:00:00', '2026-08-24 12:00:00', '2026-08-29 12:00:00', '1991-04-30', '남'),
    (35, 'INSTAGRAM', 'chi_korlife', 'chi_korlife', '정치코', 'chi_korlife', 'https://www.instagram.com/chi_korlife/', 22000, 280, '2026-08-28 10:00:00', '2026-08-24 13:00:00', '2026-08-29 13:00:00', '1993-10-14', '여'),
    (36, 'INSTAGRAM', 'moogguzzang', 'moogguzzang', '강무꾸', 'moogguzzang', 'https://www.instagram.com/moogguzzang/', 47000, 610, '2026-08-29 10:00:00', '2026-08-24 14:00:00', '2026-08-29 14:00:00', '1995-12-25', '여'),
    (37, 'INSTAGRAM', 'bobom_._', 'bobom_._', '조보봄', 'bobom_._', 'https://www.instagram.com/bobom_._/', 16000, 190, '2026-08-27 10:00:00', '2026-08-24 15:00:00', '2026-08-29 15:00:00', '1998-02-18', '여');

START TRANSACTION;

-- =========================================================
-- 1. 더현대Hi 사용자(Users) 생성
-- =========================================================
INSERT INTO `users` (
    `hi_id`, `hi_password`, `name`, `birth_date`, `gender`,
    `email`, `phone`, `created_at`, `updated_at`, `is_deleted`, `alimtalk`
)
SELECT
    `seed`.`hi_id`,
    '$2a$10$yqyTDn5aDasCKPMZTGc3LuF6SaFqcZsYKG6G2D4yFhtjslZeJcpaW',
    `seed`.`user_name`,
    `seed`.`birth_date`,
    `seed`.`gender`,
    CONCAT(REPLACE(`seed`.`hi_id`, '.', '_'), '@selectors.example.com'),
    CONCAT('010-0088-', LPAD(`seed`.`seed_no`, 4, '0')),
    `seed`.`application_created_at`,
    `seed`.`application_created_at`,
    0,
    'Y'
FROM `tmp_new_selectors_accounts` AS `seed`
WHERE NOT EXISTS (
    SELECT 1 FROM `users` AS `existing_user` WHERE `existing_user`.`hi_id` = `seed`.`hi_id`
);

-- 현재 활성화된(ACTIVE) 기수 ID 조회 (2026-08-31 기준 10기 배정)
SET @active_generation_id := (
    SELECT `generation_id`
    FROM `generation`
    WHERE `status` = 'ACTIVE'
      AND CURRENT_TIMESTAMP BETWEEN `start_date` AND `end_date`
    ORDER BY `start_date` ASC
    LIMIT 1
);

-- =========================================================
-- 2. 셀렉터스 승인 상태의 지원서(Application) 생성
-- =========================================================
INSERT INTO `application` (
    `user_id`, `sns_code`, `generation_id`, `sns_account_id`,
    `policy_agreed_at`, `alarm_yn`, `follower_count`, `content_count`,
    `last_content_at`, `created_at`, `updated_at`, `status`,
    `media_collection_status`, `analysis_status`, `profile_url`
)
SELECT
    `u`.`user_id`,
    `seed`.`sns_code`,
    @active_generation_id,
    `seed`.`account_id`,
    `seed`.`application_created_at`,
    b'0',
    `seed`.`follower_count`,
    `seed`.`content_count`,
    `seed`.`last_content_at`,
    `seed`.`application_created_at`,
    `seed`.`approved_at`,
    'APPROVED',
    'PENDING',
    'PENDING',
    `seed`.`profile_url`
FROM `tmp_new_selectors_accounts` AS `seed`
         JOIN `users` AS `u` ON `u`.`hi_id` = `seed`.`hi_id`
WHERE NOT EXISTS (
    SELECT 1 FROM `application` WHERE `user_id` = `u`.`user_id` AND `generation_id` = @active_generation_id
);

-- =========================================================
-- 3. 셀렉터스 권한 부여(Selectors) 등록
-- =========================================================
INSERT INTO `selectors` (
    `application_id`, `user_id`, `selectors_role_id`, `selectors_code`,
    `selectors_nickname`, `created_at`, `updated_at`, `is_deleted`
)
SELECT
    `app`.`application_id`,
    `u`.`user_id`,
    'ACTIVE',
    CONCAT('RC', LPAD(`app`.`application_id` * 2003 - 806, 9, '0'), 'T'),
    `seed`.`channel_name`,
    `seed`.`approved_at`,
    `seed`.`approved_at`,
    0
FROM `tmp_new_selectors_accounts` AS `seed`
         JOIN `users` AS `u` ON `u`.`hi_id` = `seed`.`hi_id`
         JOIN `application` AS `app` ON `app`.`user_id` = `u`.`user_id` AND `app`.`generation_id` = @active_generation_id
WHERE NOT EXISTS (
    SELECT 1 FROM `selectors` WHERE `user_id` = `u`.`user_id`
);

-- =========================================================
-- 4. 기수별 셀렉터스 활동(Selectors_generation) 등록
-- =========================================================
INSERT INTO `selectors_generation` (
    `selectors_id`, `generation_id`, `created_at`,
    `total_sales`, `confirmed_purchase_count`, `paid_commission_amount`
)
SELECT
    `s`.`selectors_id`,
    @active_generation_id,
    `seed`.`approved_at`,
    0, 0, 0
FROM `tmp_new_selectors_accounts` AS `seed`
         JOIN `users` AS `u` ON `u`.`hi_id` = `seed`.`hi_id`
         JOIN `selectors` AS `s` ON `s`.`user_id` = `u`.`user_id`
WHERE NOT EXISTS (
    SELECT 1 FROM `selectors_generation` WHERE `selectors_id` = `s`.`selectors_id` AND `generation_id` = @active_generation_id
);

-- =========================================================
-- 5. 셀렉터스 대표 SNS 계정(Selectors_sns_account) 등록
-- =========================================================
INSERT INTO `selectors_sns_account` (
    `created_at`, `updated_at`, `account_id`, `profile_url`,
    `is_deleted`, `follower_count`, `selectors_id`, `sns_code`
)
SELECT
    `seed`.`approved_at`,
    `seed`.`approved_at`,
    `seed`.`account_id`,
    `seed`.`profile_url`,
    b'0',
    `seed`.`follower_count`,
    `s`.`selectors_id`,
    `seed`.`sns_code`
FROM `tmp_new_selectors_accounts` AS `seed`
         JOIN `users` AS `u` ON `u`.`hi_id` = `seed`.`hi_id`
         JOIN `selectors` AS `s` ON `s`.`user_id` = `u`.`user_id`
WHERE NOT EXISTS (
    SELECT 1 FROM `selectors_sns_account` WHERE `selectors_id` = `s`.`selectors_id`
);

COMMIT;

-- 임시 테이블 삭제
DROP TEMPORARY TABLE IF EXISTS `tmp_new_selectors_accounts`;