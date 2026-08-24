-- selectors_generation 기수별 정산 성과 집계 컬럼
ALTER TABLE selectors_generation
    ADD COLUMN total_sales BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN confirmed_purchase_count BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN paid_commission_amount BIGINT NOT NULL DEFAULT 0;

-- 기존 SETTLED 정산 이력 백필.
-- 기수 활동 기간과 활동월이 겹치는 정산 이력을 기수별로 다시 합산한다.
UPDATE selectors_generation sg
LEFT JOIN (
    SELECT
        membership.selectors_generation_id,
        COALESCE(SUM(history.total_sales), 0) AS total_sales,
        COALESCE(SUM(history.confirmed_purchase_count), 0) AS confirmed_purchase_count,
        COALESCE(SUM(history.commission), 0) AS paid_commission_amount
    FROM selectors_generation membership
    JOIN generation generation_info
      ON generation_info.generation_id = membership.generation_id
    LEFT JOIN settlement_history history
      ON history.selectors_id = membership.selectors_id
     AND history.status = 'SETTLED'
     AND history.activity_year_month >=
         (YEAR(generation_info.activity_start_date) * 100
             + MONTH(generation_info.activity_start_date))
     AND history.activity_year_month <=
         (YEAR(generation_info.activity_end_date) * 100
             + MONTH(generation_info.activity_end_date))
    GROUP BY membership.selectors_generation_id
) aggregate
  ON aggregate.selectors_generation_id = sg.selectors_generation_id
SET sg.total_sales = COALESCE(aggregate.total_sales, 0),
    sg.confirmed_purchase_count = COALESCE(aggregate.confirmed_purchase_count, 0),
    sg.paid_commission_amount = COALESCE(aggregate.paid_commission_amount, 0);
