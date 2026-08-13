package com.fuma.hiselectors.content.client;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.client.dto.RawContent;
import com.fuma.hiselectors.content.client.dto.RawContentMedia;
import com.fuma.hiselectors.content.client.dto.RawContentMedia.MediaType;
import com.fuma.hiselectors.content.client.dto.YoutubeChannelResponse;
import com.fuma.hiselectors.content.client.dto.YoutubeContentResponse;
import com.fuma.hiselectors.content.client.dto.YoutubeContentResponse.Item;
import com.fuma.hiselectors.content.config.YoutubeCollectionProperties;
import com.fuma.hiselectors.content.model.ContentType;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * YouTube Data API 채널 및 영상 조회
 *
 * 조회 순서: 채널 ID → 업로드 영상 목록 ID → 실제 영상 목록
 */
@Slf4j
@Component
public class YoutubeContentClient implements ContentPlatformClient {

    private static final String CHANNELS_URI =
            "https://www.googleapis.com/youtube/v3/channels";
    private static final String PLAYLIST_ITEMS_URI =
            "https://www.googleapis.com/youtube/v3/playlistItems";

    private static final String VIDEO_URL = "https://www.youtube.com/watch?v=";

    // 비정상적인 무한 호출 방지
    private static final int PAGE_SIZE = 50;
    private static final int MAX_PAGES = 10;

    // YouTube 영상 공개 시각을 변환할 서비스 기준 시간대
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final YoutubeCollectionProperties properties;
    private final RestClient restClient;

    public YoutubeContentClient(
            YoutubeCollectionProperties properties,
            @Qualifier("contentRestClient") RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    @Override
    public SnsPlatform supports() {
        return SnsPlatform.YOUTUBE;
    }

    /**
     * channelId의 YouTube 영상 중 collectedAfter 이후 영상 조회
     *
     * accountId: {@code UC...} 형식의 채널 ID
     * 반환값: 셀렉터스 콘텐츠 판별용 임시 데이터
     */
    @Override
    public List<RawContent> collect(String accountId, LocalDateTime collectedAfter) {
        validateRequest(accountId, collectedAfter);

        long startedAtNanos = System.nanoTime();
        int fetchedCount = 0;

        // 1. 채널 ID에 연결된 업로드 영상 목록 ID 조회
        String uploadsPlaylistId = requestUploadsPlaylistId(accountId);
        List<RawContent> contents = new ArrayList<>();
        String pageToken = null;

        for (int pageCount = 0; pageCount < MAX_PAGES; pageCount++) {
            // 2. 실제 영상 목록 페이지 조회
            YoutubeContentResponse page = requestPlaylistPage(
                    uploadsPlaylistId, pageToken);
            // 신규 여부와 관계없이 API가 반환한 영상 항목 수 합산 (로깅)
            fetchedCount += page.items() == null ? 0 : page.items().size();
            boolean reachedCollectedAt = addNewContents(
                    page.items(), collectedAfter, contents);
            if (reachedCollectedAt) {
                break;
            }

            // nextPageToken이 없으면 마지막 페이지
            pageToken = page.nextPageToken();
            if (!StringUtils.hasText(pageToken)) {
                break;
            }
            if (pageCount == MAX_PAGES - 1) {
                log.warn("YouTube 콘텐츠 수집 페이지 상한 도달. maxPages={}", MAX_PAGES);
                throw new BusinessException(ErrorCode.YOUTUBE_API_CALL_FAILED);
            }
        }
        // 플랫폼과 계정, 신규 기준 시각, 조회 건수, 신규 건수, 소요 시간 기록
        log.info(
                "콘텐츠 수집 완료. platform={} accountId={} collectedAfter={} "
                        + "fetchedCount={} newCount={} durationMs={}",
                supports(), accountId, collectedAfter, fetchedCount, contents.size(),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos));
        return List.copyOf(contents);
    }

    private void validateRequest(String channelId, LocalDateTime collectedAfter) {
        // application-local.yaml 값 정상인지 확인
        if (!properties.hasApiKey()) {
            throw new BusinessException(ErrorCode.YOUTUBE_API_KEY_MISSING);
        }

        // channelId, collectedAfter가 정상인지 확인
        if (!StringUtils.hasText(channelId) || collectedAfter == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private String requestUploadsPlaylistId(String channelId) {
        // channels API의 contentDetails로 업로드 영상 목록 ID 조회
        URI uri = UriComponentsBuilder.fromUriString(CHANNELS_URI)
                .queryParam("part", "contentDetails")
                .queryParam("id", channelId)
                .queryParam("key", properties.apiKey())
                .build()
                .encode()
                .toUri();

        // channels API의 JSON 구조를 YoutubeChannelResponse로 변환
        YoutubeChannelResponse response = request(
                uri, YoutubeChannelResponse.class);
        if (response.items() == null || response.items().isEmpty()
                || response.items().getFirst().contentDetails() == null
                || response.items().getFirst().contentDetails().relatedPlaylists() == null
                || !StringUtils.hasText(
                        response.items().getFirst().contentDetails().relatedPlaylists().uploads())) {
            throw new BusinessException(ErrorCode.YOUTUBE_CHANNEL_NOT_FOUND);
        }
        return response.items().getFirst().contentDetails().relatedPlaylists().uploads();
    }

    private YoutubeContentResponse requestPlaylistPage(
            String playlistId, String pageToken) {
        // snippet에서 제목과 설명을 받고, contentDetails에서 영상 ID와 공개 시각을 받음
        UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(PLAYLIST_ITEMS_URI)
                .queryParam("part", "snippet,contentDetails")
                .queryParam("playlistId", playlistId)
                .queryParam("maxResults", PAGE_SIZE)
                .queryParam("key", properties.apiKey());
        if (StringUtils.hasText(pageToken)) {
            uri.queryParam("pageToken", pageToken);
        }
        // playlistItems API의 JSON 구조를 YoutubeContentResponse로 변환
        return request(uri.build().encode().toUri(), YoutubeContentResponse.class);
    }

    private <T> T request(URI uri, Class<T> responseType) {
        try {
            // responseType을 기준으로 YouTube JSON 변환
            T response = restClient.get().uri(uri).retrieve().body(responseType);
            if (response == null) {
                throw new BusinessException(ErrorCode.YOUTUBE_API_CALL_FAILED);
            }
            return response;
        } catch (RestClientException | IllegalArgumentException e) {
            log.warn("YouTube Data API 호출 실패. cause={}",
                    e.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.YOUTUBE_API_CALL_FAILED);
        }
    }

    private boolean addNewContents(List<Item> items, LocalDateTime collectedAfter,
                                   List<RawContent> contents) {
        if (items == null) {
            return false;
        }

        boolean hasPublishedVideo = false;
        boolean hasNewContent = false;
        for (Item item : items) {
            // 삭제되거나 비공개여서 필수 정보가 없는 영상 항목 제외
            if (!hasVideo(item)) {
                continue;
            }
            hasPublishedVideo = true;
            // createdAt: YouTube 영상 공개 시각
            LocalDateTime createdAt = parseCreatedAt(item.contentDetails().videoPublishedAt());
            if (!createdAt.isAfter(collectedAfter)) {
                continue;
            }
            contents.add(toRawContent(item, createdAt));
            hasNewContent = true;
        }
        // 현재 페이지의 공개 영상이 모두 수집 기준 시각 이전이면 조회 종료
        return hasPublishedVideo && !hasNewContent;
    }

    private boolean hasVideo(Item item) {
        return item != null && item.contentDetails() != null
                && StringUtils.hasText(item.contentDetails().videoId())
                && StringUtils.hasText(item.contentDetails().videoPublishedAt());
    }

    private RawContent toRawContent(Item item, LocalDateTime createdAt) {
        String videoId = item.contentDetails().videoId();
        String videoUrl = VIDEO_URL + videoId;

        // YouTube 응답을 RawContent로 변환
        return new RawContent(
                SnsPlatform.YOUTUBE,
                videoId,
                videoUrl,
                ContentType.LONG_FORM,
                caption(item),
                createdAt,
                // YouTube는 영상 파일 직접 주소를 제공하지 않아 mediaUrl은 null
                List.of(new RawContentMedia(videoId, MediaType.VIDEO, null)));
    }

    private String caption(Item item) {
        if (item.snippet() == null) {
            return "";
        }
        // 셀렉터스 콘텐츠 판별 범위 확보를 위해 제목과 설명 결합
        String title = item.snippet().title();
        String description = item.snippet().description();
        if (!StringUtils.hasText(title)) {
            return description == null ? "" : description;
        }
        return StringUtils.hasText(description) ? title + "\n" + description : title;
    }

    private LocalDateTime parseCreatedAt(String publishedAt) {
        try {
            // ISO 형식의 영상 공개 시각을 한국 시간으로 변환
            return OffsetDateTime.parse(publishedAt)
                    .atZoneSameInstant(KOREA_ZONE)
                    .toLocalDateTime();
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCode.YOUTUBE_API_CALL_FAILED);
        }
    }
}
