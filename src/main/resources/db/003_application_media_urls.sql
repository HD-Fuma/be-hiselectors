-- 게시물 원문 링크와 이미지·영상 CDN 링크를 분리한다.
ALTER TABLE application_media
    ADD COLUMN content_url TEXT NULL
        COMMENT 'YouTube watch URL 또는 Instagram permalink'
        AFTER sns_content_id,
    MODIFY COLUMN media_url TEXT NULL
        COMMENT 'Instagram 이미지·영상 CDN URL. YouTube는 NULL';

-- 기존 원문 링크는 content_url로 옮긴다. CDN 링크는 재수집 시 content_url이 채워진다.
UPDATE application_media
SET content_url = media_url,
    media_url = NULL
WHERE sns_code = 'YOUTUBE'
   OR media_url LIKE 'https://www.instagram.com/%';
