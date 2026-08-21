package com.fuma.hiselectors.stt;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 지원자 평가 요청. 이미 분석된 콘텐츠 결과들을 넘긴다(각 콘텐츠는 POST /api/stt/instagram 으로
 * 미리 분석). 비싼 STT/OCR 을 여기서 재실행하지 않으므로 평가 실패 시 이 요청만 재시도하면 된다.
 */
public record ApplicantEvaluateRequest(

        @Schema(description = "콘텐츠별 분석 결과 목록(/api/stt/instagram 응답을 그대로 모아서 전달)")
        List<InstagramAnalysisResult> contents) {
}
