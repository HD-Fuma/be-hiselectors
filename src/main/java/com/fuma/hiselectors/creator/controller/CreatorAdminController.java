package com.fuma.hiselectors.creator.controller;

import com.fuma.hiselectors.creator.discovery.YoutubeDiscoveryProperties;
import com.fuma.hiselectors.creator.dto.CategoryRefreshResponse;
import com.fuma.hiselectors.creator.dto.CategoryShare;
import com.fuma.hiselectors.creator.dto.CreatorDetailResponse;
import com.fuma.hiselectors.creator.dto.CreatorSummary;
import com.fuma.hiselectors.creator.dto.DailyReportCandidatesResponse;
import com.fuma.hiselectors.creator.dto.TopPercentInfluenceResponse;
import com.fuma.hiselectors.creator.service.CreatorDiscoveryService;
import com.fuma.hiselectors.creator.service.CreatorInfluenceService;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
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
@Validated
public class CreatorAdminController {

    private final CreatorDiscoveryService creatorDiscoveryService;
    private final CreatorInfluenceService creatorInfluenceService;

    @Operation(summary = "발굴 크리에이터 목록 조회",
            description = "발굴 결과는 걸러내지 않고 모두 저장하므로, 브랜드 계정이나 구독자 미달 "
                    + "계정을 빼는 일은 이 API 의 조건으로 한다. 기준이 맞지 않으면 파라미터만 "
                    + "바꿔 다시 조회하면 되고 재수집은 필요 없다. 비워 둔 조건은 적용되지 않는다.")
    @GetMapping
    public ResponseEntity<Page<CreatorSummary>> search(

            @Parameter(description = "계정명 또는 플랫폼 계정 ID")
            @RequestParam(required = false) String keyword,

            @Parameter(description = "카테고리 코드", example = "BEAUTY")
            @RequestParam(required = false) String categoryCode,

            @Parameter(description = "SNS 코드", example = "YOUTUBE")
            @RequestParam(required = false) String snsCode,

            @Parameter(description = "최소 팔로워/구독자 수", example = "5000")
            @RequestParam(required = false) @Min(0) Long minFollower,

            @Parameter(description = "최대 팔로워/구독자 수", example = "100000")
            @RequestParam(required = false) @Min(0) Long maxFollower,

            @Parameter(description = "최소 ER", example = "2.5")
            @RequestParam(required = false) @DecimalMin("0") BigDecimal minEngagementRate,

            @Parameter(description = "최근 90일 최소 공개 콘텐츠 수 (0~25)", example = "3")
            @RequestParam(required = false) @Min(0)
            @Max(YoutubeDiscoveryProperties.MAX_FILTERABLE_RECENT_ACTIVITY_COUNT)
            Integer minRecent90DayContentCount,

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

        if (minFollower != null && maxFollower != null && minFollower > maxFollower) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT, "최소 팔로워/구독자 수는 최대값보다 클 수 없습니다.");
        }
        return ResponseEntity.ok(creatorDiscoveryService.search(
                keyword, categoryCode, snsCode, minFollower, maxFollower,
                minEngagementRate, minRecent90DayContentCount, maxBrandScore,
                minIgConfidence, activeWithinDays, pageable));
    }

    @Operation(summary = "발굴 크리에이터 기본 상세 조회",
            description = "크리에이터 계정 지표, 대표 카테고리, 카테고리별 발굴 비중과 "
                    + "브랜드·Instagram 판정 근거를 함께 조회한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "크리에이터 없음", content = @Content)
    })
    @GetMapping("/{creatorPoolId}")
    public ResponseEntity<CreatorDetailResponse> findDetail(
            @Parameter(description = "크리에이터 풀 ID", example = "113", required = true)
            @PathVariable Long creatorPoolId) {
        return ResponseEntity.ok(creatorDiscoveryService.findDetail(creatorPoolId));
    }

    @Operation(summary = "카테고리·플랫폼별 영향력 상위 N% 조회",
            description = "같은 카테고리·플랫폼의 최근 활동 계정을 대상으로 팔로워 40%, "
                    + "ER 40%, 최근성 20%의 백분위 점수를 계산한다. 브랜드 신호 점수가 "
                    + "2 이상인 계정은 후보에서 제외한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "요청 값 오류", content = @Content)
    })
    @GetMapping("/top-percent")
    public ResponseEntity<TopPercentInfluenceResponse> findTopPercent(
            @Parameter(description = "카테고리 코드", example = "BEAUTY", required = true)
            @RequestParam String categoryCode,
            @Parameter(description = "SNS 코드", example = "YOUTUBE", required = true)
            @RequestParam String snsCode,
            @Parameter(description = "상위 비율 (1~100)", example = "10", required = true)
            @RequestParam @Min(1) @Max(100) int topPercent,
            @Parameter(description = "최근 N일 안에 콘텐츠가 있는 계정만 후보로 포함 (1~3650)",
                    example = "90")
            @RequestParam(defaultValue = "90")
            @Min(1) @Max(3_650) int activeWithinDays) {
        return ResponseEntity.ok(
                creatorInfluenceService.findTopPercent(
                        categoryCode, snsCode, topPercent, activeWithinDays));
    }

    @Operation(summary = "카테고리별 일일 리포트 생성 후보 조회",
            description = "최근 활동 계정을 플랫폼별로 백분위 평가한다. 그 점수를 이용해 "
                    + "기준일에 발굴·갱신된 계정끼리 다시 정렬하고, 당일 대상 중 상위 N%에 "
                    + "카테고리 일일 최대 인원을 적용한다. 실제 리포트 생성은 하지 않는다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "요청 값 오류", content = @Content)
    })
    @GetMapping("/daily-report-candidates")
    public ResponseEntity<DailyReportCandidatesResponse> findDailyReportCandidates(
            @Parameter(description = "카테고리 코드", example = "BEAUTY", required = true)
            @RequestParam String categoryCode,
            @Parameter(description = "당일 발굴·갱신 대상 중 선정할 상위 비율 (1~100)",
                    example = "10")
            @RequestParam(defaultValue = "10")
            @Min(1) @Max(100) int topPercent,
            @Parameter(description = "최근 N일 안에 콘텐츠가 있는 계정만 비교 (1~3650)",
                    example = "90")
            @RequestParam(defaultValue = "90")
            @Min(1) @Max(3_650) int activeWithinDays,
            @Parameter(description = "카테고리당 하루 최대 선정 인원 (1~100)", example = "5")
            @RequestParam(defaultValue = "5")
            @Min(1) @Max(100) int dailyLimit,
            @Parameter(description = "선정 기준일. 생략하면 한국 시간 오늘", example = "2026-08-13")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate selectionDate) {
        return ResponseEntity.ok(creatorInfluenceService.findDailyReportCandidates(
                categoryCode, topPercent, activeWithinDays, dailyLimit, selectionDate));
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
