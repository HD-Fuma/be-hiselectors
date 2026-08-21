ALTER TABLE application
    ADD COLUMN media_collection_status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        COMMENT 'PENDING / DONE / FAILED',
    ADD COLUMN media_collection_retry_count INT NOT NULL DEFAULT 0,
    ADD COLUMN media_collected_at DATETIME(6) NULL,
    ADD COLUMN media_collection_error VARCHAR(500) NULL,
    ADD KEY idx_application_media_collection
        (media_collection_status, media_collection_retry_count, application_id);
