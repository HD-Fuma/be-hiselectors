-- notification 발송 주체 추가
-- JPA ddl-auto=validate 이므로 애플리케이션 배포 전에 적용한다.

ALTER TABLE notification
    ADD COLUMN initiated_by_type VARCHAR(20) NOT NULL DEFAULT 'SYSTEM',
    ADD COLUMN initiated_by_id BIGINT NULL;
