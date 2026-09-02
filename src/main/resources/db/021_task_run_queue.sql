-- Apply before any API, scheduler, or worker using the durable queue contract.
-- Additive and repeatable: legacy jobs remain local; no business rows are rewritten.
-- Existing columns/indexes are never replaced. The deployment verifies their shape.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout = 15;

SET @task_run_queue_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'task_run'
              AND column_name = 'business_payload'),
    'SELECT 1',
    'ALTER TABLE task_run ADD COLUMN business_payload TEXT NULL'
);
PREPARE task_run_queue_ddl FROM @task_run_queue_ddl;
EXECUTE task_run_queue_ddl;
DEALLOCATE PREPARE task_run_queue_ddl;

SET @task_run_queue_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'task_run'
              AND column_name = 'queue_managed'),
    'SELECT 1',
    'ALTER TABLE task_run ADD COLUMN queue_managed BOOLEAN NOT NULL DEFAULT FALSE'
);
PREPARE task_run_queue_ddl FROM @task_run_queue_ddl;
EXECUTE task_run_queue_ddl;
DEALLOCATE PREPARE task_run_queue_ddl;

SET @task_run_queue_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'task_run'
              AND column_name = 'queue_attempts'),
    'SELECT 1',
    'ALTER TABLE task_run ADD COLUMN queue_attempts INT NOT NULL DEFAULT 0'
);
PREPARE task_run_queue_ddl FROM @task_run_queue_ddl;
EXECUTE task_run_queue_ddl;
DEALLOCATE PREPARE task_run_queue_ddl;

SET @task_run_queue_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'task_run'
              AND column_name = 'queue_available_at'),
    'SELECT 1',
    'ALTER TABLE task_run ADD COLUMN queue_available_at DATETIME(6) NULL'
);
PREPARE task_run_queue_ddl FROM @task_run_queue_ddl;
EXECUTE task_run_queue_ddl;
DEALLOCATE PREPARE task_run_queue_ddl;

SET @task_run_queue_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'task_run'
              AND column_name = 'queue_lease_until'),
    'SELECT 1',
    'ALTER TABLE task_run ADD COLUMN queue_lease_until DATETIME(6) NULL'
);
PREPARE task_run_queue_ddl FROM @task_run_queue_ddl;
EXECUTE task_run_queue_ddl;
DEALLOCATE PREPARE task_run_queue_ddl;

SET @task_run_queue_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'task_run'
              AND column_name = 'last_enqueued_at'),
    'SELECT 1',
    'ALTER TABLE task_run ADD COLUMN last_enqueued_at DATETIME(6) NULL'
);
PREPARE task_run_queue_ddl FROM @task_run_queue_ddl;
EXECUTE task_run_queue_ddl;
DEALLOCATE PREPARE task_run_queue_ddl;

SET @task_run_queue_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = 'task_run'
              AND index_name = 'idx_task_run_queue_pending'),
    'SELECT 1',
    'ALTER TABLE task_run ADD INDEX idx_task_run_queue_pending (queue_managed, status, queue_available_at, last_enqueued_at), ALGORITHM=INPLACE, LOCK=NONE'
);
PREPARE task_run_queue_ddl FROM @task_run_queue_ddl;
EXECUTE task_run_queue_ddl;
DEALLOCATE PREPARE task_run_queue_ddl;

SET @task_run_queue_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = 'task_run'
              AND index_name = 'idx_task_run_queue_heartbeat'),
    'SELECT 1',
    'ALTER TABLE task_run ADD INDEX idx_task_run_queue_heartbeat (queue_managed, heartbeat_at), ALGORITHM=INPLACE, LOCK=NONE'
);
PREPARE task_run_queue_ddl FROM @task_run_queue_ddl;
EXECUTE task_run_queue_ddl;
DEALLOCATE PREPARE task_run_queue_ddl;
