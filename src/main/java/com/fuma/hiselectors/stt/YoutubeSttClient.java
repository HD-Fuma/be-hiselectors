package com.fuma.hiselectors.stt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class YoutubeSttClient {

    private static final String ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";

    private static final String SUMMARY_MARKER = "===요약===";
    private static final String STT_MARKER = "===음성===";
    private static final String OCR_MARKER = "===자막===";
    private static final String ANALYSIS_MARKER = "===분석===";

    // 영상 입력 비용은 어차피 이 호출에서 낸다. 맥락 판단이 필요한 정성 필드(요약·스타일·톤·강점·
    // 위험·브랜드·욕설확정)를 같은 호출에 얹어 output 토큰만 추가한다. 키워드·카테고리·1차 욕설
    // 스크리닝은 결정적/DB정렬이라 로컬 엔진(stt-worker)이 transcript 위에서 따로 처리한다.
    private static final String PROMPT = """
            이 유튜브 영상을 분석해 아래 형식 그대로만 출력하세요. 설명은 붙이지 마세요.
            각 항목은 서로 독립적으로, 겹쳐도 그대로 각각 완전히 채우세요.
            ===음성===
            오디오에서 사람이 말한 내용을 처음부터 끝까지 한국어로 빠짐없이 전사하세요. \
            요약과 별개입니다. 축약·생략·요약하지 말고 들리는 말을 그대로 전부 적으세요. \
            말이 전혀 없으면 비워 두세요.
            ===자막===
            화면에 보이는 텍스트를 전부 적으세요(자막, 제목, 상표·라벨, 그래픽 문구 등). \
            음성과 겹치더라도 그대로 적으세요. 없으면 비워 두세요.
            ===요약===
            영상 내용을 한국어 3~5문장으로 요약하세요. 없으면 비워 두세요.
            ===분석===
            아래 JSON 하나만 출력하세요. 키·형식을 정확히 지키고 코드펜스는 쓰지 마세요.
            {
              "contentStyle": "리뷰언박싱|튜토리얼|브이로그|챌린지|소통Q&A|하울|비교추천|정보설명|인터뷰|숏폼밈|라이브 중 하나",
              "tone": "유쾌코믹|차분잔잔|진지전문|친근수다|감성무드|자극과장|시니컬솔직 중 하나",
              "strengths": ["크리에이터·콘텐츠의 강점"],
              "cautions": ["브랜드 협업 시 유의점"],
              "risks": ["정치종교논란|허위과장광고|미검증건강주장|선정성|저작권|사행성 중 해당되는 것만, 없으면 빈 배열"],
              "hateConfirmed": false,
              "collabBrands": ["영상에서 협찬·협업으로 보이는 브랜드명, 없으면 빈 배열"]
            }""";

    private final GeminiProperties properties;
    // 작은 분석 JSON 파싱용. 상태 없는 파서라 빈 주입 없이 직접 만든다.
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestClient restClient;

    public YoutubeSttClient(GeminiProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        // 영상 분석은 수십 초~분까지 걸릴 수 있어 응답 제한을 넉넉히 둔다.
        factory.setReadTimeout(Duration.ofMinutes(5));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    /** @return 음성·자막을 구분한 결과. 둘 다 없으면 빈 값. 저장하지 않는다. */
    public SttResult transcribe(String videoId) {
        if (!properties.hasApiKey()) {
            throw new BusinessException(ErrorCode.GEMINI_API_KEY_MISSING);
        }

        String url = "https://www.youtube.com/watch?v=" + videoId;
        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", List.of(
                        Map.of("fileData", Map.of("fileUri", url)),
                        Map.of("text", PROMPT)))),
                "generationConfig", Map.of(
                        "mediaResolution", properties.mediaResolutionApiValue(),
                        "maxOutputTokens", properties.maxOutputTokensOrDefault()));

        return parse(rawText(call(body)));
    }

    private GeminiResponse call(Map<String, Object> body) {
        String uri = ENDPOINT.formatted(properties.modelOrDefault());
        try {
            return restClient.post()
                    .uri(uri)
                    .header("x-goog-api-key", properties.apiKey())  // 키를 URL 대신 헤더로(로그 유출 방지)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(GeminiResponse.class);
        } catch (RestClientException e) {
            log.warn("Gemini STT 호출 실패. model={}", properties.modelOrDefault(), e);
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
            // 잘린 전사를 성공으로 반환하지 않는다. 잘리면 gemini.max-output-tokens 를 올린다.
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

    record GeminiResponse(List<Candidate> candidates, PromptFeedback promptFeedback) {
        record Candidate(Content content, String finishReason) { }

        record Content(List<Part> parts) { }

        record Part(String text) { }

        record PromptFeedback(String blockReason) { }
    }
}
