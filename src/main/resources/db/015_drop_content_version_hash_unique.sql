-- 동일한 원본 해시가 과거 버전에 존재해도 새 변경 이력을 저장할 수 있어야 한다.
SET @drop_content_version_hash_unique = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'content_version'
          AND index_name = 'uk_content_version_content_hash'
    ),
    'ALTER TABLE content_version DROP INDEX uk_content_version_content_hash',
    'SELECT 1'
);

PREPARE drop_content_version_hash_unique
    FROM @drop_content_version_hash_unique;
EXECUTE drop_content_version_hash_unique;
DEALLOCATE PREPARE drop_content_version_hash_unique;
