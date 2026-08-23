CREATE TABLE IF NOT EXISTS task_run (
    task_run_id BIGINT NOT NULL AUTO_INCREMENT,
    run_id VARCHAR(36) NOT NULL,
    task_type VARCHAR(40) NOT NULL,
    trigger_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    current_step VARCHAR(255) NULL,
    progress_message VARCHAR(500) NULL,
    total_count BIGINT NULL,
    processed_count BIGINT NOT NULL DEFAULT 0,
    succeeded_count BIGINT NOT NULL DEFAULT 0,
    failed_count BIGINT NOT NULL DEFAULT 0,
    skipped_count BIGINT NOT NULL DEFAULT 0,
    started_by_admin_id BIGINT NULL,
    concurrency_key VARCHAR(191) NULL,
    idempotency_key VARCHAR(36) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    lease_token VARCHAR(36) NULL,
    heartbeat_at DATETIME(6) NOT NULL,
    started_at DATETIME(6) NULL,
    finished_at DATETIME(6) NULL,
    error_type VARCHAR(100) NULL,
    error_message VARCHAR(1000) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (task_run_id),
    CONSTRAINT uq_task_run_run_id UNIQUE (run_id),
    CONSTRAINT uq_task_run_concurrency_key UNIQUE (concurrency_key),
    CONSTRAINT uq_task_run_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT fk_task_run_started_by_admin
        FOREIGN KEY (started_by_admin_id) REFERENCES admin (admin_id),
    CONSTRAINT chk_task_run_total_count
        CHECK (total_count IS NULL OR total_count >= 0),
    CONSTRAINT chk_task_run_processed_count
        CHECK (processed_count >= 0),
    CONSTRAINT chk_task_run_succeeded_count
        CHECK (succeeded_count >= 0),
    CONSTRAINT chk_task_run_failed_count
        CHECK (failed_count >= 0),
    CONSTRAINT chk_task_run_skipped_count
        CHECK (skipped_count >= 0),
    CONSTRAINT chk_task_run_processed_sum
        CHECK (processed_count = succeeded_count + failed_count + skipped_count),
    CONSTRAINT chk_task_run_processed_total
        CHECK (total_count IS NULL OR processed_count <= total_count),
    INDEX idx_task_run_status_started_at (status, started_at),
    INDEX idx_task_run_finished_at (finished_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
