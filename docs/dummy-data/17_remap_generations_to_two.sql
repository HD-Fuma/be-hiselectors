-- 로컬 더미 DB의 기존 9기/10기를 1기/2기로 재편한다.
--
-- 매핑
--   기존 9기  -> 새 1기 (기간과 상태 유지)
--   기존 10기 -> 새 2기 (기간과 상태 유지)
--   기존 1~8기와 그 기수 전용 이력은 제거
--
-- generation PK를 직접 UPDATE하지 않는다. 새 1/2기 행을 만든 뒤 FK를 옮기므로
-- ON UPDATE CASCADE가 없는 현재 스키마에서도 FK 검사를 끄지 않고 실행할 수 있다.

SELECT
    @@hostname AS db_host,
    DATABASE() AS current_schema,
    CURRENT_USER() AS connected_user;

USE `hi_selectors`;

DELIMITER //

DROP PROCEDURE IF EXISTS `remap_dummy_generations_to_two`//

CREATE PROCEDURE `remap_dummy_generations_to_two`()
main: BEGIN
    DECLARE source_generation_count INT DEFAULT 0;
    DECLARE retained_generation_count INT DEFAULT 0;
    DECLARE old_application_count BIGINT DEFAULT 0;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        DROP TEMPORARY TABLE IF EXISTS `tmp_generation_remap_source`;
        RESIGNAL;
    END;

    SELECT COUNT(*)
      INTO source_generation_count
      FROM `generation`
     WHERE `generation_id` IN (9, 10);

    -- 이미 변환된 DB에서는 아무것도 변경하지 않는다.
    IF source_generation_count = 0 THEN
        SELECT COUNT(*)
          INTO retained_generation_count
          FROM `generation`
         WHERE `generation_id` IN (1, 2);

        IF retained_generation_count = 2
           AND (SELECT COUNT(*) FROM `generation`) = 2 THEN
            SELECT 'already migrated' AS migration_result;
            LEAVE main;
        END IF;

        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '기존 9기와 10기를 모두 찾을 수 없습니다.';
    END IF;

    IF source_generation_count <> 2 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '기존 9기와 10기가 모두 존재해야 합니다.';
    END IF;

    -- 더미 시드에서는 과거 1~8기에 application이 없어야 한다.
    -- 지원서는 selectors가 직접 참조할 수 있으므로 예상 밖 데이터가 있으면 삭제하지 않고 중단한다.
    SELECT COUNT(*)
      INTO old_application_count
      FROM `application`
     WHERE `generation_id` BETWEEN 1 AND 8;

    IF old_application_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '기존 1~8기에 지원서가 있어 자동 삭제할 수 없습니다.';
    END IF;

    START TRANSACTION;

    DROP TEMPORARY TABLE IF EXISTS `tmp_generation_remap_source`;
    CREATE TEMPORARY TABLE `tmp_generation_remap_source` AS
    SELECT *
      FROM `generation`
     WHERE `generation_id` IN (9, 10);

    -- 기존 1~8기 전용 이력을 FK 자식부터 정리한다.
    DELETE `best`
      FROM `best_selectors` AS `best`
      JOIN `selectors_generation` AS `membership`
        ON `membership`.`selectors_generation_id` = `best`.`selectors_generation_id`
     WHERE `membership`.`generation_id` BETWEEN 1 AND 8;

    DELETE FROM `selector_excellence_selection`
     WHERE `generation_id` BETWEEN 1 AND 8;

    DELETE FROM `penalty_history`
     WHERE `generation_id` BETWEEN 1 AND 8;

    DELETE FROM `selectors_generation`
     WHERE `generation_id` BETWEEN 1 AND 8;

    DELETE FROM `generation`
     WHERE `generation_id` BETWEEN 1 AND 8;

    -- 기존 9기/10기의 날짜, 우수 활동자 선정 시각, 상태는 유지하고 이름과 PK만 바꾼다.
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
    SELECT
        CASE `generation_id` WHEN 9 THEN 1 ELSE 2 END,
        CASE `generation_id` WHEN 9 THEN '셀렉터스 1기' ELSE '셀렉터스 2기' END,
        `start_date`,
        `end_date`,
        `activity_start_date`,
        `activity_end_date`,
        `selector_excellence_selected_at`,
        `status`
      FROM `tmp_generation_remap_source`
     ORDER BY `generation_id`;

    -- 9기/10기를 직접 참조하는 데이터를 새 1기/2기로 이동한다.
    UPDATE `application`
       SET `generation_id` = CASE `generation_id` WHEN 9 THEN 1 ELSE 2 END
     WHERE `generation_id` IN (9, 10);

    UPDATE `selectors_generation`
       SET `generation_id` = CASE `generation_id` WHEN 9 THEN 1 ELSE 2 END
     WHERE `generation_id` IN (9, 10);

    UPDATE `penalty_history`
       SET `generation_id` = CASE `generation_id` WHEN 9 THEN 1 ELSE 2 END
     WHERE `generation_id` IN (9, 10);

    UPDATE `selector_excellence_selection`
       SET `generation_id` = CASE `generation_id` WHEN 9 THEN 1 ELSE 2 END
     WHERE `generation_id` IN (9, 10);

    -- 모든 FK가 새 1/2기를 가리킨 뒤 원본 9/10기를 제거한다.
    DELETE FROM `generation`
     WHERE `generation_id` IN (9, 10);

    DROP TEMPORARY TABLE `tmp_generation_remap_source`;

    IF (SELECT COUNT(*) FROM `generation`) <> 2
       OR (SELECT COUNT(*) FROM `generation` WHERE `generation_id` IN (1, 2)) <> 2
       OR (SELECT COUNT(*) FROM `generation` WHERE `status` = 'ACTIVE') <> 1
       OR (SELECT COUNT(*) FROM `generation`
            WHERE `generation_id` = 2 AND `status` = 'ACTIVE') <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '변환 후 기수 상태 검증에 실패했습니다.';
    END IF;

    COMMIT;

    SELECT 'migrated' AS migration_result;
END//

CALL `remap_dummy_generations_to_two`()//
DROP PROCEDURE `remap_dummy_generations_to_two`//

DELIMITER ;

-- 다음 기수 생성 시 PK 3부터 사용한다. ALTER TABLE은 자동 커밋 DDL이므로
-- 위 트랜잭션과 검증이 모두 성공한 다음 실행한다.
ALTER TABLE `generation` AUTO_INCREMENT = 3;

-- 최종 검증
SELECT
    `generation_id`,
    `generation_name`,
    `start_date`,
    `end_date`,
    `activity_start_date`,
    `activity_end_date`,
    `selector_excellence_selected_at`,
    `status`
FROM `generation`
ORDER BY `generation_id`;

SELECT 'application' AS reference_table, `generation_id`, COUNT(*) AS row_count
  FROM `application`
 GROUP BY `generation_id`
UNION ALL
SELECT 'selectors_generation', `generation_id`, COUNT(*)
  FROM `selectors_generation`
 GROUP BY `generation_id`
UNION ALL
SELECT 'penalty_history', `generation_id`, COUNT(*)
  FROM `penalty_history`
 WHERE `generation_id` IS NOT NULL
 GROUP BY `generation_id`
UNION ALL
SELECT 'selector_excellence_selection', `generation_id`, COUNT(*)
  FROM `selector_excellence_selection`
 GROUP BY `generation_id`
ORDER BY reference_table, generation_id;
