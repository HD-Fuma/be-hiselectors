package com.fuma.hiselectors.creator.discovery;

import com.fuma.hiselectors.creator.discovery.dto.YoutubeChannelListResponse;
import com.fuma.hiselectors.creator.discovery.dto.YoutubePlaylistItemListResponse;
import com.fuma.hiselectors.creator.discovery.dto.YoutubeSearchResponse;
import com.fuma.hiselectors.creator.discovery.dto.YoutubeVideoListResponse;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 실제 YouTube Data API v3 호출.
 *
 * <p><b>쿼터가 이 기능의 가장 큰 제약이다.</b> 일일 한도 10,000 units 인데
 * {@code search.list} 가 1회 100 units 를 쓴다. 나머지는 1 unit(id 50개 배치)이라
 * 키워드 검색·통계 조회에 약 102 units, 최근 90일 활동 수 조회에 채널별 최소
 * 1 unit가 추가된다. 검색을 아끼고 배치 조회를 늘리는 것이 최적화 방향이다.
 */
@Slf4j
@Component
public class YoutubeApiClient implements YoutubeDiscoveryClient {

    private static final String SEARCH_URI = "https://www.googleapis.com/youtube/v3/search";
    private static final String VIDEOS_URI = "https://www.googleapis.com/youtube/v3/videos";
    private static final String CHANNELS_URI = "https://www.googleapis.com/youtube/v3/channels";
    private static final String PLAYLIST_ITEMS_URI =
            "https://www.googleapis.com/youtube/v3/playlistItems";
    private static final int ACTIVITY_WINDOW_DAYS = 90;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    /** id 를 한 번에 넘길 수 있는 최대 개수. 이 단위로 묶어야 1 unit 으로 끝난다. */
    private static final int BATCH_SIZE = 50;

    private static final int SEARCH_COST = 100;
    private static final int LIST_COST = 1;

    private final YoutubeDiscoveryProperties properties;
    private final RestClient restClient;

    private int consumedQuota;

    public YoutubeApiClient(YoutubeDiscoveryProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.create();
    }

    @Override
    public int consumedQuota() {
        return consumedQuota;
    }

    @Override
    public List<DiscoveredChannel> discoverByKeyword(String keyword, int maxResults) {
        // 싱글턴 컴포넌트이므로 이전 실행의 사용량이 다음 응답에 누적되지 않게 초기화한다.
        consumedQuota = 0;

        if (!properties.hasApiKey()) {
            throw new BusinessException(ErrorCode.YOUTUBE_API_KEY_MISSING);
        }

        List<String> videoIds = searchVideoIds(keyword, maxResults);
        if (videoIds.isEmpty()) {
            log.info("발굴 검색 결과 없음. keyword={}", keyword);
            return List.of();
        }

        List<YoutubeVideoListResponse.Item> videos = fetchVideos(videoIds);
        Map<String, ChannelAccumulator> byChannel = groupByChannel(videos);
        if (byChannel.isEmpty()) {
            return List.of();
        }

        return fetchChannels(byChannel);
    }

    /**
     * 채널이 아니라 영상을 검색한다. 채널명에 키워드가 없어도 그 주제로 콘텐츠를
     * 만드는 채널을 찾기 위해서다.
     */
    private List<String> searchVideoIds(String keyword, int maxResults) {
        String uri = UriComponentsBuilder.fromUriString(SEARCH_URI)
                .queryParam("part", "id")
                .queryParam("q", keyword)
                .queryParam("type", "video")
                .queryParam("order", "viewCount")
                .queryParam("regionCode", "KR")
                .queryParam("relevanceLanguage", "ko")
                .queryParam("maxResults", Math.min(maxResults, BATCH_SIZE))
                .queryParam("key", properties.apiKey())
                .build().toUriString();

        YoutubeSearchResponse response = call(uri, YoutubeSearchResponse.class, SEARCH_COST);
        if (response == null || response.items() == null) {
            return List.of();
        }

        return response.items().stream()
                .map(YoutubeSearchResponse.Item::id)
                .filter(id -> id != null && id.videoId() != null)
                .map(YoutubeSearchResponse.Id::videoId)
                .toList();
    }

    /** search.list 는 통계를 주지 않는다. 조회수와 채널 ID 는 여기서 얻는다. */
    private List<YoutubeVideoListResponse.Item> fetchVideos(List<String> videoIds) {
        List<YoutubeVideoListResponse.Item> items = new ArrayList<>();

        for (List<String> batch : partition(videoIds)) {
            String uri = UriComponentsBuilder.fromUriString(VIDEOS_URI)
                    .queryParam("part", "snippet,statistics")
                    .queryParam("id", String.join(",", batch))
                    .queryParam("key", properties.apiKey())
                    .build().toUriString();

            YoutubeVideoListResponse response =
                    call(uri, YoutubeVideoListResponse.class, LIST_COST);
            if (response != null && response.items() != null) {
                items.addAll(response.items());
            }
        }
        return items;
    }

    /** 채널 설명이 여기서 나온다. 인스타 핸들의 유일한 소스다. */
    private List<DiscoveredChannel> fetchChannels(Map<String, ChannelAccumulator> byChannel) {
        List<DiscoveredChannel> channels = new ArrayList<>();

        for (List<String> batch : partition(List.copyOf(byChannel.keySet()))) {
            String uri = UriComponentsBuilder.fromUriString(CHANNELS_URI)
                    .queryParam("part", "snippet,statistics,contentDetails")
                    .queryParam("id", String.join(",", batch))
                    .queryParam("key", properties.apiKey())
                    .build().toUriString();

            YoutubeChannelListResponse response =
                    call(uri, YoutubeChannelListResponse.class, LIST_COST);
            if (response == null || response.items() == null) {
                continue;
            }

            for (YoutubeChannelListResponse.Item item : response.items()) {
                ChannelAccumulator acc = byChannel.get(item.id());
                if (acc != null) {
                    channels.add(toDiscoveredChannel(item, acc));
                }
            }
        }
        return channels;
    }

    private DiscoveredChannel toDiscoveredChannel(YoutubeChannelListResponse.Item item,
                                                  ChannelAccumulator acc) {
        YoutubeChannelListResponse.Snippet snippet = item.snippet();
        YoutubeChannelListResponse.Statistics stats = item.statistics();

        return new DiscoveredChannel(
                item.id(),
                snippet == null ? null : snippet.title(),
                snippet == null ? null : snippet.description(),
                stats == null ? null : parseLongOrNull(stats.subscriberCount()),
                stats == null ? null : parseLongOrNull(stats.viewCount()),
                acc.lastUploadAt,
                fetchRecent90DayContentCount(item),
                acc.views, acc.likes, acc.comments);
    }

    private Integer fetchRecent90DayContentCount(YoutubeChannelListResponse.Item channel) {
        String uploads = channel.contentDetails() == null
                || channel.contentDetails().relatedPlaylists() == null
                ? null
                : channel.contentDetails().relatedPlaylists().uploads();
        if (uploads == null || uploads.isBlank()) {
            return null;
        }

        LocalDateTime cutoff = LocalDateTime.now(SEOUL).minusDays(ACTIVITY_WINDOW_DAYS);
        int count = 0;
        int fetchedPages = 0;
        String pageToken = null;
        do {
            UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(PLAYLIST_ITEMS_URI)
                    .queryParam("part", "contentDetails")
                    .queryParam("playlistId", uploads)
                    .queryParam("maxResults", BATCH_SIZE)
                    .queryParam("key", properties.apiKey());
            if (pageToken != null) {
                uri.queryParam("pageToken", pageToken);
            }

            YoutubePlaylistItemListResponse response = call(
                    uri.build().toUriString(), YoutubePlaylistItemListResponse.class, LIST_COST);
            fetchedPages++;
            if (response == null || response.items() == null) {
                return count;
            }

            boolean reachedOlderContent = false;
            for (YoutubePlaylistItemListResponse.Item item : response.items()) {
                LocalDateTime publishedAt = parsePublishedAt(item);
                if (publishedAt == null) {
                    continue;
                }
                if (publishedAt.isBefore(cutoff)) {
                    reachedOlderContent = true;
                } else {
                    count++;
                }
            }
            if (reachedOlderContent) {
                return count;
            }
            pageToken = response.nextPageToken();
        } while (pageToken != null && !pageToken.isBlank()
                && fetchedPages < YoutubeDiscoveryProperties.MAX_ACTIVITY_PAGES_PER_CHANNEL);
        return count;
    }

    private LocalDateTime parsePublishedAt(YoutubePlaylistItemListResponse.Item item) {
        if (item == null || item.contentDetails() == null
                || item.contentDetails().videoPublishedAt() == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(item.contentDetails().videoPublishedAt())
                    .atZoneSameInstant(SEOUL)
                    .toLocalDateTime();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** 영상들을 채널별로 묶으면서 조회수·좋아요·댓글을 합산한다. */
    private Map<String, ChannelAccumulator> groupByChannel(
            List<YoutubeVideoListResponse.Item> videos) {

        Map<String, ChannelAccumulator> byChannel = new LinkedHashMap<>();

        for (YoutubeVideoListResponse.Item video : videos) {
            YoutubeVideoListResponse.Snippet snippet = video.snippet();
            if (snippet == null || snippet.channelId() == null) {
                continue;
            }

            ChannelAccumulator acc = byChannel.computeIfAbsent(
                    snippet.channelId(), k -> new ChannelAccumulator());
            acc.add(video);
        }
        return byChannel;
    }

    private <T> T call(String uri, Class<T> responseType, int cost) {
        try {
            T response = restClient.get().uri(uri).retrieve().body(responseType);
            consumedQuota += cost;
            return response;
        } catch (RestClientException e) {
            // 쿼터 초과, 잘못된 키, 네트워크 오류가 모두 여기로 온다.
            // 한 키워드가 실패해도 배치 전체를 멈추지 않도록 예외로 감싸 올린다.
            log.warn("YouTube API 호출 실패. uri={}", maskKey(uri), e);
            throw new BusinessException(ErrorCode.YOUTUBE_API_CALL_FAILED);
        }
    }

    /** 로그에 API 키가 남지 않게 가린다. */
    private String maskKey(String uri) {
        return uri.replaceAll("key=[^&]*", "key=***");
    }

    private List<List<String>> partition(List<String> ids) {
        List<List<String>> batches = new ArrayList<>();
        for (int i = 0; i < ids.size(); i += BATCH_SIZE) {
            batches.add(ids.subList(i, Math.min(i + BATCH_SIZE, ids.size())));
        }
        return batches;
    }

    private static Long parseLongOrNull(String value) {
        try {
            return value == null ? null : Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 채널별 집계용 가변 버퍼. */
    private static final class ChannelAccumulator {

        private long views;
        private long likes;
        private long comments;
        private LocalDateTime lastUploadAt;

        private void add(YoutubeVideoListResponse.Item video) {
            YoutubeVideoListResponse.Statistics stats = video.statistics();
            if (stats != null) {
                views += zeroIfNull(parseLongOrNull(stats.viewCount()));
                likes += zeroIfNull(parseLongOrNull(stats.likeCount()));
                comments += zeroIfNull(parseLongOrNull(stats.commentCount()));
            }

            LocalDateTime publishedAt = parsePublishedAt(video.snippet());
            if (publishedAt != null && (lastUploadAt == null || publishedAt.isAfter(lastUploadAt))) {
                lastUploadAt = publishedAt;
            }
        }

        private static long zeroIfNull(Long value) {
            return value == null ? 0 : value;
        }

        /** publishedAt 은 {@code 2026-07-28T09:00:00Z} 형태로 온다. */
        private static LocalDateTime parsePublishedAt(YoutubeVideoListResponse.Snippet snippet) {
            if (snippet == null || snippet.publishedAt() == null) {
                return null;
            }
            try {
                return OffsetDateTime.parse(snippet.publishedAt())
                        .atZoneSameInstant(ZoneId.of("Asia/Seoul"))
                        .toLocalDateTime();
            } catch (DateTimeParseException e) {
                return null;
            }
        }
    }
}
