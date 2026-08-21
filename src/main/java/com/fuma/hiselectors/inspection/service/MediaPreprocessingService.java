package com.fuma.hiselectors.inspection.service;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.model.Content;
import com.fuma.hiselectors.content.model.ContentMedia;
import com.fuma.hiselectors.content.model.MediaType;
import com.fuma.hiselectors.inspection.ai.YoutubeIntegratedInspectionClient;
import com.fuma.hiselectors.inspection.model.AiInspectionResult;
import com.fuma.hiselectors.inspection.model.InspectionPolicy;
import com.fuma.hiselectors.inspection.model.IntegratedInspectionResult;
import com.fuma.hiselectors.inspection.repository.InspectionPolicyRepository;
import com.fuma.hiselectors.stt.SttResult;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MediaPreprocessingService {

    private final YoutubeIntegratedInspectionClient youtubeClient;
    private final InspectionPolicyRepository policyRepository;
    private final Clock clock;

    /**
     * YouTube는 추출이 필요할 때 영상 통합 검수 결과까지 반환한다.
     * 이미 추출된 텍스트를 재사용하거나 Instagram인 경우 AI 결과는 비어 있다.
     */
    public PreprocessingResult preprocess(
            Content content, List<ContentMedia> media, InspectionPolicy activePolicy) {
        if (content.getSnsCode() != SnsPlatform.YOUTUBE) {
            return PreprocessingResult.reused();
        }
        Optional<ContentMedia> video = media.stream()
                .filter(item -> item.getMediaType() == MediaType.VIDEO)
                .filter(item -> youtubeVideoId(item.getMediaUrl()) != null)
                .findFirst();
        if (video.isEmpty()) {
            return PreprocessingResult.reused();
        }

        ContentMedia target = video.get();
        String videoId = youtubeVideoId(target.getMediaUrl());
        String inputHash = sha256(videoId);
        if (canReuse(target, inputHash, activePolicy)) {
            return PreprocessingResult.reused();
        }

        IntegratedInspectionResult result = youtubeClient.inspect(
                videoId, content, target, media, activePolicy);
        MediaExtractionUpdate extractionUpdate = applyExtraction(
                target, result.extraction(), activePolicy.getId(), inputHash);
        return new PreprocessingResult(
                Optional.of(result.inspection()), Optional.of(extractionUpdate));
    }

    private boolean canReuse(ContentMedia media, String inputHash, InspectionPolicy activePolicy) {
        if (!media.bodyOrEmpty().containsKey("stt")
                || !media.bodyOrEmpty().containsKey("ocr")
                || media.getExtractedWithPolicyId() == null
                || !inputHash.equals(media.getExtractionInputHash())) {
            return false;
        }
        return policyRepository.findById(media.getExtractedWithPolicyId())
                .map(previous -> previous.getExtractionConfigHash()
                        .equals(activePolicy.getExtractionConfigHash()))
                .orElse(false);
    }

    private MediaExtractionUpdate applyExtraction(ContentMedia media, SttResult result,
                                                  Long policyId, String inputHash) {
        Map<String, Object> body = new LinkedHashMap<>(media.bodyOrEmpty());
        body.put("summary", result.summary());
        body.put("stt", result.stt().isBlank() ? List.of()
                : List.of(Map.of("text", result.stt())));
        body.put("ocr", result.ocr().isBlank() ? List.of()
                : List.of(Map.of("text", result.ocr())));
        LocalDateTime extractedAt = LocalDateTime.now(clock);
        media.replaceBody(body);
        media.markExtracted(policyId, inputHash, extractedAt);
        return new MediaExtractionUpdate(
                media.getId(), Map.copyOf(body), policyId, inputHash, extractedAt);
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

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
        }
    }

    public record PreprocessingResult(
            Optional<AiInspectionResult> integratedAiResult,
            Optional<MediaExtractionUpdate> extractionUpdate) {

        public PreprocessingResult {
            integratedAiResult = integratedAiResult == null
                    ? Optional.empty() : integratedAiResult;
            extractionUpdate = extractionUpdate == null
                    ? Optional.empty() : extractionUpdate;
        }

        public static PreprocessingResult reused() {
            return new PreprocessingResult(Optional.empty(), Optional.empty());
        }
    }

    public record MediaExtractionUpdate(
            Long contentMediaId,
            Map<String, Object> body,
            Long inspectionPolicyId,
            String inputHash,
            LocalDateTime extractedAt) {
    }
}
