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
     * 수집 기준 시각 이후의 콘텐츠 조회
     *
     * @param accountId Instagram username 또는 YouTube 채널 ID
     * @param collectedAfter 최초 수집은 기수 시작 시각, 이후 수집은 최종 수집 시각
     * @return 신규·기존 여부를 판단하지 않은 수집 기준 시각 이후 콘텐츠
     */
    CollectionResult collect(String accountId, LocalDateTime collectedAfter);

    record CollectionResult(int fetchedCount, List<RawContent> contents) {

        public CollectionResult {
            contents = List.copyOf(contents);
        }
    }
}
