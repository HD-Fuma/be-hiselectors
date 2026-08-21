package com.fuma.hiselectors.application.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AdminAiReportResponse(
        Long applicationId,
        String summary,          // 사람이 읽는 요약(json 컬럼 디코드)
        String category,         // 대표 카테고리 코드(BEAUTY 등)
        List<String> keywords,
        String contentStyle,
        String tone,
        String strength,
        String warning,          // 유의점 + 위험 + 욕설확정
        String brandHistory,     // 언급된 협업/협찬 브랜드
        String status,
        LocalDateTime createdAt
) {
}
