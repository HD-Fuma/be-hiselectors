package com.fuma.hiselectors.creator.controller;

import com.fuma.hiselectors.creator.discovery.DiscoveryPipelineService;
import com.fuma.hiselectors.creator.discovery.dto.DiscoveryRunResult;
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
 * 발굴 실행 API.
 *
 * <p>{@code /api/admin/**} 은 SecurityConfig 에서 ROLE_ADMIN 으로 제한되어 있다.
 */
@Tag(name = "크리에이터 발굴 실행", description = "등록된 키워드로 YouTube 를 검색해 크리에이터를 발굴 (관리자 전용)")
@RestController
@RequestMapping("/api/admin/discovery")
@RequiredArgsConstructor
public class DiscoveryAdminController {

    private final DiscoveryPipelineService discoveryPipelineService;

    @Operation(summary = "키워드로 발굴 실행",
            description = "등록된 키워드 하나로 YouTube 를 검색해 채널을 발굴하고 저장한다. "
                    + "약 102 units 를 소모하며 일일 한도는 10,000 units 다. "
                    + "브랜드 계정이나 구독자 미달 계정도 걸러내지 않고 모두 저장하며, "
                    + "제외는 조회 API 조건으로 한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "발굴 성공"),
            @ApiResponse(responseCode = "404", description = "키워드 없음", content = @Content),
            @ApiResponse(responseCode = "502", description = "YouTube API 호출 실패 (쿼터 초과 등)",
                    content = @Content)
    })
    @PostMapping("/keywords/{keywordId}/run")
    public ResponseEntity<DiscoveryRunResult> runByKeyword(
            @PathVariable Long keywordId,

            @Parameter(description = "검색할 영상 수. 많을수록 채널을 더 찾지만 쿼터는 동일하다",
                    example = "25")
            @RequestParam(required = false, defaultValue = "25") Integer maxResults) {

        return ResponseEntity.ok(
                discoveryPipelineService.runByKeyword(keywordId, maxResults));
    }
}
