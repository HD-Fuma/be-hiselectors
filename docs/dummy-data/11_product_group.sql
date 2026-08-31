-- hi_selectors 로컬 테스트용 셀렉터스 상품 그룹 데이터
--
-- 기준일: 2026-09-01
-- 선행 스크립트:
--   02_campaign.sql
--   03_generation.sql
--   06_campaign_product.sql
--   09_selectors.sql
--   10_blacklist_history.sql
--
-- 구성:
--   셀렉터스 6명, 상품 그룹 15개, 상품 그룹 항목 117개
--   셀렉터스별 그룹 2~3개
--   그룹별 상품 6~9개
--   동일 캠페인의 그룹은 최대 3개
--
-- 이전 기수 참여자의 과거 그룹:
--   _eunvitamin_  : 9기 / 가정의 달 감사 선물전
--   by.ellenlee   : 9기 / 초여름 리빙 리프레시
--   hyosun_kim_   : 8기 / 새학기 캠퍼스 스타일,
--                   9기 / 장마철 라이프 케어
--
-- UK 기준:
--   애플리케이션 ProductGroup 엔티티:
--     uk_product_group_selector_no(selectors_id, group_no)
--   실제 DDL product_group_item:
--     uk_product_group_item_product(group_id, product_id)
--
-- 현재 docs/hi_selectors_demo_ddl.sql의 product_group에는 첫 번째 UK가 빠져 있다.
-- 이 스크립트는 NOT EXISTS로 같은 의미의 중복을 막지만, 운영 스키마에는
-- uk_product_group_selector_no를 별도 반영하는 것이 안전하다.
-- 모든 PK는 AUTO_INCREMENT가 자동 배정한다.

USE `hi_selectors`;
SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;

DROP TEMPORARY TABLE IF EXISTS `tmp_product_group_items`;
DROP TEMPORARY TABLE IF EXISTS `tmp_product_groups`;

CREATE TEMPORARY TABLE `tmp_product_groups` (
    `group_seed_no` INT NOT NULL,
    `hi_id` VARCHAR(20) NOT NULL,
    `group_no` SMALLINT NOT NULL,
    `campaign_id` BIGINT NOT NULL,
    `title` VARCHAR(100) NOT NULL,
    `created_at` DATETIME NOT NULL,
    PRIMARY KEY (`group_seed_no`),
    UNIQUE KEY `uq_tmp_product_group_selector_no` (`hi_id`, `group_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `tmp_product_groups` (
    `group_seed_no`, `hi_id`, `group_no`, `campaign_id`, `title`, `created_at`
)
VALUES
    -- _ejchoi: 블랙리스트 지정 전 생성한 현재 기수 그룹
    ( 1, '_ejchoi',          1, 20, '출근부터 주말까지 가을 기본템', '2026-08-29 17:40:00'),
    ( 2, '_ejchoi',          2, 17, '부담 없이 고르는 추석 선물',     '2026-08-30 10:10:00'),

    -- _eunvitamin_: 9기 그룹 1개 + 10기 그룹 1개
    ( 3, '_eunvitamin_',     1, 12, '부모님께 전하는 감사 선물',       '2026-05-03 20:15:00'),
    ( 4, '_eunvitamin_',     2, 19, '환절기 보습 루틴',                 '2026-08-23 21:05:00'),

    -- by.ellenlee: 9기 그룹 1개 + 10기 그룹 2개
    ( 5, 'by.ellenlee',      1, 13, '작은 변화로 완성하는 여름 집',     '2026-05-28 19:20:00'),
    ( 6, 'by.ellenlee',      2, 18, '오후를 위한 홈카페 셀렉션',        '2026-08-28 16:35:00'),
    ( 7, 'by.ellenlee',      3, 20, '단정한 가을 데일리 룩',            '2026-08-29 11:10:00'),

    -- hyosun_kim_: 8기·9기 그룹 각 1개 + 10기 그룹 1개
    ( 8, 'hyosun_kim_',      1, 10, '새학기 일주일 캠퍼스 룩',          '2026-03-02 18:50:00'),
    ( 9, 'hyosun_kim_',      2, 14, '습한 날을 위한 집 관리',           '2026-06-22 10:40:00'),
    (10, 'hyosun_kim_',      3, 16, '가볍게 시작하는 러닝 장비',        '2026-08-31 07:15:00'),

    -- jennifer_wanna.b: 블랙리스트 지정 전 생성한 현재 기수 그룹
    (11, 'jennifer_wanna.b', 1, 19, '민감해진 피부 진정 조합',           '2026-08-30 15:10:00'),
    (12, 'jennifer_wanna.b', 2, 18, '차분한 홈카페 테이블',              '2026-08-31 10:45:00'),

    -- jieunisong: 현재 기수 그룹 3개
    (13, 'jieunisong',       1, 17, '온 가족을 위한 명절 선물',          '2026-08-30 18:10:00'),
    (14, 'jieunisong',       2, 16, '주말 러닝 필수 아이템',             '2026-08-31 08:20:00'),
    (15, 'jieunisong',       3, 20, '가을 통학·출근 레이어드',           '2026-08-31 12:40:00');

CREATE TEMPORARY TABLE `tmp_product_group_items` (
    `group_seed_no` INT NOT NULL,
    `product_id` BIGINT NOT NULL,
    `display_order` SMALLINT NOT NULL,
    PRIMARY KEY (`group_seed_no`, `product_id`),
    UNIQUE KEY `uq_tmp_product_group_display_order` (`group_seed_no`, `display_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `tmp_product_group_items` (
    `group_seed_no`, `product_id`, `display_order`
)
VALUES
    -- 1. 출근부터 주말까지 가을 기본템 (8개)
    (1, 57, 1), (1, 58, 2), (1, 61, 3), (1, 64, 4),
    (1, 67, 5), (1, 70, 6), (1, 75, 7), (1, 84, 8),

    -- 2. 부담 없이 고르는 추석 선물 (6개)
    (2, 93, 1), (2, 95, 2), (2, 100, 3),
    (2, 103, 4), (2, 111, 5), (2, 117, 6),

    -- 3. 부모님께 전하는 감사 선물 (8개)
    (3, 3, 1), (3, 11, 2), (3, 93, 3), (3, 96, 4),
    (3, 100, 5), (3, 103, 6), (3, 106, 7), (3, 129, 8),

    -- 4. 환절기 보습 루틴 (9개)
    (4, 21, 1), (4, 25, 2), (4, 28, 3), (4, 32, 4), (4, 39, 5),
    (4, 43, 6), (4, 47, 7), (4, 52, 8), (4, 55, 9),

    -- 5. 작은 변화로 완성하는 여름 집 (7개)
    (5, 111, 1), (5, 112, 2), (5, 115, 3), (5, 118, 4),
    (5, 122, 5), (5, 127, 6), (5, 188, 7),

    -- 6. 오후를 위한 홈카페 셀렉션 (8개)
    (6, 4, 1), (6, 5, 2), (6, 111, 3), (6, 113, 4),
    (6, 116, 5), (6, 120, 6), (6, 123, 7), (6, 128, 8),

    -- 7. 단정한 가을 데일리 룩 (7개)
    (7, 12, 1), (7, 14, 2), (7, 17, 3), (7, 19, 4),
    (7, 59, 5), (7, 63, 6), (7, 66, 7),

    -- 8. 새학기 일주일 캠퍼스 룩 (9개)
    (8, 57, 1), (8, 60, 2), (8, 62, 3), (8, 65, 4), (8, 69, 5),
    (8, 73, 6), (8, 77, 7), (8, 81, 8), (8, 89, 9),

    -- 9. 습한 날을 위한 집 관리 (8개)
    (9, 111, 1), (9, 114, 2), (9, 119, 3), (9, 124, 4),
    (9, 128, 5), (9, 189, 6), (9, 194, 7), (9, 195, 8),

    -- 10. 가볍게 시작하는 러닝 장비 (7개)
    (10, 165, 1), (10, 167, 2), (10, 168, 3), (10, 173, 4),
    (10, 175, 5), (10, 179, 6), (10, 181, 7),

    -- 11. 민감해진 피부 진정 조합 (8개)
    (11, 22, 1), (11, 24, 2), (11, 29, 3), (11, 33, 4),
    (11, 36, 5), (11, 40, 6), (11, 44, 7), (11, 49, 8),

    -- 12. 차분한 홈카페 테이블 (7개)
    (12, 6, 1), (12, 7, 2), (12, 112, 3), (12, 115, 4),
    (12, 117, 5), (12, 121, 6), (12, 126, 7),

    -- 13. 온 가족을 위한 명절 선물 (8개)
    (13, 94, 1), (13, 97, 2), (13, 101, 3), (13, 105, 4),
    (13, 109, 5), (13, 113, 6), (13, 120, 7), (13, 127, 8),

    -- 14. 주말 러닝 필수 아이템 (8개)
    (14, 166, 1), (14, 170, 2), (14, 171, 3), (14, 174, 4),
    (14, 176, 5), (14, 178, 6), (14, 182, 7), (14, 75, 8),

    -- 15. 가을 통학·출근 레이어드 (9개)
    (15, 13, 1), (15, 15, 2), (15, 18, 3), (15, 57, 4), (15, 62, 5),
    (15, 68, 6), (15, 72, 7), (15, 80, 8), (15, 91, 9);

-- 선행 데이터 및 시드 자체 검증. 모두 0이어야 한다.
SELECT
    SUM(`u`.`user_id` IS NULL OR `s`.`selectors_id` IS NULL)
        AS `missing_selectors_count`,
    SUM(`c`.`campaign_id` IS NULL) AS `missing_campaign_count`,
    SUM(DATE(`group_seed`.`created_at`) NOT BETWEEN `c`.`start_date` AND `c`.`end_date`)
        AS `group_outside_campaign_period_count`,
    SUM(NOT EXISTS (
        SELECT 1
        FROM `selectors_generation` AS `sg`
        JOIN `generation` AS `g`
          ON `g`.`generation_id` = `sg`.`generation_id`
        WHERE `sg`.`selectors_id` = `s`.`selectors_id`
          AND `group_seed`.`created_at`
              BETWEEN `g`.`activity_start_date` AND `g`.`activity_end_date`
    )) AS `group_outside_membership_period_count`
FROM `tmp_product_groups` AS `group_seed`
LEFT JOIN `users` AS `u`
       ON `u`.`hi_id` = `group_seed`.`hi_id`
LEFT JOIN `selectors` AS `s`
       ON `s`.`user_id` = `u`.`user_id`
LEFT JOIN `campaign` AS `c`
       ON `c`.`campaign_id` = `group_seed`.`campaign_id`;

SELECT COUNT(*) AS `campaign_product_mismatch_count`
FROM `tmp_product_group_items` AS `item_seed`
JOIN `tmp_product_groups` AS `group_seed`
  ON `group_seed`.`group_seed_no` = `item_seed`.`group_seed_no`
LEFT JOIN `campaign_product` AS `cp`
       ON `cp`.`campaign_id` = `group_seed`.`campaign_id`
      AND `cp`.`product_id` = `item_seed`.`product_id`
WHERE `cp`.`campaign_product_id` IS NULL;

START TRANSACTION;

-- 1. 상품 그룹 생성.
INSERT INTO `product_group` (
    `selectors_id`, `campaign_id`, `group_no`, `title`,
    `created_at`, `updated_at`, `is_deleted`
)
SELECT
    `s`.`selectors_id`,
    `group_seed`.`campaign_id`,
    `group_seed`.`group_no`,
    `group_seed`.`title`,
    `group_seed`.`created_at`,
    `group_seed`.`created_at`,
    0
FROM `tmp_product_groups` AS `group_seed`
JOIN `users` AS `u`
  ON `u`.`hi_id` = `group_seed`.`hi_id`
JOIN `selectors` AS `s`
  ON `s`.`user_id` = `u`.`user_id`
JOIN `campaign` AS `c`
  ON `c`.`campaign_id` = `group_seed`.`campaign_id`
 AND `c`.`is_deleted` = 0
 AND DATE(`group_seed`.`created_at`) BETWEEN `c`.`start_date` AND `c`.`end_date`
WHERE NOT EXISTS (
    SELECT 1
    FROM `product_group` AS `existing_group`
    WHERE `existing_group`.`selectors_id` = `s`.`selectors_id`
      AND `existing_group`.`group_no` = `group_seed`.`group_no`
);

-- 2. 캠페인에 실제로 연결된 상품만 그룹 항목으로 생성.
INSERT INTO `product_group_item` (
    `group_id`, `product_id`, `display_order`,
    `created_at`, `updated_at`, `is_deleted`
)
SELECT
    `pg`.`product_group_id`,
    `item_seed`.`product_id`,
    `item_seed`.`display_order`,
    TIMESTAMPADD(MINUTE, `item_seed`.`display_order`, `group_seed`.`created_at`),
    TIMESTAMPADD(MINUTE, `item_seed`.`display_order`, `group_seed`.`created_at`),
    0
FROM `tmp_product_group_items` AS `item_seed`
JOIN `tmp_product_groups` AS `group_seed`
  ON `group_seed`.`group_seed_no` = `item_seed`.`group_seed_no`
JOIN `users` AS `u`
  ON `u`.`hi_id` = `group_seed`.`hi_id`
JOIN `selectors` AS `s`
  ON `s`.`user_id` = `u`.`user_id`
JOIN `product_group` AS `pg`
  ON `pg`.`selectors_id` = `s`.`selectors_id`
 AND `pg`.`group_no` = `group_seed`.`group_no`
 AND `pg`.`campaign_id` = `group_seed`.`campaign_id`
JOIN `campaign_product` AS `cp`
  ON `cp`.`campaign_id` = `group_seed`.`campaign_id`
 AND `cp`.`product_id` = `item_seed`.`product_id`
WHERE NOT EXISTS (
    SELECT 1
    FROM `product_group_item` AS `existing_item`
    WHERE `existing_item`.`group_id` = `pg`.`product_group_id`
      AND `existing_item`.`product_id` = `item_seed`.`product_id`
);

COMMIT;

-- 검증 1: 셀렉터스별 그룹은 2~3개여야 한다.
SELECT
    `group_seed`.`hi_id`,
    COUNT(`pg`.`product_group_id`) AS `group_count`,
    MIN(`pg`.`group_no`) AS `first_group_no`,
    MAX(`pg`.`group_no`) AS `last_group_no`
FROM `tmp_product_groups` AS `group_seed`
JOIN `users` AS `u`
  ON `u`.`hi_id` = `group_seed`.`hi_id`
JOIN `selectors` AS `s`
  ON `s`.`user_id` = `u`.`user_id`
LEFT JOIN `product_group` AS `pg`
       ON `pg`.`selectors_id` = `s`.`selectors_id`
      AND `pg`.`group_no` = `group_seed`.`group_no`
GROUP BY `group_seed`.`hi_id`
ORDER BY MIN(`group_seed`.`group_seed_no`);

-- 검증 2: 그룹별 상품은 2~10개이며 표시 순서는 1부터 연속되어야 한다.
SELECT
    `group_seed`.`hi_id`,
    `pg`.`group_no`,
    `c`.`title` AS `campaign_title`,
    `pg`.`title` AS `group_title`,
    `pg`.`created_at`,
    COUNT(`pgi`.`product_group_item_id`) AS `item_count`,
    MIN(`pgi`.`display_order`) AS `first_display_order`,
    MAX(`pgi`.`display_order`) AS `last_display_order`
FROM `tmp_product_groups` AS `group_seed`
JOIN `users` AS `u`
  ON `u`.`hi_id` = `group_seed`.`hi_id`
JOIN `selectors` AS `s`
  ON `s`.`user_id` = `u`.`user_id`
JOIN `product_group` AS `pg`
  ON `pg`.`selectors_id` = `s`.`selectors_id`
 AND `pg`.`group_no` = `group_seed`.`group_no`
JOIN `campaign` AS `c`
  ON `c`.`campaign_id` = `pg`.`campaign_id`
LEFT JOIN `product_group_item` AS `pgi`
       ON `pgi`.`group_id` = `pg`.`product_group_id`
      AND `pgi`.`is_deleted` = 0
GROUP BY
    `group_seed`.`group_seed_no`, `group_seed`.`hi_id`,
    `pg`.`product_group_id`, `pg`.`group_no`, `c`.`title`,
    `pg`.`title`, `pg`.`created_at`
ORDER BY `group_seed`.`group_seed_no`;

-- 검증 3: 아래 오류 수는 모두 0, 최종 건수는 15개/117개여야 한다.
SELECT
    SUM(`summary`.`item_count` NOT BETWEEN 2 AND 10) AS `invalid_item_count`,
    SUM(`summary`.`first_order` <> 1 OR `summary`.`last_order` <> `summary`.`item_count`)
        AS `invalid_display_order_count`
FROM (
    SELECT
        `pg`.`product_group_id`,
        COUNT(`pgi`.`product_group_item_id`) AS `item_count`,
        MIN(`pgi`.`display_order`) AS `first_order`,
        MAX(`pgi`.`display_order`) AS `last_order`
    FROM `tmp_product_groups` AS `group_seed`
    JOIN `users` AS `u`
      ON `u`.`hi_id` = `group_seed`.`hi_id`
    JOIN `selectors` AS `s`
      ON `s`.`user_id` = `u`.`user_id`
    JOIN `product_group` AS `pg`
      ON `pg`.`selectors_id` = `s`.`selectors_id`
     AND `pg`.`group_no` = `group_seed`.`group_no`
    LEFT JOIN `product_group_item` AS `pgi`
           ON `pgi`.`group_id` = `pg`.`product_group_id`
          AND `pgi`.`is_deleted` = 0
    GROUP BY `pg`.`product_group_id`
) AS `summary`;

SELECT
    COUNT(DISTINCT `pg`.`product_group_id`) AS `product_group_count`,
    COUNT(`pgi`.`product_group_item_id`) AS `product_group_item_count`
FROM `tmp_product_groups` AS `group_seed`
JOIN `users` AS `u`
  ON `u`.`hi_id` = `group_seed`.`hi_id`
JOIN `selectors` AS `s`
  ON `s`.`user_id` = `u`.`user_id`
JOIN `product_group` AS `pg`
  ON `pg`.`selectors_id` = `s`.`selectors_id`
 AND `pg`.`group_no` = `group_seed`.`group_no`
LEFT JOIN `product_group_item` AS `pgi`
       ON `pgi`.`group_id` = `pg`.`product_group_id`
      AND `pgi`.`is_deleted` = 0;

DROP TEMPORARY TABLE `tmp_product_group_items`;
DROP TEMPORARY TABLE `tmp_product_groups`;
