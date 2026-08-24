-- 기수별 우수 활동자 선정은 한 번만 완료하며, 선정 시점의 매출·주문·혜택 값을 보존한다.
ALTER TABLE generation
    ADD COLUMN selector_excellence_selected_at DATETIME(6) NULL AFTER activity_end_date,
    ADD INDEX idx_generation_excellence_candidate
        (selector_excellence_selected_at, activity_end_date);

CREATE TABLE selector_excellence_selection (
    selection_id BIGINT NOT NULL AUTO_INCREMENT,
    generation_id BIGINT NOT NULL,
    selectors_id BIGINT NOT NULL,
    selection_type VARCHAR(30) NOT NULL,
    generation_sales DECIMAL(19, 2) NOT NULL,
    confirmed_order_count BIGINT NOT NULL,
    rank_no INT NULL,
    reward_type VARCHAR(30) NOT NULL,
    reward_value BIGINT NOT NULL,
    reward_quantity INT NOT NULL,
    selected_at DATETIME(6) NOT NULL,
    PRIMARY KEY (selection_id),
    CONSTRAINT uq_selector_excellence_generation_selector_type
        UNIQUE (generation_id, selectors_id, selection_type),
    CONSTRAINT fk_selector_excellence_generation
        FOREIGN KEY (generation_id) REFERENCES generation (generation_id),
    CONSTRAINT fk_selector_excellence_selectors
        FOREIGN KEY (selectors_id) REFERENCES selectors (selectors_id),
    INDEX idx_selector_excellence_selector_generation (selectors_id, generation_id),
    INDEX idx_selector_excellence_generation_type_rank
        (generation_id, selection_type, rank_no)
);

-- 기수 멤버 조회 후 셀렉터스별 확정 매출을 집계하는 선정 쿼리를 지원한다.
ALTER TABLE selectors_generation
    ADD INDEX idx_selectors_generation_generation_selector (generation_id, selectors_id);

ALTER TABLE purchase_history
    ADD INDEX idx_purchase_selector_status_purchased
        (selectors_id, status, purchased_at, confirmed_at);
