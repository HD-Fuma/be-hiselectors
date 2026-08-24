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
        String cautions,         // 유의점(브랜드 협업 시 조언)
        String risks,            // 위험요소(정치/광고/건강/선정성/저작권/사행성 + 욕설확정)
        String brandHistory,     // 언급된 협업/협찬 브랜드
        String representativeContentUrl,   // 대표 콘텐츠(조회수 최고) 링크
        String representativeContentType,  // REELS/VIDEO 등
        Long representativeViewCount,      // 대표 선정 근거(조회수)
        String representativeCategory,     // 대표 콘텐츠 카테고리
        List<String> representativeKeywords,
        String status,
        LocalDateTime createdAt
) {
}
