ALTER TABLE content_version
    ADD COLUMN inspection_decision VARCHAR(20) NULL AFTER status;
