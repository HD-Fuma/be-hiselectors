package com.fuma.hiselectors.content.service;

import com.fuma.hiselectors.application.model.SnsPlatform;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/** DB에 저장된 콘텐츠 링크·SNS ID에서 플랫폼 API 조회 키를 꺼낸다. */
final class SnsContentIdResolver {

    private static final Pattern YOUTUBE_VIDEO_ID = Pattern.compile("^[A-Za-z0-9_-]{11}$");
    private static final Pattern INSTAGRAM_MEDIA_ID = Pattern.compile("^\\d+$");
    private static final List<String> YOUTUBE_PATH_PREFIXES =
            List.of("/shorts/", "/embed/", "/live/", "/v/");

    private SnsContentIdResolver() {
    }

    static String resolve(SnsPlatform platform, String snsContentId, String contentUrl) {
        if (platform == SnsPlatform.YOUTUBE) {
            return firstNonBlank(
                    youtubeVideoId(snsContentId),
                    youtubeVideoId(contentUrl));
        }
        if (platform == SnsPlatform.INSTAGRAM) {
            return firstNonBlank(
                    instagramMediaId(snsContentId),
                    instagramMediaId(contentUrl));
        }
        return firstNonBlank(snsContentId, contentUrl);
    }

    private static String youtubeVideoId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.strip();
        if (YOUTUBE_VIDEO_ID.matcher(trimmed).matches()) {
            return trimmed;
        }
        try {
            URI uri = URI.create(trimmed);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
            if ("youtu.be".equals(host)) {
                return videoIdFromPath(uri.getPath());
            }
            if (!host.contains("youtube.com")) {
                return null;
            }
            String fromQuery = queryValue(uri, "v");
            if (fromQuery != null) {
                return youtubeVideoId(fromQuery);
            }
            String path = uri.getPath() == null ? "" : uri.getPath();
            for (String prefix : YOUTUBE_PATH_PREFIXES) {
                if (path.toLowerCase().startsWith(prefix)) {
                    return videoIdFromPath(path.substring(prefix.length() - 1));
                }
            }
            return null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static String instagramMediaId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.strip();
        return INSTAGRAM_MEDIA_ID.matcher(trimmed).matches() ? trimmed : null;
    }

    private static String videoIdFromPath(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String[] segments = path.split("/");
        for (String segment : segments) {
            if (segment == null || segment.isBlank()) {
                continue;
            }
            int queryAt = segment.indexOf('?');
            String candidate = queryAt < 0 ? segment : segment.substring(0, queryAt);
            if (YOUTUBE_VIDEO_ID.matcher(candidate).matches()) {
                return candidate;
            }
        }
        return null;
    }

    private static String queryValue(URI uri, String name) {
        if (uri.getRawQuery() == null) {
            return null;
        }
        return Arrays.stream(uri.getRawQuery().split("&"))
                .map(parameter -> parameter.split("=", 2))
                .filter(pair -> URLDecoder.decode(pair[0], StandardCharsets.UTF_8).equals(name))
                .map(pair -> pair.length == 2
                        ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "")
                .findFirst()
                .orElse(null);
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }
}
