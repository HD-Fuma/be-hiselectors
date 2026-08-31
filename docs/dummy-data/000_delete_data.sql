-- 반드시 hi-selectors-demo DB 연결인지 먼저 확인합니다.
SELECT
    @@hostname AS db_host,
    DATABASE() AS current_schema,
    CURRENT_USER() AS connected_user;

USE `hi_selectors`;

-- 전체 DELETE가 Safe Updates 설정에 막히지 않도록
-- 현재 세션에서만 일시적으로 해제합니다.
SET @OLD_SQL_SAFE_UPDATES := @@SESSION.SQL_SAFE_UPDATES;
SET SESSION SQL_SAFE_UPDATES = 0;

-- FK 검사는 끄지 않습니다.
SET SESSION FOREIGN_KEY_CHECKS = 1;

START TRANSACTION;

-- =========================================================
-- 1. 집계·알림·작업 이력
-- =========================================================
DELETE FROM `task_run`;
DELETE FROM `notification`;
DELETE FROM `best_selectors`;
DELETE FROM `selector_excellence_selection`;
DELETE FROM `settlement_history`;
DELETE FROM `campaign_performance`;

-- =========================================================
-- 2. 위반 증거·항목
-- =========================================================
DELETE FROM `violation_evidence_history`;
DELETE FROM `violation_item`;

-- =========================================================
-- 3. 검수·콘텐츠 버전
-- =========================================================
DELETE FROM `penalty_history`;
DELETE FROM `content_report`;
DELETE FROM `content_media`;
DELETE FROM `product_group_item`;
DELETE FROM `content_version`;
DELETE FROM `content_engagement`;

-- =========================================================
-- 4. 셀렉터 하위 데이터
-- =========================================================
DELETE FROM `click_log`;
DELETE FROM `settlement_account`;
DELETE FROM `selectors_sns_account`;
DELETE FROM `selectors_generation`;
DELETE FROM `purchase_history`;
DELETE FROM `product_group`;
DELETE FROM `content`;
DELETE FROM `blacklist_history`;
DELETE FROM `selectors`;

-- =========================================================
-- 5. 지원자 콘텐츠
-- =========================================================
DELETE FROM `application_report`;
DELETE FROM `application_content_analysis`;
DELETE FROM `application_media_url`;
DELETE FROM `application_media`;

-- =========================================================
-- 6. 지원·크리에이터 상세
-- =========================================================
DELETE FROM `application`;
DELETE FROM `proposal_history`;
DELETE FROM `creator_report`;
DELETE FROM `creator_discovery_source`;
DELETE FROM `creator_discovery_info`;

-- =========================================================
-- 7. 캠페인·사용자·발굴
-- =========================================================
DELETE FROM `campaign_product`;
DELETE FROM `user_kakao_recipient`;
DELETE FROM `discovery_keyword`;
DELETE FROM `creator_pool`;

-- =========================================================
-- 8. 최상위 초기화 대상
-- =========================================================
DELETE FROM `users`;
DELETE FROM `inspection_policy`;
DELETE FROM `generation`;
DELETE FROM `campaign`;

COMMIT;

SET SESSION SQL_SAFE_UPDATES = @OLD_SQL_SAFE_UPDATES;

-- =========================================================
-- AUTO_INCREMENT 초기화
--
-- ALTER TABLE은 자동 커밋되는 DDL입니다.
-- 위 DELETE와 COMMIT이 모두 성공한 뒤 실행해야 합니다.
-- =========================================================

ALTER TABLE `application`
    AUTO_INCREMENT = 1;

ALTER TABLE `application_content_analysis`
    AUTO_INCREMENT = 1;

ALTER TABLE `application_media`
    AUTO_INCREMENT = 1;

ALTER TABLE `application_report`
    AUTO_INCREMENT = 1;

ALTER TABLE `best_selectors`
    AUTO_INCREMENT = 1;

ALTER TABLE `blacklist_history`
    AUTO_INCREMENT = 1;

ALTER TABLE `campaign`
    AUTO_INCREMENT = 1;

ALTER TABLE `campaign_performance`
    AUTO_INCREMENT = 1;

ALTER TABLE `campaign_product`
    AUTO_INCREMENT = 1;

ALTER TABLE `click_log`
    AUTO_INCREMENT = 1;

ALTER TABLE `content`
    AUTO_INCREMENT = 1;

ALTER TABLE `content_engagement`
    AUTO_INCREMENT = 1;

ALTER TABLE `content_media`
    AUTO_INCREMENT = 1;

ALTER TABLE `content_report`
    AUTO_INCREMENT = 1;

ALTER TABLE `content_version`
    AUTO_INCREMENT = 1;

ALTER TABLE `creator_discovery_source`
    AUTO_INCREMENT = 1;

ALTER TABLE `creator_pool`
    AUTO_INCREMENT = 1;

ALTER TABLE `creator_report`
    AUTO_INCREMENT = 1;

ALTER TABLE `discovery_keyword`
    AUTO_INCREMENT = 1;

ALTER TABLE `generation`
    AUTO_INCREMENT = 1;

ALTER TABLE `inspection_policy`
    AUTO_INCREMENT = 1;

ALTER TABLE `notification`
    AUTO_INCREMENT = 1;

ALTER TABLE `penalty_history`
    AUTO_INCREMENT = 1;

ALTER TABLE `product_group`
    AUTO_INCREMENT = 1;

ALTER TABLE `product_group_item`
    AUTO_INCREMENT = 1;

ALTER TABLE `proposal_history`
    AUTO_INCREMENT = 1;

ALTER TABLE `purchase_history`
    AUTO_INCREMENT = 1;

ALTER TABLE `selector_excellence_selection`
    AUTO_INCREMENT = 1;

ALTER TABLE `selectors`
    AUTO_INCREMENT = 1;

ALTER TABLE `selectors_generation`
    AUTO_INCREMENT = 1;

ALTER TABLE `selectors_sns_account`
    AUTO_INCREMENT = 1;

ALTER TABLE `settlement_account`
    AUTO_INCREMENT = 1;

ALTER TABLE `settlement_history`
    AUTO_INCREMENT = 1;

ALTER TABLE `task_run`
    AUTO_INCREMENT = 1;

ALTER TABLE `user_kakao_recipient`
    AUTO_INCREMENT = 1;

ALTER TABLE `users`
    AUTO_INCREMENT = 1;

ALTER TABLE `violation_evidence_history`
    AUTO_INCREMENT = 1;

ALTER TABLE `violation_item`
    AUTO_INCREMENT = 1;