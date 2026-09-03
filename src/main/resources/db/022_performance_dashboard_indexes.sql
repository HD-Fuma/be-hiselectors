-- 셀렉터스 성과 summary/trend 집계용. 정산 커서가 쓰는 purchased_at 인덱스와 별개로 둔다.
-- 같은 테이블에 인덱스를 여러 개 둘 수 있고, 쿼리는 WHERE에 맞는 인덱스를 탄다.
-- 성과 집계는 confirmed_at 범위를 쓰므로 아래 인덱스를 FORCE INDEX로 고정한다.
--
-- idx_purchase_selector_status_confirmed
--   summarizeConfirmedSales, summarizeConfirmedSalesByDay/Month,
--   summarizeConfirmedSalesBySelectorAndDay, breakdown
-- idx_click_selector_type_created
--   countProductClicks
-- idx_content_selector_deleted_created
--   countContents

SET @idx_exists := (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'purchase_history'
      AND index_name = 'idx_purchase_selector_status_confirmed'
);
SET @sql := IF(
    @idx_exists = 0,
    'CREATE INDEX idx_purchase_selector_status_confirmed ON purchase_history (selectors_id, status, confirmed_at)',
    'DO 0'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'click_log'
      AND index_name = 'idx_click_selector_type_created'
);
SET @sql := IF(
    @idx_exists = 0,
    'CREATE INDEX idx_click_selector_type_created ON click_log (selectors_id, link_type, created_at)',
    'DO 0'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'content'
      AND index_name = 'idx_content_selector_deleted_created'
);
SET @sql := IF(
    @idx_exists = 0,
    'CREATE INDEX idx_content_selector_deleted_created ON content (selectors_id, is_deleted, created_at)',
    'DO 0'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
-- 관리자 대시보드와 셀렉터스 성과 화면의 확정 매출 기간 집계용.
-- 기존 인덱스는 purchased_at이 confirmed_at 앞에 있어 확정일 범위 검색에 부적합하다.
CREATE INDEX idx_purchase_selector_status_confirmed
    ON purchase_history (selectors_id, status, confirmed_at);

-- 셀렉터스 성과 요약의 기간별 상품 클릭 집계용.
CREATE INDEX idx_click_selector_type_created
    ON click_log (selectors_id, link_type, created_at);

-- 셀렉터스 성과 요약의 기간별 콘텐츠 집계용.
CREATE INDEX idx_content_selector_deleted_created
    ON content (selectors_id, is_deleted, created_at);
