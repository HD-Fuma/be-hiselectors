package com.fuma.hiselectors.content.controller;

import com.fuma.hiselectors.content.dto.ContentInspectionListItemResponse;
import com.fuma.hiselectors.content.dto.ContentDetailResponse;
import com.fuma.hiselectors.content.service.ContentDetailQueryService;
import com.fuma.hiselectors.content.service.ContentInspectionQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 현재 활성 기수의 콘텐츠 검수 목록을 조회하는 관리자 API. */
@Tag(name = "콘텐츠 검수", description = "활성 기수 콘텐츠 검수 조회 (관리자 전용)")
@RestController
@RequestMapping("/api/admin/contents")
@RequiredArgsConstructor
@Validated
public class ContentInspectionAdminController {

    private final ContentInspectionQueryService contentInspectionQueryService;
    private final ContentDetailQueryService contentDetailQueryService;

    @Operation(summary = "활성 기수 콘텐츠 검수 목록 조회",
            description = "현재 활성 기수의 삭제되지 않은 콘텐츠와 최신 버전을 "
                    + "최초 저장 시각 내림차순으로 반환한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "페이지 요청 값 오류", content = @Content),
            @ApiResponse(responseCode = "409", description = "현재 활성 기수 없음", content = @Content)
    })
    @GetMapping
    public ResponseEntity<Page<ContentInspectionListItemResponse>> getContents(
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "page는 0 이상이어야 합니다.") int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size는 1 이상이어야 합니다.")
            @Max(value = 100, message = "size는 100 이하여야 합니다.") int size) {
        return ResponseEntity.ok(
                contentInspectionQueryService.getCurrentGenerationContents(page, size));
    }

    @Operation(summary = "콘텐츠 최신 버전 상세 조회",
            description = "콘텐츠의 전체 버전 목록과 최신 버전의 본문, 리포트, 위반 사항을 조회합니다.")
    @GetMapping("/{contentId}")
    public ResponseEntity<ContentDetailResponse> getContentDetail(
            @PathVariable Long contentId) {
        return ResponseEntity.ok(contentDetailQueryService.getLatest(contentId));
    }

    @Operation(summary = "콘텐츠 특정 버전 상세 조회",
            description = "콘텐츠의 전체 버전 목록과 선택한 버전의 본문, 리포트, 위반 사항을 조회합니다.")
    @GetMapping("/{contentId}/versions/{contentVersionId}")
    public ResponseEntity<ContentDetailResponse> getContentVersionDetail(
            @PathVariable Long contentId,
            @PathVariable Long contentVersionId) {
        return ResponseEntity.ok(
                contentDetailQueryService.getVersion(contentId, contentVersionId));
    }
}
