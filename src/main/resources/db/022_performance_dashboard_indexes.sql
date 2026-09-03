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
