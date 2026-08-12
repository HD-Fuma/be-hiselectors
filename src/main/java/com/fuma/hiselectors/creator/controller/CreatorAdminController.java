package com.fuma.hiselectors.creator.controller;

import com.fuma.hiselectors.creator.dto.CategoryRefreshResponse;
import com.fuma.hiselectors.creator.dto.CategoryShare;
import com.fuma.hiselectors.creator.dto.CreatorSummary;
import com.fuma.hiselectors.creator.service.CreatorDiscoveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 발굴된 크리에이터 조회 API.
 *
 * <p>{@code /api/admin/**} 은 SecurityConfig 에서 ROLE_ADMIN 으로 제한되어 있다.
 */
@Tag(name = "발굴 크리에이터 관리", description = "발굴된 크리에이터 조회 및 대표 카테고리 산출 (관리자 전용)")
@RestController
@RequestMapping("/api/admin/creators")
@RequiredArgsConstructor
public class CreatorAdminController {

    private final CreatorDiscoveryService creatorDiscoveryService;

    @Operation(summary = "발굴 크리에이터 목록 조회",
            description = "발굴 결과는 걸러내지 않고 모두 저장하므로, 브랜드 계정이나 구독자 미달 "
                    + "계정을 빼는 일은 이 API 의 조건으로 한다. 기준이 맞지 않으면 파라미터만 "
                    + "바꿔 다시 조회하면 되고 재수집은 필요 없다. 비워 둔 조건은 적용되지 않는다.")
    @GetMapping
    public ResponseEntity<Page<CreatorSummary>> search(

            @Parameter(description = "카테고리 코드", example = "BEAUTY")
            @RequestParam(required = false) String categoryCode,

            @Parameter(description = "SNS 코드", example = "YOUTUBE")
            @RequestParam(required = false) String snsCode,

            @Parameter(description = "최소 팔로워/구독자 수", example = "5000")
            @RequestParam(required = false) Long minFollower,

            @Parameter(description = "브랜드 신호 점수 상한. 1 을 주면 브랜드 계정(2점 이상)이 빠진다",
                    example = "1")
            @RequestParam(required = false) Integer maxBrandScore,

            @Parameter(description = "인스타 핸들 최소 신뢰도. 0.95 를 주면 URL 로 찾은 것만 남는다",
                    example = "0.75")
            @RequestParam(required = false) BigDecimal minIgConfidence,

            @Parameter(description = "최근 N일 안에 활동한 계정만", example = "180")
            @RequestParam(required = false) Integer activeWithinDays,

            @PageableDefault(size = 20, sort = "followerCount", direction = Sort.Direction.DESC)
            Pageable pageable) {

        return ResponseEntity.ok(creatorDiscoveryService.search(
                categoryCode, snsCode, minFollower, maxBrandScore,
                minIgConfidence, activeWithinDays, pageable));
    }

    @Operation(summary = "카테고리별 발굴 비중 조회",
            description = "이 계정이 어떤 카테고리에서 얼마나 걸렸는지 보여준다. "
                    + "대표 카테고리가 왜 그렇게 정해졌는지 확인하는 용도.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "크리에이터 없음", content = @Content)
    })
    @GetMapping("/{creatorPoolId}/category-shares")
    public ResponseEntity<List<CategoryShare>> findCategoryShares(
            @PathVariable Long creatorPoolId) {
        return ResponseEntity.ok(creatorDiscoveryService.findCategoryShares(creatorPoolId));
    }

    @Operation(summary = "대표 카테고리 재산출",
            description = "발굴 출처를 다시 집계해 조회수 비중이 가장 큰 카테고리로 갱신한다. "
                    + "산출 규칙을 바꾼 뒤 재수집 없이 반영할 때 쓴다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "재산출 성공. 발굴 출처가 없으면 changed=false"),
            @ApiResponse(responseCode = "404", description = "크리에이터 없음", content = @Content)
    })
    @PostMapping("/{creatorPoolId}/category/refresh")
    public ResponseEntity<CategoryRefreshResponse> refreshCategory(
            @PathVariable Long creatorPoolId) {
        return ResponseEntity.ok(
                creatorDiscoveryService.refreshRepresentativeCategory(creatorPoolId));
    }
}
