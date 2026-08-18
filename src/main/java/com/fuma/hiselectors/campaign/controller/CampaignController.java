package com.fuma.hiselectors.campaign.controller;

import com.fuma.hiselectors.campaign.dto.CampaignDetailResponse;
import com.fuma.hiselectors.campaign.dto.CampaignListResponse;
import com.fuma.hiselectors.campaign.service.CampaignClientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "캠페인", description = "진행 중이거나 예정된 캠페인 조회")
@RestController
@RequestMapping("/api/campaigns")
@RequiredArgsConstructor
public class CampaignController {

    private final CampaignClientService campaignClientService;

    @Operation(summary = "캠페인 목록 조회", description = "삭제되지 않은 캠페인 목록과 연결 상품을 최신순으로 조회한다.")
    @GetMapping
    public ResponseEntity<List<CampaignListResponse>> findAll() {
        return ResponseEntity.ok(campaignClientService.findAll());
    }

    @Operation(summary = "캠페인 상세 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "캠페인 없음", content = @Content)
    })
    @GetMapping("/{campaignId}")
    public ResponseEntity<CampaignDetailResponse> findOne(@PathVariable Long campaignId) {
        return ResponseEntity.ok(campaignClientService.findOne(campaignId));
    }
}
