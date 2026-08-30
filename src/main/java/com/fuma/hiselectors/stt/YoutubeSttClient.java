package com.fuma.hiselectors.stt;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.inspection.service.InspectionPromptProvider;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
public class YoutubeSttClient {

    private static final int MAX_OUTPUT_TOKENS = 1024;
    private static final int SHORTS_MAX_SECONDS = 180;
    private static final int MAX_ANALYZE_SECONDS = 300;
    private static final int LONG_FORM_WINDOW_SECONDS = 60;
    private static final int LONG_FORM_WINDOW_COUNT = 5;

    private static final String ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";

    private static final String SUMMARY_MARKER = "===요약===";
    private static final String STT_MARKER = "===음성===";
    private static final String OCR_MARKER = "===자막===";
    private static final String ANALYSIS_MARKER = "===분석===";

    private final GeminiProperties properties;
    private final GeminiRequestExecutor requestExecutor;
    private final InspectionPromptProvider promptProvider;
    // 작은 분석 JSON 파싱용. 상태 없는 파서라 빈 주입 없이 직접 만든다.
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestClient restClient;

    public YoutubeSttClient(GeminiProperties properties, GeminiRequestExecutor requestExecutor,
                            InspectionPromptProvider promptProvider) {
        this.properties = properties;
        this.requestExecutor = requestExecutor;
        this.promptProvider = promptProvider;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        // 영상 분석은 수십 초~분까지 걸릴 수 있어 응답 제한을 넉넉히 둔다.
        factory.setReadTimeout(Duration.ofMinutes(5));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    /** @return 음성·자막을 구분한 결과. 둘 다 없으면 빈 값. 저장하지 않는다. */
    public SttResult transcribe(String videoId) {
        return transcribeMeasured(videoId).result();
    }

    /** 테스트·관측용. 전사 결과와 전체 구간의 시간·토큰·재시도 횟수 합계를 반환한다. */
    public YoutubeSttExecutionResult transcribeMeasured(String videoId) {
        return transcribeMeasured(videoId, null);
    }

    /** 롱폼은 전체 타임라인에서 총 5분을 균등 분산해 앞부분 편향을 줄인다. */
    public SttResult transcribe(String videoId, Long durationSeconds) {
        return transcribeMeasured(videoId, durationSeconds).result();
    }

    /** 롱폼 분산 구간의 성공한 호출 결과와 측정값을 합산한다. */
    public YoutubeSttExecutionResult transcribeMeasured(String videoId, Long durationSeconds) {
        if (!properties.hasApiKey()) {
            throw new BusinessException(ErrorCode.GEMINI_API_KEY_MISSING);
        }

        String url = "https://www.youtube.com/watch?v=" + videoId;
        List<VideoWindow> windows = windows(durationSeconds);
        boolean longForm = durationSeconds != null && durationSeconds > SHORTS_MAX_SECONDS;
        String prompt = longForm
                ? promptProvider.youtubeLongFormSttPrompt()
                : promptProvider.youtubeSttPrompt();
        List<WindowResult> results = new ArrayList<>();
        RuntimeException lastFailure = null;
        long started = System.nanoTime();
        for (VideoWindow window : windows) {
            try {
                results.add(new WindowResult(window,
                        transcribeWindow(videoId, url, window, prompt)));
            } catch (RuntimeException e) {
                lastFailure = e;
                log.warn("YouTube 롱폼 구간 분석 skip. videoId={}, window={}s-{}s, reason={}",
                        videoId, window.startSeconds(), window.endSeconds(), e.getMessage());
            }
        }
        if (results.isEmpty()) {
            throw lastFailure == null
                    ? new BusinessException(ErrorCode.GEMINI_API_CALL_FAILED) : lastFailure;
        }
        long elapsedMs = Duration.ofNanos(System.nanoTime() - started).toMillis();
        return aggregate(results, elapsedMs);
    }

    private YoutubeSttExecutionResult transcribeWindow(
            String videoId, String url, VideoWindow window, String prompt) {
        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", List.of(
                        Map.of("fileData", Map.of("fileUri", url),
                                "videoMetadata", Map.of(
                                        "startOffset", window.startSeconds() + "s",
                                        "endOffset", window.endSeconds() + "s")),
                        Map.of("text", prompt)))),
                "generationConfig", Map.of(
                        "mediaResolution", properties.mediaResolutionApiValue(),
                        "thinkingConfig", Map.of("thinkingLevel", "minimal"),
                        "maxOutputTokens", MAX_OUTPUT_TOKENS));

        long started = System.nanoTime();
        GeminiRequestExecutor.Execution<GeminiResponse> execution = callMeasured(body);
        long elapsedMs = Duration.ofNanos(System.nanoTime() - started).toMillis();
        GeminiResponse response = execution.value();
        UsageMetadata usage = response == null ? null : response.usageMetadata();
        String model = response == null ? null : response.modelVersion();
        if (usage == null) {
            log.info("YouTube Gemini 영상 분석 완료. videoId={}, window={}s-{}s, model={}, latencyMs={}, tokenUsage=unavailable",
                    videoId, window.startSeconds(), window.endSeconds(), model, elapsedMs);
        } else {
            log.info("YouTube Gemini 영상 분석 완료. videoId={}, window={}s-{}s, model={}, latencyMs={}, promptTokens={}, outputTokens={}, thoughtTokens={}, totalTokens={}",
                    videoId, window.startSeconds(), window.endSeconds(), model, elapsedMs,
                    usage.promptTokenCount(),
                    usage.candidatesTokenCount(), usage.thoughtsTokenCount(),
                    usage.totalTokenCount());
        }
        SttResult result = parse(rawText(response));
        return new YoutubeSttExecutionResult(
                result,
                properties.modelOrDefault(),
                execution.selectedModel(),
                model,
                properties.mediaResolutionApiValue(),
                elapsedMs,
                execution.attemptCount(),
                execution.retryCount(),
                usage == null ? null : usage.promptTokenCount(),
                usage == null ? null : usage.candidatesTokenCount(),
                usage == null ? null : usage.thoughtsTokenCount(),
                usage == null ? null : usage.totalTokenCount());
    }

    static List<VideoWindow> windows(Long durationSeconds) {
        if (durationSeconds == null || durationSeconds <= 0) {
            return List.of(new VideoWindow(0, MAX_ANALYZE_SECONDS));
        }
        if (durationSeconds <= MAX_ANALYZE_SECONDS) {
            return List.of(new VideoWindow(0, durationSeconds));
        }
        long maxStart = durationSeconds - LONG_FORM_WINDOW_SECONDS;
        List<VideoWindow> result = new ArrayList<>(LONG_FORM_WINDOW_COUNT);
        for (int i = 0; i < LONG_FORM_WINDOW_COUNT; i++) {
            long start = maxStart * i / (LONG_FORM_WINDOW_COUNT - 1);
            result.add(new VideoWindow(start, start + LONG_FORM_WINDOW_SECONDS));
        }
        return List.copyOf(result);
    }

    private SttResult merge(List<WindowResult> results) {
        return new SttResult(
                joinWindows(results, result -> result.execution().result().summary()),
                joinWindows(results, result -> result.execution().result().stt()),
                joinWindows(results, result -> result.execution().result().ocr()),
                ContentInsight.empty());
    }

    private String joinWindows(List<WindowResult> results,
                               java.util.function.Function<WindowResult, String> getter) {
        return String.join("\n", results.stream()
                .filter(result -> getter.apply(result) != null && !getter.apply(result).isBlank())
                .map(result -> "[" + timestamp(result.window().startSeconds()) + "-"
                        + timestamp(result.window().endSeconds()) + "] " + getter.apply(result).trim())
                .toList());
    }

    private String timestamp(long seconds) {
        return "%02d:%02d:%02d".formatted(seconds / 3600, (seconds % 3600) / 60, seconds % 60);
    }

    private YoutubeSttExecutionResult aggregate(List<WindowResult> results, long elapsedMs) {
        YoutubeSttExecutionResult last = results.getLast().execution();
        SttResult result = results.size() == 1 ? last.result() : merge(results);
        return new YoutubeSttExecutionResult(
                result,
                properties.modelOrDefault(),
                last.selectedModel(),
                last.responseModel(),
                properties.mediaResolutionApiValue(),
                elapsedMs,
                results.stream().mapToInt(value -> value.execution().attemptCount()).sum(),
                results.stream().mapToInt(value -> value.execution().retryCount()).sum(),
                sumTokens(results, YoutubeSttExecutionResult::promptTokens),
                sumTokens(results, YoutubeSttExecutionResult::outputTokens),
                sumTokens(results, YoutubeSttExecutionResult::thoughtTokens),
                sumTokens(results, YoutubeSttExecutionResult::totalTokens));
    }

    private Integer sumTokens(
            List<WindowResult> results,
            java.util.function.Function<YoutubeSttExecutionResult, Integer> getter) {
        List<Integer> values = results.stream()
                .map(WindowResult::execution)
                .map(getter)
                .filter(java.util.Objects::nonNull)
                .toList();
        return values.isEmpty() ? null : values.stream().mapToInt(Integer::intValue).sum();
    }

    private GeminiRequestExecutor.Execution<GeminiResponse> callMeasured(Map<String, Object> body) {
        try {
            return requestExecutor.executeMeasured(properties.modelOrDefault(), attempt ->
                    restClient.post()
                            .uri(ENDPOINT.formatted(attempt.model()))
                            .header("x-goog-api-key", attempt.apiKey())
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(body)
                            .retrieve()
                            .body(GeminiResponse.class));
        } catch (RestClientException e) {
            log.warn("Gemini STT 후보를 모두 소진했습니다.", e);
            throw new BusinessException(ErrorCode.GEMINI_API_CALL_FAILED);
        }
    }

    private String rawText(GeminiResponse r) {
        if (r == null || r.candidates() == null || r.candidates().isEmpty()) {
            // 후보 없음 = 안전 차단 또는 실패. 빈 성공으로 넘기지 않고 실패 처리.
            log.warn("Gemini 응답에 후보 없음. blockReason={}", blockReason(r));
            throw new BusinessException(ErrorCode.GEMINI_API_CALL_FAILED);
        }
        GeminiResponse.Candidate candidate = r.candidates().get(0);
        String finish = candidate.finishReason();
        if (finish != null && !"STOP".equals(finish)) {
            // MAX_TOKENS(출력 잘림), SAFETY, RECITATION 등 → 불완전/차단이므로 실패.
            // 압축 추출이 잘렸다면 불완전 결과이므로 실패 처리한다.
            log.warn("Gemini 정상 종료 아님. finishReason={}", finish);
            throw new BusinessException(ErrorCode.GEMINI_API_CALL_FAILED);
        }
        GeminiResponse.Content content = candidate.content();
        if (content == null || content.parts() == null || content.parts().isEmpty()) {
            log.warn("Gemini 응답에 콘텐츠 없음. finishReason={}", finish);
            throw new BusinessException(ErrorCode.GEMINI_API_CALL_FAILED);
        }
        StringBuilder sb = new StringBuilder();
        for (GeminiResponse.Part part : content.parts()) {
            if (part.text() != null) {
                sb.append(part.text());
            }
        }
        return sb.toString();
    }

    private String blockReason(GeminiResponse r) {
        return r != null && r.promptFeedback() != null ? r.promptFeedback().blockReason() : null;
    }

    private static final String[] MARKERS =
            { SUMMARY_MARKER, STT_MARKER, OCR_MARKER, ANALYSIS_MARKER };

    private SttResult parse(String text) {
        boolean noMarkers = text.indexOf(SUMMARY_MARKER) < 0 && text.indexOf(STT_MARKER) < 0
                && text.indexOf(OCR_MARKER) < 0 && text.indexOf(ANALYSIS_MARKER) < 0;
        if (noMarkers) {
            // 형식 이탈 = 마커 없는 응답. 통째로 음성 자리에 넣는다(요약으로 오인 방지).
            return new SttResult("", text.trim(), "", ContentInsight.empty());
        }
        return new SttResult(
                section(text, SUMMARY_MARKER),
                section(text, STT_MARKER),
                section(text, OCR_MARKER),
                parseInsight(section(text, ANALYSIS_MARKER)));
    }

    /** ===분석=== 섹션의 JSON을 파싱. 실패해도 STT 결과는 살리도록 빈 값으로 폴백한다. */
    private ContentInsight parseInsight(String raw) {
        int open = raw.indexOf('{');
        int close = raw.lastIndexOf('}');
        if (open < 0 || close <= open) {
            return ContentInsight.empty();
        }
        try {
            return objectMapper.readValue(raw.substring(open, close + 1), ContentInsight.class);
        } catch (Exception e) {
            log.warn("Gemini 분석 JSON 파싱 실패. 빈 분석으로 대체한다.", e);
            return ContentInsight.empty();
        }
    }

    /** marker 뒤부터 다음 마커(어느 것이든) 직전까지. 마커 순서가 바뀌어도 안전. */
    private String section(String text, String marker) {
        int start = text.indexOf(marker);
        if (start < 0) {
            return "";
        }
        start += marker.length();
        int end = text.length();
        for (String other : MARKERS) {
            if (other.equals(marker)) {
                continue;
            }
            int i = text.indexOf(other);
            if (i >= start && i < end) {
                end = i;
            }
        }
        return text.substring(start, end).trim();
    }

    record GeminiResponse(List<Candidate> candidates, PromptFeedback promptFeedback,
                          UsageMetadata usageMetadata, String modelVersion) {
        record Candidate(Content content, String finishReason) { }

        record Content(List<Part> parts) { }

        record Part(String text) { }

        record PromptFeedback(String blockReason) { }
    }

    record UsageMetadata(Integer promptTokenCount, Integer candidatesTokenCount,
                         Integer thoughtsTokenCount, Integer totalTokenCount) { }

    record VideoWindow(long startSeconds, long endSeconds) { }

    private record WindowResult(VideoWindow window, YoutubeSttExecutionResult execution) { }
}
