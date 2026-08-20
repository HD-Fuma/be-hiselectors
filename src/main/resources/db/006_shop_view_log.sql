ALTER TABLE click_log
    ADD COLUMN viewer_user_id BIGINT NULL AFTER reference_id,
    ADD COLUMN visitor_id VARCHAR(64) NULL AFTER viewer_user_id;

UPDATE click_log
SET visitor_id = CONCAT('legacy-', click_log_id)
WHERE visitor_id IS NULL;

ALTER TABLE click_log
    MODIFY COLUMN visitor_id VARCHAR(64) NOT NULL,
    ADD INDEX idx_click_log_viewer (viewer_user_id),
    ADD INDEX idx_click_log_visitor (visitor_id),
    ADD CONSTRAINT fk_click_log_viewer
        FOREIGN KEY (viewer_user_id) REFERENCES users (user_id);
