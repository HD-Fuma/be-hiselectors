-- hi_selectors 로컬 성능 테스트용 구매이력(purchase_history) 데이터
--
-- 기준일: 2026-08-31
-- 구성: 총 200,000건
--
-- 정합성 보장 규칙:
-- 1. 유효한 판매 조합: 셀렉터스가 그룹에 등록한(product_group_item) 상품만 구매 가능[cite: 2, 7]
-- 2. 가격 정책: product 테이블의 regular_price를 원가로 사용하며 최대 20% 할인[cite: 7, 8]
-- 3. 구매 시점: 연결된 캠페인의 시작일(start_date)부터 현재(2026-08-31) 사이에서 발생[cite: 3, 5]
-- 4. 현실적 편향: 특정 셀렉터스에게 판매량이 집중되도록 가중치(skew) 적용

USE `hi_selectors`;
SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- =========================================================
-- 1. 매핑을 위한 임시 테이블 생성
-- =========================================================

DROP TEMPORARY TABLE IF EXISTS tmp_valid_sales_combos;
DROP TEMPORARY TABLE IF EXISTS tmp_valid_buyers;

-- 구매자(User) 매핑용 (1부터 순차적인 Index 생성)
CREATE TEMPORARY TABLE tmp_valid_buyers AS
SELECT user_id, ROW_NUMBER() OVER (ORDER BY user_id) as u_idx
FROM `users`;

SET @total_buyers = (SELECT COUNT(*) FROM tmp_valid_buyers);

-- 셀렉터스-상품-캠페인 유효 조합 매핑용
CREATE TEMPORARY TABLE tmp_valid_sales_combos (
                                                  combo_id INT AUTO_INCREMENT PRIMARY KEY,
                                                  selectors_id BIGINT,
                                                  product_id BIGINT,
                                                  regular_price DECIMAL(19,2),
                                                  start_time DATETIME,
                                                  end_time DATETIME
);

INSERT INTO tmp_valid_sales_combos (selectors_id, product_id, regular_price, start_time, end_time)
SELECT
    pg.selectors_id,
    pgi.product_id,
    p.regular_price,
    c.start_date,
    -- 캠페인 종료일과 현재 시점 중 빠른 날짜를 최대 구매 가능 시점으로 설정
    LEAST(c.end_date, '2026-08-31 23:59:59')
FROM `product_group_item` pgi
         JOIN `product_group` pg ON pgi.group_id = pg.product_group_id
         JOIN `campaign` c ON pg.campaign_id = c.campaign_id
         JOIN `product` p ON pgi.product_id = p.product_id
WHERE c.start_date <= '2026-08-31 23:59:59'
  AND c.is_deleted = 0;

SET @total_combos = (SELECT COUNT(*) FROM tmp_valid_sales_combos);

-- =========================================================
-- 2. 20만 건 데이터 계산 및 삽입
-- =========================================================

START TRANSACTION;

INSERT INTO `purchase_history` (
    `created_at`,
    `updated_at`,
    `confirmed_at`,
    `discount_amount`,
    `order_no`,
    `paid_amount`,
    `product_id`,
    `purchased_at`,
    `quantity`,
    `regular_unit_price`,
    `sale_unit_price`,
    `selectors_id`,
    `status`,
    `user_id`
)
WITH `digits` AS (
    SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
    UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
),
     `sequence_numbers` AS (
         -- 1부터 200,000까지의 시퀀스 생성
         SELECT
             d0.n + d1.n*10 + d2.n*100 + d3.n*1000 + d4.n*10000 + d5.n*100000 + 1 AS seq
         FROM `digits` d0
                  CROSS JOIN `digits` d1
                  CROSS JOIN `digits` d2
                  CROSS JOIN `digits` d3
                  CROSS JOIN `digits` d4
                  CROSS JOIN (SELECT 0 AS n UNION ALL SELECT 1) d5
     ),
     `purchases` AS (
         SELECT
             seq,

             -- 셀렉터스 매출 집중도(Skew) 부여: 상위 20% 조합이 전체 판매의 다수를 점유하도록 설정
             FLOOR(@total_combos * (
                 CASE
                     WHEN MOD(seq, 100) < 5 THEN POWER(RAND(seq), 5)  -- 5%: 최상위 인플루언서 (급상승/초대량)
                     WHEN MOD(seq, 100) < 20 THEN POWER(RAND(seq), 3) -- 15%: 꾸준한 상위권
                     WHEN MOD(seq, 100) < 50 THEN POWER(RAND(seq), 2) -- 30%: 일반적인 셀렉터스
                     ELSE RAND(seq)                                   -- 50%: 롱테일 (소량 판매)
                     END
                 )) + 1 AS combo_id,

             -- 구매자(유저) 무작위 분배 (소수 유저가 여러번 사는 현상 포함)
             MOD(seq * 73 + 17, @total_buyers) + 1 AS buyer_idx,

             -- 수량 (1개~5개)
             CASE
                 WHEN MOD(seq, 10) < 7 THEN 1
                 WHEN MOD(seq, 10) < 9 THEN 2
                 ELSE MOD(seq, 3) + 3
                 END AS quantity,

             -- 할인율 (0%, 5%, 10%, 15%, 20%)[cite: 7]
             (MOD(seq, 5) * 0.05) AS discount_rate,

             -- 상태 분포 (대부분 구매확정, 일부 취소/반품/대기)
             CASE MOD(seq, 100)
                 WHEN 0 THEN 'CANCELED'
                 WHEN 1 THEN 'CANCEL_REQUESTED'
                 WHEN 2 THEN 'RETURNED'
                 WHEN 3 THEN 'RETURN_REQUESTED'
                 WHEN 4 THEN 'PURCHASED'
                 WHEN 5 THEN 'PURCHASED'
                 ELSE 'PURCHASE_CONFIRMED'
                 END AS status_code,

             -- 캠페인 기간 내 랜덤 구매시점 생성을 위한 시드
             RAND(seq * 3) AS time_rand
         FROM `sequence_numbers`
     ),
     `mapped_purchases` AS (
         SELECT
             p.seq,
             c.selectors_id,
             c.product_id,
             b.user_id,
             c.regular_price AS regular_unit_price,
             FLOOR(c.regular_price * (1 - p.discount_rate)) AS sale_unit_price,
             p.quantity,
             p.status_code,
             DATE_ADD(c.start_time, INTERVAL FLOOR(p.time_rand * GREATEST(0, TIMESTAMPDIFF(SECOND, c.start_time, c.end_time))) SECOND) AS purchased_at
         FROM `purchases` p
                  JOIN tmp_valid_sales_combos c ON c.combo_id = p.combo_id
                  JOIN tmp_valid_buyers b ON b.u_idx = p.buyer_idx
     )
SELECT
    purchased_at AS created_at,
    CASE
        WHEN status_code IN ('PURCHASE_CONFIRMED', 'RETURNED', 'CANCELED')
            THEN LEAST(DATE_ADD(purchased_at, INTERVAL (MOD(seq, 5) + 3) DAY), '2026-08-31 23:59:59')
        ELSE purchased_at
        END AS updated_at,
    CASE
        WHEN status_code = 'PURCHASE_CONFIRMED'
            THEN LEAST(DATE_ADD(purchased_at, INTERVAL (MOD(seq, 5) + 3) DAY), '2026-08-31 23:59:59')
        ELSE NULL
        END AS confirmed_at,
    (regular_unit_price - sale_unit_price) * quantity AS discount_amount,
    CONCAT('ORD-', DATE_FORMAT(purchased_at, '%Y%m%d'), '-', LPAD(seq, 8, '0')) AS order_no,
    (sale_unit_price * quantity) AS paid_amount,
    product_id,
    purchased_at,
    quantity,
    regular_unit_price,
    sale_unit_price,
    selectors_id,
    status_code AS status,
    user_id
FROM `mapped_purchases`;

COMMIT;

-- =========================================================
-- 3. 삽입 결과 및 정합성 검증
-- =========================================================

-- 데이터 총합 및 기본 검증
SELECT
    COUNT(*) AS total_purchase_count,
    SUM(paid_amount) AS total_revenue,
    SUM(discount_amount) AS total_discount,
    SUM(status = 'PURCHASE_CONFIRMED') AS confirmed_count,
    SUM(status = 'CANCELED' OR status = 'RETURNED') AS cancel_return_count
FROM `purchase_history`;

-- 셀렉터스별 판매량 편향(Skew) 분포 확인 (실적 상위 5명)
SELECT
    selectors_id,
    COUNT(purchase_history_id) AS total_orders,
    SUM(paid_amount) AS total_sales_amount
FROM `purchase_history`
GROUP BY selectors_id
ORDER BY total_sales_amount DESC
LIMIT 5;

-- 임시 테이블 삭제
DROP TEMPORARY TABLE IF EXISTS tmp_valid_sales_combos;
DROP TEMPORARY TABLE IF EXISTS tmp_valid_buyers;