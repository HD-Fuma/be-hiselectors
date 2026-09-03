-- 관리자 정산 구매내역의 고정 정렬과 Cursor 탐색을 지원한다.
CREATE INDEX idx_purchase_history_purchased_id
    ON purchase_history (purchased_at DESC, purchase_history_id DESC);

CREATE INDEX idx_purchase_history_selector_purchased_id
    ON purchase_history (selectors_id, purchased_at DESC, purchase_history_id DESC);
