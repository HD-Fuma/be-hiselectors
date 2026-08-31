package com.fuma.hiselectors.selectors.controller;

import com.fuma.hiselectors.selectors.dto.SelectorSnsEnrichmentResponse;
import com.fuma.hiselectors.selectors.service.SelectorSnsEnrichmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 지원서 없이 셀렉터스 SNS 프로필 이미지·카테고리만 채우는 관리자 API.
 *
 * <p>{@code /api/admin/**} 은 SecurityConfig 에서 ROLE_ADMIN 으로 제한되어 있다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/selectors")
@Tag(name = "관리자 셀렉터스", description = "셀렉터스 목록·상세 조회 (관리자 전용)")
public class SelectorSnsEnrichmentAdminController {

    private final SelectorSnsEnrichmentService enrichmentService;

    @Operation(summary = "셀렉터스 SNS 프로필·카테고리 보강",
            description = "지원서가 없어도 셀렉터스 대표 SNS 계정으로 공개 프로필 이미지와 "
                    + "최근 콘텐츠 텍스트 기반 대표 카테고리를 채운다. "
                    + "force=false(기본)이면 비어 있는 값만 채운다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "보강 완료 또는 이미 채워짐"),
            @ApiResponse(responseCode = "404", description = "셀렉터스 또는 SNS 계정 없음",
                    content = @Content),
            @ApiResponse(responseCode = "502", description = "로컬 분석 워커 장애", content = @Content)
    })
    @PostMapping("/{selectorsId:\\d+}/sns-enrichment")
    public ResponseEntity<SelectorSnsEnrichmentResponse> enrichOne(
            @Parameter(description = "셀렉터스 ID", example = "1")
            @PathVariable Long selectorsId,
            @Parameter(description = "이미 값이 있어도 다시 채운다")
            @RequestParam(defaultValue = "false") boolean force) {
        return ResponseEntity.ok(enrichmentService.enrich(selectorsId, force));
    }

    @Operation(summary = "빈 프로필·카테고리 일괄 보강",
            description = "프로필 이미지 또는 카테고리가 비어 있는 셀렉터스를 앞에서부터 집어 "
                    + "공개 SNS 정보로 채운다. force=true 이면 이미 값이 있는 대상도 다시 분류한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "일괄 보강 결과")
    })
    @PostMapping("/sns-enrichment")
    public ResponseEntity<SelectorSnsEnrichmentResponse.Batch> enrichMissing(
            @Parameter(description = "이미 값이 있어도 다시 채운다")
            @RequestParam(defaultValue = "false") boolean force,
            @Parameter(description = "한 번에 처리할 최대 인원. 1~50")
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(enrichmentService.enrichMissing(force, limit));
    }
}
