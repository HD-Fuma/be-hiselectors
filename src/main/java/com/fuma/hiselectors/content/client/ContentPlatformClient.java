package com.fuma.hiselectors.content.client;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.client.dto.RawContent;
import java.time.LocalDateTime;
import java.util.List;

/** 콘텐츠 수집 클라이언트의 공통 규칙 (Instagram, Youtube 등) */
public interface ContentPlatformClient {

    /** 클라이언트가 지원하는 SNS 플랫폼 */
    SnsPlatform supports();

    /**
     * 마지막 수집 시각 이후의 콘텐츠 조회
     *
     * @param accountId Instagram username 또는 YouTube 채널 ID
     * @param collectedAfter 신규 콘텐츠 판정 기준 시각
     * @return DB 저장 전 플랫폼 공통 수집 결과
     */
    List<RawContent> collect(String accountId, LocalDateTime collectedAfter);
}
