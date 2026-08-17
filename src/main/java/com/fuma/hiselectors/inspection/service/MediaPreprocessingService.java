package com.fuma.hiselectors.inspection.service;

import com.fuma.hiselectors.content.model.ContentMedia;
import com.fuma.hiselectors.content.model.MediaType;
import com.fuma.hiselectors.stt.SttResult;
import com.fuma.hiselectors.stt.YoutubeSttClient;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MediaPreprocessingService {

    private final YoutubeSttClient youtubeSttClient;

    public void preprocess(List<ContentMedia> media) {
        media.stream()
                .filter(item -> item.getMediaType() == MediaType.VIDEO)
                .filter(item -> !hasExtractedText(item))
                .forEach(this::preprocessYoutubeVideo);
    }

    private boolean hasExtractedText(ContentMedia media) {
        return media.bodyOrEmpty().containsKey("stt") || media.bodyOrEmpty().containsKey("ocr");
    }

    private void preprocessYoutubeVideo(ContentMedia media) {
        String videoId = youtubeVideoId(media.getMediaUrl());
        if (videoId == null) {
            return;
        }
        SttResult result = youtubeSttClient.transcribe(videoId);
        Map<String, Object> body = new LinkedHashMap<>(media.bodyOrEmpty());
        body.put("summary", result.summary());
        body.put("stt", result.stt().isBlank() ? List.of()
                : List.of(Map.of("text", result.stt())));
        body.put("ocr", result.ocr().isBlank() ? List.of()
                : List.of(Map.of("text", result.ocr())));
        media.replaceBody(body);
    }

    private String youtubeVideoId(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(url);
            if ("youtu.be".equalsIgnoreCase(uri.getHost())) {
                return uri.getPath() == null ? null : uri.getPath().replaceFirst("^/", "");
            }
            if (uri.getHost() != null && uri.getHost().toLowerCase().contains("youtube.com")) {
                return queryValue(uri, "v");
            }
            return null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String queryValue(URI uri, String name) {
        if (uri.getRawQuery() == null) {
            return null;
        }
        return Arrays.stream(uri.getRawQuery().split("&"))
                .map(parameter -> parameter.split("=", 2))
                .filter(pair -> URLDecoder.decode(pair[0], StandardCharsets.UTF_8).equals(name))
                .map(pair -> pair.length == 2
                        ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "")
                .findFirst().orElse(null);
    }
}
