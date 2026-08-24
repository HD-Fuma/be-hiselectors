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
import com.fuma.hiselectors.creator.discovery.dto.YoutubeVideoListResponse;
import com.fuma.hiselectors.creator.discovery.dto.YoutubeVideoListResponse.Statistics;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
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
 * 조회 순서: 핸들 → 업로드 영상 목록 ID → 실제 영상 목록
 */
@Slf4j
@Component
public class YoutubeContentFetcher implements ContentFetcher {

    private static final String CHANNELS_URI =
            "https://www.googleapis.com/youtube/v3/channels";
    private static final String PLAYLIST_ITEMS_URI =
            "https://www.googleapis.com/youtube/v3/playlistItems";
    private static final String VIDEOS_URI =
            "https://www.googleapis.com/youtube/v3/videos";

    private static final String VIDEO_URL = "https://www.youtube.com/watch?v=";

    private static final int PAGE_SIZE = 50;

    /** 이 길이 이하 영상은 SHORTS 로 본다. YouTube Shorts 최대 길이(3분) 기준. */
    private static final int SHORTS_MAX_SECONDS = 180;
    private static final Pattern CHANNEL_ID = Pattern.compile("^UC[A-Za-z0-9_-]{22}$");

    // YouTube 영상 공개 시각을 변환할 서비스 기준 시간대
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final YoutubeCollectionProperties properties;
    private final RestClient restClient;

    public YoutubeContentFetcher(
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
     * accountId에 해당하는 YouTube 영상 중 수집 기준 시각 이후 영상 조회
     *
     * accountId: {@code UC...} 채널 ID, {@code @handle}, 핸들 또는 채널 URL
     * 반환값: 셀렉터스 콘텐츠 판별용 임시 데이터
     */
    @Override
    public List<RawContent> fetchByAccount(String accountId, LocalDateTime since) {
        validateRequest(accountId, since);

        // 1. 채널 ID에 연결된 업로드 영상 목록 ID 조회
        String uploadsPlaylistId = requestUploadsPlaylistId(accountId);
        List<RawContent> contents = new ArrayList<>();
        Set<String> requestedPageTokens = new HashSet<>();
        String pageToken = null;

        while (true) {
            if (StringUtils.hasText(pageToken)
                    && !requestedPageTokens.add(pageToken)) {
                log.warn("YouTube 페이지 토큰 반복 감지");
                throw new BusinessException(ErrorCode.YOUTUBE_API_CALL_FAILED);
            }

            // 2. 실제 영상 목록 페이지 조회
            YoutubeContentResponse page = requestPlaylistPage(
                    uploadsPlaylistId, pageToken);
            boolean reachedBeforeGeneration = addGenerationContents(
                    page.items(), since, contents);
            if (reachedBeforeGeneration) {
                break;
            }

            // nextPageToken이 없으면 마지막 페이지
            pageToken = page.nextPageToken();
            if (!StringUtils.hasText(pageToken)) {
                break;
            }
        }
        return List.copyOf(contents);
    }

    @Override
    public List<FetchResult> fetchByContentIds(List<String> snsContentIds) {
        validateIds(snsContentIds);
        List<FetchResult> results = new ArrayList<>(snsContentIds.size());
        for (int start = 0; start < snsContentIds.size(); start += PAGE_SIZE) {
            results.addAll(fetchBatch(snsContentIds.subList(
                    start, Math.min(start + PAGE_SIZE, snsContentIds.size()))));
        }
        return results;
    }

    public Map<String, String> fetchChannelTitles(List<String> channelIds) {
        if (!properties.hasApiKey() || channelIds == null || channelIds.isEmpty()) {
            return Map.of();
        }
        List<String> ids = channelIds.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .filter(CHANNEL_ID.asMatchPredicate())
                .distinct()
                .toList();
        Map<String, String> titles = new HashMap<>();
        for (int start = 0; start < ids.size(); start += PAGE_SIZE) {
            List<String> batch = ids.subList(start, Math.min(start + PAGE_SIZE, ids.size()));
            URI uri = UriComponentsBuilder.fromUriString(CHANNELS_URI)
                    .queryParam("part", "snippet")
                    .queryParam("id", String.join(",", batch))
                    .queryParam("key", properties.apiKey())
                    .build()
                    .encode()
                    .toUri();
            try {
                YoutubeChannelResponse response = request(uri, YoutubeChannelResponse.class);
                if (response.items() != null) {
                    response.items().stream()
                            .filter(item -> item != null && StringUtils.hasText(item.id()))
                            .filter(item -> item.snippet() != null
                                    && StringUtils.hasText(item.snippet().title()))
                            .forEach(item -> titles.put(item.id(), item.snippet().title()));
                }
            } catch (BusinessException e) {
                log.warn("YouTube 채널명 조회 실패. 채널 수={}", batch.size());
            }
        }
        return Map.copyOf(titles);
    }

    private List<FetchResult> fetchBatch(List<String> ids) {
        URI uri = UriComponentsBuilder.fromUriString(VIDEOS_URI)
                .queryParam("part", "snippet,statistics,contentDetails")
                .queryParam("id", String.join(",", ids))
                .queryParam("key", properties.apiKey())
                .build()
                .encode()
                .toUri();
        YoutubeContentResponse response;
        try {
            response = request(uri, YoutubeContentResponse.class);
        } catch (BusinessException e) {
            return ids.stream()
                    .map(id -> new FetchResult(id, FetchStatus.FAILED, null, null))
                    .toList();
        }

        Map<String, Item> itemsById = new HashMap<>();
        if (response.items() != null) {
            response.items().stream()
                    .filter(item -> item != null && StringUtils.hasText(item.id()))
                    .forEach(item -> itemsById.put(item.id(), item));
        }
        return ids.stream().map(id -> toFetchResult(id, itemsById.get(id))).toList();
    }

    private FetchResult toFetchResult(String id, Item item) {
        if (item == null) {
            return new FetchResult(id, FetchStatus.NOT_FOUND, null, null);
        }
        try {
            YoutubeContentResponse.Snippet snippet = item.snippet();
            if (snippet == null || !StringUtils.hasText(snippet.publishedAt())) {
                return new FetchResult(id, FetchStatus.FAILED, null, null);
            }
            RawContent content = new RawContent(
                    SnsPlatform.YOUTUBE,
                    id,
                    VIDEO_URL + id,
                    classifyByDuration(item.contentDetails() == null
                            ? null : item.contentDetails().duration()),
                    texts(item),
                    parseCreatedAt(snippet.publishedAt()),
                    List.of(new RawContentMedia(
                            id, MediaType.VIDEO, null, thumbnailUrls(snippet))));
            YoutubeContentResponse.Statistics statistics = item.statistics();
            Engagement engagement = new Engagement(
                    count(statistics == null ? null : statistics.viewCount()),
                    count(statistics == null ? null : statistics.likeCount()),
                    count(statistics == null ? null : statistics.commentCount()),
                    null);
            return new FetchResult(id, FetchStatus.FOUND, content, engagement);
        } catch (BusinessException | NumberFormatException e) {
            log.warn("YouTube 영상 응답 변환 실패. ID={}", id);
            return new FetchResult(id, FetchStatus.FAILED, null, null);
        }
    }

    private Long count(String value) {
        return StringUtils.hasText(value) ? Long.valueOf(value) : null;
    }

    private void validateIds(List<String> snsContentIds) {
        if (!properties.hasApiKey()) {
            throw new BusinessException(ErrorCode.YOUTUBE_API_KEY_MISSING);
        }
        if (snsContentIds == null
                || snsContentIds.stream().anyMatch(id -> !StringUtils.hasText(id))) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private void validateRequest(String accountId, LocalDateTime since) {
        // application-local.yaml 값 정상인지 확인
        if (!properties.hasApiKey()) {
            throw new BusinessException(ErrorCode.YOUTUBE_API_KEY_MISSING);
        }

        // accountId, collectedAfter가 정상인지 확인
        if (!StringUtils.hasText(accountId) || since == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private String requestUploadsPlaylistId(String accountId) {
        return uploadsPlaylistId(requestChannel(resolveChannelLookup(accountId)));
    }

    private YoutubeChannelResponse.Item requestChannel(ChannelLookup lookup) {
        // snippet에서 공개 핸들, contentDetails에서 업로드 영상 목록 ID 조회
        URI uri = UriComponentsBuilder.fromUriString(CHANNELS_URI)
                .queryParam("part", "snippet,contentDetails")
                .queryParam(lookup.parameter(), lookup.value())
                .queryParam("key", properties.apiKey())
                .build()
                .encode()
                .toUri();

        // channels API의 JSON 구조를 YoutubeChannelResponse로 변환
        YoutubeChannelResponse response = request(
                uri, YoutubeChannelResponse.class);
        if (response.items() == null || response.items().isEmpty()) {
            throw new BusinessException(ErrorCode.YOUTUBE_CHANNEL_NOT_FOUND);
        }
        return response.items().getFirst();
    }

    private String uploadsPlaylistId(YoutubeChannelResponse.Item channel) {
        if (channel.contentDetails() == null
                || channel.contentDetails().relatedPlaylists() == null
                || !StringUtils.hasText(
                        channel.contentDetails().relatedPlaylists().uploads())) {
            throw new BusinessException(ErrorCode.YOUTUBE_CHANNEL_NOT_FOUND);
        }
        return channel.contentDetails().relatedPlaylists().uploads();
    }

    private ChannelLookup resolveChannelLookup(String accountId) {
        String value = accountId.trim();
        if (CHANNEL_ID.matcher(value).matches()) {
            return new ChannelLookup("id", value);
        }
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            return new ChannelLookup("forHandle", removeHandlePrefix(value));
        }

        try {
            URI channelUri = URI.create(value);
            String host = channelUri.getHost();
            if (host == null || !(host.equalsIgnoreCase("youtube.com")
                    || host.equalsIgnoreCase("www.youtube.com")
                    || host.equalsIgnoreCase("m.youtube.com"))) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }

            String[] segments = channelUri.getPath().split("/");
            if (segments.length >= 3 && "channel".equals(segments[1])) {
                if (!CHANNEL_ID.matcher(segments[2]).matches()) {
                    throw new BusinessException(ErrorCode.INVALID_INPUT);
                }
                return new ChannelLookup("id", segments[2]);
            }
            if (segments.length >= 3 && "user".equals(segments[1])) {
                return new ChannelLookup("forUsername", segments[2]);
            }
            if (segments.length >= 2 && segments[1].startsWith("@")) {
                return new ChannelLookup("forHandle", removeHandlePrefix(segments[1]));
            }
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        throw new BusinessException(ErrorCode.INVALID_INPUT);
    }

    private String removeHandlePrefix(String value) {
        String handle = value.startsWith("@") ? value.substring(1) : value;
        if (!StringUtils.hasText(handle)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return handle;
    }

    private record ChannelLookup(String parameter, String value) {
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
            log.warn("YouTube Data API 호출 실패. 원인={}",
                    e.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.YOUTUBE_API_CALL_FAILED);
        }
    }

    private boolean addGenerationContents(
            List<Item> items,
            LocalDateTime since,
            List<RawContent> contents) {
        if (items == null) {
            return false;
        }

        boolean hasPublishedVideo = false;
        boolean hasGenerationContent = false;
        for (Item item : items) {
            // 삭제되거나 비공개여서 필수 정보가 없는 영상 항목 제외
            if (!hasVideo(item)) {
                continue;
            }
            hasPublishedVideo = true;
            // createdAt: YouTube 영상 공개 시각
            LocalDateTime createdAt = parseCreatedAt(item.contentDetails().videoPublishedAt());
            if (createdAt.isBefore(since)) {
                continue;
            }
            contents.add(toRawContent(item, createdAt));
            hasGenerationContent = true;
        }
        // 현재 페이지의 공개 영상이 모두 기수 시작 전이면 조회 종료
        return hasPublishedVideo && !hasGenerationContent;
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
                texts(item),
                createdAt,
                // YouTube는 영상 파일 직접 주소를 제공하지 않아 mediaUrl은 null
                List.of(new RawContentMedia(
                        videoId, MediaType.VIDEO, null, thumbnailUrls(item.snippet()))));
    }

    @Override
    public List<RawContent> addStatistics(List<RawContent> contents) {
        if (contents.isEmpty()) {
            return contents;
        }

        Map<String, YoutubeVideoListResponse.Item> itemsById = new HashMap<>();
        for (int start = 0; start < contents.size(); start += PAGE_SIZE) {
            List<String> videoIds = contents.subList(start, Math.min(start + PAGE_SIZE, contents.size()))
                    .stream()
                    .map(RawContent::snsContentId)
                    .toList();
            URI uri = UriComponentsBuilder.fromUriString(VIDEOS_URI)
                    .queryParam("part", "statistics,contentDetails")
                    .queryParam("id", String.join(",", videoIds))
                    .queryParam("key", properties.apiKey())
                    .build()
                    .encode()
                    .toUri();
            YoutubeVideoListResponse response = request(uri, YoutubeVideoListResponse.class);
            if (response.items() != null) {
                response.items().stream()
                        .filter(item -> item != null && item.id() != null)
                        .forEach(item -> itemsById.put(item.id(), item));
            }
        }

        return contents.stream().map(content -> {
            YoutubeVideoListResponse.Item item = itemsById.get(content.snsContentId());
            if (item == null) {
                return content;
            }
            // playlistItems 응답엔 길이가 없어 LONG_FORM 으로 왔으므로 여기서 Shorts 여부를 반영한다.
            ContentType contentType = classifyByDuration(item.contentDetails() == null
                    ? null : item.contentDetails().duration());
            RawContent typed = content.contentType() == contentType
                    ? content : content.withContentType(contentType);
            Statistics statistics = item.statistics();
            return statistics == null ? typed : typed.withMetrics(
                    parseCount(statistics.viewCount()),
                    parseCount(statistics.likeCount()),
                    parseCount(statistics.commentCount()));
        }).toList();
    }

    // ponytail: duration 휴리스틱. YouTube 가 Shorts 여부 플래그를 안 줘서 영상 길이로 추정한다.
    // 정확히 하려면 youtube.com/shorts/{id} 리다이렉트 확인이 필요(요청 1회 추가).
    private ContentType classifyByDuration(String isoDuration) {
        if (!StringUtils.hasText(isoDuration)) {
            return ContentType.LONG_FORM;
        }
        try {
            return Duration.parse(isoDuration).getSeconds() <= SHORTS_MAX_SECONDS
                    ? ContentType.SHORTS : ContentType.LONG_FORM;
        } catch (DateTimeParseException e) {
            return ContentType.LONG_FORM;
        }
    }

    private Long parseCount(String count) {
        try {
            return count == null ? null : Long.parseLong(count);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private List<String> texts(Item item) {
        if (item.snippet() == null) {
            return List.of();
        }
        // 제목과 설명을 각각 TEXT로 유지
        String title = item.snippet().title();
        String description = item.snippet().description();
        List<String> texts = new ArrayList<>(2);
        if (StringUtils.hasText(title)) {
            texts.add(title);
        }
        if (StringUtils.hasText(description)) {
            texts.add(description);
        }
        return texts;
    }

    private List<String> thumbnailUrls(YoutubeContentResponse.Snippet snippet) {
        if (snippet == null || snippet.thumbnails() == null) {
            return List.of();
        }
        return snippet.thumbnails().values().stream()
                .filter(Objects::nonNull)
                .map(YoutubeContentResponse.Thumbnail::url)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
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
