-- hi_selectors 로컬 테스트용 블랙리스트 이력 데이터
--
-- 기준일: 2026-09-01
-- 구성: 활성 블랙리스트 2명
-- 선행 스크립트: 09_selectors.sql
--
-- 09_selectors.sql에서 생성한 users.hi_id로 대상을 직접 찾는다.
-- blacklist_history만 추가하면 실제 권한 제한이 적용되지 않으므로,
-- selectors.selectors_role_id도 BLACKLIST로 함께 변경한다.
-- application.status=APPROVED와 기수/SNS 연결은 과거 승인 사실이므로 그대로 유지한다.
-- 모든 PK는 AUTO_INCREMENT가 자동 배정한다.

USE `hi_selectors`;
SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- 선행 데이터 확인: 아래 결과가 2행이어야 한다.
SELECT
    `u`.`hi_id`,
    `u`.`name`,
    `s`.`selectors_id`,
    `s`.`selectors_role_id`
FROM `users` AS `u`
JOIN `selectors` AS `s`
  ON `s`.`user_id` = `u`.`user_id`
WHERE `u`.`hi_id` IN ('_ejchoi', 'jennifer_wanna.b')
ORDER BY FIELD(`u`.`hi_id`, '_ejchoi', 'jennifer_wanna.b');

START TRANSACTION;

-- 1. 실제 서비스 접근 제한에 사용되는 현재 역할을 BLACKLIST로 변경한다.
UPDATE `selectors` AS `s`
JOIN `users` AS `u`
  ON `u`.`user_id` = `s`.`user_id`
SET
    `s`.`selectors_role_id` = 'BLACKLIST',
    `s`.`updated_at` = CASE `u`.`hi_id`
        WHEN '_ejchoi' THEN '2026-08-31 18:20:00'
        WHEN 'jennifer_wanna.b' THEN '2026-09-01 09:15:00'
        ELSE `s`.`updated_at`
    END
WHERE `u`.`hi_id` IN ('_ejchoi', 'jennifer_wanna.b');

-- 2. 활성 블랙리스트 이력 생성. 동일 셀렉터스의 ACTIVE 이력이 있으면 중복 생성하지 않는다.
INSERT INTO `blacklist_history` (
    `selectors_id`, `reason`, `status`, `created_at`, `updated_at`
)
SELECT
    `s`.`selectors_id`,
    CASE `u`.`hi_id`
        WHEN '_ejchoi'
            THEN '콘텐츠 운영 정책 반복 위반 및 수정 요청 불이행'
        WHEN 'jennifer_wanna.b'
            THEN '협찬 상품 반환 의무 불이행 및 담당자 장기 연락 두절'
    END,
    'ACTIVE',
    CASE `u`.`hi_id`
        WHEN '_ejchoi' THEN '2026-08-31 18:20:00'
        WHEN 'jennifer_wanna.b' THEN '2026-09-01 09:15:00'
    END,
    CASE `u`.`hi_id`
        WHEN '_ejchoi' THEN '2026-08-31 18:20:00'
        WHEN 'jennifer_wanna.b' THEN '2026-09-01 09:15:00'
    END
FROM `users` AS `u`
JOIN `selectors` AS `s`
  ON `s`.`user_id` = `u`.`user_id`
WHERE `u`.`hi_id` IN ('_ejchoi', 'jennifer_wanna.b')
  AND NOT EXISTS (
      SELECT 1
      FROM `blacklist_history` AS `existing_history`
      WHERE `existing_history`.`selectors_id` = `s`.`selectors_id`
        AND `existing_history`.`status` = 'ACTIVE'
  );

COMMIT;

-- 최종 검증: 2명 모두 role/status가 BLACKLIST/ACTIVE여야 한다.
SELECT
    `u`.`hi_id`,
    `u`.`name`,
    `s`.`selectors_id`,
    `s`.`selectors_role_id`,
    `history`.`blacklist_history_id`,
    `history`.`reason`,
    `history`.`status`,
    `history`.`created_at`
FROM `users` AS `u`
JOIN `selectors` AS `s`
  ON `s`.`user_id` = `u`.`user_id`
JOIN `blacklist_history` AS `history`
  ON `history`.`selectors_id` = `s`.`selectors_id`
 AND `history`.`status` = 'ACTIVE'
WHERE `u`.`hi_id` IN ('_ejchoi', 'jennifer_wanna.b')
ORDER BY FIELD(`u`.`hi_id`, '_ejchoi', 'jennifer_wanna.b');
