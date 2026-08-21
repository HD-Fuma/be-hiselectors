package com.fuma.hiselectors.stt;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 지원자 콘텐츠 N개 → 캐시 축적 → 합본 transcript로 Gemini 1회 평가.
 * (A안: 워커가 준 transcript를 Java가 모아 한 번에 LLM.)
 */
@Service
@RequiredArgsConstructor
public class CreatorEvaluationService {

    private final InstagramSttClient instagramClient;
    private final TranscriptCache cache;
    private final GeminiEvalClient evalClient;

    /** 콘텐츠 1개 분석 후 지원자 캐시에 적재. 콘텐츠별 결과도 돌려준다. */
    public InstagramAnalysisResult addContent(Long applicantId, String reelUrl) {
        InstagramAnalysisResult result = instagramClient.analyze(reelUrl);
        cache.add(applicantId, result);
        return result;
    }

    /** 쌓인 콘텐츠를 합쳐 Gemini 1회 평가. 평가 후 캐시 파기(무저장). */
    public ApplicantEvaluation evaluate(Long applicantId) {
        List<InstagramAnalysisResult> contents = cache.get(applicantId);
        if (contents.isEmpty()) {
            throw new BusinessException(ErrorCode.NO_CACHED_CONTENT);
        }
        String merged = contents.stream()
                .map(c -> (safe(c.stt()) + " " + safe(c.ocr())).strip())
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining("\n\n"));

        ApplicantEvaluation evaluation = evalClient.evaluate(merged);
        cache.clear(applicantId);
        return evaluation;
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
