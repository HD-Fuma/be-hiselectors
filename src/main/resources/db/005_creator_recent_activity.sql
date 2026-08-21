-- 발굴 크리에이터의 공개 정량 지표만 목록에서 필터링하기 위한 최근 활동 수.
ALTER TABLE creator_discovery_info
    ADD COLUMN recent_90_day_content_count INT NULL
        COMMENT '공개 API에서 확인한 최근 90일 콘텐츠 수';
