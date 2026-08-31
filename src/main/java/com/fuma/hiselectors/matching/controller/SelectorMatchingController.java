package com.fuma.hiselectors.matching.controller;

import com.fuma.hiselectors.matching.dto.SelectorMatchResponse;
import com.fuma.hiselectors.matching.service.SelectorMatchingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "셀렉터스 매칭", description = "신규 상품·캠페인에 적합한 셀렉터스 추천 (관리자 전용)")
@RestController
@RequestMapping("/api/admin/selector-matching")
@RequiredArgsConstructor
@Validated
public class SelectorMatchingController {

    private final SelectorMatchingService selectorMatchingService;

    @Operation(summary = "카테고리 기반 추천 셀렉터스 조회",
            description = "해당 카테고리에서 과거 확정 매출이 높았던 셀렉터스를 우선 추천하고,"
                    + " 실적이 없어도 대표 카테고리가 일치하는 셀렉터스를 함께 반환한다."
                    + " category · productId · campaignId 중 하나로 대상 카테고리를 지정한다"
                    + "(productId·campaignId 는 서버가 상품 카테고리를 도출). 기간 생략 시 전체 기간.")
    @GetMapping
    public ResponseEntity<List<SelectorMatchResponse>> recommend(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Long campaignId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return ResponseEntity.ok(selectorMatchingService.recommend(
                category, productId, campaignId, startDate, endDate, limit));
    }
}
