package com.fuma.hiselectors.content.client;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.client.dto.RawContent;
import java.time.LocalDateTime;
import java.util.List;

/** SNS 콘텐츠 조회 공통 규칙 (Instagram, YouTube 등) */
public interface ContentFetcher {

    /** 클라이언트가 지원하는 SNS 플랫폼 */
    SnsPlatform supports();

    /**
     * 수집 기준 시각 이후의 계정 콘텐츠 조회
     *
     * @param accountId Instagram username 또는 YouTube 채널 ID
     * @param since 포함되는 조회 시작 시각
     * @return 신규·기존 여부를 판단하지 않은 수집 기준 시각 이후 콘텐츠
     */
    List<RawContent> fetchByAccount(String accountId, LocalDateTime since);

    /** SNS 콘텐츠 ID별 최신 내용과 성과 조회 */
    List<FetchResult> fetchByContentIds(List<String> snsContentIds);

    record FetchResult(
            String snsContentId,
            FetchStatus status,
            RawContent content,
            Engagement engagement) {
    }

    enum FetchStatus {
        FOUND,
        NOT_FOUND,
        FAILED
    }

    record Engagement(
            Long viewCount,
            Long likeCount,
            Long commentCount,
            Long shareCount) {
    }
}
