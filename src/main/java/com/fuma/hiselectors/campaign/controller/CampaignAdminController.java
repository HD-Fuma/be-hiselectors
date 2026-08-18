package com.fuma.hiselectors.campaign.controller;

import com.fuma.hiselectors.campaign.dto.CampaignCreateRequest;
import com.fuma.hiselectors.campaign.dto.CampaignParticipantResponse;
import com.fuma.hiselectors.campaign.dto.CampaignResponse;
import com.fuma.hiselectors.campaign.dto.CampaignUpdateRequest;
import com.fuma.hiselectors.campaign.model.CampaignStatus;
import com.fuma.hiselectors.campaign.service.CampaignAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "관리자 캠페인", description = "캠페인 생성·조회·수정·삭제 및 참여 셀렉터스 조회 (관리자 전용)")
@RestController
@RequestMapping("/api/admin/campaigns")
@RequiredArgsConstructor
public class CampaignAdminController {

    private final CampaignAdminService campaignAdminService;

    @Operation(summary = "캠페인 목록 조회",
            description = "제목 또는 캠페인 ID, 기간, 현재 상태로 캠페인을 검색한다. 상태는 시작일·종료일과 오늘 날짜로 계산한다.")
    @GetMapping
    public ResponseEntity<Page<CampaignResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) CampaignStatus status,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(campaignAdminService.search(keyword, startDate, endDate, status, pageable));
    }

    @Operation(summary = "캠페인 상세 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "캠페인 없음", content = @Content)
    })
    @GetMapping("/{campaignId}")
    public ResponseEntity<CampaignResponse> findOne(@PathVariable Long campaignId) {
        return ResponseEntity.ok(campaignAdminService.findOne(campaignId));
    }

    @Operation(summary = "캠페인 참여 셀렉터스 조회",
            description = "캠페인 기간에 상품 그룹을 등록한 셀렉터스를 페이지 단위로 조회한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "캠페인 없음", content = @Content)
    })
    @GetMapping("/{campaignId}/participants")
    public ResponseEntity<Page<CampaignParticipantResponse>> findParticipants(@PathVariable Long campaignId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(campaignAdminService.findParticipants(campaignId, pageable));
    }

    @Operation(summary = "캠페인 생성",
            description = "캠페인 기본 정보와 연결할 판매 가능 상품을 등록한다. 상품 ID를 생략하면 상품 없이 생성한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "생성 성공"),
            @ApiResponse(responseCode = "400", description = "요청 값 또는 기간이 올바르지 않음", content = @Content),
            @ApiResponse(responseCode = "404", description = "상품 없음", content = @Content),
            @ApiResponse(responseCode = "409", description = "판매할 수 없는 상품 포함", content = @Content)
    })
    @PostMapping
    public ResponseEntity<CampaignResponse> create(@Valid @RequestBody CampaignCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(campaignAdminService.create(request));
    }

    @Operation(summary = "캠페인 수정",
            description = "null 인 필드는 변경하지 않는다. productIds를 전달하면 기존 연결 상품을 해당 목록으로 교체한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "400", description = "요청 값 또는 기간이 올바르지 않음", content = @Content),
            @ApiResponse(responseCode = "404", description = "캠페인 또는 상품 없음", content = @Content),
            @ApiResponse(responseCode = "409", description = "판매할 수 없는 상품 포함", content = @Content)
    })
    @PatchMapping("/{campaignId}")
    public ResponseEntity<CampaignResponse> update(@PathVariable Long campaignId,
                                                    @Valid @RequestBody CampaignUpdateRequest request) {
        return ResponseEntity.ok(campaignAdminService.update(campaignId, request));
    }

    @Operation(summary = "캠페인 삭제", description = "종료일이 지난 캠페인만 삭제 처리한다. 데이터는 soft delete 된다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제 성공"),
            @ApiResponse(responseCode = "404", description = "캠페인 없음", content = @Content),
            @ApiResponse(responseCode = "409", description = "종료되지 않은 캠페인", content = @Content)
    })
    @DeleteMapping("/{campaignId}")
    public ResponseEntity<Void> delete(@PathVariable Long campaignId) {
        campaignAdminService.delete(campaignId);
        return ResponseEntity.noContent().build();
    }
}
