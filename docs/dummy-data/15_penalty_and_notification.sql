-- hi_selectors 로컬 성능 테스트용 페널티 해제 완료 더미데이터
--
-- 기준일: 2026-08-31
-- 구성:
--   - 무작위 셀렉터스 15명 1회 페널티 부여 후 해제 완료
--   - 그 중 5명 2회차 누적 페널티 부여 후 해제 완료
--   - 페널티 상태: 전부 'RELEASED'
--   - 알림톡: 부여 안내(20건) + 해제 안내(20건) 동시 생성

USE `hi_selectors`;
SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- =========================================================
-- 1. 필수 기초 데이터 확보 (부여 및 해제 목적 코드)
-- =========================================================
INSERT IGNORE INTO `notification_purpose` (`notification_purpose_code`, `notification_purpose_name`)
VALUES
    ('PENALTY_NOTICE', '페널티 부여 안내'),
    ('PENALTY_RELEASE', '페널티 해제 안내');

INSERT IGNORE INTO `violation_type` (`violation_type_id`, `code`, `description`, `created_at`, `updated_at`)
VALUES
    (1, 'V_GUIDELINE', '캠페인 가이드라인 미준수', NOW(), NOW()),
    (2, 'V_DELAY', '콘텐츠 업로드 기한 지연', NOW(), NOW()),
    (3, 'V_DELETE', '콘텐츠 무단 삭제 및 비공개', NOW(), NOW()),
    (4, 'V_ATTITUDE', '브랜드/타인 비방 및 부적절 언행', NOW(), NOW()),
    (5, 'V_OTHER', '기타 페널티 사유', NOW(), NOW());

-- =========================================================
-- 2. 페널티 대상자 임시 테이블 (해제 시간 포함)
-- =========================================================
DROP TEMPORARY TABLE IF EXISTS `tmp_penalty_targets`;
CREATE TEMPORARY TABLE `tmp_penalty_targets` (
    `seq` INT AUTO_INCREMENT PRIMARY KEY,
    `selectors_id` BIGINT,
    `generation_id` BIGINT,
    `violation_type_id` BIGINT,
    `admin_id` BIGINT,
    `reason` VARCHAR(500),
    `penalty_time` DATETIME,
    `release_time` DATETIME,
    `phone` VARCHAR(20)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 2-1. 기존 셀렉터스 중 랜덤 15명 (1차 페널티)
INSERT INTO `tmp_penalty_targets` (
    `selectors_id`, `generation_id`, `violation_type_id`, `admin_id`, `reason`, `penalty_time`, `release_time`, `phone`
)
SELECT
    s.selectors_id,
    (SELECT MAX(generation_id) FROM `selectors_generation` sg WHERE sg.selectors_id = s.selectors_id),
    FLOOR(RAND() * 5) + 1,
    1,
    ELT(FLOOR(RAND() * 4) + 1,
        '필수 해시태그 누락 및 가이드라인 미준수',
        '사전 협의 없는 콘텐츠 업로드 지연 (3일 이상)',
        '캠페인 유지 기간 내 콘텐츠 무단 삭제',
        '담당자 대상 부적절한 언행 접수'),
    DATE_SUB('2026-08-31 10:00:00', INTERVAL FLOOR(RAND() * 30 * 24 * 60) MINUTE) AS p_time,
    NULL,
    u.phone
FROM `selectors` s
         JOIN `users` u ON s.user_id = u.user_id
WHERE s.is_deleted = 0
ORDER BY RAND()
    LIMIT 15;

-- 2-2. 15명 중 5명 추가 (2차 누적 페널티)
INSERT INTO `tmp_penalty_targets` (
    `selectors_id`, `generation_id`, `violation_type_id`, `admin_id`, `reason`, `penalty_time`, `release_time`, `phone`
)
SELECT
    selectors_id,
    generation_id,
    FLOOR(RAND() * 5) + 1,
    1,
    '1차 경고 이후 가이드라인 재위반 (누적 페널티)',
    DATE_ADD(penalty_time, INTERVAL (FLOOR(RAND() * 5) + 5) DAY), -- 1차 후 5~9일 뒤 2차 부여
    NULL,
    phone
FROM `tmp_penalty_targets`
ORDER BY RAND()
    LIMIT 5;

-- 2-3. 해제 시간(release_time) 업데이트 (부여 후 1~4일 뒤 해제)
UPDATE `tmp_penalty_targets`
SET `release_time` = DATE_ADD(`penalty_time`, INTERVAL FLOOR(RAND() * 4) + 1 DAY);


START TRANSACTION;

-- =========================================================
-- 3. 페널티 이력 (penalty_history) 등록 (해제 상태)
-- =========================================================
INSERT INTO `penalty_history` (
    `selectors_id`, `generation_id`, `reason`, `source`, `granted_by_admin_id`, `released_by_admin_id`,
    `status`, `created_at`, `updated_at`, `started_at`, `ended_at`, `violation_type_id`
)
SELECT
    `selectors_id`,
    `generation_id`,
    `reason`,
    'ADMIN',
    `admin_id`, -- 부여 관리자
    `admin_id`, -- 해제 관리자
    'RELEASED', -- 해제 완료 상태
    `penalty_time`,
    `release_time`, -- 수정 일시는 해제 시점
    `penalty_time`,
    `release_time`, -- 종료 일시를 해제 시점으로 단축
    `violation_type_id`
FROM `tmp_penalty_targets`;

-- =========================================================
-- 4. 알림 발송 이력 (notification) 등록
-- =========================================================
-- 4-1. 페널티 부여 알림
INSERT INTO `notification` (
    `created_at`, `updated_at`, `body`, `notification_channel`, `notification_purpose_code`,
    `receiver`, `reference_id`, `request_at`, `sent_at`, `status`, `initiated_by_type`, `initiated_by_id`
)
SELECT
    ph.created_at,
    ph.created_at,
    CONCAT('[더현대Hi 셀렉터스] 페널티 안내\n\n안녕하세요. 더현대Hi 셀렉터스 운영팀입니다.\n아래 사유로 인하여 페널티가 부여되었습니다.\n\n- 사유: ', ph.reason),
    'KAKAO_MESSAGE',
    'PENALTY_NOTICE',
    t.phone,
    ph.penalty_history_id,
    ph.created_at,
    DATE_ADD(ph.created_at, INTERVAL 1 MINUTE),
    'SENT',
    'ADMIN',
    ph.granted_by_admin_id
FROM `penalty_history` ph
         JOIN `tmp_penalty_targets` t
              ON ph.selectors_id = t.selectors_id
                  AND ph.created_at = t.penalty_time
                  AND ph.reason = t.reason;

-- 4-2. 페널티 해제 알림
INSERT INTO `notification` (
    `created_at`, `updated_at`, `body`, `notification_channel`, `notification_purpose_code`,
    `receiver`, `reference_id`, `request_at`, `sent_at`, `status`, `initiated_by_type`, `initiated_by_id`
)
SELECT
    ph.updated_at,
    ph.updated_at,
    '[더현대Hi 셀렉터스] 페널티 해제 안내\n\n안녕하세요. 부여되었던 페널티가 정상적으로 해제 처리되었습니다. 앞으로도 원활한 활동 부탁드립니다.',
    'KAKAO_MESSAGE',
    'PENALTY_RELEASE',
    t.phone,
    ph.penalty_history_id,
    ph.updated_at,
    DATE_ADD(ph.updated_at, INTERVAL 1 MINUTE),
    'SENT',
    'ADMIN',
    ph.released_by_admin_id
FROM `penalty_history` ph
         JOIN `tmp_penalty_targets` t
              ON ph.selectors_id = t.selectors_id
                  AND ph.created_at = t.penalty_time
                  AND ph.reason = t.reason;

COMMIT;

-- =========================================================
-- 5. 검증
-- =========================================================
SELECT
    ph.penalty_history_id,
    s.selectors_code,
    ph.reason AS penalty_reason,
    ph.status AS penalty_status,
    ph.created_at AS granted_at,
    ph.updated_at AS released_at,
    (SELECT COUNT(*) FROM `notification` n WHERE n.reference_id = ph.penalty_history_id) AS linked_notification_count
FROM `penalty_history` ph
         JOIN `selectors` s ON ph.selectors_id = s.selectors_id
ORDER BY ph.created_at DESC;

DROP TEMPORARY TABLE IF EXISTS `tmp_penalty_targets`;