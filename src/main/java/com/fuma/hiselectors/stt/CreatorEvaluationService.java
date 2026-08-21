package com.fuma.hiselectors.stt;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 지원자 종합 평가(A안). 콘텐츠별 STT/OCR 분석(비쌈)은 이미 끝난 결과를 받아,
 * 합본 transcript로 Gemini 1회만 돌린다. 분석과 평가를 분리했으므로 평가가 실패해도
 * 성공한 분석을 재실행하지 않는다. 서버는 상태를 들고 있지 않는다(무저장).
 */
@Service
@RequiredArgsConstructor
public class CreatorEvaluationService {

    private final GeminiEvalClient evalClient;

    public ApplicantEvaluation evaluate(List<InstagramAnalysisResult> contents) {
        if (contents == null || contents.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "평가할 콘텐츠가 없습니다.");
        }
        String merged = contents.stream()
                .map(c -> (safe(c.stt()) + " " + safe(c.ocr())).strip())
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining("\n\n"));

        if (merged.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "콘텐츠에 전사·자막 내용이 없습니다.");
        }
        return evalClient.evaluate(merged);
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
