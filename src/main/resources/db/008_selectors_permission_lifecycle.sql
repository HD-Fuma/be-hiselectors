-- 모집 기간과 별도로 셀렉터스 권한·실적 산정에 쓰는 활동 기간을 둔다.
ALTER TABLE generation
    ADD COLUMN activity_start_date DATETIME(6) NULL AFTER end_date,
    ADD COLUMN activity_end_date DATETIME(6) NULL AFTER activity_start_date;

-- 기존 기수는 종전 기간을 활동 기간으로 보존한다. 관리 화면에서 실제 활동 기간으로 보정한다.
UPDATE generation
SET activity_start_date = COALESCE(activity_start_date, start_date),
    activity_end_date = COALESCE(activity_end_date, end_date)
WHERE activity_start_date IS NULL
   OR activity_end_date IS NULL;

-- 구버전 API도 배포 전까지 기수를 생성할 수 있도록 nullable 상태로 확장한다.
-- NOT NULL 전환은 신버전 API 배포가 끝난 뒤 deploy-prod workflow가 수행한다.
ALTER TABLE generation
    ADD INDEX idx_generation_activity_period (activity_start_date, activity_end_date);

-- 승인·자동 연장 재시도에도 한 셀렉터스가 같은 기수에 한 번만 속한다.
ALTER TABLE selectors_generation
    ADD CONSTRAINT uq_selectors_generation UNIQUE (selectors_id, generation_id);

-- user 당 셀렉터스 중심 행은 하나다. 동시 기수 승인도 중복 생성할 수 없다.
ALTER TABLE selectors
    ADD CONSTRAINT uq_selectors_user UNIQUE (user_id);

-- 과거 패널티는 유지하되 새 기수 누적 횟수에는 포함하지 않는다.
ALTER TABLE penalty_history
    ADD COLUMN generation_id BIGINT NULL AFTER selectors_id,
    ADD INDEX idx_penalty_selectors_generation (selectors_id, generation_id),
    ADD CONSTRAINT fk_penalty_generation
        FOREIGN KEY (generation_id) REFERENCES generation (generation_id);
