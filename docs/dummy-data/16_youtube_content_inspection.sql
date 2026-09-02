-- YouTube 셀렉터스 4명 + 콘텐츠 검수 생성용 Shorts 11건
--
-- 기준일: 2026-09-01 (Asia/Seoul)
-- 선행 스크립트:
--   03_generation.sql
--   04_inspection_policy.sql
--   09_selectors.sql
--   14_add_users.sql
--
-- 중요:
--   1) 신규 채널 4명은 실제 YouTube 채널 정보로 셀렉터스 등록한다.
--      생년월일, 이메일, 전화번호는 테스트용 가상 정보다.
--   2) Shorts 11건은 검수 파이프라인 자체를 확인하기 위한 데이터다.
--      실제 영상 소유 채널과 무관하게 14_add_users.sql에서 등록된 기존 셀렉터스
--      210, 212, 214, 216, 218, 220번에 의도적으로 분산 연결한다.
--   3) content_report는 이 파일에서 직접 만들지 않는다.
--      content_version을 PENDING으로 만들고, 미디어 추출 정보도 비워 두어
--      실제 콘텐츠 리포트 생성 작업이 content_report와 위반 항목을 만들게 한다.
--   4) 재실행해도 동일 사용자/지원서/셀렉터스/콘텐츠/버전을 중복 생성하지 않는다.
--      이미 검수가 끝난 콘텐츠를 PENDING으로 되돌리거나 기존 리포트를 삭제하지 않는다.

USE `hi_selectors`;
SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;

DROP TEMPORARY TABLE IF EXISTS `tmp_youtube_new_selectors`;
DROP TEMPORARY TABLE IF EXISTS `tmp_youtube_inspection_content`;
DROP TEMPORARY TABLE IF EXISTS `tmp_youtube_seed_assert`;

CREATE TEMPORARY TABLE `tmp_youtube_new_selectors` (
    `seed_no` INT NOT NULL,
    `hi_id` VARCHAR(20) NOT NULL,
    `user_name` VARCHAR(50) NOT NULL,
    `selectors_nickname` VARCHAR(20) NOT NULL,
    `account_id` VARCHAR(100) NOT NULL,
    `channel_id` VARCHAR(100) NOT NULL,
    `profile_url` VARCHAR(500) NOT NULL,
    `profile_image_url` VARCHAR(500) NOT NULL,
    `follower_count` BIGINT NOT NULL,
    `content_count` BIGINT NOT NULL,
    `birth_date` DATE NOT NULL,
    `gender` CHAR(2) NOT NULL,
    `email` VARCHAR(100) NOT NULL,
    `phone` VARCHAR(20) NOT NULL,
    PRIMARY KEY (`seed_no`),
    UNIQUE KEY `uq_tmp_youtube_new_hi_id` (`hi_id`),
    UNIQUE KEY `uq_tmp_youtube_new_account_id` (`account_id`),
    UNIQUE KEY `uq_tmp_youtube_new_channel_id` (`channel_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `tmp_youtube_new_selectors` (
    `seed_no`, `hi_id`, `user_name`, `selectors_nickname`, `account_id`, `channel_id`,
    `profile_url`, `profile_image_url`, `follower_count`, `content_count`,
    `birth_date`, `gender`, `email`, `phone`
)
VALUES
    (
        1, 'everbabygreen', '에베그', '에베그', '@Everbabygreen',
        'UCRuGUKE_7WldwsQPpWrayVA',
        'https://www.youtube.com/@Everbabygreen',
        'https://yt3.ggpht.com/HkZWD8DHLuLTMJ_nDvRloMv9FCJ9-uYRNIiks4GzR4kylvHgiZ9e59IylyLcCBe35ozWP-vZWg=s800-c-k-c0x00ffffff-no-rj',
        6070, 68, '1993-04-15', '여', 'everbabygreen@selectors.example.com', '010-0091-0001'
    ),
    (
        2, 'yujin_n9w', '박유진', '박유진YUJIN', '@박유진YUJIN-n9w',
        'UCPTUVk62Skq6rlSpDJR5olQ',
        'https://www.youtube.com/@박유진YUJIN-n9w',
        'https://yt3.ggpht.com/mTclBdMkjRjNh576FuI6U5OJWHthjLlTpNbLnBy33eMoQsOpLowhjq6rB2R13z-4VNoM8QyJWg=s800-c-k-c0x00ffffff-no-rj',
        1550, 11, '1997-09-21', '여', 'yujin_n9w@selectors.example.com', '010-0091-0002'
    ),
    (
        3, 'tinkerlyn_log', '팅커린', '팅커린', '@tinkerlyn-log',
        'UCOER6F899xV35dPsvzUF_1w',
        'https://www.youtube.com/@tinkerlyn-log',
        'https://yt3.ggpht.com/ohXH0PaHVOdDE5Nxjd1xw6fNW3LxIqZ-U5g-iXZbVj9U8auU_V8J4ykXvVBL22wRifcyhj2m=s800-c-k-c0x00ffffff-no-rj',
        1790, 13, '1995-02-08', '여', 'tinkerlyn_log@selectors.example.com', '010-0091-0003'
    ),
    (
        4, 'gonayun', '고나윤', '고나윤 Nayun Ko', '@gonayun',
        'UCX7JYcZIbRUxXaFMKDXLxtA',
        'https://www.youtube.com/@gonayun',
        'https://yt3.ggpht.com/t8inAvD7LL0UVypPqzrhrPHam-HJZzKbtbqEfk2_W2SnwXDjtVFiRSBBjN7ZYesCvU0ph_ql6VA=s800-c-k-c0x00ffffff-no-rj',
        146000, 292, '1994-06-12', '여', 'gonayun@selectors.example.com', '010-0091-0004'
    );

CREATE TEMPORARY TABLE `tmp_youtube_inspection_content` (
    `seed_no` INT NOT NULL,
    `video_id` VARCHAR(20) NOT NULL,
    `content_url` VARCHAR(500) NOT NULL,
    `thumbnail_url` VARCHAR(500) NOT NULL,
    `source_channel` VARCHAR(100) NOT NULL,
    `video_title` VARCHAR(500) NOT NULL,
    `published_at` DATETIME NOT NULL COMMENT 'YouTube 공개 시각을 KST로 변환한 값',
    `duration_seconds` BIGINT NOT NULL,
    `owner_hi_id` VARCHAR(20) NOT NULL,
    `expected_selectors_id` BIGINT NOT NULL,
    PRIMARY KEY (`seed_no`),
    UNIQUE KEY `uq_tmp_youtube_video_id` (`video_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `tmp_youtube_inspection_content` (
    `seed_no`, `video_id`, `content_url`, `thumbnail_url`, `source_channel`,
    `video_title`, `published_at`, `duration_seconds`, `owner_hi_id`, `expected_selectors_id`
)
VALUES
    (1, 'FcceHtZRS5I', 'https://www.youtube.com/shorts/FcceHtZRS5I', 'https://i.ytimg.com/vi/FcceHtZRS5I/hqdefault.jpg', '에일리룩', '지금 더현대서울 가야하는 이유', '2024-09-04 09:27:39', 37, 'mama', 210),
    (2, '_LiARJTy_ZU', 'https://www.youtube.com/shorts/_LiARJTy_ZU', 'https://i.ytimg.com/vi/_LiARJTy_ZU/hqdefault.jpg', '고나윤 Nayun Ko', '판교 현백에서 봄옷 쇼핑🛋️🤍', '2026-03-08 12:01:42', 61, 'ggyonghouse', 212),
    (3, 'jS6s-4UGX1w', 'https://www.youtube.com/shorts/jS6s-4UGX1w', 'https://i.ytimg.com/vi/jS6s-4UGX1w/hqdefault.jpg', '연우', '더현대 쇼핑브이로그🌳🤎 가을 니트랑 아우터 보고 왔어요 🎧', '2025-09-18 19:18:51', 44, 'HOSU00', 214),
    (4, '_4A2upyLL90', 'https://www.youtube.com/shorts/_4A2upyLL90', 'https://i.ytimg.com/vi/_4A2upyLL90/hqdefault.jpg', 'Leekyoung 리경', '163cm 54kg 더현대에서 구경한것들🫧 #패션하울 #데일리룩코디 #더현대서울', '2026-06-28 22:49:31', 29, 'namcook', 216),
    (5, 'xiAYLhu-IOE', 'https://www.youtube.com/shorts/xiAYLhu-IOE', 'https://i.ytimg.com/vi/xiAYLhu-IOE/hqdefault.jpg', '심톨 𝐒𝐈𝐌𝐓𝐎𝐇𝐋', '더현대 추구미 에겐녀 브랜드 추천🐬🛼🩵', '2026-07-06 18:46:35', 18, 'majifoodtv', 218),
    (6, 'ITsPpZovMrI', 'https://www.youtube.com/shorts/ITsPpZovMrI', 'https://i.ytimg.com/vi/ITsPpZovMrI/hqdefault.jpg', '밤양갱스터', '더현대서울 정원뷰가 좋은 이탈리안 레스토랑 이탈리', '2025-01-02 17:13:34', 12, 'hongsi_v', 220),
    (7, 'XYU_-2WG-is', 'https://www.youtube.com/shorts/XYU_-2WG-is', 'https://i.ytimg.com/vi/XYU_-2WG-is/hqdefault.jpg', '순쥬 soonzzu', '요즘 핫한 더현대서울 쇼룸 best 4✨ #더바넷 #아우로 #코이세이오 #틸아이다이 #더현대서울 #쇼룸 #가을코디 #겨울코디', '2025-10-25 12:18:34', 18, 'mama', 210),
    (8, 'AkrkZS1Lry0', 'https://www.youtube.com/shorts/AkrkZS1Lry0', 'https://i.ytimg.com/vi/AkrkZS1Lry0/hqdefault.jpg', '김아아kimaa', '(더현대 쇼핑🌸) 던스트 봄신상 추천템은요? #봄신상 #기본템 #던스트 #아우터', '2026-03-09 19:55:34', 35, 'ggyonghouse', 212),
    (9, 'GAsrBnRYRgo', 'https://www.youtube.com/shorts/GAsrBnRYRgo', 'https://i.ytimg.com/vi/GAsrBnRYRgo/hqdefault.jpg', '홀로나나 NANA', '프로혼밥러의 더현대 또또또간집 소개', '2024-03-12 22:10:00', 48, 'HOSU00', 214),
    (10, 'yrqYKwBBhrw', 'https://www.youtube.com/shorts/yrqYKwBBhrw', 'https://i.ytimg.com/vi/yrqYKwBBhrw/hqdefault.jpg', '나나자매Nana', '판교현백 쇼핑 코스 추천 💌 #판교현백 #판교현대백화점', '2026-08-06 18:00:34', 31, 'namcook', 216),
    (11, 'FlGUl3OiqRY', 'https://www.youtube.com/shorts/FlGUl3OiqRY', 'https://i.ytimg.com/vi/FlGUl3OiqRY/hqdefault.jpg', '연우', '봄옷사러 더현대 다녀왔어요 -🌸 유라고 팝업 | 더바넷 | 루에브르 (내돈내산)', '2026-03-27 21:45:01', 68, 'majifoodtv', 218);

-- 현재 활동 기수와 활성 YouTube 검수 정책을 확정한다.
SET @active_generation_id := (
    SELECT `generation_id`
    FROM `generation`
    WHERE `status` = 'ACTIVE'
      AND CURRENT_TIMESTAMP BETWEEN COALESCE(`activity_start_date`, `start_date`)
                                AND COALESCE(`activity_end_date`, `end_date`)
    ORDER BY COALESCE(`activity_start_date`, `start_date`) DESC
    LIMIT 1
);

SET @active_youtube_policy_id := (
    SELECT `inspection_policy_id`
    FROM `inspection_policy`
    WHERE `platform` = 'YOUTUBE'
      AND `is_active` = 1
    ORDER BY `activated_at` DESC, `inspection_policy_id` DESC
    LIMIT 1
);

-- 조건이 맞지 않으면 INSERT 전에 즉시 중단하기 위한 실행 전 검증 테이블.
CREATE TEMPORARY TABLE `tmp_youtube_seed_assert` (
    `assertion_name` VARCHAR(100) NOT NULL,
    `is_valid` TINYINT NOT NULL,
    PRIMARY KEY (`assertion_name`),
    CONSTRAINT `chk_tmp_youtube_seed_assert` CHECK (`is_valid` = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `tmp_youtube_seed_assert` VALUES
    ('active generation exists', IF(@active_generation_id IS NOT NULL, 1, 0)),
    ('active YouTube inspection policy exists', IF(@active_youtube_policy_id IS NOT NULL, 1, 0));

-- 요청 범위(204~221) 안의 기존 셀렉터스가 예상 PK와 자연키로 정확히 연결되고,
-- 현재 기수에 속하며 활성 YouTube 계정을 가진 상태인지 검증한다.
INSERT INTO `tmp_youtube_seed_assert` (`assertion_name`, `is_valid`)
SELECT
    'existing selectors 210/212/214/216/218/220 are valid',
    IF(COUNT(DISTINCT `s`.`selectors_id`) = 6, 1, 0)
FROM (
    SELECT DISTINCT `owner_hi_id`, `expected_selectors_id`
    FROM `tmp_youtube_inspection_content`
) AS `owner`
JOIN `users` AS `u`
  ON `u`.`hi_id` = `owner`.`owner_hi_id`
 AND `u`.`is_deleted` = 0
JOIN `selectors` AS `s`
  ON `s`.`user_id` = `u`.`user_id`
 AND `s`.`selectors_id` = `owner`.`expected_selectors_id`
 AND `s`.`selectors_role_id` = 'ACTIVE'
 AND `s`.`is_deleted` = 0
JOIN `selectors_generation` AS `sg`
  ON `sg`.`selectors_id` = `s`.`selectors_id`
 AND `sg`.`generation_id` = @active_generation_id
JOIN `selectors_sns_account` AS `ssa`
  ON `ssa`.`selectors_id` = `s`.`selectors_id`
 AND `ssa`.`sns_code` = 'YOUTUBE'
 AND `ssa`.`is_deleted` = b'0';

-- 동일 YouTube 영상이 이미 다른 셀렉터스 소유로 들어간 경우에는 덮어쓰지 않고 중단한다.
INSERT INTO `tmp_youtube_seed_assert` (`assertion_name`, `is_valid`)
SELECT
    'no existing content owner conflict',
    IF(COUNT(*) = 0, 1, 0)
FROM `tmp_youtube_inspection_content` AS `seed`
JOIN `content` AS `c`
  ON `c`.`sns_code` = 'YOUTUBE'
 AND `c`.`sns_content_id` = `seed`.`video_id`
WHERE `c`.`selectors_id` <> `seed`.`expected_selectors_id`;

START TRANSACTION;

-- =========================================================
-- 1. 신규 YouTube 채널 4명의 사용자 생성
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
    `seed`.`email`,
    `seed`.`phone`,
    '2026-08-31 18:00:00',
    '2026-08-31 18:00:00',
    0,
    'Y'
FROM `tmp_youtube_new_selectors` AS `seed`
WHERE NOT EXISTS (
    SELECT 1
    FROM `users` AS `existing_user`
    WHERE `existing_user`.`hi_id` = `seed`.`hi_id`
);

-- =========================================================
-- 2. 승인된 지원서 생성
-- =========================================================
INSERT INTO `application` (
    `user_id`, `sns_code`, `generation_id`, `sns_account_id`,
    `policy_agreed_at`, `alarm_yn`, `follower_count`, `content_count`,
    `last_content_at`, `created_at`, `updated_at`, `status`,
    `media_collection_status`, `analysis_status`, `profile_url`, `profile_image_url`
)
SELECT
    `u`.`user_id`,
    'YOUTUBE',
    @active_generation_id,
    `seed`.`account_id`,
    '2026-08-31 18:00:00',
    b'0',
    `seed`.`follower_count`,
    `seed`.`content_count`,
    '2026-08-31 18:00:00',
    '2026-08-31 18:00:00',
    '2026-08-31 18:10:00',
    'APPROVED',
    'PENDING',
    'PENDING',
    `seed`.`profile_url`,
    `seed`.`profile_image_url`
FROM `tmp_youtube_new_selectors` AS `seed`
JOIN `users` AS `u` ON `u`.`hi_id` = `seed`.`hi_id`
WHERE NOT EXISTS (
    SELECT 1
    FROM `application` AS `existing_application`
    WHERE `existing_application`.`user_id` = `u`.`user_id`
      AND `existing_application`.`generation_id` = @active_generation_id
);

-- =========================================================
-- 3. ACTIVE 셀렉터스 등록
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
    `seed`.`selectors_nickname`,
    '2026-08-31 18:10:00',
    '2026-08-31 18:10:00',
    0
FROM `tmp_youtube_new_selectors` AS `seed`
JOIN `users` AS `u` ON `u`.`hi_id` = `seed`.`hi_id`
JOIN `application` AS `app`
  ON `app`.`user_id` = `u`.`user_id`
 AND `app`.`generation_id` = @active_generation_id
WHERE NOT EXISTS (
    SELECT 1
    FROM `selectors` AS `existing_selectors`
    WHERE `existing_selectors`.`user_id` = `u`.`user_id`
);

-- =========================================================
-- 4. 현재 기수 활동 등록
-- =========================================================
INSERT INTO `selectors_generation` (
    `selectors_id`, `generation_id`, `created_at`,
    `total_sales`, `confirmed_purchase_count`, `paid_commission_amount`
)
SELECT
    `s`.`selectors_id`,
    @active_generation_id,
    '2026-08-31 18:10:00',
    0, 0, 0
FROM `tmp_youtube_new_selectors` AS `seed`
JOIN `users` AS `u` ON `u`.`hi_id` = `seed`.`hi_id`
JOIN `selectors` AS `s` ON `s`.`user_id` = `u`.`user_id`
WHERE NOT EXISTS (
    SELECT 1
    FROM `selectors_generation` AS `existing_generation`
    WHERE `existing_generation`.`selectors_id` = `s`.`selectors_id`
      AND `existing_generation`.`generation_id` = @active_generation_id
);

-- =========================================================
-- 5. 대표 YouTube 계정 등록
-- =========================================================
INSERT INTO `selectors_sns_account` (
    `created_at`, `updated_at`, `account_id`, `profile_url`, `is_deleted`,
    `follower_count`, `last_collected_at`, `profile_image_url`, `selectors_id`, `sns_code`
)
SELECT
    '2026-08-31 18:10:00',
    '2026-09-01 00:00:00',
    `seed`.`account_id`,
    `seed`.`profile_url`,
    b'0',
    `seed`.`follower_count`,
    '2026-09-01 00:00:00',
    `seed`.`profile_image_url`,
    `s`.`selectors_id`,
    'YOUTUBE'
FROM `tmp_youtube_new_selectors` AS `seed`
JOIN `users` AS `u` ON `u`.`hi_id` = `seed`.`hi_id`
JOIN `selectors` AS `s` ON `s`.`user_id` = `u`.`user_id`
WHERE NOT EXISTS (
    SELECT 1
    FROM `selectors_sns_account` AS `existing_account`
    WHERE `existing_account`.`selectors_id` = `s`.`selectors_id`
);

-- =========================================================
-- 6. 검수 대상 Content 11건 생성
-- =========================================================
INSERT INTO `content` (
    `selectors_id`, `sns_code`, `content_url`, `content_type`, `last_version_no`,
    `created_at`, `updated_at`, `is_deleted`, `sns_content_id`
)
SELECT
    `s`.`selectors_id`,
    'YOUTUBE',
    `seed`.`content_url`,
    'SHORTS',
    1,
    `seed`.`published_at`,
    `seed`.`published_at`,
    0,
    `seed`.`video_id`
FROM `tmp_youtube_inspection_content` AS `seed`
JOIN `users` AS `u` ON `u`.`hi_id` = `seed`.`owner_hi_id`
JOIN `selectors` AS `s`
  ON `s`.`user_id` = `u`.`user_id`
 AND `s`.`selectors_id` = `seed`.`expected_selectors_id`
WHERE NOT EXISTS (
    SELECT 1
    FROM `content` AS `existing_content`
    WHERE `existing_content`.`sns_code` = 'YOUTUBE'
      AND `existing_content`.`sns_content_id` = `seed`.`video_id`
);

-- =========================================================
-- 7. 최초 ContentVersion 생성
-- status=PENDING, inspection_decision=NULL이어야 리포트 생성 작업이 가져간다.
-- =========================================================
INSERT INTO `content_version` (
    `content_id`, `admin_id`, `version_no`, `content_hash`, `creation_reason`,
    `created_at`, `status`, `inspection_decision`, `inspected_at`, `updated_at`
)
SELECT
    `c`.`content_id`,
    NULL,
    1,
    LOWER(SHA2(CONCAT_WS(CHAR(10), 'YOUTUBE', `seed`.`video_id`, 'SHORTS', `seed`.`content_url`), 256)),
    'INITIAL',
    `seed`.`published_at`,
    'PENDING',
    NULL,
    NULL,
    `seed`.`published_at`
FROM `tmp_youtube_inspection_content` AS `seed`
JOIN `content` AS `c`
  ON `c`.`sns_code` = 'YOUTUBE'
 AND `c`.`sns_content_id` = `seed`.`video_id`
WHERE NOT EXISTS (
    SELECT 1
    FROM `content_version` AS `existing_version`
    WHERE `existing_version`.`content_id` = `c`.`content_id`
);

-- =========================================================
-- 8. YouTube 영상 미디어 생성
-- body={} / extracted_* = NULL 상태로 두어 실제 전처리(STT/OCR)부터 실행되게 한다.
-- =========================================================
INSERT INTO `content_media` (
    `content_version_id`, `media_url`, `thumbnail_url`, `media_type`, `body`,
    `sequence_no`, `sns_media_id`, `created_at`, `updated_at`,
    `extracted_with_policy_id`, `extraction_input_hash`, `extracted_at`
)
SELECT
    `cv`.`content_version_id`,
    `seed`.`content_url`,
    `seed`.`thumbnail_url`,
    'VIDEO',
    JSON_OBJECT(),
    0,
    `seed`.`video_id`,
    `seed`.`published_at`,
    `seed`.`published_at`,
    NULL,
    NULL,
    NULL
FROM `tmp_youtube_inspection_content` AS `seed`
JOIN `content` AS `c`
  ON `c`.`sns_code` = 'YOUTUBE'
 AND `c`.`sns_content_id` = `seed`.`video_id`
JOIN `content_version` AS `cv`
  ON `cv`.`content_id` = `c`.`content_id`
 AND `cv`.`version_no` = `c`.`last_version_no`
WHERE NOT EXISTS (
    SELECT 1
    FROM `content_media` AS `existing_media`
    WHERE `existing_media`.`content_version_id` = `cv`.`content_version_id`
      AND `existing_media`.`sequence_no` = 0
);

COMMIT;

-- =========================================================
-- 실행 결과 확인
-- =========================================================

-- 새로 등록한 채널 4명
SELECT
    `s`.`selectors_id`,
    `u`.`hi_id`,
    `u`.`name`,
    `s`.`selectors_nickname`,
    `ssa`.`account_id`,
    `ssa`.`follower_count`,
    `sg`.`generation_id`
FROM `tmp_youtube_new_selectors` AS `seed`
JOIN `users` AS `u` ON `u`.`hi_id` = `seed`.`hi_id`
JOIN `selectors` AS `s` ON `s`.`user_id` = `u`.`user_id`
JOIN `selectors_sns_account` AS `ssa` ON `ssa`.`selectors_id` = `s`.`selectors_id`
JOIN `selectors_generation` AS `sg`
  ON `sg`.`selectors_id` = `s`.`selectors_id`
 AND `sg`.`generation_id` = @active_generation_id
ORDER BY `seed`.`seed_no`;

-- 콘텐츠별 연결 대상과 검수/리포트 상태
SELECT
    `seed`.`seed_no`,
    `seed`.`video_id`,
    `seed`.`source_channel`,
    `seed`.`video_title`,
    `s`.`selectors_id`,
    `u`.`hi_id` AS `assigned_owner_hi_id`,
    `cv`.`content_version_id`,
    `cv`.`status` AS `version_status`,
    `cv`.`inspection_decision`,
    COUNT(DISTINCT `cm`.`content_media_id`) AS `media_count`,
    COUNT(DISTINCT `cr`.`content_report_id`) AS `content_report_count`
FROM `tmp_youtube_inspection_content` AS `seed`
JOIN `content` AS `c`
  ON `c`.`sns_code` = 'YOUTUBE'
 AND `c`.`sns_content_id` = `seed`.`video_id`
JOIN `selectors` AS `s` ON `s`.`selectors_id` = `c`.`selectors_id`
JOIN `users` AS `u` ON `u`.`user_id` = `s`.`user_id`
JOIN `content_version` AS `cv`
  ON `cv`.`content_id` = `c`.`content_id`
 AND `cv`.`version_no` = `c`.`last_version_no`
LEFT JOIN `content_media` AS `cm` ON `cm`.`content_version_id` = `cv`.`content_version_id`
LEFT JOIN `content_report` AS `cr`
  ON `cr`.`content_version_id` = `cv`.`content_version_id`
 AND `cr`.`inspection_policy_id` = @active_youtube_policy_id
GROUP BY
    `seed`.`seed_no`, `seed`.`video_id`, `seed`.`source_channel`, `seed`.`video_title`,
    `s`.`selectors_id`, `u`.`hi_id`, `cv`.`content_version_id`, `cv`.`status`, `cv`.`inspection_decision`
ORDER BY `seed`.`seed_no`;

-- 최초 실행 직후에는 ready_for_report_generation=11, content_report_count=0이 정상이다.
-- 리포트 생성 작업 실행 후에는 content_report_count가 증가하고 version_status도 변경된다.
SELECT
    SUM(
        CASE
            WHEN `cv`.`status` <> 'INSPECTING'
             AND `cv`.`inspection_decision` IS NULL
             AND `cr`.`content_report_id` IS NULL
            THEN 1 ELSE 0
        END
    ) AS `ready_for_report_generation`,
    COUNT(DISTINCT `cr`.`content_report_id`) AS `content_report_count`
FROM `tmp_youtube_inspection_content` AS `seed`
JOIN `content` AS `c`
  ON `c`.`sns_code` = 'YOUTUBE'
 AND `c`.`sns_content_id` = `seed`.`video_id`
JOIN `content_version` AS `cv`
  ON `cv`.`content_id` = `c`.`content_id`
 AND `cv`.`version_no` = `c`.`last_version_no`
LEFT JOIN `content_report` AS `cr`
  ON `cr`.`content_version_id` = `cv`.`content_version_id`
 AND `cr`.`inspection_policy_id` = @active_youtube_policy_id;

DROP TEMPORARY TABLE IF EXISTS `tmp_youtube_seed_assert`;
DROP TEMPORARY TABLE IF EXISTS `tmp_youtube_inspection_content`;
DROP TEMPORARY TABLE IF EXISTS `tmp_youtube_new_selectors`;
